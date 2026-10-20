package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ResolvedTheme;
import dev.jasper.app.config.AnimationSettings;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.ParentTabs;
import dev.jasper.app.layout.TabId;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.swing.JPanel;

/**
 * A window's parent tabs (parent tabs spec 4 and 7): a {@link WorkspaceView} for each, and the {@link ParentTabs} they
 * make up, which every layout change keeps current. It stands where the terminal column stood, in the dock's centre,
 * with the parent-tab bar ({@link ParentTabBar}) on top, and paints nothing itself. Only the shown parent tab's
 * workspace is visible; the others stay in the tree, hidden, with their groups, tabs and sessions as they were, so a
 * switch restarts nothing. A parent tab joins holding its first tab and leaves with its last one, except the window's
 * only parent tab, which the main window keeps as its empty workspace. EDT only.
 */
final class ParentTabsView extends JPanel {
    private final WindowContent owner;
    /** Each parent tab's workspace; the model gives their order. */
    private final Map<ParentTabId, WorkspaceView> views = new HashMap<>();
    /** The bar over the parent tabs (parent tabs spec 7), the view's last child (workspace plan 7a Rulings 19). */
    private final ParentTabBar bar;
    private ParentTabs model;
    private boolean closed;

    ParentTabsView(WindowContent owner) {
        super(new Stack());
        this.owner = Objects.requireNonNull(owner, "owner");
        setName("parentTabs");
        setOpaque(false);
        var first = new WorkspaceView(owner, ParentTabId.random());
        views.put(first.id(), first);
        add(first);
        model = ParentTabs.of(new ParentTab(first.id(), first.model()));
        bar = new ParentTabBar(owner, this);
        add(bar.strip().component());
    }

    /** The parent tabs in bar order with their layouts, and the one that shows. */
    ParentTabs model() { return model; }
    /** The parent-tab bar. */
    ParentTabBar bar() { return bar; }
    /** The shown parent tab's workspace. */
    WorkspaceView active() { return views.get(model.active()); }
    /** Every parent tab's workspace, in bar order. */
    List<WorkspaceView> views() { return model.parentTabs().stream().map(parent -> views.get(parent.id())).toList(); }
    /** How many parent tabs the window has. */
    int count() { return model.parentTabs().size(); }
    /** The workspace of the parent tab {@code id}; empty for a parent tab the window does not have. */
    Optional<WorkspaceView> view(ParentTabId id) { return Optional.ofNullable(views.get(id)); }

    /** The workspace of the parent tab holding {@code tab}; empty for a tab the window does not hold, and for null. */
    Optional<WorkspaceView> holding(Tab tab) {
        for (WorkspaceView view : views.values()) if (view.contains(tab)) return Optional.of(view);
        return Optional.empty();
    }

    /** The workspace of the parent tab holding {@code group} (workspace plan 7a Rulings 8). */
    Optional<WorkspaceView> holding(GroupId group) {
        for (WorkspaceView view : views.values()) if (view.model().group(group).isPresent()) return Optional.of(view);
        return Optional.empty();
    }

    /** The group holding {@code tab}, in whichever parent tab holds it: the tab registry's {@code groupOf}. */
    Optional<GroupId> groupOf(TabId tab) {
        for (WorkspaceView view : views.values()) {
            Optional<GroupId> group = view.model().groupOf(tab);
            if (group.isPresent()) return group;
        }
        return Optional.empty();
    }

    boolean contains(Tab tab) { return holding(tab).isPresent(); }

    /** Every tab of the window: the parent tabs in bar order, each one's tabs in workspace order. */
    List<Tab> tabs() {
        var all = new ArrayList<Tab>();
        for (WorkspaceView view : views()) all.addAll(view.tabs());
        return all;
    }

    int tabCount() {
        int count = 0;
        for (WorkspaceView view : views.values()) count += view.tabCount();
        return count;
    }

    /** Whether the window holds no tab: its only parent tab is empty, as only the main window's may stay. */
    boolean isEmpty() { return count() == 1 && active().isEmpty(); }

    /**
     * A new parent tab holding {@code tab}, at the end of the bar and shown (New Tab, parent tabs spec 8; workspace plan
     * 7a Rulings 3). While the window's only parent tab is empty, the new one takes its place. Its workspace takes the
     * window's theme and glass first, so the splits it builds take them too. The tab takes the focus when the window's
     * parent tabs held it.
     */
    WorkspaceView openParentTab(Tab tab) { return openParentTab(tab, ParentTabId.random(), count()); }

