package dev.jasper.terminal.config;

/** How an attached terminal view signals a program's bell. */
public enum BellMode {
    /** Flash the view briefly. */
    VISUAL,
    /** Invoke the desktop beep. */
    SOUND,
    /** Ignore bell signals. */
    NONE
}
