package dev.jasper.app.restart;

/** How the replacement process is launched. */
public enum RestartMode {
    /** The same launch again: every flag kept except {@code --background}. */
    SAME,
    /** Leaving safe mode: {@code --safe-mode} is dropped too. */
    NORMAL,
    /** Leaving safe mode while a resident still runs: also {@code --standalone}, which can never hand off. */
    STANDALONE
}
