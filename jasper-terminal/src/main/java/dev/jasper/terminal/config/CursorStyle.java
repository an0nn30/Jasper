package dev.jasper.terminal.config;

/** The configured cursor shape, used unless a program requests another. */
public enum CursorStyle {
    /** Fill the cursor cell. */
    BLOCK,
    /** Draw a vertical caret. */
    BEAM,
    /** Draw below the cursor cell. */
    UNDERLINE
}
