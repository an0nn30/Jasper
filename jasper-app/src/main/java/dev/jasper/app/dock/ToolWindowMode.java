package dev.jasper.app.dock;

/**
 * How a tool window is placed: {@link #DOCKED} in one of its window's slots, or detached into a window of its own, as
 * {@link #FLOAT} (a window that stays above the main window and shows only with it) or {@link #WINDOW} (an ordinary
 * separate window). A detached tool window keeps its slot, which is where its stripe button sits and where it docks back.
 */
public enum ToolWindowMode {
    DOCKED, FLOAT, WINDOW;

    /** Whether the tool window has a window of its own. */
    public boolean detached() { return this != DOCKED; }
}
