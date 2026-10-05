package dev.jasper.indexer.watch;

import dev.jasper.indexer.IndexerSettings;
import dev.jasper.indexer.detect.Detection;
import dev.jasper.indexer.index.Indexer;
import dev.jasper.indexer.index.Project;
import dev.jasper.indexer.index.ProjectKey;
import dev.jasper.indexer.index.Projects;
import dev.jasper.indexer.index.RemotePlace;
import dev.jasper.sdk.notifications.NotificationAction;
import dev.jasper.sdk.notifications.NotificationGroup;
import dev.jasper.sdk.notifications.NotificationHandle;
import dev.jasper.sdk.notifications.NotificationSpec;
import dev.jasper.sdk.notifications.Severity;
import dev.jasper.sdk.terminal.TerminalEvents;
import dev.jasper.sdk.terminal.WindowHandle;
import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Turns working-directory reports, local and remote, into "Index project?" cards and refreshes, and finished commands
 * into refreshes. Keeps this session's declined roots and open cards. UI thread.
 */
public final class CwdWatcher implements AutoCloseable {
    /** How long after the last finished command a project is refreshed. */
    public static final Duration DEBOUNCE = Duration.ofSeconds(3);
    private static final Pattern REMOTE_ENTRY = Pattern.compile("([0-9a-fA-F-]{36}):(/.*)");

    /**
     * What the watcher asks of the plugin.
     *
     * @param detect finds a local folder's project root off the UI thread, completing on the UI thread
     * @param locate where an SSH pane is, only while its host is connected
     * @param detectRemote finds a remote folder's project root, completing on the UI thread
     * @param windowOf the window a pane is in
     * @param after runs a task on the UI thread after a delay; returns its cancel
     * @param home the user's home folder
     */
    public record Hooks(Function<Path, CompletionStage<Optional<Detection<Path>>>> detect, Function<UUID, Optional<RemotePlace>> locate,
                        Function<RemotePlace, CompletionStage<Optional<Detection<String>>>> detectRemote,
                        Function<UUID, Optional<WindowHandle>> windowOf, BiFunction<Duration, Runnable, Runnable> after, Path home) {
        /** Rejects nulls. */
        public Hooks {
            Objects.requireNonNull(detect); Objects.requireNonNull(locate); Objects.requireNonNull(detectRemote);
            Objects.requireNonNull(windowOf); Objects.requireNonNull(after); Objects.requireNonNull(home);
        }
    }

    private final Projects projects;
    private final Indexer indexer;
    private final IndexerSettings settings;
    private final NotificationGroup prompts;
    private final Hooks hooks;
    private final Set<ProjectKey> declined = new HashSet<>();
    private final Map<ProjectKey, NotificationHandle> cards = new HashMap<>();
    private final Map<ProjectKey, Runnable> pending = new HashMap<>();
    private boolean closed;

    /**
     * @param projects the indexed projects
     * @param indexer runs indexing and refreshes
     * @param settings the plugin's settings
     * @param prompts the "Project indexing" group
     * @param hooks what the watcher asks of the plugin
     */
    public CwdWatcher(Projects projects, Indexer indexer, IndexerSettings settings, NotificationGroup prompts, Hooks hooks) {
        this.projects = projects; this.indexer = indexer; this.settings = settings; this.prompts = prompts; this.hooks = hooks;
    }

    /** @param event a shell reported its working directory */
    public void cwd(TerminalEvents.CwdChanged event) {
        if (closed) return;
        Optional<Path> local = event.workingDirectory().filter(Path::isAbsolute);
        if (local.isPresent()) local(event.paneId(), local.get().normalize());
        else if (event.remoteDirectory().isPresent()) hooks.locate().apply(event.paneId()).ifPresent(place -> remote(event.paneId(), place));
    }

    /** @param event a command finished */
    public void finished(TerminalEvents.CommandFinished event) {
        if (closed) return;
        Optional<Path> local = event.workingDirectory().filter(Path::isAbsolute);
        Optional<Projects.Entry> inside = local.isPresent() ? projects.containing(local.get().normalize())
            : event.remoteDirectory().isPresent()
                ? hooks.locate().apply(event.paneId()).flatMap(place -> projects.containing(place.host(), place.folder()))
                : Optional.empty();
        inside.map(entry -> entry.project().key()).ifPresent(this::debounce);
    }

    /** Cancels pending refreshes and withdraws open cards. */
    @Override public void close() {
        closed = true;
        pending.values().forEach(Runnable::run);
        pending.clear();
        cards.values().forEach(NotificationHandle::expire);
        cards.clear();
    }

    private void local(UUID paneId, Path folder) {
        Optional<Projects.Entry> inside = projects.containing(folder);
        if (inside.isPresent()) {
            indexer.refreshIfDue(inside.get().project().key(), refreshAge());
            return;
        }
        if (!settings.prompt.get() || ignored(settings.ignored.get(), folder) || covered(key -> key.contains(folder))) return;
        hooks.detect().apply(folder).thenAccept(found -> found.ifPresent(detection -> {
            ProjectKey key = ProjectKey.local(detection.root());
            offer(paneId, key, display(detection.root(), hooks.home()), detection.marker().kind(), Optional.empty(),
                detection.root().toString());
        }));
    }

