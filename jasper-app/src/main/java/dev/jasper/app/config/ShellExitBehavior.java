package dev.jasper.app.config;

/** Whether to retain a terminal pane after its shell process exits. */
public enum ShellExitBehavior {
    KEEP_OPEN, CLOSE_ON_SUCCESS, CLOSE
}
