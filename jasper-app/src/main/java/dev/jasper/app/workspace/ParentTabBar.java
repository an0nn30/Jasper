package dev.jasper.app.workspace;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.ParentTabId;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.Icon;
import javax.swing.JPopupMenu;

/**
 * A window's one tab strip (tabs and panes spec 5): the look's strip over the window's tabs, each shown as its active
 * pane's item, as terminal apps title a tab. It shows from two tabs under the equal tabs and always under the classic
 * tabs (Enterprise), over a window's empty workspace too (workspace plan 8a Rulings 10). A click shows a tab, and
 * its close button, a middle click and its menu close it after its items' guards; a press on an entry may start the tab
 * drag (spec 6). Under the equal tabs, tabs open and close with the motion where the look has it, and take their places
 * at once otherwise (Rulings 20). The code calls a tab a parent tab (Rulings 1). EDT only.
 */
final class ParentTabBar {
    private final WindowContent owner;
    private final ParentTabsView parentTabs;
    private final TabStrip strip;
    /** While {@link #atOnce} runs: the strip takes every change at once. */
    private boolean still;

    ParentTabBar(WindowContent owner, ParentTabsView parentTabs) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.parentTabs = Objects.requireNonNull(parentTabs, "parentTabs");
        strip = owner.look().tabs(this);
        strip.setAnimations(owner.animations());
        WorkspaceTheme.apply(strip.component(), owner.theme());
        // The window starts with one tab; refresh keeps the strip's visibility from then on.
        strip.component().setVisible(owner.look().showsTabStrip(1));
    }

    /** The look's strip. */
    TabStrip strip() { return strip; }

    /** Takes the tabs and shows or hides the strip for their count at once: hidden first, shown last. */
    void refresh() {
        boolean shown = owner.look().showsTabStrip(parentTabs.count());
        if (!shown) strip.component().setVisible(false);
        strip.setActive(owner.windowActive());
        strip.component().setVisible(shown);
    }

    void close() { strip.close(); }

    /** The window the strip is in. */
    WindowContent owner() { return owner; }

    /** Each tab's active item, in strip order; a window's empty workspace has none. */
    List<Tab> tabs() {
        var shown = new ArrayList<Tab>();
        for (WorkspaceView view : parentTabs.views()) if (view.selected() != null) shown.add(view.selected());
        return shown;
    }

    /** The shown tab's active item; null in the empty workspace. */
    Tab selected() { return parentTabs.active().selected(); }
    String title(Tab tab) { return owner.tabTitle(tab); }
    Icon icon(Tab tab) { return owner.tabIcon(tab); }
    String tooltip(Tab tab) { return owner.tabTooltip(tab); }
    /** Select Tab's shortcut for the entry at {@code index}, as this platform writes it; empty for none. */
    String shortcut(int index) { return owner.tabShortcut(index); }
    /** Whether the entry is a terminal's, which the classic tabs mark with a status dot. */
    boolean terminal(Tab tab) { return tab.kind() == Tab.Kind.TERMINAL; }
    /** Whether the entry's shell runs, which the strip tells assistive technology. */
    boolean running(Tab tab) { return owner.running(tab); }
    /** The entry's menu (workspace plan 8a Rulings 8). */
    JPopupMenu menu(Tab tab) { return owner.tabMenu(tab); }
    /** Shows the tab whose entry shows {@code tab}. */
    void select(Tab tab) { owner.parentTabOf(tab).ifPresent(owner::selectParentTab); }
    /** Closes the tab whose entry shows {@code tab}: every pane, after their items' guards. */
    void close(Tab tab) { owner.parentTabOf(tab).ifPresent(owner::closeParentTab); }
    /**
     * A left press on a tab's entry: the tab drag may start from it, carrying the whole tab (spec 6). A dock drag still
     * running lost its release, so it ends first.
     */
    void pressed(Tab tab, MouseEvent event) {
        owner.dockDrag().cancel();
        owner.tabDrag().pressed(this, tab, event);
    }
    /** A drag event from the strip, which the tab drag follows. */
    void dragged(MouseEvent event) { owner.tabDrag().dragged(event); }
    /** A release on the strip, which ends the tab drag. */
    void released(MouseEvent event) { owner.tabDrag().released(event); }
    /** The classic tabs' "+": New Tab (workspace plan 8a Rulings 11). */
    void newTab() { owner.invoke(ActionId.NEW_TAB); }
    /** New Tab: what the "+" opens. */
    String newTabName() { return ActionId.NEW_TAB.label(); }
    /** Close Tab: an entry's × closes every pane of its tab. */
    String closeName() { return ActionId.CLOSE_TAB.label(); }
    /** The tab whose entry shows {@code tab}; null for an item no tab of the window holds, such as a closed one. */
    ParentTabId id(Tab tab) { return owner.parentTabOf(tab).orElse(null); }
    /**
     * Runs {@code change} with the strip taking its places at once, as it takes a tab moving in from another window: a
     * pane dropped on a new-tab place makes a tab that opens with no motion (tabs and panes spec 5 and 7; workspace plan
     * 8c Rulings 9).
     */
    void atOnce(Runnable change) {
        boolean was = still;
        still = true;
        try {
            change.run();
        } finally {
            still = was;
        }
    }
    /** Whether the strip's entries move as tabs open and close: while the look has motion (Rulings 20), outside {@link #atOnce}. */
    boolean animates() { return !still && owner.lookAnimates(); }
}
