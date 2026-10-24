package dev.jasper.app.restart;

/** How the replacement process is launched. */
public enum RestartMode {
    /** The same launch again: its flags kept, except {@code --background} and {@code --no-window}, without its paths. */
    SAME,
    /** Leaving safe mode: {@code --safe-mode} is dropped too. */
    NORMAL
}
