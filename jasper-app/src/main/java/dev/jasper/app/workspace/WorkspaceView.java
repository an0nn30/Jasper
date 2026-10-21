package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ResolvedTheme;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.layout.Child;
import dev.jasper.app.layout.Group;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.Node;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.Split;
import dev.jasper.app.layout.TabId;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.util.ArrayList;
import java.util.HashMap;
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
 * One tab's workspace (tabs and panes spec 4), under the tab's id: its {@link Layout} and the items the layout places,
 * drawn as {@link SplitContainer}s over {@link GroupView}s, one pane each, or the maximized pane alone. Every change is a
 * layout operation; the view renders the active pane and the maximize the layout holds and never picks them itself. It
 * rebuilds its splits only when the layout's shape changes (orientation, children, panes, maximize); otherwise it moves
 * items' components between pane views and passes divider weights on. It never creates or disposes an item: its window
 * attaches and detaches them. Its layout accessor is {@code model()}: a {@code JPanel} cannot declare a {@code layout()}
 * that returns a value, because {@code java.awt.Container} has a {@code void layout()}. The code calls a tab a parent
 * tab, a pane a group and an item a tab (workspace plan 8a Rulings 1). EDT only.
 */
final class WorkspaceView extends JPanel {
    private final WindowContent owner;
    private ParentTabId id;
    private final Map<GroupId, GroupView> groups = new LinkedHashMap<>();
    private final Map<TabId, Tab> tabs = new HashMap<>();
    private final Map<List<Integer>, SplitContainer> splits = new HashMap<>();
    private Layout layout = Layout.empty();
    private Object shape;
    /** The shape of the main window's empty workspace, which shows {@link #empty} (workspace tabs spec 10). */
    private static final Object EMPTY = new Object();
    /** The main window's empty state, made the first time its workspace is empty. */
    private EmptyWorkspace empty;
    private boolean glass, closed;

    WorkspaceView(WindowContent owner, ParentTabId id) {
        super(new BorderLayout());
        this.owner = Objects.requireNonNull(owner, "owner");
        this.id = Objects.requireNonNull(id, "id");
        setName("workspace");
        render();
    }

    /** The layout this view renders. */
    Layout model() { return layout; }
    /** The tab this workspace is. */
    ParentTabId id() { return id; }
    /** The tab takes a new id ({@link ParentTabsView#reid}); its layout and items stay. */
    void id(ParentTabId value) { id = Objects.requireNonNull(value, "value"); }
    boolean isEmpty() { return layout.isEmpty(); }
    boolean contains(Tab tab) { return tab != null && tabs.get(tab.id()) == tab; }
    /** Every item in workspace order: the panes' items in tree order. */
    List<Tab> tabs() { return layout.tabs().stream().map(tabs::get).toList(); }
    int tabCount() { return tabs.size(); }
    /** The active pane's item; null in the empty workspace. */
    Tab selected() { return layout.activeTab().map(tabs::get).orElse(null); }
    Optional<Tab> tab(TabId id) { return Optional.ofNullable(tabs.get(id)); }
    List<GroupView> groupViews() { return List.copyOf(groups.values()); }
    GroupView groupView(GroupId id) { return groups.get(id); }
    GroupView activeGroupView() { return groups.get(layout.activeGroup()); }
    /** The split at {@code path}, while the layout has one there and shows it; null otherwise. */
    SplitContainer split(List<Integer> path) { return splits.get(path); }

    /** Puts {@code tab} into the empty workspace's pane, as a new tab's first item. */
    void open(Tab tab) { place(tab, layout.open(tab.id())); }

    /** Opens {@code tab} in a new pane on {@code side} of {@code group}; the empty workspace takes it in its pane. */
    void openBeside(Tab tab, GroupId group, Side side) {
        if (layout.isEmpty()) open(tab);
        else place(tab, layout.split(tab.id(), group, side));
    }

    /**
     * Shows a saved tab's layout over {@code restored}, items no window showed before, in this workspace, which holds none
     * (workspace tabs spec 13; tabs and panes spec 12): each item takes its saved pane at once.
     */
    void restoreSaved(Layout saved, List<Tab> restored) {
        if (!tabs.isEmpty() || !layout.isEmpty()) throw new IllegalStateException("Only an empty workspace restores");
        var placed = new HashMap<TabId, Tab>();
        for (Tab tab : restored) placed.put(tab.id(), tab);
        if (!placed.keySet().equals(Set.copyOf(saved.tabs()))) throw new IllegalArgumentException("The layout places other items");
        tabs.putAll(placed);
        apply(saved);
    }

