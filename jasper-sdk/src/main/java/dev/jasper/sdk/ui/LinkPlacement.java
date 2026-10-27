package dev.jasper.sdk.ui;

/**
 * Where a clicked link asks to open, as {@link WebLink#placement()} carries it.
 *
 * @since 0.23.0
 */
public enum LinkPlacement {
    /** A tab, as the browser places it. */
    TAB,
    /**
     * A split to the right of the pane the link was clicked in, which is already the window's focused pane when
     * {@link WebBrowser#open} runs.
     */
    SPLIT_RIGHT,
    /**
     * A split below the pane the link was clicked in, which is already the window's focused pane when
     * {@link WebBrowser#open} runs.
     */
    SPLIT_DOWN
}
