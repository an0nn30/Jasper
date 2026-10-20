package dev.jasper.app.layout;

import java.util.List;

/**
 * What a window holds (parent tabs spec 4): parent tabs with an active one, as the main window and full windows do, or
 * one layout, as a pane window does.
 */
public sealed interface WindowLayout permits ParentTabs, PaneLayout {
    /**
     * Every tab the window holds, in its order.
     *
     * @return the tabs
     */
    List<TabId> tabs();
}
