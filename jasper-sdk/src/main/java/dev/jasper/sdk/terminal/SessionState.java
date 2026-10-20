package dev.jasper.sdk.terminal;

/** Where a pane's session is in its life. */
public enum SessionState {
    /**
     * Not started: the pane of a terminal tab Jasper restored at start and has not shown yet. Its shell starts, or its
     * plugin's session connects, when the tab is first shown; until then nothing runs and text sent to it is dropped.
     *
     * @since 0.21.0
     */
    NOT_STARTED,
    /** Not running yet: a local shell is starting, or a plugin is connecting. */
    CONNECTING,
    /** Running. */
    RUNNING,
    /** Ended; the pane may still be open and showing its last output. */
    EXITED
}
