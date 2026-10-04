package dev.jasper.app.platform;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.SwingUtilities;

/**
 * Jasper's notifications as the operating system shows them (spec section 9). One daemon worker hands each call to
 * the platform's backend, so a slow or broken backend never holds up the EDT; a backend failure is logged once and
 * never thrown. Text is plain: control characters, bidirectional controls and lone surrogates are dropped, the title is
 * one line of at most {@value #TITLE_LIMIT} characters and the body at most {@value #BODY_LIMIT}. A click on a
 * notification reaches the click handler on the EDT as the notification's id. Any thread; close at shutdown.
 */
public final class SystemNotifier implements AutoCloseable {
    /** The longest title the operating system is given, in characters. */
    public static final int TITLE_LIMIT = 120;
    /** The longest body the operating system is given, in characters. */
    public static final int BODY_LIMIT = 300;
    private static final System.Logger LOG = System.getLogger(SystemNotifier.class.getName());

    /** How serious a notification is, for a platform that shows it (the Windows balloon's icon). */
    public enum Level { INFO, WARNING, ERROR }

    /**
     * One notification as a backend receives it.
     *
     * @param id the notification's id as text, which a click hands back
     * @param level how serious it is
     * @param title one line, never blank
     * @param body plain text, may be empty
     */
    public record Message(String id, Level level, String title, String body) {
        public Message {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(body, "body");
        }
    }

    /**
     * What a platform does, as functions the worker runs.
     *
     * @param show shows a notification, replacing the one already shown for its id
     * @param withdraw removes one by id, where the platform can
     * @param withdrawAll removes every one Jasper showed, where the platform can
     * @param focused hears that Jasper is focused again
     * @param close releases what the platform holds; nothing runs after it
     */
    public record Backend(Consumer<Message> show, Consumer<String> withdraw, Runnable withdrawAll, Runnable focused,
                          Runnable close) {
        /** A platform that shows nothing. */
        public static final Backend NONE = new Backend(message -> { }, id -> { }, () -> { }, () -> { }, () -> { });

        public Backend {
            Objects.requireNonNull(show, "show");
            Objects.requireNonNull(withdraw, "withdraw");
            Objects.requireNonNull(withdrawAll, "withdrawAll");
            Objects.requireNonNull(focused, "focused");
            Objects.requireNonNull(close, "close");
        }
    }

    private final Executor worker;
    private final Runnable stopWorker;
    private final Backend backend;
    private volatile Consumer<UUID> clicks = id -> { };
    private volatile boolean closed;
    /** Worker-confined: whether a backend failure was logged already. */
    private boolean warned;

    private SystemNotifier(Executor worker, Runnable stopWorker, Function<Consumer<String>, Backend> backend) {
        this.worker = Objects.requireNonNull(worker, "worker");
        this.stopWorker = Objects.requireNonNull(stopWorker, "stopWorker");
        this.backend = Objects.requireNonNull(backend.apply(this::clicked), "backend");
    }

    /** A notifier that shows nothing and starts no thread: tests, and platforms without notifications. */
    public static SystemNotifier none() {
        return using(task -> { }, clicked -> Backend.NONE);
    }

    /**
     * A notifier over the backend {@code backend} builds from the function that reports clicks; {@code worker} runs
     * every call. For composition and tests.
     */
    public static SystemNotifier using(Executor worker, Function<Consumer<String>, Backend> backend) {
        return new SystemNotifier(worker, () -> { }, backend);
    }

