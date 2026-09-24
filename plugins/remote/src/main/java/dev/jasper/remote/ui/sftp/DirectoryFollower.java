package dev.jasper.remote.ui.sftp;

import dev.jasper.remote.client.ShellFolder;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Decides when to probe a followed pane's shell folder: after Enter, and when a view asks (focus, shown,
 * follow turned back on). One probe per pane at a time; OSC 7 reports end probing for that pane. UI-thread
 * state; probes complete elsewhere and hop back through {@code ui}.
 */
public final class DirectoryFollower implements AutoCloseable {
    public static final String UNSUPPORTED = "This host does not report the shell's folder";
    static final Duration ENTER_DELAY = Duration.ofMillis(250);
    static final int FAILURE_LIMIT = 3;

    private static final class Pane {
        final Supplier<CompletableFuture<Optional<String>>> probe;
        boolean running, again, stopped;
        int failures;
        String delivered;
        Runnable cancelDelay;
        Pane(Supplier<CompletableFuture<Optional<String>>> probe) { this.probe = probe; }
        void cancelDelay() { if (cancelDelay != null) { cancelDelay.run(); cancelDelay = null; } }
    }

    private final Executor ui;
    private final BiFunction<Duration, Runnable, Runnable> schedule;
    private final Predicate<UUID> wanted;
    private final BiConsumer<UUID, String> deliver, notice;
    private final Map<UUID, Pane> panes = new HashMap<>();
    private final Set<UUID> reported = new HashSet<>();
    private boolean closed;

    public DirectoryFollower(Executor ui, BiFunction<Duration, Runnable, Runnable> schedule, Predicate<UUID> wanted,
                             BiConsumer<UUID, String> deliver, BiConsumer<UUID, String> notice) {
        this.ui = ui; this.schedule = schedule; this.wanted = wanted; this.deliver = deliver; this.notice = notice;
    }

    /** Starts following {@code pane}, unless it has reported its own directory. */
    public synchronized void track(UUID pane, Supplier<CompletableFuture<Optional<String>>> probe) {
        if (closed || reported.contains(pane)) return;
        var prior = panes.put(pane, new Pane(probe));
        if (prior != null) prior.cancelDelay();
    }

    /** The pane sent a carriage return: probe after {@link #ENTER_DELAY}, restarting any pending delay. */
    public synchronized void enter(UUID pane) {
        var state = panes.get(pane);
        if (state == null || state.stopped) return;
        state.cancelDelay();
        state.cancelDelay = schedule.apply(ENTER_DELAY, () -> ui.execute(() -> fire(pane, state)));
    }

    /** A view wants the pane's folder now. */
    public synchronized void request(UUID pane) {
        var state = panes.get(pane);
        if (state != null) start(pane, state);
    }

    /** The pane sent OSC 7: its own reports win, and it is never probed again. */
    public synchronized void reported(UUID pane) {
        reported.add(pane);
        var state = panes.remove(pane);
        if (state != null) state.cancelDelay();
    }

    /** The pane closed. */
    public synchronized void forget(UUID pane) {
        reported.remove(pane);
        var state = panes.remove(pane);
        if (state != null) state.cancelDelay();
    }

    @Override public synchronized void close() {
        closed = true;
        for (Pane state : List.copyOf(panes.values())) state.cancelDelay();
        panes.clear(); reported.clear();
    }

    private synchronized void fire(UUID pane, Pane state) {
        state.cancelDelay = null;
        if (panes.get(pane) == state) start(pane, state);
    }

    private void start(UUID pane, Pane state) {
        if (!wanted.test(pane)) return;
        if (state.stopped) { notice.accept(pane, UNSUPPORTED); return; }
        if (state.running) { state.again = true; return; }
        state.running = true;
        CompletableFuture<Optional<String>> future;
        try { future = state.probe.get(); } catch (RuntimeException failure) { future = CompletableFuture.failedFuture(failure); }
        future.whenComplete((path, failure) -> ui.execute(() -> finish(pane, state, path, failure)));
    }

    private synchronized void finish(UUID pane, Pane state, Optional<String> path, Throwable failure) {
        state.running = false;
        if (panes.get(pane) != state) return;
        if (failure != null) {
            if (unwrap(failure) instanceof ShellFolder.Unsupported || ++state.failures >= FAILURE_LIMIT) {
                state.stopped = true; state.again = false;
                if (wanted.test(pane)) notice.accept(pane, UNSUPPORTED);
                return;
            }
        } else {
            state.failures = 0;
            path.filter(value -> value.startsWith("/")).filter(value -> !value.equals(state.delivered))
                .ifPresent(value -> { state.delivered = value; deliver.accept(pane, value); });
        }
        if (state.again) { state.again = false; start(pane, state); }
    }

    private static Throwable unwrap(Throwable failure) {
        while ((failure instanceof CompletionException || failure instanceof ExecutionException) && failure.getCause() != null) failure = failure.getCause();
        return failure;
    }
}
