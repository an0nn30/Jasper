package dev.jasper.app.terminals;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.terminal.session.AttachedConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * One attempt to connect a pane. Exactly one of attach, fail and cancel takes effect; the first wins and the
 * rest are ignored. Ownership of a connection transfers at {@link #attach}, whatever the outcome: a connection
 * offered to an attempt that is no longer pending is never read and is closed at once, so a connect that
 * finishes after the user cancelled cannot leak. Every method is safe from any thread; the pane's callbacks
 * run on the UI thread, closes and cancellation handlers on the cleanup executor.
 */
public final class SessionAttempt {
    /** Where the attempt is. */
    public enum State { PENDING, ATTACHED, FAILED, CANCELLED }

    private static final System.Logger LOG = System.getLogger(SessionAttempt.class.getName());
    private final UUID paneId;
    private final int columns;
    private final int rows;
    private final Executor cleanup;
    private final Consumer<Runnable> ui;
    private final AtomicReference<State> state = new AtomicReference<>(State.PENDING);
    private final List<Runnable> cancelHandlers = new ArrayList<>();
    /** Pane callbacks: set on the UI thread before the connector runs, invoked on the UI thread. */
    public Consumer<String> onStatus = text -> { };
    public Consumer<AttachedConnection> onAttached = connection -> { };
    public Consumer<String> onFailed = message -> { };

    public SessionAttempt(UUID paneId, int columns, int rows, Executor cleanup, Consumer<Runnable> ui) {
        this.paneId = Objects.requireNonNull(paneId); this.columns = columns; this.rows = rows;
        this.cleanup = Objects.requireNonNull(cleanup); this.ui = Objects.requireNonNull(ui);
    }

    public UUID paneId() { return paneId; }
    /** The grid to request from the remote side; the pane resizes it once it has been laid out. */
    public int columns() { return columns; }
    public int rows() { return rows; }
    public State state() { return state.get(); }
    public boolean isCancelled() { return state.get() == State.CANCELLED; }

    public void status(String text) {
        Objects.requireNonNull(text, "text");
        if (state.get() != State.PENDING) { LOG.log(System.Logger.Level.DEBUG, "Status for a finished attempt ignored"); return; }
        ui.accept(() -> { if (state.get() == State.PENDING) onStatus.accept(text); });
    }

    public void attach(AttachedConnection connection) {
        AttachedConnection owned = owned(Objects.requireNonNull(connection, "connection"));
        if (!state.compareAndSet(State.PENDING, State.ATTACHED)) {
            LOG.log(System.Logger.Level.DEBUG, "A connection arrived for a finished attempt; closing it");
            owned.close().run();
            return;
        }
        ui.accept(() -> onAttached.accept(owned));
    }

    /** The same connection, whose close reaches the original exactly once and on the cleanup executor. */
    private AttachedConnection owned(AttachedConnection connection) {
        var once = new AtomicBoolean();
        return new AttachedConnection(connection.output(), connection.input(), connection.resize(), connection.exited(),
            () -> { if (once.compareAndSet(false, true)) cleanup.execute(connection.close()); });
    }

    public void fail(String message) {
        String text = message == null || message.isBlank() ? "The connection failed" : message;
        if (!state.compareAndSet(State.PENDING, State.FAILED)) { LOG.log(System.Logger.Level.DEBUG, "Failure of a finished attempt ignored"); return; }
        ui.accept(() -> onFailed.accept(text));
    }

    /** The user pressed Cancel, the pane closed, the provider stopped, or Jasper is shutting down. */
    public void cancel() {
        List<Runnable> handlers;
        synchronized (cancelHandlers) {
            if (!state.compareAndSet(State.PENDING, State.CANCELLED)) return;
            handlers = new ArrayList<>(cancelHandlers);
            cancelHandlers.clear();
        }
        handlers.forEach(cleanup::execute);
    }

    /** Runs at most once, on the cleanup executor; at once when the attempt is already cancelled. */
    public Subscription onCancelled(Runnable handler) {
        Objects.requireNonNull(handler, "handler");
        synchronized (cancelHandlers) {
            if (state.get() == State.PENDING) {
                cancelHandlers.add(handler);
                return new Subscription(() -> { synchronized (cancelHandlers) { cancelHandlers.remove(handler); } });
            }
        }
        if (state.get() == State.CANCELLED) cleanup.execute(handler);
        return new Subscription(() -> { });
    }
}
