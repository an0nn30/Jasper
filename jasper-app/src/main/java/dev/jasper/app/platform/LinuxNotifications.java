package dev.jasper.app.platform;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Linux notifications through {@code notify-send} (spec section 9), found on {@code PATH} once. Arguments go straight
 * to the process, never through a shell, and the body's markup characters are escaped. When {@code notify-send --help}
 * lists {@code --action}, each notification waits for a click with {@code --action=default=Open --wait}, which prints
 * {@code default} when it gets one; at most {@value #MAX_WAITING} wait at once, the oldest killed to make room, and a
 * notification read in Jasper, or Jasper shutting down, kills its process. Without {@code --action} a notification is
 * fire-and-forget; without {@code notify-send} there are none, which is logged once. Calls come from the system
 * notifier's worker and exits from other threads, so the state is guarded by this object's lock.
 */
final class LinuxNotifications {
    static final int MAX_WAITING = 8;
    static final Duration PROBE_TIMEOUT = Duration.ofSeconds(2);
    private static final String ICON_RESOURCE = "/dev/jasper/app/icons/app/linux/icon-128.png";
    private static final System.Logger LOG = System.getLogger(LinuxNotifications.class.getName());

    private record Waiting(String id, Process process) {}

    private final Supplier<Optional<Path>> locate;
    private final Function<List<String>, Process> start;
    private final Supplier<Optional<Path>> icon;
    private final Consumer<String> clicked;
    private final ArrayDeque<Waiting> waiting = new ArrayDeque<>();
    private boolean resolved;
    private Path program;
    private boolean actions;
    private Optional<Path> iconPath = Optional.empty();
    private boolean closed;

    /**
     * @param locate finds {@code notify-send}, asked once
     * @param start starts a process from its arguments, the program first; throws when it cannot
     * @param icon writes Jasper's icon where {@code notify-send} can read it, asked once
     * @param clicked hears a clicked notification's id, on the thread that saw its process exit
     */
    LinuxNotifications(Supplier<Optional<Path>> locate, Function<List<String>, Process> start,
                       Supplier<Optional<Path>> icon, Consumer<String> clicked) {
        this.locate = Objects.requireNonNull(locate, "locate");
        this.start = Objects.requireNonNull(start, "start");
        this.icon = Objects.requireNonNull(icon, "icon");
        this.clicked = Objects.requireNonNull(clicked, "clicked");
    }

    /** This desktop's backend: {@code notify-send} on {@code PATH}, with Jasper's icon written to {@code iconFile}. */
    static SystemNotifier.Backend backend(Path iconFile, Consumer<String> clicked) {
        var linux = new LinuxNotifications(() -> onPath(System.getenv("PATH")), LinuxNotifications::launch,
            () -> iconIn(iconFile), clicked);
        return new SystemNotifier.Backend(linux::show, linux::withdraw, linux::withdrawAll, () -> { }, linux::close);
    }

    synchronized void show(SystemNotifier.Message message) {
        if (closed || !resolve()) return;
        Process process = start.apply(arguments(message));
        if (!actions) return;
        if (waiting.size() >= MAX_WAITING) waiting.removeFirst().process().destroy();
        var entry = new Waiting(message.id(), process);
        waiting.addLast(entry);
        process.onExit().thenAccept(done -> finished(entry));
    }

    synchronized void withdraw(String id) {
        var ended = new ArrayList<Waiting>();
        for (Waiting entry : waiting) if (entry.id().equals(id)) ended.add(entry);
        waiting.removeAll(ended);
        ended.forEach(entry -> entry.process().destroy());
    }

    synchronized void withdrawAll() {
        var ended = List.copyOf(waiting);
        waiting.clear();
        ended.forEach(entry -> entry.process().destroy());
    }

    synchronized void close() {
        closed = true;
        withdrawAll();
    }

    /** A waiting process ended: a click when it printed {@code default}; nothing when Jasper killed it. */
    private void finished(Waiting entry) {
        synchronized (this) {
            if (!waiting.remove(entry)) return; // withdrawn, crowded out, or shut down
        }
        String printed;
        try (InputStream output = entry.process().getInputStream()) {
            printed = new String(output.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException unreadable) {
            return;
        }
        if (printed.equals("default")) clicked.accept(entry.id());
    }

    /** Finds {@code notify-send} and asks it about {@code --action}, once. */
    private boolean resolve() {
        if (!resolved) {
            resolved = true;
            program = locate.get().orElse(null);
            if (program == null) {
                LOG.log(System.Logger.Level.INFO, "notify-send is not on PATH, so Jasper shows no system notifications");
                return false;
            }
            actions = supportsActions(program);
            iconPath = icon.get();
        }
        return program != null;
    }

    private boolean supportsActions(Path program) {
        Process probe;
        try {
            probe = start.apply(List.of(program.toString(), "--help"));
        } catch (RuntimeException failure) {
            return false;
        }
        try {
            if (!probe.waitFor(PROBE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                probe.destroyForcibly();
                return false;
            }
            try (InputStream output = probe.getInputStream()) {
                return new String(output.readAllBytes(), StandardCharsets.UTF_8).contains("--action");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            probe.destroyForcibly();
            return false;
        } catch (IOException unreadable) {
            return false;
        }
    }

    private List<String> arguments(SystemNotifier.Message message) {
        var arguments = new ArrayList<String>();
        arguments.add(program.toString());
        arguments.add("--app-name=Jasper");
        iconPath.ifPresent(path -> arguments.add("--icon=" + path));
        if (actions) {
            arguments.add("--action=default=Open");
            arguments.add("--wait");
        }
        arguments.add("--");
        arguments.add(message.title());
        arguments.add(markupEscaped(message.body()));
        return arguments;
    }

    /** Notification servers read the body as markup; Jasper's text is plain. */
    static String markupEscaped(String body) {
        return body.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** The first {@code notify-send} on {@code path} that is an executable file. */
    static Optional<Path> onPath(String path) {
        if (path == null) return Optional.empty();
        for (String directory : path.split(java.io.File.pathSeparator)) {
            if (directory.isBlank()) continue;
            Path candidate;
            try {
                candidate = Path.of(directory, "notify-send");
            } catch (InvalidPathException unusable) {
                continue;
            }
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    /** Writes Jasper's 128-pixel icon to {@code target}; empty when it cannot, so the notification goes without one. */
    static Optional<Path> iconIn(Path target) {
        try (InputStream png = LinuxNotifications.class.getResourceAsStream(ICON_RESOURCE)) {
            if (png == null) return Optional.empty();
            Path parent = target.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.copy(png, target, StandardCopyOption.REPLACE_EXISTING);
            return Optional.of(target);
        } catch (IOException | RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Could not write the notification icon", failure);
            return Optional.empty();
        }
    }

    private static Process launch(List<String> arguments) {
        try {
            return new ProcessBuilder(arguments).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
