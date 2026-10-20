package dev.jasper.app.workspace;

import dev.jasper.app.config.AnimationSettings;
import java.awt.Rectangle;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;

/**
 * A window's tab strip (tabs and panes spec 5; Enterprise presentation spec 10.5): the equal tabs, hidden while the
 * window holds one tab, or the classic tabs, always shown. It lists, selects, closes and opens through its
 * {@link ParentTabBar} only, a press on an entry starts the tab drag of a whole tab (spec 6), and it shows as active
 * while its window is. The equal tabs open and close with the configured motion where the look has it (workspace plan
 * 8a Rulings 20); the classic tabs never move. EDT only.
 */
sealed interface TabStrip extends AutoCloseable permits WindowTabs, ClassicTabs {
    /** The component mounted above the window's tabs' workspaces. */
    JComponent component();
    /** Takes the window's tabs, titles, selection and states. */
    void refresh();
    /** Whether the strip shows as active: its window active. */
    void setActive(boolean active);
    /** The configured animations; the classic tabs never move. */
    void setAnimations(AnimationSettings settings);
    /** The window's tabs, as a menu of literal titles of at most {@code WindowTabs.TITLE_LIMIT} characters. */
    JPopupMenu tabList();
    /** Each tab's rectangle in this strip's coordinates, in strip order: a tab drag drops between them. */
    List<Rectangle> tabBounds();
    /** Releases the tabs. */
    @Override void close();
}
