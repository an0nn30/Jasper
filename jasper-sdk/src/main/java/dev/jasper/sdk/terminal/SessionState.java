package dev.jasper.sdk.terminal;

/** Where a pane's session is in its life. */
public enum SessionState {
    /** Not running yet: a local shell is starting, or a plugin is connecting. */
    CONNECTING,
    /** Running. */
    RUNNING,
    /** Ended; the pane may still be open and showing its last output. */
    EXITED
}
