package dev.jasper.sdk.terminal;

import dev.jasper.sdk.WindowOwner;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** One Jasper window. Handles keep an id and no Swing object; two handles for the same window are equal. EDT only. */
public interface WindowHandle extends WindowOwner {
    /**
     * The window's stable id.
     *
     * @return the id
     */
    UUID id();

    /**
     * The window's tabs, terminal and content: parent tab by parent tab in bar order, then group by group, each group's
     * tabs in strip order;
     * empty once the window is closed.
     *
     * @return the tabs
     */
    List<TabHandle> tabs();

    /**
     * The active tab: the selected tab of the active group of the window's shown parent tab, terminal or content.
     *
     * @return the tab, or empty in an empty workspace and once the window is closed
     */
    Optional<TabHandle> activeTab();

    /**
     * Whether this window has the user's attention right now.
     *
     * @return true while it is the active window of a foreground Jasper
     */
    boolean isActive();

    /**
     * Whether the window still exists.
     *
     * @return true while it is open
     */
    boolean isOpen();

    /**
     * Whether this is the main window, the one with Jasper's toolbar, tool windows and status bar, which stays open
     * when its last tab closes; any other window is a child window, a title bar over its tabs, which closes with its
     * last tab (workspace tabs spec 6).
     *
     * @return true for the main window while it is open
     * @since 0.20.0
     */
    boolean isMain();

    /** Raises the window. Ignored once it is closed. */
    void toFront();
}
