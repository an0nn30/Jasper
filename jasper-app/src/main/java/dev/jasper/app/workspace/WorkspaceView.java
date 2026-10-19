package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ResolvedTheme;
import dev.jasper.app.config.AnimationSettings;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.Child;
import dev.jasper.app.layout.Group;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.Node;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.Split;
import dev.jasper.app.layout.TabId;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * One window's workspace (workspace tabs spec 10): its {@link Layout} and the tabs the layout places, drawn as
 * {@link SplitContainer}s over {@link GroupView}s, or the maximized group alone. Every change is a layout operation; the
 * view renders the selection, the active group and the maximize the layout holds and never picks them itself. It
 * rebuilds its splits only when the layout's shape changes (orientation, children, groups, maximize); otherwise it moves
 * tab components between group views, shows selections and passes divider weights on. It never creates or disposes a
 * tab: its window attaches and detaches them. Its layout accessor is {@code model()}: a {@code JPanel} cannot declare a
 * {@code layout()} that returns a value, because {@code java.awt.Container} has a {@code void layout()}. EDT only.
 */
final class WorkspaceView extends JPanel {
    private final WindowContent owner;
    private final Map<GroupId, GroupView> groups = new LinkedHashMap<>();
    private final Map<TabId, Tab> tabs = new HashMap<>();
    private final Map<List<Integer>, SplitContainer> splits = new HashMap<>();
    /** The tabs that came into their group by a move, which the strips show at once, without motion (Rulings 27). */
    private final Set<TabId> moved = new HashSet<>();
    private Layout layout = Layout.empty();
    private Object shape;
    /** The shape of the main window's empty workspace, which shows {@link #empty} (workspace tabs spec 10). */
    private static final Object EMPTY = new Object();
    /** The main window's empty state, made the first time its workspace is empty. */
    private EmptyWorkspace empty;
    private boolean glass, closed;

    WorkspaceView(WindowContent owner) {
        super(new BorderLayout());
        this.owner = Objects.requireNonNull(owner, "owner");
        setName("workspace");
        render();
    }

    /** The layout this view renders. */
    Layout model() { return layout; }
    boolean isEmpty() { return layout.isEmpty(); }
    boolean contains(Tab tab) { return tab != null && tabs.get(tab.id()) == tab; }
    /** Every tab in workspace order: the groups in tree order, each group's tabs in strip order. */
    List<Tab> tabs() { return layout.tabs().stream().map(tabs::get).toList(); }
    int tabCount() { return tabs.size(); }
    /** The active group's selected tab; null in the empty workspace. */
    Tab selected() { return layout.activeTab().map(tabs::get).orElse(null); }
    Optional<Tab> tab(TabId id) { return Optional.ofNullable(tabs.get(id)); }
    List<GroupView> groupViews() { return List.copyOf(groups.values()); }
    GroupView groupView(GroupId id) { return groups.get(id); }
    GroupView activeGroupView() { return groups.get(layout.activeGroup()); }
    /** The split at {@code path}, while the layout has one there and shows it; null otherwise. */
    SplitContainer split(List<Integer> path) { return splits.get(path); }

    /** Opens {@code tab} at the end of the active group, selected; a tab arriving from another window counts as moved. */
    void open(Tab tab) {
        if (tab.moving()) moved.add(tab.id());
        Group active = layout.group(layout.activeGroup()).orElseThrow();
        place(tab, layout.open(tab.id(), active.id(), active.tabs().size()));
    }

    /** Opens {@code tab} in a new group on {@code side} of {@code group}; the empty workspace opens it in its group. */
    void openBeside(Tab tab, GroupId group, Side side) {
        if (layout.isEmpty()) open(tab);
        else place(tab, layout.split(tab.id(), group, side));
    }

    private void place(Tab tab, Layout next) {
        tabs.put(tab.id(), tab);
        apply(next);
    }

    /** Takes {@code tab} out of the layout; its component leaves its group view. */
    void remove(Tab tab) {
        if (!contains(tab)) return;
        Layout next = layout.remove(tab.id());
        tabs.remove(tab.id());
        moved.remove(tab.id());
        apply(next);
    }

    void select(Tab tab) { if (contains(tab)) apply(layout.select(tab.id())); }

    /**
     * Focus went into {@code tab}: it becomes the active tab without being focused again, so focus stays where it went
     * (Rulings 26); nothing changes while it already is the active tab, so no focus request loops back here.
     */
    void focused(Tab tab) { if (contains(tab) && selected() != tab) apply(layout.select(tab.id()), false); }
    void activate(GroupId group) { apply(layout.activate(group)); }
    /** Moves {@code tab} to {@code index} of {@code group}, which may be another group. */
    void moveTab(Tab tab, GroupId group, int index) {
        if (!contains(tab)) return;
        moved.add(tab.id());
        apply(layout.move(tab.id(), group, index));
    }

    /** Moves {@code tab} to {@code index} of {@code group}, its own, keeping the tab that group showed. */
    void reorder(Tab tab, GroupId group, int index) {
        if (!contains(tab)) return;
        Optional<TabId> shown = layout.group(group).flatMap(Group::selected);
        Layout next = layout.move(tab.id(), group, index);
        if (shown.isPresent() && !shown.get().equals(tab.id())) next = next.select(shown.get());
        apply(next);
    }

