package dev.jasper.app.config;

/** What the window Jasper opens while none is open holds ({@code system.reopen}). */
public enum ReopenMode {
    /** The last window the user closed, as it was saved. */
    LAST_KNOWN_STATE,
    /** The built-in layout, at the saved window's place. */
    EMPTY_STATE
}
