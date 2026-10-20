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
 * and paints nothing itself. Only the shown parent tab's workspace is visible; the others stay in the tree, hidden, with
 * their groups, tabs and sessions as they were, so a switch restarts nothing. A parent tab joins holding its first tab
 * and leaves with its last one, except the window's only parent tab, which the main window keeps as its empty
 * workspace. EDT only.
 */
final class ParentTabsView extends JPanel {
    private final WindowContent owner;
    /** Each parent tab's workspace; the model gives their order. */
    private final Map<ParentTabId, WorkspaceView> views = new HashMap<>();
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
    }

    /** The parent tabs in bar order with their layouts, and the one that shows. */
    ParentTabs model() { return model; }
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
     * 7a Rulings 3). The window's only parent tab must not be empty: the empty workspace takes a first tab itself. The tab
     * takes the focus when the window's parent tabs held it.
     */
    WorkspaceView openParentTab(Tab tab) {
        boolean held = WorkspaceView.holdsFocus(owner, this);
        var view = new WorkspaceView(owner, ParentTabId.random());
        try {
            view.open(tab);
            model = model.open(new ParentTab(view.id(), view.model()), count());
        } catch (RuntimeException | Error failure) {
            view.close();
            throw failure;
        }
        views.put(view.id(), view);
        add(view, 0);
        reveal(view);
        owner.workspaceChanged(view, held);
        return view;
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

    /** Refreshes every parent tab's strips. */
    void refresh() {
        if (closed) return;
        for (WorkspaceView view : views.values()) view.refresh();
    }

    /** Every parent tab takes the theme; a hidden one is in the tree, so the window's tree update reaches its delegates. */
    void applyTheme(ResolvedTheme theme, boolean updateDelegates) {
        for (WorkspaceView view : views.values()) view.applyTheme(theme, updateDelegates);
    }

    void setGlass(boolean value) {
        for (WorkspaceView view : views.values()) view.setGlass(value);
        repaint();
    }

    void setAnimations(AnimationSettings settings) {
        for (WorkspaceView view : views.values()) view.setAnimations(settings);
    }

    /** Every strip takes a new tab height. */
    void revalidateStrips() {
        for (WorkspaceView view : views.values())
            for (GroupView group : view.groupViews()) {
                group.strip().component().revalidate();
                group.strip().component().repaint();
            }
    }

    /** The window closed: every parent tab's strips are released; the tabs are its registry's to close. */
    void close() {
        if (closed) return;
        closed = true;
        for (WorkspaceView view : views.values()) view.close();
    }

    /** Every parent tab's workspace fills the view; only the shown one is visible. */
    private static final class Stack implements LayoutManager {
        @Override public void addLayoutComponent(String name, Component component) { }
        @Override public void removeLayoutComponent(Component component) { }
        @Override public Dimension preferredLayoutSize(Container parent) { return largest(parent, false); }
        @Override public Dimension minimumLayoutSize(Container parent) { return largest(parent, true); }
        @Override public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            for (Component child : parent.getComponents())
                child.setBounds(insets.left, insets.top, parent.getWidth() - insets.left - insets.right,
                    parent.getHeight() - insets.top - insets.bottom);
        }
        private static Dimension largest(Container parent, boolean minimum) {
            int width = 0, height = 0;
            for (Component child : parent.getComponents()) {
                Dimension size = minimum ? child.getMinimumSize() : child.getPreferredSize();
                width = Math.max(width, size.width);
                height = Math.max(height, size.height);
            }
            Insets insets = parent.getInsets();
            return new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom);
        }
    }
}
