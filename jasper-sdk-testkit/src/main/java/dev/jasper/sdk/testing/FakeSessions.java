package dev.jasper.sdk.testing;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.terminal.ExitPolicy;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.PendingSession;
import dev.jasper.sdk.terminal.SessionSpec;
import dev.jasper.sdk.terminal.SessionState;
import dev.jasper.sdk.terminal.TerminalConnection;
import dev.jasper.sdk.terminal.TerminalEvents;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Provided sessions with the application's rules and none of its threads: cancellation handlers and closes run
 * inline where the application uses its cleanup thread, and the test decides when an exit is noticed.
 */
final class FakeSessions {
    enum Outcome { PENDING, ATTACHED, FAILED, CANCELLED }

    /** What one pane knows about its provided session. */
    static final class Session {
        final FakePluginContext owner; final SessionSpec spec;
        Attempt attempt; TerminalConnection connection; boolean everAttached; String state = "CONNECTING"; String status = "";
        Session(FakePluginContext owner, SessionSpec spec) { this.owner = owner; this.spec = spec; }
    }

    final class Attempt implements PendingSession {
        final FakeWorkspace.Pane pane;
        final AtomicReference<Outcome> outcome = new AtomicReference<>(Outcome.PENDING);
        final List<Runnable> handlers = new ArrayList<>();
        Attempt(FakeWorkspace.Pane pane) { this.pane = pane; }

        @Override public PaneHandle pane() { return pane.session.owner.terminals.paneHandle(pane.id); }
        @Override public int columns() { return 80; }
        @Override public int rows() { return 24; }
        @Override public void status(String text) {
            Objects.requireNonNull(text, "text");
            if (outcome.get() == Outcome.PENDING && pane.session.attempt == this) pane.session.status = text;
        }
        @Override public void attach(TerminalConnection connection) {
            Objects.requireNonNull(connection, "connection");
            if (!outcome.compareAndSet(Outcome.PENDING, Outcome.ATTACHED) || pane.session.attempt != this || !pane.open) { close(connection); return; }
            pane.session.connection = connection; pane.session.everAttached = true; pane.session.state = "RUNNING"; pane.session.status = "";
            pane.info = FakeTerminals.withState(pane.info, SessionState.RUNNING, OptionalInt.empty());
            host.publishApp(TerminalEvents.SESSION_STATE_CHANGED, new TerminalEvents.SessionStateChanged(pane.id, SessionState.RUNNING, OptionalInt.empty()));
        }
        @Override public void fail(String message) {
            if (!outcome.compareAndSet(Outcome.PENDING, Outcome.FAILED) || pane.session.attempt != this) return;
            ended(pane, message == null || message.isBlank() ? "The connection failed" : message, OptionalInt.empty());
        }
        @Override public boolean isCancelled() { return outcome.get() == Outcome.CANCELLED; }
        @Override public Subscription onCancelled(Runnable handler) {
            Objects.requireNonNull(handler, "handler");
            synchronized (handlers) {
                if (outcome.get() == Outcome.PENDING) {
                    handlers.add(handler);
                    var closed = new AtomicBoolean();
                    return () -> { if (closed.compareAndSet(false, true)) synchronized (handlers) { handlers.remove(handler); } };
                }
            }
            if (outcome.get() == Outcome.CANCELLED) contained(pane.session.owner, "session cancellation", handler);
            return () -> { };
        }
        void cancel() {
            List<Runnable> copy;
            synchronized (handlers) {
                if (!outcome.compareAndSet(Outcome.PENDING, Outcome.CANCELLED)) return;
                copy = new ArrayList<>(handlers);
                handlers.clear();
            }
            copy.forEach(handler -> contained(pane.session.owner, "session cancellation", handler));
        }
    }

    private final FakePluginHost host;
    private final FakeWorkspace workspace;

    FakeSessions(FakePluginHost host, FakeWorkspace workspace) { this.host = host; this.workspace = workspace; }

    private void contained(FakePluginContext owner, String what, Runnable action) {
        try { action.run(); }
        catch (RuntimeException | LinkageError failure) { host.recordFailure(owner.plugin().id() + " " + what + ": " + failure); }
    }

    /** Ownership transferred to the host at attach: the original close runs exactly once. */
    private void close(TerminalConnection connection) {
        try { connection.close().run(); }
        catch (RuntimeException | LinkageError failure) { host.recordFailure("session close: " + failure); }
    }

