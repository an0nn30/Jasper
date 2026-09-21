package dev.jasper.app.application;

import dev.jasper.terminal.session.TerminalSession;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/** Owns launch admission and exit tracking across worker/EDT races; panes own admitted-session close. */
final class SessionLaunchCoordinator implements Executor, AutoCloseable {
    private final Object lock = new Object();
    private final ExecutorService executor;
    private final Set<TerminalSession> sessions = new LinkedHashSet<>();
    private final CompletableFuture<Void> drained = new CompletableFuture<>();
    private int inFlight;
    private boolean closed;

    SessionLaunchCoordinator(ExecutorService executor) { this.executor = Objects.requireNonNull(executor); }

    @Override public void execute(Runnable launch) {
        synchronized (lock) {
            if (closed) throw new RejectedExecutionException("Application launch owner is closed");
            Objects.requireNonNull(launch);
            inFlight++;
            try {
                executor.execute(() -> {
                    try { launch.run(); }
                    finally { synchronized (lock) { inFlight--; completeDrain(); } }
                });
            } catch (RuntimeException | Error failure) {
                inFlight--; completeDrain(); throw failure;
            }
        }
    }

    /** Tracks an acquired session until exit, closing it immediately if shutdown won the race. */
    TerminalSession track(TerminalSession session) {
        Objects.requireNonNull(session);
        synchronized (lock) {
            sessions.add(session);
            session.exitFuture().whenComplete((code, failure) -> {
                synchronized (lock) { sessions.remove(session); completeDrain(); }
            });
            if (!closed) return session;
        }
        session.close();
        return session;
    }

    /** Snapshot of exits plus accepted launches and their late-child cleanup during shutdown. */
    List<CompletableFuture<?>> pendingExits() {
        synchronized (lock) {
            var pending = new ArrayList<CompletableFuture<?>>();
            for (var session : sessions) if (!session.exitFuture().isDone()) pending.add(session.exitFuture());
            if (closed && !drained.isDone()) pending.add(drained.copy());
            return List.copyOf(pending);
        }
    }

    /** Called under lock: no accepted factory or tracked child can outlive this completion. */
    private void completeDrain() {
        if (closed && inFlight == 0 && sessions.isEmpty()) drained.complete(null);
    }

    /** Stops admission; accepted sessions remain owned by their panes until those panes close. */
    @Override public void close() {
        synchronized (lock) {
            if (closed) return;
            closed = true;
            executor.shutdown();
            completeDrain();
        }
    }
}
