package dev.jasper.sdk.ui;

/**
 * Where a tool window is: docked in its window's dock, or in a window of its own. The user chooses with its
 * header's View Mode menu, or by dragging it out of its window.
 *
 * @since 0.22.0
 */
public enum PanelMode {
    /** In its side's half of its window's dock. */
    DOCKED,
    /** In a window of its own that stays above its window and shows only with it. */
    FLOAT,
    /** In an ordinary window of its own, which may go behind its window. */
    WINDOW
}
