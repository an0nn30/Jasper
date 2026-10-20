package dev.jasper.sdk.ui;

/**
 * Where {@link Windows#openTab(ContentSpec, dev.jasper.sdk.WindowOwner, UiView, TabPlacement)} puts a new content tab
 * (workspace tabs spec 14). A tab already open for the same key is selected where it is, whatever the placement.
 *
 * @since 0.20.0
 */
public enum TabPlacement {
    /**
     * At the end of the active pane of the owner's window's shown parent tab, or of the main window's when there is
     * no owner.
     */
    ACTIVE_GROUP,
    /** In a new group to the right of that active group. */
    SPLIT_RIGHT,
    /** In a new group below that active group. */
    SPLIT_DOWN,
    /**
     * In a new child window of its own, opened for it; the owner's window is not touched. The topics report
     * {@code WINDOW_OPENED}, then {@code TAB_OPENED} in the new window.
     */
    NEW_WINDOW
}