    private void ended(FakeWorkspace.Pane pane, String status, OptionalInt exitStatus) {
        pane.session.state = "EXITED"; pane.session.status = status;
        pane.info = FakeTerminals.withState(pane.info, SessionState.EXITED, exitStatus);
        host.publishApp(TerminalEvents.SESSION_STATE_CHANGED, new TerminalEvents.SessionStateChanged(pane.id, SessionState.EXITED, exitStatus));
    }

    void connect(FakePluginContext owner, FakeWorkspace.Pane pane, SessionSpec spec) {
        if (pane.session == null) pane.session = new Session(owner, spec);
        var attempt = new Attempt(pane);
        pane.session.attempt = attempt;
        pane.session.state = "CONNECTING"; pane.session.status = "";
        pane.info = FakeTerminals.withState(pane.info, SessionState.CONNECTING, OptionalInt.empty());
        host.publishApp(TerminalEvents.SESSION_STATE_CHANGED, new TerminalEvents.SessionStateChanged(pane.id, SessionState.CONNECTING, OptionalInt.empty()));
        try { spec.connector().accept(attempt); }
        catch (RuntimeException | LinkageError failure) {
            host.recordFailure(owner.plugin().id() + " session connector: " + failure);
            attempt.fail(failure.getMessage() == null ? failure.toString() : failure.getMessage());
        }
    }

    String state(FakeWorkspace.Pane paneOrNull) {
        if (paneOrNull == null || !paneOrNull.open) return "CLOSED|";
        return paneOrNull.session == null ? "LOCAL|" : paneOrNull.session.state + "|" + paneOrNull.session.status;
    }

    void cancel(FakeWorkspace.Pane pane) {
        if (pane.session == null || pane.session.attempt == null) return;
        pane.session.attempt.cancel();
        // As in a real pane: with nothing ever shown there is nothing to return to.
        if (!pane.session.everAttached) workspace.close(pane);
        else ended(pane, "Connection cancelled", OptionalInt.empty());
    }

    void reconnect(FakeWorkspace.Pane pane) {
        if (pane.session != null && pane.session.state.equals("EXITED")) connect(pane.session.owner, pane, pane.session.spec);
    }

    /** The pane is closing: a pending attempt is cancelled and an attached connection is closed. */
    void closing(FakeWorkspace.Pane pane) {
        if (pane.session == null) return;
        if (pane.session.attempt != null) pane.session.attempt.cancel();
        if (pane.session.connection != null) { close(pane.session.connection); pane.session.connection = null; }
    }

    /** The plugin is stopping: whatever it is still connecting is cancelled. */
    void stopping(FakePluginContext owner) {
        for (FakeWorkspace.Pane pane : workspace.everyOpenPane())
            if (pane.session != null && pane.session.owner == owner && pane.session.attempt != null) pane.session.attempt.cancel();
    }

    void type(FakeWorkspace.Pane pane, String text) {
        if (pane.session == null || pane.session.connection == null) return;
        try { pane.session.connection.input().write(text.getBytes(StandardCharsets.UTF_8)); pane.session.connection.input().flush(); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    String output(FakeWorkspace.Pane pane) {
        if (pane.session == null || pane.session.connection == null) return "";
        try {
            var stream = pane.session.connection.output();
            return new String(stream.readNBytes(stream.available()), StandardCharsets.UTF_8);
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    /** Notices attached sessions whose {@code exited} completed: closes them, reports the exit, applies the exit policy. */
    void pump() {
        for (FakeWorkspace.Pane pane : workspace.everyOpenPane()) {
            if (pane.session == null || pane.session.connection == null || !pane.session.connection.exited().isDone()) continue;
            TerminalConnection connection = pane.session.connection;
            pane.session.connection = null;
            close(connection);
            Integer status = connection.exited().isCompletedExceptionally() ? null : connection.exited().getNow(null);
            String message = status != null ? "exit " + status
                : String.valueOf(connection.exited().handle((value, error) -> error == null ? "unknown" : String.valueOf(error.getMessage())).join());
            ended(pane, message, status == null ? OptionalInt.empty() : OptionalInt.of(status));
            if (pane.session.spec.onExit() == ExitPolicy.CLOSE_PANE) workspace.close(pane);
        }
    }
}
