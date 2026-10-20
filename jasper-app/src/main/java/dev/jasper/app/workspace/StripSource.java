package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Tab;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.Icon;
import javax.swing.JPopupMenu;

/**
 * What a tab strip shows and does (workspace tabs spec 10; parent tabs spec 7): a pane's tabs ({@link GroupView}), or a
 * window's parent tabs, each shown as its active tab ({@link ParentTabBar}). A strip lists, selects, closes and opens
 * through its source only. EDT only.
 */
sealed interface StripSource permits GroupView, ParentTabBar {
    /** The window the strip is in. */
    WindowContent owner();
    /** The entries in strip order. */
    List<Tab> tabs();
    /** The entry shown as selected; null for none. */
    Tab selected();
    String title(Tab tab);
    Icon icon(Tab tab);
    String tooltip(Tab tab);
    /** The shortcut that selects the entry at {@code index}, as this platform writes it; empty for none. */
    String shortcut(int index);
    /** Whether the entry is a terminal's, which the classic tabs mark with a status dot. */
    boolean terminal(Tab tab);
    /** Whether the entry's shell runs, which the strip tells assistive technology. */
    boolean running(Tab tab);
    JPopupMenu menu(Tab tab);
    void select(Tab tab);
    void close(Tab tab);
    /** A left press on an entry, where a drag may start. */
    void pressed(Tab tab, MouseEvent event);
    /** A drag event from the strip. */
    void dragged(MouseEvent event);
    /** A release on the strip. */
    void released(MouseEvent event);
    /** The classic tabs' "+". */
    void newTab();
    /** The "+"'s name: what it opens. */
    String newTabName();
    /** Whether the entry takes its place at once, without the open motion. */
    boolean moved(Tab tab);
    /** Whether the strip's entries may move as they open and close. */
    boolean animates();
}