    /** A notifier whose calls run on one daemon worker thread, stopped by {@link #close()}. */
    static SystemNotifier onWorker(Function<Consumer<String>, Backend> backend) {
        ExecutorService worker = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("jasper-system-notifications").factory());
        return new SystemNotifier(worker, worker::shutdown, backend);
    }

    /**
     * The notifier for this desktop: banners from a packaged macOS {@code Jasper.app}, {@code notify-send} on Linux,
     * and nothing elsewhere, including Gradle and IDE runs on macOS, which lack Jasper's bundle identity.
     *
     * @param osName the {@code os.name} property
     * @param dirs where Jasper keeps the icon {@code notify-send} shows
     */
    public static SystemNotifier forThisPlatform(String osName, AppDirs dirs) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (os.startsWith("mac"))
            return MacNotifications.libraryPath(System.getProperty("jpackage.app-path")) == null ? none() : onWorker(SystemNotifier::mac);
        if (os.startsWith("linux"))
            return onWorker(clicked -> LinuxNotifications.backend(dirs.notificationIcon(), clicked));
        return none();
    }

    /** macOS: the UserNotifications bridge, which hands a click's id to {@code clicked}. */
    static Backend mac(Consumer<String> clicked) {
        MacNotifications.onClick(clicked);
        return new Backend(message -> MacNotifications.send(message.id(), message.title(), message.body()),
            MacNotifications::withdraw, MacNotifications::withdrawAll, () -> { }, () -> { });
    }

    /** Where a click goes: the clicked notification's id, on the EDT. */
    public void onClick(Consumer<UUID> handler) {
        clicks = Objects.requireNonNull(handler, "handler");
    }

    /** Shows a notification, or replaces the one already shown for {@code id}. */
    public void send(UUID id, Level level, String title, String body) {
        var message = new Message(Objects.requireNonNull(id, "id").toString(), level, title(title), body(body));
        run(() -> backend.show().accept(message));
    }

    /** Removes the notification shown for {@code id}, where the platform can. */
    public void withdraw(UUID id) {
        String text = Objects.requireNonNull(id, "id").toString();
        run(() -> backend.withdraw().accept(text));
    }

    /** Removes every notification Jasper showed, where the platform can. */
    public void withdrawAll() { run(backend.withdrawAll()); }

    /** Jasper is focused again. */
    public void focused() { run(backend.focused()); }

    /** Whether this notifier shows nothing at all. */
    boolean silent() { return backend == Backend.NONE; }

    /** Lets the backend release what it holds, then stops the worker; nothing is shown afterwards. */
    @Override public synchronized void close() {
        if (closed) return;
        try {
            worker.execute(() -> contained(backend.close()));
        } catch (RejectedExecutionException stopped) {
            // Already shutting down.
        }
        closed = true;
        stopWorker.run();
    }

    private void run(Runnable task) {
        if (closed) return;
        try {
            worker.execute(() -> contained(task));
        } catch (RejectedExecutionException stopped) {
            // Shutting down: a missed notification is not worth reporting.
        }
    }

    private void contained(Runnable task) {
        try {
            task.run();
        } catch (RuntimeException | LinkageError failure) {
            // A missing native library or a broken notifier must never take Jasper down.
            if (!warned) {
                warned = true;
                LOG.log(System.Logger.Level.WARNING, "Could not show a system notification", failure);
            }
        }
    }

    /** A backend reports a click on a thread of its own: the handler hears it on the EDT. */
    private void clicked(String id) {
        if (closed || id == null) return;
        UUID parsed;
        try {
            parsed = UUID.fromString(id);
        } catch (IllegalArgumentException foreign) {
            return; // not one of Jasper's
        }
        SwingUtilities.invokeLater(() -> {
            if (!closed) clicks.accept(parsed);
        });
    }

    /** One line of plain text, at most {@value #TITLE_LIMIT} characters; "Jasper" when nothing is left. */
    static String title(String text) {
        String plain = plain(text, false);
        return plain.isEmpty() ? "Jasper" : capped(plain, TITLE_LIMIT);
    }

    /** Plain text that keeps its lines, at most {@value #BODY_LIMIT} characters. */
    static String body(String text) {
        return capped(plain(text, true), BODY_LIMIT);
    }

    /**
     * Drops control characters (keeping line breaks when {@code lines}), bidirectional controls, which can make text
     * read differently from what it is, and lone surrogates; tabs become spaces.
     */
    private static String plain(String text, boolean lines) {
        if (text == null) return "";
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        var out = new StringBuilder(normalized.length());
        normalized.codePoints().forEach(c -> {
            if (c == '\n') out.append(lines ? '\n' : ' ');
            else if (c == '\t') out.append(' ');
            else if (!Character.isISOControl(c) && !bidiControl(c) && Character.getType(c) != Character.SURROGATE)
                out.appendCodePoint(c);
        });
        return out.toString().strip();
    }

    private static boolean bidiControl(int c) {
        return c == 0x061C || c == 0x200E || c == 0x200F || (c >= 0x202A && c <= 0x202E) || (c >= 0x2066 && c <= 0x2069);
    }

    /** At most {@code limit} code points, the last an ellipsis when it was cut. */
    private static String capped(String text, int limit) {
        if (text.codePointCount(0, text.length()) <= limit) return text;
        return text.substring(0, text.offsetByCodePoints(0, limit - 1)) + "…";
    }
}