    /**
     * As {@link #openParentTab(Tab)}, under {@code id} at {@code index} of the bar (its end past it): a parent tab moving
     * in from another window keeps its id (parent tabs spec 11; workspace plan 7b Rulings 10). While the window's only
     * parent tab is empty, the new one takes its place.
     */
    WorkspaceView openParentTab(Tab tab, ParentTabId id, int index) {
        boolean held = WorkspaceView.holdsFocus(owner, this);
        WorkspaceView empty = isEmpty() ? active() : null;
        var view = workspace(id);
        int at = empty != null ? 0 : Math.max(0, Math.min(index, count()));
        try {
            view.open(tab);
            var opened = new ParentTab(id, view.model());
            model = empty != null ? ParentTabs.of(opened) : model.open(opened, at);
        } catch (RuntimeException | Error failure) {
            view.close();
            throw failure;
        }
        if (empty != null) {
            views.remove(empty.id());
            super.remove(empty);
            empty.close();
        }
        views.put(view.id(), view);
        // The views keep the bar's order, ahead of any other child.
        add(view, at);
        reveal(view);
        owner.workspaceChanged(view, held);
        return view;
    }

    /**
     * Puts {@code tab}, moving in, in the active pane of the parent tab {@code id}; a parent tab the window lacks opens
     * under that id at {@code index} of the bar ({@link #openParentTab(Tab, ParentTabId, int)}). Workspace plan 7b's moves.
     */
    void land(Tab tab, ParentTabId id, int index) {
        WorkspaceView view = views.get(id);
        if (view != null) view.open(tab);
        else openParentTab(tab, id, index);
    }

    /**
     * Moves the parent tab {@code id} to {@code index} among the others in the bar, where a drag along the bar dropped
     * it (parent tabs spec 5): its workspace takes its new place among the views. The one that shows stays, no tab
     * moves, and the bar takes the new order at once.
     */
    void reorder(ParentTabId id, int index) {
        if (closed || !views.containsKey(id) || index < 0 || index >= count() || model.indexOf(id) == index) return;
        model = model.move(id, index);
        // The views keep the bar's order, ahead of any other child; a z-order move keeps the focus where it is.
        setComponentZOrder(views.get(id), index);
        owner.workspaceChanged(active(), false);
    }

    /** Shows the parent tab {@code id} as it was left (spec 7); the focus stays where it is, for the caller to move. */
    void select(ParentTabId id) {
        if (closed || model.active().equals(id) || !views.containsKey(id)) return;
        model = model.select(id);
        reveal(views.get(id));
        owner.workspaceChanged(active(), false);
    }

    /**
     * Takes {@code tab} out of its parent tab, as it closes or moves to another window. A parent tab's last tab takes the
     * parent tab with it and the one used before it shows, except the window's only parent tab (spec 4; workspace plan 7a
     * Rulings 2 and 6); the one that shows then takes the focus when the parent tabs held it (Rulings 10).
     */
    void remove(Tab tab) {
        WorkspaceView view = holding(tab).orElse(null);
        if (view == null) return;
        if (view.tabCount() > 1 || count() == 1) {
            view.remove(tab);
            return;
        }
        boolean held = view == active() && WorkspaceView.holdsFocus(owner, this);
        model = model.remove(view.id());
        views.remove(view.id());
        super.remove(view);
        view.close();
        reveal(active());
        owner.workspaceChanged(active(), held);
    }

    /**
     * The window's only parent tab {@code id}, which moved whole to another window and keeps its id there (parent tabs spec
     * 11), gives way to an empty workspace under a new id, so ids stay unique across windows (spec 4; workspace plan 7b
     * Rulings 10). The window manager's {@code moveParentTab} calls it; it does nothing unless {@code id} is this window's
     * only parent tab and empty, and the window is open.
     */
    void renew(ParentTabId id) {
        WorkspaceView emptied = views.get(id);
        if (closed || emptied == null || count() != 1 || !emptied.isEmpty()) return;
        boolean held = WorkspaceView.holdsFocus(owner, this);
        var fresh = workspace(ParentTabId.random());
        views.remove(emptied.id());
        super.remove(emptied);
        emptied.close();
        views.put(fresh.id(), fresh);
        add(fresh, 0);
        model = ParentTabs.of(new ParentTab(fresh.id(), fresh.model()));
        reveal(fresh);
        owner.workspaceChanged(fresh, held);
    }

