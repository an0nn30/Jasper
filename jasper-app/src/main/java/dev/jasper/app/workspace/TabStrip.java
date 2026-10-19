package dev.jasper.app.workspace;

import dev.jasper.app.config.AnimationSettings;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;

/**
 * One tab group's strip (workspace tabs spec 10; Enterprise presentation spec 10.5): today's equal tabs or the classic
 * tabs, always shown, even for one tab. It lists, selects, closes, reorders and opens tabs in its group view only, and
 * shows as active while that group is the active group of an active window. EDT only.
 */
sealed interface TabStrip extends AutoCloseable permits WindowTabs, ClassicTabs {
    /** The component mounted above the group's cards. */
    JComponent component();
    /** Takes the group's tabs, titles, selection and states. */
    void refresh();
    /** Whether the strip shows as active: its window active and its group the active group. */
    void setActive(boolean active);
    /** The configured animations; classic tabs never move. */
    void setAnimations(AnimationSettings settings);
    /** The group's tabs, as a menu of literal titles of at most {@code WindowTabs.TITLE_LIMIT} characters. */
    JPopupMenu tabList();
    /** Releases the tabs. */
    @Override void close();
}
