package dev.jasper.sdk.terminal;

/** What happens to a provided session's pane when the session ends. */
public enum ExitPolicy {
    /** The pane stays, shows how the session ended, and offers Reconnect. What plugins normally choose. */
    KEEP_OPEN,
    /** The pane closes once the final output has been shown. */
    CLOSE_PANE
}