    /**
     * The parent tab {@code old} takes the id {@code fresh}, in its place, with its layout, tabs and selection, as the one
     * that shows if it did (parent tabs spec 4: ids are unique across windows). The window manager's
     * {@code moveParentTab} calls it for a parent tab that moved here in part while the rest stayed under {@code old} in
     * the window it came from. It does nothing for a parent tab the window lacks, for an id it has, or once the window
     * closed.
     */
    void reid(ParentTabId old, ParentTabId fresh) {
        WorkspaceView view = views.get(old);
        if (closed || view == null || views.containsKey(fresh)) return;
        model = model.reid(old, fresh);
        views.remove(old);
        view.id(fresh);
        views.put(fresh, view);
        owner.workspaceChanged(view, false);
    }

    /**
     * A new parent tab's workspace under {@code id}, with the window's theme and glass, so the splits it builds take them
     * too.
     */
    private WorkspaceView workspace(ParentTabId id) {
        var view = new WorkspaceView(owner, id);
        try {
            view.applyTheme(owner.theme(), false);
            view.setGlass((float) owner.backgroundOpacity() < 1f);
        } catch (RuntimeException | Error failure) {
            view.close();
            throw failure;
        }
        return view;
    }

    /** A parent tab's layout changed: the model takes it. A workspace that is not, or no longer, a parent tab's is ignored. */
    void changed(WorkspaceView view) {
        if (!closed && views.get(view.id()) == view) model = model.replace(new ParentTab(view.id(), view.model()));
    }

    /** Shows {@code shown} alone; the others stay in the tree, hidden. */
    private void reveal(WorkspaceView shown) {
        shown.setVisible(true);
        for (WorkspaceView view : views.values()) if (view != shown) view.setVisible(false);
        revalidate();
        repaint();
    }

    /** Refreshes the bar and every parent tab's strips. */
    void refresh() {
        if (closed) return;
        bar.refresh();
        for (WorkspaceView view : views.values()) view.refresh();
    }

    /** Every parent tab takes the theme; a hidden one is in the tree, so the window's tree update reaches its delegates. */
    void applyTheme(ResolvedTheme theme, boolean updateDelegates) {
        for (WorkspaceView view : views.values()) view.applyTheme(theme, updateDelegates);
        WorkspaceTheme.apply(bar.strip().component(), theme);
        bar.refresh();
    }

    void setGlass(boolean value) {
        for (WorkspaceView view : views.values()) view.setGlass(value);
        repaint();
    }

    void setAnimations(AnimationSettings settings) {
        for (WorkspaceView view : views.values()) view.setAnimations(settings);
        bar.strip().setAnimations(settings);
    }

    /** Every strip, the bar's too, takes a new tab height. */
    void revalidateStrips() {
        bar.strip().component().revalidate();
        bar.strip().component().repaint();
        for (WorkspaceView view : views.values())
            for (GroupView group : view.groupViews()) {
                group.strip().component().revalidate();
                group.strip().component().repaint();
            }
    }

    /** The window closed: the bar and every parent tab's strips are released; the tabs are its registry's to close. */
    void close() {
        if (closed) return;
        closed = true;
        bar.close();
        for (WorkspaceView view : views.values()) view.close();
    }

    /** The bar on top at its preferred height while it shows; every parent tab's workspace fills the rest. */
    private static final class Stack implements LayoutManager {
        @Override public void addLayoutComponent(String name, Component component) { }
        @Override public void removeLayoutComponent(Component component) { }
        @Override public Dimension preferredLayoutSize(Container parent) { return size(parent, false); }
        @Override public Dimension minimumLayoutSize(Container parent) { return size(parent, true); }
        @Override public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            int width = parent.getWidth() - insets.left - insets.right, top = insets.top;
            Component bar = bar(parent);
            if (bar != null) {
                int height = bar.isVisible() ? bar.getPreferredSize().height : 0;
                bar.setBounds(insets.left, top, width, height);
                top += height;
            }
            for (Component child : parent.getComponents())
                if (child != bar) child.setBounds(insets.left, top, width, Math.max(0, parent.getHeight() - top - insets.bottom));
        }
        /** The bar's strip; null while the view is being built. */
        private static Component bar(Container parent) {
            ParentTabBar bar = ((ParentTabsView) parent).bar;
            return bar == null ? null : bar.strip().component();
        }
        private static Dimension size(Container parent, boolean minimum) {
            Component bar = bar(parent);
            int width = 0, height = 0;
            for (Component child : parent.getComponents()) {
                if (child == bar) continue;
                Dimension size = minimum ? child.getMinimumSize() : child.getPreferredSize();
                width = Math.max(width, size.width);
                height = Math.max(height, size.height);
            }
            if (bar != null && bar.isVisible()) {
                Dimension size = minimum ? bar.getMinimumSize() : bar.getPreferredSize();
                width = Math.max(width, size.width);
                height += size.height;
            }
            Insets insets = parent.getInsets();
            return new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom);
        }
    }
}
