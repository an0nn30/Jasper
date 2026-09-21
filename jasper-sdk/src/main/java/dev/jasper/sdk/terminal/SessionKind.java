package dev.jasper.sdk.terminal;

/** Who provides a pane's session. */
public enum SessionKind {
    /** A local process on a pseudo-terminal. */
    LOCAL,
    /** A plugin-provided connection. */
    PLUGIN
}
