package dev.jasper.app.workspace;

import dev.jasper.app.config.AnimationSettings;
import java.awt.Rectangle;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;

/**
 * One tab group's strip, or a window's parent-tab bar (workspace tabs spec 10; parent tabs spec 7; Enterprise presentation
 * spec 10.5): today's equal tabs, hidden while their pane holds a single tab or their window a single parent tab, or the
 * classic tabs, always shown in a pane and never as the bar. It lists, selects, closes and opens through its
 * {@link StripSource} only, a press on the bar's entries starts the tab drag of a whole parent tab (tabs and panes spec
 * 6) while a pane's strip starts none, and it shows as active while its group is the active group of an active window,
 * or while the bar's window is active. EDT only.
 */
sealed interface TabStrip extends AutoCloseable permits WindowTabs, ClassicTabs {
    /** The component mounted above its source's content: a pane's cards, or the parent tabs' workspaces. */
    JComponent component();
    /** Takes its source's tabs (a pane's, or the parent tabs' in the bar), titles, selection and states. */
    void refresh();
    /** Whether the strip shows as active: its window active and its group the active group. */
    void setActive(boolean active);
    /** The configured animations; classic tabs never move. */
    void setAnimations(AnimationSettings settings);
    /** Its source's tabs, as a menu of literal titles of at most {@code WindowTabs.TITLE_LIMIT} characters. */
    JPopupMenu tabList();
    /** Each tab's rectangle in this strip's coordinates, in its source's tab order: a tab drag drops between them. */
    List<Rectangle> tabBounds();
    /** Releases the tabs. */
    @Override void close();
}
