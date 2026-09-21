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

/** Owns launch admission and sessions across worker/EDT races; closing never waits for a worker. */
final class SessionLaunchCoordinator implements Executor, AutoCloseable {
    private final Object lock = new Object();
    private final ExecutorService executor;
    private final Set<TerminalSession> sessions = new LinkedHashSet<>();
    private boolean closed;

    SessionLaunchCoordinator(ExecutorService executor) { this.executor = Objects.requireNonNull(executor); }

    @Override public void execute(Runnable launch) {
        synchronized (lock) {
            if (closed) throw new RejectedExecutionException("Application launch owner is closed");
            executor.execute(launch);
        }
    }

    /** Transfers an acquired session to this owner, or closes it if shutdown won the race. */
    TerminalSession track(TerminalSession session) {
        Objects.requireNonNull(session);
        synchronized (lock) {
            if (!closed) {
                sessions.add(session);
                session.exitFuture().whenComplete((code, failure) -> {
                    synchronized (lock) { sessions.remove(session); }
                });
                return session;
            }
        }
        session.close();
        return session;
    }

    /** Snapshot of unfinished exits, suitable for the application's bounded shutdown wait. */
    List<CompletableFuture<?>> pendingExits() {
        synchronized (lock) {
            var pending = new ArrayList<CompletableFuture<?>>();
            for (var session : sessions) if (!session.exitFuture().isDone()) pending.add(session.exitFuture());
            return List.copyOf(pending);
        }
    }

    /** Stops admission; accepted sessions remain owned by their panes until those panes close. */
    @Override public void close() {
        synchronized (lock) {
            if (closed) return;
            closed = true;
            executor.shutdown();
        }
    }
}
