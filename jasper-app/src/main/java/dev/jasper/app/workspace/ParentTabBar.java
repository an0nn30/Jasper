package dev.jasper.app.workspace;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.contributions.Tab;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.Icon;
import javax.swing.JPopupMenu;

/**
 * A window's parent-tab bar (parent tabs spec 7): the look's strip over the window's parent tabs, each shown as its
 * active tab, as terminal apps title a tab by its active pane (spec 4). It shows while the look says so for the window's
 * count of parent tabs: from two under the equal tabs, always under the classic tabs, which show no entry for the main
 * window's empty workspace. A click shows a parent tab, its close button and its menu close one after its tabs' guards,
 * the classic "+" is New Tab; its entries never move, and a press on one starts no drag. EDT only.
 */
final class ParentTabBar implements StripSource {
    private final WindowContent owner;
    private final ParentTabsView parentTabs;
    private final TabStrip strip;

    ParentTabBar(WindowContent owner, ParentTabsView parentTabs) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.parentTabs = Objects.requireNonNull(parentTabs, "parentTabs");
        strip = owner.look().tabs(this);
        // A search by name meets a pane's strip, never the bar (workspace plan 7a Rulings 19).
        strip.component().setName("parentTabBar");
        strip.setAnimations(owner.animations());
        WorkspaceTheme.apply(strip.component(), owner.theme());
        // The window starts with one parent tab; refresh keeps the bar's visibility from then on.
        strip.component().setVisible(owner.look().showsParentTabBar(1));
    }

    TabStrip strip() { return strip; }

    /** Takes the parent tabs and shows or hides the bar for their count at once: hidden first, shown last. */
    void refresh() {
        boolean shown = owner.look().showsParentTabBar(parentTabs.count());
        if (!shown) strip.component().setVisible(false);
        strip.setActive(owner.windowActive());
        strip.component().setVisible(shown);
    }

    void close() { strip.close(); }

    @Override public WindowContent owner() { return owner; }

    /** Each parent tab's active tab, in bar order; the main window's empty workspace has none. */
    @Override public List<Tab> tabs() {
        var shown = new ArrayList<Tab>();
        for (WorkspaceView view : parentTabs.views()) if (view.selected() != null) shown.add(view.selected());
        return shown;
    }

    @Override public Tab selected() { return parentTabs.active().selected(); }
    @Override public String title(Tab tab) { return owner.tabTitle(tab); }
    @Override public Icon icon(Tab tab) { return owner.tabIcon(tab); }
    @Override public String tooltip(Tab tab) { return owner.tabTooltip(tab); }
    /** Select Parent Tab's shortcut for the entry at {@code index}. */
    @Override public String shortcut(int index) { return owner.parentTabShortcut(index); }
    @Override public boolean terminal(Tab tab) { return tab.kind() == Tab.Kind.TERMINAL; }
    @Override public boolean running(Tab tab) { return owner.running(tab); }
    @Override public JPopupMenu menu(Tab tab) { return owner.parentTabMenu(tab); }
    @Override public void select(Tab tab) { owner.parentTabOf(tab).ifPresent(owner::selectParentTab); }
    @Override public void close(Tab tab) { owner.parentTabOf(tab).ifPresent(owner::closeParentTab); }
    /** A press ends a dock drag whose release was lost; a parent tab does not drag. */
    @Override public void pressed(Tab tab, MouseEvent event) { owner.dockDrag().cancel(); }
    @Override public void dragged(MouseEvent event) { }
    @Override public void released(MouseEvent event) { }
    /** The classic tabs' "+": New Tab, a parent tab. */
    @Override public void newTab() { owner.invoke(ActionId.NEW_TAB); }
    /** New Tab: the bar's "+" opens a parent tab. */
    @Override public String newTabName() { return ActionId.NEW_TAB.label(); }
    /** Close Parent Tab: an entry's × closes every tab of its parent tab. */
    @Override public String closeName() { return ActionId.CLOSE_PARENT_TAB.label(); }
    /** The bar's entries take their places at once. */
    @Override public boolean moved(Tab tab) { return true; }
    @Override public boolean animates() { return false; }
}