    /**
     * {@code rebuilt} takes {@code placeholder}'s pane in its place, selected: a placeholder's Retry (workspace plan 6b
     * Rulings 6). The placeholder leaves this workspace; its window closes it.
     */
    void replace(Tab placeholder, Tab rebuilt) {
        if (!contains(placeholder)) return;
        Layout next = layout.replaceTab(placeholder.id(), rebuilt.id()).select(rebuilt.id());
        tabs.remove(placeholder.id());
        tabs.put(rebuilt.id(), rebuilt);
        apply(next);
    }

    private void place(Tab tab, Layout next) {
        tabs.put(tab.id(), tab);
        apply(next);
    }

    /** Takes {@code tab} out of the layout; its component leaves its pane, and its pane goes with it. */
    void remove(Tab tab) {
        if (!contains(tab)) return;
        Layout next = layout.remove(tab.id());
        tabs.remove(tab.id());
        apply(next);
    }

    /**
     * Takes back a pane move that failed (workspace plan 8c Rulings 17): this workspace returns to {@code before}, its
     * layout when the move began, holding {@code tab} again if {@code before} places it and letting it go if not.
     */
    void restore(Tab tab, Layout before) { restore(List.of(tab), before); }

    /**
     * As {@link #restore(Tab, Layout)}, for every item of {@code items}: a whole tab whose move into another tab failed
     * (workspace plan 9a Rulings 12).
     */
    void restore(List<Tab> items, Layout before) {
        for (Tab tab : items) {
            if (before.contains(tab.id())) tabs.put(tab.id(), tab);
            else tabs.remove(tab.id());
        }
        apply(before);
    }

    void select(Tab tab) { if (contains(tab)) apply(layout.select(tab.id())); }

    /**
     * Focus went into {@code tab}: it becomes the active item without being focused again, so focus stays where it went
     * (Rulings 26); nothing changes while it already is the active item, so no focus request loops back here.
     */
    void focused(Tab tab) { if (contains(tab) && selected() != tab) apply(layout.select(tab.id()), false); }
    void activate(GroupId group) { apply(layout.activate(group)); }

    /** Moves {@code tab}'s pane to {@code side} of {@code group}, another pane of this workspace (plan 8c's pane moves). */
    void moveToNewGroup(Tab tab, GroupId group, Side side) {
        if (!contains(tab)) return;
        apply(layout.moveToNewGroup(tab.id(), group, side));
    }

    /**
     * Takes {@code arrived}, every item of another tab, beside {@code group} as {@code arranged}, that tab's layout, gives
     * them (pane flexibility spec 5): one nested split on {@code side} of the pane ({@link Layout#insertBeside}), the moved
     * tab's active pane active and a maximize ended. Their components leave the workspace that showed them.
     */
    void join(List<Tab> arrived, Layout arranged, GroupId group, Side side) {
        Layout next = layout.insertBeside(arranged, group, side);
        for (Tab tab : arrived) tabs.put(tab.id(), tab);
        apply(next);
    }

    /**
     * Gives this workspace {@code arranged}: its own items in another arrangement, as a tab moves between windows
     * (workspace plan 7b Rulings 6). Items {@code arranged} names that this workspace does not hold leave it first, and an
     * item it holds that {@code arranged} lacks, such as one a listener opened here meanwhile, joins in a new pane to the
     * right of the active one, so no item is lost and {@code arranged}'s active item still shows. Nothing changes when
     * {@code arranged} names none of this workspace's items.
     */
    void rearrange(Layout arranged) {
        Layout next = arranged;
        for (TabId tab : arranged.tabs()) if (!tabs.containsKey(tab)) next = next.remove(tab);
        if (next.isEmpty()) return;
        Optional<TabId> shown = next.activeTab();
        boolean joined = false;
        for (TabId tab : layout.tabs()) {
            if (next.contains(tab)) continue;
            next = next.split(tab, next.activeGroup(), Side.RIGHT);
            joined = true;
        }
        if (joined && shown.isPresent()) next = next.select(shown.get());
        apply(next);
    }

    void resizeSplit(List<Integer> path, List<Double> weights) { apply(layout.resize(path, weights)); }
    void toggleMaximize() { apply(layout.toggleMaximize(layout.activeGroup())); }
    /** Ends a maximize, as an item moving in or out of the window does (spec 15). */
    void unmaximize() { if (layout.maximized().isPresent()) apply(layout.unmaximize()); }
    /** The pane in {@code direction} from the active one, by the layout's geometry. */
    Optional<GroupId> neighbour(Side direction) { return layout.neighbour(layout.activeGroup(), direction); }