    void moveToNewGroup(Tab tab, GroupId group, Side side) {
        if (!contains(tab)) return;
        moved.add(tab.id());
        apply(layout.moveToNewGroup(tab.id(), group, side));
    }
    void resizeSplit(List<Integer> path, List<Double> weights) { apply(layout.resize(path, weights)); }
    void toggleMaximize() { apply(layout.toggleMaximize(layout.activeGroup())); }
    /** Ends a maximize, as a tab moving in or out of the window does (spec 15). */
    void unmaximize() { if (layout.maximized().isPresent()) apply(layout.unmaximize()); }
    /** Whether {@code tab} came into its group by a move rather than by opening (Rulings 27). */
    boolean moved(Tab tab) { return moved.contains(tab.id()); }
    /** The group in {@code direction} from the active one, by the layout's geometry. */
    Optional<GroupId> neighbour(Side direction) { return layout.neighbour(layout.activeGroup(), direction); }

    private void apply(Layout next) { apply(next, true); }

    /**
     * Renders {@code next}; with {@code refocus} the active tab takes focus when it or the shape changed (Rulings 10),
     * provided the workspace held focus when the change began.
     */
    private void apply(Layout next, boolean refocus) {
        if (closed) return;
        Tab before = selected();
        Object previous = shape;
        boolean held = refocus && holdsFocus();
        layout = next;
        render();
        owner.workspaceChanged(held && (selected() != before || !Objects.equals(previous, shape)));
    }

    /**
     * Whether focus may follow a change of this workspace: the palette's card does not hold the window's input, and the
     * permanent focus owner is in this workspace or nowhere a person sees it (a closed terminal, or the palette that just
     * closed to run a command). Focus in the open palette, the Run window or a tool window stays there.
     */
    private boolean holdsFocus() {
        if (owner.commandPalette() != null && owner.commandPalette().blocksActions()) return false;
        Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner();
        return focus == null || !focus.isShowing() || SwingUtilities.isDescendingFrom(focus, this);
    }

    private void render() {
        var live = new LinkedHashMap<GroupId, GroupView>();
        for (Group group : layout.groups()) {
            GroupView view = groups.remove(group.id());
            live.put(group.id(), view != null ? view : new GroupView(owner, this, group.id()));
        }
        for (GroupView gone : groups.values()) gone.close();
        groups.clear();
        groups.putAll(live);
        for (Group group : layout.groups())
            groups.get(group.id()).display(group.tabs().stream().map(tabs::get).toList(), group.selected().map(tabs::get).orElse(null));
        Node shown = layout.maximized().<Node>map(id -> layout.group(id).orElseThrow()).orElse(layout.root());
        // The main window's empty workspace shows New Terminal Tab and Open File in place of its empty group (spec 10).
        boolean showEmpty = layout.isEmpty() && owner.isMain();
        Object next = showEmpty ? EMPTY : shape(shown);
        if (!next.equals(shape)) {
            shape = next;
            splits.clear();
            removeAll();
            add(showEmpty ? emptyState() : build(shown, List.of()), BorderLayout.CENTER);
            revalidate();
        } else {
            for (var entry : splits.entrySet()) entry.getValue().setWeights(weightsAt(entry.getKey()));
        }
        repaint();
    }

    private EmptyWorkspace emptyState() {
        if (empty == null) empty = new EmptyWorkspace(owner);
        empty.refresh();
        return empty;
    }

    /** The main window's empty state while it shows; null otherwise. */
    EmptyWorkspace emptyWorkspace() { return shape == EMPTY ? empty : null; }

    /** The tree's shape without its weights: what a rebuild depends on. */
    private static Object shape(Node node) {
        return switch (node) {
            case Group group -> group.id();
            case Split split -> List.of(split.orientation(), split.children().stream().map(child -> shape(child.node())).toList());
        };
    }

    private JComponent build(Node node, List<Integer> path) {
        return switch (node) {
            case Group group -> groups.get(group.id());
            case Split split -> {
                var children = new ArrayList<JComponent>();
                for (int i = 0; i < split.children().size(); i++) {
                    var child = new ArrayList<>(path);
                    child.add(i);
                    children.add(build(split.children().get(i).node(), List.copyOf(child)));
                }
                var container = new SplitContainer(split.orientation(), path, split.children().stream().map(Child::weight).toList(),
                    children, this::resizeSplit);
                container.setBackground(getBackground());
                container.setGlass(glass);
                splits.put(path, container);
                yield container;
            }
        };
    }

    private List<Double> weightsAt(List<Integer> path) {
        Node node = layout.root();
        for (int index : path) node = ((Split) node).children().get(index).node();
        return ((Split) node).children().stream().map(Child::weight).toList();
    }

    /** Refreshes every group's strip: its tabs, titles, selection and activity. */
    void refresh() {
        if (closed) return;
        for (GroupView group : groups.values()) group.refresh();
        if (empty != null) empty.refresh();
    }

    /** Takes the theme: backgrounds, the strips' zone, and the delegates of a group the maximize keeps out of the tree. */
    void applyTheme(ResolvedTheme theme, boolean updateDelegates) {
        setBackground(theme.surface());
        for (SplitContainer split : splits.values()) split.setBackground(theme.surface());
        for (GroupView group : groups.values()) {
            if (updateDelegates && !SwingUtilities.isDescendingFrom(group, this)) SwingUtilities.updateComponentTreeUI(group);
            WorkspaceTheme.apply(group.strip().component(), theme);
            group.refresh();
        }
    }

    /** Below opacity 1 the workspace and its splits paint nothing behind the tabs; dividers stay solid. */
    void setGlass(boolean value) {
        glass = value;
        setOpaque(!value);
        for (SplitContainer split : splits.values()) split.setGlass(value);
        repaint();
    }

    void setAnimations(AnimationSettings settings) { for (GroupView group : groups.values()) group.strip().setAnimations(settings); }

    /** The window closed: every strip is released; the tabs are its registry's to close. */
    void close() {
        if (closed) return;
        closed = true;
        for (GroupView group : groups.values()) group.close();
    }
}
