package dev.jasper.sdk.ui;

/**
 * Where a tool window is: docked in the main window's dock, or in a window of its own. The user chooses with its
 * header's View Mode menu, or by dragging it out of the main window.
 *
 * @since 0.22.0
 */
public enum PanelMode {
    /** In its side's half of the main window's dock. */
    DOCKED,
    /** In a window of its own that stays above the main window and shows only with it. */
    FLOAT,
    /** In an ordinary window of its own, which may go behind the main window. */
    WINDOW
}
