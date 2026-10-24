package dev.jasper.app.restart;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * The Plugins page's restart banner: Restart Now and Restart Normally restart at once, the replacement waiting for this
 * process's instance lock (workspace-tabs spec 5.3); a command line that cannot be determined leaves the banner saying
 * so. UI thread only.
 */
public final class RestartFlow {
    /** Where the banner is. */
    public enum State {
        /** Nothing in progress. */
        IDLE,
        /** The command line cannot be determined; the user must quit and reopen Jasper. */
        UNAVAILABLE
    }

    private final Predicate<RestartMode> restart;
    private final List<Runnable> listeners = new ArrayList<>();
    private State state = State.IDLE;

    /**
     * Creates an idle flow.
     *
     * @param restart quits and relaunches in the given mode; false when the command line is unknown
     */
    public RestartFlow(Predicate<RestartMode> restart) { this.restart = Objects.requireNonNull(restart); }

    /**
     * The current state.
     *
     * @return the state
     */
    public State state() { return state; }

    /**
     * Registers a listener for state changes.
     *
     * @param listener run after each change
     */
    public void onChanged(Runnable listener) { listeners.add(Objects.requireNonNull(listener)); }

    /** Restarts the same launch: for a process that is not in safe mode. */
    public void restartNow() { relaunch(RestartMode.SAME); }

    /** Leaves safe mode. */
    public void restartNormally() { relaunch(RestartMode.NORMAL); }

    /** Forgets an unavailable restart. */
    public void cancel() { enter(State.IDLE); }

    private void relaunch(RestartMode mode) { enter(restart.test(mode) ? State.IDLE : State.UNAVAILABLE); }

    private void enter(State next) {
        state = next;
        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }
}