    private void remote(UUID paneId, RemotePlace place) {
        Optional<Projects.Entry> inside = projects.containing(place.host(), place.folder());
        if (inside.isPresent()) {
            indexer.refreshIfDue(inside.get().project().key(), refreshAge());
            return;
        }
        if (!settings.prompt.get() || ignoredRemote(settings.ignored.get(), place.host(), place.folder())
            || covered(key -> key.contains(place.host(), place.folder()))) return;
        hooks.detectRemote().apply(place).thenAccept(found -> found.ifPresent(detection -> {
            ProjectKey key = ProjectKey.remote(place.host(), detection.root());
            offer(paneId, key, detection.root(), detection.marker().kind(), Optional.of(place.hostName()), place.host() + ":" + detection.root());
        }));
    }

    /** Whether a declined root or one with an open card covers a folder. */
    private boolean covered(Predicate<ProjectKey> contains) {
        cards.entrySet().removeIf(entry -> {
            if (!entry.getValue().expired()) return false;
            declined.add(entry.getKey());
            return true;
        });
        return declined.stream().anyMatch(contains) || cards.keySet().stream().anyMatch(contains);
    }

    private void offer(UUID paneId, ProjectKey key, String location, String kind, Optional<String> host, String ignoreEntry) {
        if (closed || !settings.prompt.get()) return;
        if (projects.get(key).isPresent() || declined.contains(key) || ignoredKey(key)) return;
        NotificationHandle open = cards.get(key);
        if (open != null) {
            if (open.expired()) {
                cards.remove(key);
                declined.add(key);
            }
            return;
        }
        Optional<WindowHandle> window = hooks.windowOf().apply(paneId);
        String name = key.name();
        NotificationSpec spec = card(name, location, kind, host, window,
            () -> {
                cards.remove(key);
                indexer.add(new Project(key, name, false));
                indexer.request(key, true, window);
            },
            () -> {
                cards.remove(key);
                declined.add(key);
            },
            () -> {
                cards.remove(key);
                ignore(ignoreEntry, name);
            });
        cards.put(key, prompts.post(spec));
    }

    private boolean ignoredKey(ProjectKey key) {
        List<String> entries = settings.ignored.get();
        return key.host().map(host -> ignoredRemote(entries, host, key.root())).orElseGet(() -> ignored(entries, key.path()));
    }

    private void ignore(String entry, String name) {
        var next = new ArrayList<>(settings.ignored.get());
        if (!next.contains(entry)) next.add(entry);
        settings.ignored.set(next).whenComplete((done, failure) -> {
            if (failure != null) prompts.error("Could not ignore " + name, failure);
        });
    }

    private void debounce(ProjectKey key) {
        Runnable previous = pending.remove(key);
        if (previous != null) previous.run();
        pending.put(key, hooks.after().apply(DEBOUNCE, () -> {
            pending.remove(key);
            if (!closed) indexer.refreshIfDue(key, refreshAge());
        }));
    }

    private Duration refreshAge() { return Duration.ofMinutes(settings.refreshMinutes.get()); }

    /**
     * The card that asks whether to index a project.
     *
     * @param name the project's name
     * @param location where it is, as shown
     * @param kind what kind of project it looks like
     * @param host the remote host's name, for a remote project
     * @param window the window to show it in
     * @param yes runs on Yes
     * @param no runs on No
     * @param ignore runs on Ignore this path
     * @return the card
     */
    static NotificationSpec card(String name, String location, String kind, Optional<String> host, Optional<WindowHandle> window,
                                 Runnable yes, Runnable no, Runnable ignore) {
        String content = name + " · " + location + "\n" + kind + host.map(found -> "\non " + found).orElse("");
        NotificationSpec spec = NotificationSpec.of(Severity.INFO, "Index project?").withContent(content)
            .withAction(NotificationAction.of("Yes", yes))
            .withAction(NotificationAction.of("No", no))
            .withAction(NotificationAction.of("Ignore this path", ignore));
        return window.map(spec::withWindow).orElse(spec);
    }

    /**
     * @param folder a local folder
     * @param home the user's home folder
     * @return the folder, with the home folder written as {@code ~}
     */
    static String display(Path folder, Path home) {
        if (!folder.startsWith(home)) return folder.toString();
        Path rest = home.relativize(folder);
        return rest.toString().isEmpty() ? "~" : "~" + File.separator + rest;
    }

    /**
     * @param entries the {@code ignored} setting
     * @param folder a local folder
     * @return whether it is an ignored folder or inside one; remote and relative entries never match
     */
    static boolean ignored(List<String> entries, Path folder) {
        for (String entry : entries) {
            if (REMOTE_ENTRY.matcher(entry).matches()) continue;
            try {
                Path path = Path.of(entry);
                if (path.isAbsolute() && folder.startsWith(path.normalize())) return true;
            } catch (InvalidPathException malformed) {
                // not a local path
            }
        }
        return false;
    }

    /**
     * @param entries the {@code ignored} setting
     * @param host a saved host
     * @param folder an absolute folder on it
     * @return whether a {@code <host id>:<path>} entry covers the folder
     */
    static boolean ignoredRemote(List<String> entries, UUID host, String folder) {
        for (String entry : entries) {
            var match = REMOTE_ENTRY.matcher(entry);
            if (!match.matches() || !match.group(1).equalsIgnoreCase(host.toString())) continue;
            String path = match.group(2);
            String prefix = path.endsWith("/") ? path : path + "/";
            if (folder.equals(path) || folder.startsWith(prefix)) return true;
        }
        return false;
    }
}
