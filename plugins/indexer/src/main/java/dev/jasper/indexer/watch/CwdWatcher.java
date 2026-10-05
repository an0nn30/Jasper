package dev.jasper.indexer.watch;

import dev.jasper.indexer.IndexerSettings;
import dev.jasper.indexer.detect.Detection;
import dev.jasper.indexer.index.Indexer;
import dev.jasper.indexer.index.Project;
import dev.jasper.indexer.index.ProjectKey;
import dev.jasper.indexer.index.Projects;
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
import java.util.regex.Pattern;

/**
 * Turns working-directory reports into "Index project?" cards and refreshes, and finished commands into refreshes.
 * Keeps this session's declined roots and open cards. UI thread.
 */
public final class CwdWatcher implements AutoCloseable {
    /** How long after the last finished command a project is refreshed. */
    public static final Duration DEBOUNCE = Duration.ofSeconds(3);
    private static final Pattern REMOTE_ENTRY = Pattern.compile("[0-9a-fA-F-]{36}:/.*");

    /**
     * What the watcher asks of the plugin.
     *
     * @param detect finds a local folder's project root off the UI thread, completing on the UI thread
     * @param windowOf the window a pane is in
     * @param after runs a task on the UI thread after a delay; returns its cancel
     * @param home the user's home folder
     */
    public record Hooks(Function<Path, CompletionStage<Optional<Detection<Path>>>> detect, Function<UUID, Optional<WindowHandle>> windowOf,
                        BiFunction<Duration, Runnable, Runnable> after, Path home) {
        /** Rejects nulls. */
        public Hooks {
            Objects.requireNonNull(detect); Objects.requireNonNull(windowOf); Objects.requireNonNull(after); Objects.requireNonNull(home);
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
        event.workingDirectory().filter(Path::isAbsolute).ifPresent(folder -> local(event.paneId(), folder.normalize()));
    }

    /** @param event a command finished */
    public void finished(TerminalEvents.CommandFinished event) {
        if (closed) return;
        event.workingDirectory().filter(Path::isAbsolute).flatMap(folder -> projects.containing(folder.normalize()))
            .map(entry -> entry.project().key()).ifPresent(this::debounce);
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
        if (!settings.prompt.get() || ignored(settings.ignored.get(), folder) || withinLocal(declined, folder)
            || withinLocal(liveCards(), folder)) return;
        hooks.detect().apply(folder).thenAccept(found -> found.ifPresent(detection -> offer(paneId, detection)));
    }

    private void offer(UUID paneId, Detection<Path> detection) {
        if (closed || !settings.prompt.get()) return;
        Path root = detection.root();
        ProjectKey key = ProjectKey.local(root);
        if (projects.get(key).isPresent() || declined.contains(key) || ignored(settings.ignored.get(), root)) return;
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
        NotificationSpec spec = card(name, display(root, hooks.home()), detection.marker().kind(), Optional.empty(), window,
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
                ignore(root.toString(), name);
            });
        cards.put(key, prompts.post(spec));
    }

    private void ignore(String entry, String name) {
        var next = new ArrayList<>(settings.ignored.get());
        if (!next.contains(entry)) next.add(entry);
        settings.ignored.set(next).whenComplete((ignored, failure) -> {
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

    private Set<ProjectKey> liveCards() {
        cards.entrySet().removeIf(entry -> {
            if (!entry.getValue().expired()) return false;
            declined.add(entry.getKey());
            return true;
        });
        return cards.keySet();
    }

    private static boolean withinLocal(Set<ProjectKey> roots, Path folder) { return roots.stream().anyMatch(root -> root.contains(folder)); }

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
}