    private void apply(Layout next) { apply(next, true); }

    /**
     * Renders {@code next}; with {@code refocus} the active item takes focus when it, its pane or the shape changed
     * (Rulings 10), provided the workspace held focus when the change began.
     */
    private void apply(Layout next, boolean refocus) {
        if (closed) return;
        Tab before = selected();
        GroupId group = layout.activeGroup();
        Object previous = shape;
        boolean held = refocus && holdsFocus();
        layout = next;
        render();
        owner.workspaceChanged(this, held
            && (selected() != before || !layout.activeGroup().equals(group) || !Objects.equals(previous, shape)));
    }

    private boolean holdsFocus() { return holdsFocus(owner, this); }

    /**
     * Whether focus may follow a change of {@code area}, a workspace or a window's tabs: the palette's card does not hold
     * the window's input, and the permanent focus owner is in {@code area} or nowhere a person sees it (a closed terminal,
     * or the palette that just closed to run a command). Focus in the open palette, the Run window or a tool window stays
     * there.
     */
    static boolean holdsFocus(WindowContent owner, Component area) {
        if (owner.commandPalette() != null && owner.commandPalette().blocksActions()) return false;
        Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner();
        return focus == null || !focus.isShowing() || SwingUtilities.isDescendingFrom(focus, area);
    }

    private void render() {
        var live = new LinkedHashMap<GroupId, GroupView>();
        for (Group group : layout.groups()) {
            GroupView view = groups.remove(group.id());
            live.put(group.id(), view != null ? view : new GroupView(group.id()));
        }
        for (GroupView gone : groups.values()) gone.close();
        groups.clear();
        groups.putAll(live);
        for (Group group : layout.groups()) groups.get(group.id()).display(group.selected().map(tabs::get).orElse(null));
        dimPanes();
        Node shown = layout.maximized().<Node>map(id -> layout.group(id).orElseThrow()).orElse(layout.root());
        // The main window's empty workspace shows New Terminal Tab and Open File in place of its empty pane (spec 10).
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
        if (empty == null) {
            empty = new EmptyWorkspace(owner);
            // It paints over the workspace surface, so its controls take the workspace theme.
            WorkspaceTheme.apply(empty, owner.theme());
        }
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

    /** Refreshes the empty state while it shows. */
    void refresh() {
        if (closed) return;
        if (empty != null) empty.refresh();
    }

    /**
     * Dims every pane but the active one while this tab holds two or more (tabs and panes spec 5; workspace plan 8a
     * Rulings 12), by the configured amount; a maximized pane is the active one and shows clear.
     */
    void dimPanes() {
        if (closed) return;
        boolean several = layout.groups().size() > 1;
        for (Group group : layout.groups()) {
            boolean dimmed = several && !group.id().equals(layout.activeGroup());
            groups.get(group.id()).dim(dimmed, dimmed ? owner.paneDim() : 0f, dimmed ? owner.theme().surface() : null);
        }
    }

    /**
     * Takes the theme: backgrounds, the empty state's zone, and the delegates of a pane the maximize keeps out of the tree
     * and of the empty state while an item hides it.
     */
    void applyTheme(ResolvedTheme theme, boolean updateDelegates) {
        setBackground(theme.surface());
        for (SplitContainer split : splits.values()) split.setBackground(theme.surface());
        for (GroupView group : groups.values())
            if (updateDelegates && !SwingUtilities.isDescendingFrom(group, this)) SwingUtilities.updateComponentTreeUI(group);
        if (updateDelegates && empty != null && !SwingUtilities.isDescendingFrom(empty, this)) SwingUtilities.updateComponentTreeUI(empty);
        if (empty != null) WorkspaceTheme.apply(empty, theme);
        dimPanes();
    }

    /** Below opacity 1 the workspace and its splits paint nothing behind the items; dividers stay solid. */
    void setGlass(boolean value) {
        glass = value;
        setOpaque(!value);
        for (SplitContainer split : splits.values()) split.setGlass(value);
        repaint();
    }

    /** The window closed, or this tab went: every pane is released; the items are its registry's to close. */
    void close() {
        if (closed) return;
        closed = true;
        for (GroupView group : groups.values()) group.close();
    }
}
