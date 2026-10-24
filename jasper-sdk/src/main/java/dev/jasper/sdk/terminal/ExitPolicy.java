package dev.jasper.sdk.terminal;

/** What happens to a provided session's pane, and so its tab, when the session ends. */
public enum ExitPolicy {
    /** The pane stays, shows how the session ended, and offers Reconnect. What plugins normally choose. */
    KEEP_OPEN,
    /**
     * The pane's tab closes once the final output has been shown, as {@code terminal.on_exit = "close"} closes a shell's.
     *
     * @since 0.19.0
     */
    CLOSE
}
