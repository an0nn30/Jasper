package dev.jasper.indexer.index;

import dev.jasper.sdk.notifications.NotificationAction;
import dev.jasper.sdk.notifications.NotificationGroup;
import dev.jasper.sdk.notifications.NotificationSpec;
import dev.jasper.sdk.notifications.Severity;
import dev.jasper.sdk.terminal.WindowHandle;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Runs listing jobs and keeps {@link Projects} and the store in step. Jobs on one host (a lane: this machine, or one
 * saved host) run one at a time; one job per project runs, with at most one merged follow-up behind it. UI thread;
 * listings run wherever the lister puts them, snapshots are built and saved on {@code io}, and results come back on
 * {@code ui}.
 */
public final class Indexer implements AutoCloseable {
    private static final String LOCAL_LANE = "local";

    /** One run of a project's lister. */
    public static final class Job {
        private final ProjectKey key;
        private final boolean foreground;
        private final Optional<WindowHandle> window;
        private volatile boolean cancelled;

        Job(ProjectKey key, boolean foreground, Optional<WindowHandle> window) {
            this.key = key;
            this.foreground = foreground;
            this.window = window;
        }

        /** @return the project */
        public ProjectKey key() { return key; }
        /** @return whether the user asked for it, so it shows progress and may ask the user to sign in */
        public boolean foreground() { return foreground; }
        /** @return the window the user acted in, for a foreground job */
        public Optional<WindowHandle> window() { return window; }
        /** @return whether the user cancelled it; safe from any thread */
        public boolean cancelled() { return cancelled; }

        void cancel() { cancelled = true; }

        Job merge(Job later) { return new Job(key, foreground || later.foreground, later.window.or(() -> window)); }
    }

    private record Loaded(Project project, Optional<ProjectIndex> index) { }

    private final Projects projects;
    private final IndexStore store;
    private final Function<Job, CompletionStage<Listing>> lister;
    private final Executor io;
    private final Executor ui;
    private final Supplier<Instant> clock;
    private final Consumer<Optional<String>> progress;
    private final NotificationGroup results;
    private final NotificationGroup failures;
    private final System.Logger log;
    private final Map<ProjectKey, Job> running = new HashMap<>();
    private final Map<ProjectKey, Job> queued = new HashMap<>();
    private final Map<String, CompletableFuture<Void>> lanes = new HashMap<>();
    private final Map<ProjectKey, Instant> attempted = new HashMap<>();
    private final Set<ProjectKey> warnedTruncated = new HashSet<>();
    private CompletableFuture<Void> saves = CompletableFuture.completedFuture(null);
    private boolean closed;

    /**
     * @param projects the model this keeps current
     * @param store where projects and snapshots are saved
     * @param lister starts a job's listing; called on the UI thread, may complete on any thread
     * @param io runs saves and loads
     * @param ui the UI thread
     * @param clock the current time
     * @param progress shown text while a foreground job runs, empty when none does; called on the UI thread
     * @param results where finished foreground jobs are reported
     * @param failures where failures and truncation are reported
     * @param log where unexpected storage failures go
     */
    public Indexer(Projects projects, IndexStore store, Function<Job, CompletionStage<Listing>> lister, Executor io, Executor ui,
                   Supplier<Instant> clock, Consumer<Optional<String>> progress, NotificationGroup results, NotificationGroup failures,
                   System.Logger log) {
        this.projects = projects; this.store = store; this.lister = lister; this.io = io; this.ui = ui; this.clock = clock;
        this.progress = progress; this.results = results; this.failures = failures; this.log = log;
    }

    /**
     * Reads the saved projects and snapshots in the background.
     *
     * @return completes on the UI thread once they are in {@link Projects}
     */
    public CompletableFuture<Void> load() {
        var done = new CompletableFuture<Void>();
        io.execute(() -> {
            List<Project> saved;
            try { saved = store.loadProjects(); }
            catch (IOException unreadable) {
                log.log(System.Logger.Level.WARNING, "Project Index could not read its project list", unreadable);
                saved = List.of();
            }
            var loaded = new ArrayList<Loaded>();
            for (Project project : saved) loaded.add(new Loaded(project, store.loadIndex(project.key())));
            ui.execute(() -> {
                if (!closed) for (Loaded entry : loaded) {
                    projects.loaded(entry.project(), entry.index());
                    entry.index().ifPresent(index -> attempted.put(index.key(), index.indexedAt()));
                }
                done.complete(null);
            });
        });
        return done;
    }

    /** @param project a project to register and save; nothing happens when it is known */
    public void add(Project project) {
        if (closed || projects.get(project.key()).isPresent()) return;
        projects.add(project);
        persist();
    }

    /** @param key a project to forget: its job is cancelled, its snapshot deleted and the list saved */
    public void remove(ProjectKey key) {
        Optional.ofNullable(running.get(key)).ifPresent(Job::cancel);
        queued.remove(key);
        attempted.remove(key);
        warnedTruncated.remove(key);
        projects.remove(key);
        persist();
        io.execute(() -> deleteQuietly(key));
    }

    /**
     * Indexes a project now, or right after the job already running for it.
     *
     * @param key a registered project
     * @param foreground whether the user asked
     * @param window the window the user acted in
     */
    public void request(ProjectKey key, boolean foreground, Optional<WindowHandle> window) {
        if (closed || projects.get(key).isEmpty()) return;
        var job = new Job(key, foreground, window);
        if (running.containsKey(key)) {
            queued.merge(key, job, Job::merge);
            return;
        }
        start(job);
    }

