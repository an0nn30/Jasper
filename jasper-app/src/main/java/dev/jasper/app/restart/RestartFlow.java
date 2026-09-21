package dev.jasper.app.restart;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The conversation behind "Restart now" and "Restart normally". Leaving safe mode must not start a
 * handoff-capable replacement while a resident process holds the endpoint: that launch would hand off
 * to the very process that still runs the old plugin set. So the flow probes, asks the resident to quit,
 * waits for the endpoint to go quiet, and otherwise offers only a standalone launch. Public methods and
 * listeners are UI-thread only; probing, retiring and waiting run on the worker.
 */
public final class RestartFlow {
    /** Where the conversation is. */
    public enum State {
        /** Nothing in progress. */
        IDLE,
        /** Looking for a resident process. */
        PROBING,
        /** A resident runs the previous plugin set; the user decides. */
        RESIDENT_FOUND,
        /** The resident was asked to quit; waiting for its endpoint to be released. */
        WAITING,
        /** The resident refused, or did not release the endpoint in time. */
        RESIDENT_STUCK,
        /** The command line cannot be determined; the user must quit and reopen Jasper. */
        UNAVAILABLE
    }

    private final ResidentControl control;
    private final Predicate<RestartMode> restart;
    private final Executor worker;
    private final Consumer<Runnable> ui;
    private final Duration wait;
    private final Duration poll;
    private final List<Runnable> listeners = new ArrayList<>();
    /** Written on the UI thread; volatile so a test or a worker may read it. */
    private volatile State state = State.IDLE;
    /** Bumped by every user decision, so an abandoned probe or wait finds its result unwanted. */
    private volatile int generation;

    /**
     * Creates an idle flow.
     *
     * @param control the resident process
     * @param restart quits and relaunches in the given mode on the UI thread; false when the command line is unknown
     * @param worker runs blocking socket work
     * @param ui posts to the UI thread
     * @param wait how long a retiring resident may take to release the endpoint
     * @param poll how often to look
     */
    public RestartFlow(ResidentControl control, Predicate<RestartMode> restart, Executor worker, Consumer<Runnable> ui,
                       Duration wait, Duration poll) {
        this.control = Objects.requireNonNull(control); this.restart = Objects.requireNonNull(restart);
        this.worker = Objects.requireNonNull(worker); this.ui = Objects.requireNonNull(ui);
        this.wait = Objects.requireNonNull(wait); this.poll = Objects.requireNonNull(poll);
    }

    /**
     * The current state.
     *
     * @return the state
     */
    public State state() { return state; }

    /**
     * Registers a listener for state changes.
     *
     * @param listener run on the UI thread after each change
     */
    public void onChanged(Runnable listener) { listeners.add(Objects.requireNonNull(listener)); }

    private void enter(State next) {
        state = next;
        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }

    private void relaunch(RestartMode mode) {
        generation++;
        enter(restart.test(mode) ? State.IDLE : State.UNAVAILABLE);
    }

    /** Restarts the same launch: for a process that is not in safe mode. */
    public void restartNow() { relaunch(RestartMode.SAME); }

    /** Leaves safe mode, first making sure no resident process would swallow the new launch. */
    public void restartNormally() {
        if (state == State.PROBING || state == State.WAITING) return;
        int mine = ++generation;
        enter(State.PROBING);
        worker.execute(() -> {
            boolean live = control.live().getAsBoolean();
            ui.accept(() -> {
                if (mine != generation) return;
                if (live) enter(State.RESIDENT_FOUND); else relaunch(RestartMode.NORMAL);
            });
        });
    }

    /** Sends the retire request and waits, bounded, for the endpoint to be released. */
    public void askResidentToQuit() {
        if (state != State.RESIDENT_FOUND && state != State.RESIDENT_STUCK) return;
        int mine = ++generation;
        enter(State.WAITING);
        worker.execute(() -> {
            boolean released = control.retire().getAsBoolean() && awaitRelease(mine);
            ui.accept(() -> {
                if (mine != generation) return;
                if (released) relaunch(RestartMode.NORMAL); else enter(State.RESIDENT_STUCK);
            });
        });
    }

    private boolean awaitRelease(int mine) {
        long deadline = System.nanoTime() + wait.toNanos();
        while (mine == generation) {
            if (!control.live().getAsBoolean()) return true;
            if (System.nanoTime() >= deadline) return false;
            try { Thread.sleep(Math.max(1, poll.toMillis())); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return false; }
        }
        return false;
    }

    /** Starts a standalone replacement, which can never hand off, and leaves the resident alone. */
    public void launchAnyway() {
        if (state != State.RESIDENT_FOUND && state != State.WAITING && state != State.RESIDENT_STUCK) return;
        relaunch(RestartMode.STANDALONE);
    }

    /** Abandons the conversation; a pending probe or wait is ignored when it answers. */
    public void cancel() {
        generation++;
        enter(State.IDLE);
    }
}
