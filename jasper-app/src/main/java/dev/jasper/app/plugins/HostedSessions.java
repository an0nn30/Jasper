package dev.jasper.app.plugins;

import dev.jasper.app.terminals.SessionAttempt;
import dev.jasper.app.terminals.SessionRequest;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.terminal.ExitPolicy;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.PendingSession;
import dev.jasper.sdk.terminal.SessionSpec;
import dev.jasper.sdk.terminal.TerminalConnection;
import dev.jasper.terminal.session.AttachedConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * One plugin's provided sessions. It turns a {@link SessionSpec} into the app-native request a pane runs, keeps
 * the attempts that are still pending so stopping the plugin cancels them, and contains every call into the
 * plugin: the connector on the EDT, cancellation handlers and closes on the cleanup worker.
 */
final class HostedSessions {
    private final String pluginId;
    private final Containment containment;
    private final Executor cleanup;
    private final Function<UUID, PaneHandle> panes;
    private final List<SessionAttempt> pending = new ArrayList<>();

    HostedSessions(String pluginId, Containment containment, Executor cleanup, Function<UUID, PaneHandle> panes) {
        this.pluginId = pluginId; this.containment = containment; this.cleanup = cleanup; this.panes = panes;
    }

    SessionRequest request(SessionSpec spec) {
        Objects.requireNonNull(spec, "spec");
        Executor contained = task -> cleanup.execute(() -> containment.run(pluginId, "session cleanup", task));
        return new SessionRequest(pluginId, spec.title(), spec.onExit() == ExitPolicy.CLOSE_PANE, attempt -> {
            track(attempt);
            Throwable failure = containment.attempt(pluginId, "session connector", () -> { spec.connector().accept(new Pending(attempt)); return null; });
            if (failure != null) attempt.fail(failure.getMessage() == null ? failure.toString() : failure.getMessage());
        }, contained, spec.icon().orElse(null));
    }

    private void track(SessionAttempt attempt) {
        synchronized (pending) {
            pending.removeIf(earlier -> earlier.state() != SessionAttempt.State.PENDING);
            pending.add(attempt);
        }
    }

    /** The plugin is stopping: whatever is still connecting is cancelled. Attached sessions belong to their panes. */
    void cancelAll() {
        List<SessionAttempt> copy;
        synchronized (pending) { copy = new ArrayList<>(pending); pending.clear(); }
        copy.forEach(SessionAttempt::cancel);
    }

    private final class Pending implements PendingSession {
        private final SessionAttempt attempt;
        Pending(SessionAttempt attempt) { this.attempt = attempt; }
        @Override public PaneHandle pane() { return panes.apply(attempt.paneId()); }
        @Override public int columns() { return attempt.columns(); }
        @Override public int rows() { return attempt.rows(); }
        @Override public void status(String text) { attempt.status(text); }
        @Override public void attach(TerminalConnection connection) {
            Objects.requireNonNull(connection, "connection");
            attempt.attach(new AttachedConnection(connection.output(), connection.input(), connection.resize(), connection.exited(), connection.close()));
        }
        @Override public void fail(String message) { attempt.fail(message); }
        @Override public boolean isCancelled() { return attempt.isCancelled(); }
        @Override public Subscription onCancelled(Runnable handler) {
            var registration = attempt.onCancelled(handler);
            var closed = new AtomicBoolean();
            return () -> { if (closed.compareAndSet(false, true)) registration.close(); };
        }
    }
}