    /**
     * A background refresh, when the last attempt is at least {@code minAge} old and no job is running.
     *
     * @param key a registered project
     * @param minAge the throttle
     */
    public void refreshIfDue(ProjectKey key, Duration minAge) {
        if (closed || projects.get(key).isEmpty() || running.containsKey(key)) return;
        Instant last = attempted.get(key);
        if (last != null && last.plus(minAge).isAfter(clock.get())) return;
        request(key, false, Optional.empty());
    }

    /** Cancels running foreground jobs and drops queued foreground follow-ups. */
    public void cancelForeground() {
        running.values().stream().filter(Job::foreground).forEach(Job::cancel);
        queued.values().removeIf(Job::foreground);
    }

    /**
     * @param key a project
     * @return whether a job for it is running or waiting in its lane
     */
    public boolean running(ProjectKey key) { return running.containsKey(key); }

    /** Cancels everything and stops reporting; called when the plugin stops. */
    @Override public void close() {
        if (closed) return;
        closed = true;
        running.values().forEach(Job::cancel);
        queued.clear();
        progress.accept(Optional.empty());
    }

    private void start(Job job) {
        running.put(job.key(), job);
        attempted.put(job.key(), clock.get());
        projects.indexing(job.key(), true);
        showProgress();
        String lane = job.key().host().map(UUID::toString).orElse(LOCAL_LANE);
        CompletableFuture<Void> previous = lanes.getOrDefault(lane, CompletableFuture.completedFuture(null));
        lanes.put(lane, previous.thenCompose(ignored -> run(job)));
    }

    private CompletableFuture<Void> run(Job job) {
        var finished = new CompletableFuture<Void>();
        CompletionStage<Listing> listing;
        if (job.cancelled()) listing = CompletableFuture.failedFuture(new CancellationException());
        else {
            try { listing = lister.apply(job); }
            catch (RuntimeException failure) { listing = CompletableFuture.failedFuture(failure); }
        }
        listing.toCompletableFuture()
            .thenApplyAsync(found -> save(job.key(), found), io)
            .whenComplete((index, failure) -> ui.execute(() -> {
                try { finish(job, index, failure); }
                finally { finished.complete(null); }
            }));
        return finished;
    }

    private ProjectIndex save(ProjectKey key, Listing listing) {
        var index = new ProjectIndex(key, listing.files(), listing.strategy(), clock.get(), listing.truncated());
        try { store.saveIndex(index); }
        catch (IOException failed) { log.log(System.Logger.Level.WARNING, "Project Index could not save " + key.id(), failed); }
        return index;
    }

    private void finish(Job job, ProjectIndex index, Throwable failure) {
        running.remove(job.key());
        if (closed) return;
        Optional<Project> project = projects.get(job.key()).map(Projects.Entry::project);
        if (project.isEmpty()) io.execute(() -> deleteQuietly(job.key()));
        else report(job, project.get().name(), index, unwrap(failure));
        Job next = queued.remove(job.key());
        if (next != null) start(next);
        showProgress();
    }

    private void report(Job job, String name, ProjectIndex index, Throwable cause) {
        projects.indexing(job.key(), false);
        if (cause == null) {
            projects.indexed(job.key(), index);
            if (job.foreground()) results.info("Indexed " + name, count(index.size()));
            if (index.truncated() && warnedTruncated.add(job.key())) failures.post(withWindow(job,
                NotificationSpec.of(Severity.WARNING, name + " has more than " + count(index.size()))
                    .withContent("Only the first " + count(index.size()) + " were indexed. Add folders you don't need to "
                        + "Excluded folders in Settings › Plugins › Project Index, or raise the limit there.")));
        } else if (cause instanceof CancellationException) {
            // the previous snapshot stays
        } else if (cause instanceof NoSuchFileException) {
            projects.outcome(job.key(), Projects.Outcome.MISSING);
            if (job.foreground()) failures.error("Couldn't index " + name, "The folder no longer exists: " + job.key().root());
        } else {
            projects.outcome(job.key(), Projects.Outcome.FAILED);
            failures.post(withWindow(job, NotificationSpec.of(Severity.ERROR, "Couldn't index " + name)
                .withContent(message(cause)).withCause(cause)
                .withAction(NotificationAction.of("Retry", () -> request(job.key(), true, job.window())))));
        }
    }

    private void showProgress() {
        Optional<String> text = running.values().stream().filter(Job::foreground).findFirst()
            .flatMap(job -> projects.get(job.key())).map(entry -> "Indexing " + entry.project().name() + "…");
        progress.accept(text);
    }

    private void persist() {
        List<Project> snapshot = projects.entries().stream().map(Projects.Entry::project).toList();
        saves = saves.thenRunAsync(() -> {
            try { store.saveProjects(snapshot); }
            catch (IOException failed) { log.log(System.Logger.Level.WARNING, "Project Index could not save its project list", failed); }
        }, io);
    }

    private void deleteQuietly(ProjectKey key) {
        try { store.deleteIndex(key); }
        catch (IOException failed) { log.log(System.Logger.Level.WARNING, "Project Index could not delete " + key.id(), failed); }
    }

    private static NotificationSpec withWindow(Job job, NotificationSpec spec) { return job.window().map(spec::withWindow).orElse(spec); }

    private static Throwable unwrap(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) cause = cause.getCause();
        return cause;
    }

    private static String message(Throwable cause) {
        String text = cause.getMessage();
        return text == null || text.isBlank() ? cause.getClass().getSimpleName() : text;
    }

    static String count(int files) { return String.format(Locale.ROOT, "%,d %s", files, files == 1 ? "file" : "files"); }
}
