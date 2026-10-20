package dev.jasper.sdk.ui;

/**
 * Where {@link Windows#openTab(ContentSpec, dev.jasper.sdk.WindowOwner, UiView, TabPlacement)} puts a new content tab
 * (workspace tabs spec 14). A tab already open for the same key is selected where it is, whatever the placement.
 *
 * @since 0.20.0
 */
public enum TabPlacement {
    /**
     * In a new tab of the owner's window, or of the main window when there is no owner: at the end of its tab strip and
     * shown, the main window's empty workspace taking it in its place. A content tab is an item, and each pane of a tab
     * holds one.
     */
    ACTIVE_GROUP,
    /** In a new pane to the right of the focused pane of that window's shown tab. */
    SPLIT_RIGHT,
    /** In a new pane below the focused pane of that window's shown tab. */
    SPLIT_DOWN,
    /**
     * In a new child window of its own, opened for it; the owner's window is not touched. The topics report
     * {@code WINDOW_OPENED}, then {@code TAB_OPENED} in the new window.
     */
    NEW_WINDOW
}
