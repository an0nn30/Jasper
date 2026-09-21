package dev.jasper.sdk.terminal;

import dev.jasper.sdk.Subscription;

/**
 * One attempt to connect a pane: the first connect, or one Reconnect. Exactly one of {@link #attach},
 * {@link #fail} and cancellation takes effect; the first wins and later calls are ignored. Every method is safe
 * from any thread, also after the plugin stopped.
 */
public interface PendingSession {
    /**
     * The pane that is waiting.
     *
     * @return its handle
     */
    PaneHandle pane();

    /**
     * The width to request for the remote pty. The pane resizes it once it has been laid out.
     *
     * @return columns
     */
    int columns();

    /**
     * The height to request for the remote pty.
     *
     * @return rows
     */
    int rows();

    /**
     * Shows progress in the waiting pane, for example "Authenticating".
     *
     * @param text one line
     */
    void status(String text);

    /**
     * Hands over a connected session. Ownership transfers at this call, whatever the outcome: when the attempt
     * was cancelled or already finished, the connection is never read and its {@code close} is invoked at once.
     *
     * @param connection the connected session
     */
    void attach(TerminalConnection connection);

    /**
     * Ends the attempt unsuccessfully; the pane shows the message and offers to try again.
     *
     * @param message why, for the user
     */
    void fail(String message);

    /**
     * Whether the user pressed Cancel, the pane closed, the plugin stopped or Jasper is quitting.
     *
     * @return true once cancelled
     */
    boolean isCancelled();

    /**
     * Registers work that aborts a blocking connect. It runs at most once, on Jasper's cleanup thread, and
     * at once when the attempt is already cancelled.
     *
     * @param handler what to run
     * @return closes the registration
     */
    Subscription onCancelled(Runnable handler);
}
