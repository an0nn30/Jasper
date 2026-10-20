package dev.jasper.app.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * One workspace's layout (spec 7): a tree of splits whose leaves are tab groups, the active group, the maximized group
 * if any, the groups' order of use and each group's tab order of use. Immutable: each operation returns a new layout and
 * rejects arguments that would break an invariant of spec 7.2 with an {@link IllegalArgumentException}. Equality covers
 * the tree, the active group and the maximized group; the orders of use only decide focus and selection after a removal
 * and are not saved.
 */
public final class Layout {
    private final Node root;
    private final GroupId activeGroup;
    private final Optional<GroupId> maximized;
    private final List<GroupId> recent;
    private final Map<GroupId, List<TabId>> tabOrders;
    private final List<Group> groups;

    /**
     * A layout over {@code root}. Its order of use starts with {@code activeGroup}, then the other groups in tree order;
     * each group's tab order of use starts with its selected tab, then the tabs after it, then those before it, nearest
     * first (workspace plan 2, Rulings 7).
     *
     * @param root the tree: no group or tab twice, and no empty group unless it is the root (the empty workspace)
     * @param activeGroup a group of the tree
     * @param maximized the active group while it fills the workspace, which needs at least two groups; else empty
     * @throws IllegalArgumentException when an invariant does not hold
     */
    public Layout(Node root, GroupId activeGroup, Optional<GroupId> maximized) {
        this(root, activeGroup, maximized, List.of(), Map.of());
    }

    private Layout(Node root, GroupId activeGroup, Optional<GroupId> maximized, List<GroupId> recent,
            Map<GroupId, List<TabId>> tabOrders) {
        this.root = Objects.requireNonNull(root, "root");
        this.activeGroup = Objects.requireNonNull(activeGroup, "activeGroup");
        this.maximized = Objects.requireNonNull(maximized, "maximized");
        var found = new ArrayList<Group>();
        collect(root, found);
        var groupIds = new HashSet<GroupId>();
        var tabIds = new HashSet<TabId>();
        for (Group group : found) {
            if (!groupIds.add(group.id())) throw new IllegalArgumentException("Group " + group.id() + " is in the layout twice");
            for (TabId tab : group.tabs())
                if (!tabIds.add(tab)) throw new IllegalArgumentException("Tab " + tab + " is in the layout twice");
            if (group.tabs().isEmpty() && !(root instanceof Group))
                throw new IllegalArgumentException("Only the empty workspace's group may be empty: " + group.id());
        }
        if (!groupIds.contains(activeGroup))
            throw new IllegalArgumentException("The active group " + activeGroup + " is not in the layout");
        if (maximized.isPresent() && !maximized.get().equals(activeGroup))
            throw new IllegalArgumentException("The maximized group " + maximized.get() + " is not the active group");
        if (maximized.isPresent() && root instanceof Group)
            throw new IllegalArgumentException("A layout with one group cannot be maximized");
        var order = new LinkedHashSet<GroupId>();
        order.add(activeGroup);
        for (GroupId id : recent) if (groupIds.contains(id)) order.add(id);
        for (Group group : found) order.add(group.id());
        this.recent = List.copyOf(order);
        var orders = new HashMap<GroupId, List<TabId>>();
        for (Group group : found) orders.put(group.id(), tabOrder(group, tabOrders.getOrDefault(group.id(), List.of())));
        this.tabOrders = Map.copyOf(orders);
        this.groups = List.copyOf(found);
    }

    /**
     * The empty workspace (spec 7.2): one new, empty group, active.
     *
     * @return the layout
     */
    public static Layout empty() {
        GroupId id = GroupId.random();
        return new Layout(new Group(id, List.of(), Optional.empty()), id, Optional.empty());
    }

    /**
     * One new group holding {@code tab}, selected and active.
     *
     * @param tab the tab
     * @return the layout
     */
    public static Layout of(TabId tab) {
        Objects.requireNonNull(tab, "tab");
        GroupId id = GroupId.random();
        return new Layout(new Group(id, List.of(tab), Optional.of(tab)), id, Optional.empty());
    }

    /**
     * The tree.
     *
     * @return the root node
     */
    public Node root() {
        return root;
    }

    /**
     * The group that has focus, first in the order of use.
     *
     * @return its id
     */
    public GroupId activeGroup() {
        return activeGroup;
    }

    /**
     * The group that fills the workspace, always the active group.
     *
     * @return its id, or empty when no group is maximized
     */
    public Optional<GroupId> maximized() {
        return maximized;
    }

    /**
     * The groups' order of use, most recent first; not part of equality or the file.
     *
     * @return every group's id, the active group first
     */
    public List<GroupId> recent() {
        return recent;
    }

    /**
     * Every group in tree order: depth first, children in order.
     *
     * @return the groups
     */
    public List<Group> groups() {
        return groups;
    }

    /**
     * The group with this id.
     *
     * @param id the group's id
     * @return the group, or empty when it is not in the layout
     */
    public Optional<Group> group(GroupId id) {
        Objects.requireNonNull(id, "id");
        for (Group group : groups) if (group.id().equals(id)) return Optional.of(group);
        return Optional.empty();
    }

    /**
     * Every tab in workspace order: the groups in tree order, each group's tabs in strip order.
     *
     * @return the tabs
     */
    public List<TabId> tabs() {
        var tabs = new ArrayList<TabId>();
        for (Group group : groups) tabs.addAll(group.tabs());
        return List.copyOf(tabs);
    }

    /**
     * The group holding the tab.
     *
     * @param tab the tab
     * @return its group's id, or empty when the tab is not in the layout
     */
    public Optional<GroupId> groupOf(TabId tab) {
        Objects.requireNonNull(tab, "tab");
        for (Group group : groups) if (group.tabs().contains(tab)) return Optional.of(group.id());
        return Optional.empty();
    }

    /**
     * Whether a group holds the tab.
     *
     * @param tab the tab
     * @return true when the layout holds it
     */
    public boolean contains(TabId tab) {
        return groupOf(tab).isPresent();
    }

    /**
     * Whether this is the empty workspace: one group and no tabs.
     *
     * @return true for the empty workspace
     */
    public boolean isEmpty() {
        return root instanceof Group group && group.tabs().isEmpty();
    }

    /**
     * The active group's selected tab.
     *
     * @return the tab, or empty in the empty workspace
     */
    public Optional<TabId> activeTab() {
        return group(activeGroup).flatMap(Group::selected);
    }

    /**
     * Inserts {@code tab} into {@code group} at {@code index}, selects it and activates the group.
     *
     * @param tab a tab not in the layout
     * @param group a group of the layout
     * @param index 0 to the group's tab count
     * @return the new layout
     * @throws IllegalArgumentException when the tab is in the layout, the group is not, or the index is outside the group
     */
    public Layout open(TabId tab, GroupId group, int index) {
        Objects.requireNonNull(tab, "tab");
        if (contains(tab)) throw new IllegalArgumentException("Tab " + tab + " is already in the layout");
        Group target = requireGroup(group);
        checkIndex(index, target.tabs().size());
        var tabs = new ArrayList<>(target.tabs());
        tabs.add(index, tab);
        return activated(replace(root, group, found -> new Group(found.id(), tabs, Optional.of(tab))), group, false);
    }

    /**
     * Shows {@code tab} in its group and activates that group (workspace plan 2, Rulings 6).
     *
     * @param tab a tab of the layout
     * @return the new layout
     * @throws IllegalArgumentException when the tab is not in the layout
     */
    public Layout select(TabId tab) {
        GroupId group = requireGroupOf(tab);
        return activated(replace(root, group, found -> new Group(found.id(), found.tabs(), Optional.of(tab))), group, false);
    }

    /**
     * Gives {@code group} focus and puts it first in the order of use; a maximize moves to it.
     *
     * @param group a group of the layout
     * @return the new layout
     * @throws IllegalArgumentException when the group is not in the layout
     */
    public Layout activate(GroupId group) {
        requireGroup(group);
        return activated(root, group, false);
    }

    /**
     * Moves {@code tab} into {@code group} at {@code index}, counted without the tab, selects it and activates the group.
     * A source group left empty is removed as {@link #remove(TabId)} removes it.
     *
     * @param tab a tab of the layout
     * @param group a group of the layout, the tab's own or another
     * @param index 0 to the group's tab count without the tab
     * @return the new layout
     * @throws IllegalArgumentException when the tab or the group is not in the layout, or the index is outside the group
     */
    public Layout move(TabId tab, GroupId group, int index) {
        GroupId source = requireGroupOf(tab);
        Group target = requireGroup(group);
        var tabs = new ArrayList<>(target.tabs());
        if (source.equals(group)) {
            tabs.remove(tab);
            checkIndex(index, tabs.size());
            tabs.add(index, tab);
            return activated(replace(root, group, found -> new Group(found.id(), tabs, Optional.of(tab))), group, false);
        }
        checkIndex(index, tabs.size());
        tabs.add(index, tab);
        Layout rest = remove(tab);
        return rest.activated(replace(rest.root, group, found -> new Group(found.id(), tabs, Optional.of(tab))), group, true);
    }

    /**
     * Removes {@code tab}, closing it or moving it out. When it was its group's selected tab, the group's most recently
     * shown remaining tab is selected (workspace plan 2, Rulings 7). A group left empty is removed: its weight goes to the
     * sibling before it (after it when it was first), a split left with one child collapses into that child and nesting
     * is flattened again, and the active group becomes the most recently used remaining group. The maximize stays while
     * the maximized group stays and two groups remain (Rulings 5). Removing the last tab leaves the empty workspace,
     * keeping its group.
     *
     * @param tab a tab of the layout
     * @return the new layout
     * @throws IllegalArgumentException when the tab is not in the layout
     */
    public Layout remove(TabId tab) {
        GroupId id = requireGroupOf(tab);
        Group holder = group(id).orElseThrow();
        if (holder.tabs().size() > 1) {
            var tabs = new ArrayList<>(holder.tabs());
            tabs.remove(tab);
            TabId shown = holder.selected().orElseThrow();
            TabId selected = shown.equals(tab) ? tabOrders.get(id).get(1) : shown;
            Node next = replace(root, id, found -> new Group(found.id(), tabs, Optional.of(selected)));
            return new Layout(next, activeGroup, maximized, recent, tabOrders);
        }
        if (root instanceof Group) return new Layout(new Group(id, List.of(), Optional.empty()), id, Optional.empty(), recent, tabOrders);
        GroupId heir = recent.stream().filter(other -> !other.equals(id)).findFirst().orElseThrow();
        Node next = without(root, id).orElseThrow();
        Optional<GroupId> kept = maximized.filter(max -> !max.equals(id) && next instanceof Split);
        return new Layout(next, heir, kept, recent, tabOrders);
    }

    /**
     * Opens {@code tab} in a new group on {@code side} of {@code group}, selected and active. The new group takes half of
     * {@code group}'s space: a parent split of the side's orientation gains it as a sibling, otherwise the two share a new
     * split in {@code group}'s place. Clears the maximize.
     *
     * @param tab a tab not in the layout
     * @param group a group of the layout holding tabs
     * @param side where the new group goes
     * @return the new layout
     * @throws IllegalArgumentException when the tab is in the layout, or the group is not or holds no tabs
     */
    public Layout split(TabId tab, GroupId group, Side side) {
        Objects.requireNonNull(tab, "tab");
        Objects.requireNonNull(side, "side");
        if (contains(tab)) throw new IllegalArgumentException("Tab " + tab + " is already in the layout");
        if (requireGroup(group).tabs().isEmpty()) throw new IllegalArgumentException("The empty workspace's group cannot be split");
        return beside(tab, group, side);
    }

    /**
     * Moves {@code tab} into a new group on {@code side} of {@code group}, as {@link #split(TabId, GroupId, Side)} opens
     * one; a source group left empty is removed first, as {@link #remove(TabId)} removes it.
     *
     * @param tab a tab of the layout
     * @param group a group of the layout; the tab's own only when it holds other tabs
     * @param side where the new group goes
     * @return the new layout
     * @throws IllegalArgumentException when the tab or the group is not in the layout, or the tab is the group's only tab
     */
    public Layout moveToNewGroup(TabId tab, GroupId group, Side side) {
        Objects.requireNonNull(side, "side");
        GroupId source = requireGroupOf(tab);
        Group target = requireGroup(group);
        if (source.equals(group) && target.tabs().size() == 1)
            throw new IllegalArgumentException("Tab " + tab + " is the only tab of group " + group + " and cannot split it");
        return remove(tab).beside(tab, group, side);
    }

    /**
     * Sets the weights of the split at {@code path} (workspace plan 2, Rulings 3), from a divider drag. Keeps the maximize.
     *
     * @param path the child indexes from the root to the split; empty for the root
     * @param weights one per child, positive and finite; normalized to sum to 1
     * @return the new layout
     * @throws IllegalArgumentException when no split is at the path or the weights do not fit it
     */
    public Layout resize(List<Integer> path, List<Double> weights) {
        return new Layout(resized(root, List.copyOf(path), 0, List.copyOf(weights)), activeGroup, maximized, recent, tabOrders);
    }

    /**
     * Maximizes {@code group} and activates it, or restores it when it is the maximized group. A layout with one group is
     * returned unchanged.
     *
     * @param group a group of the layout
     * @return the new layout
     * @throws IllegalArgumentException when the group is not in the layout
     */
    public Layout toggleMaximize(GroupId group) {
        requireGroup(group);
        if (root instanceof Group) return this;
        if (maximized.filter(group::equals).isPresent()) return unmaximize();
        return new Layout(root, group, Optional.of(group), recent, tabOrders);
    }

    /**
     * The layout with no group maximized; plans 4 and 5 call it when a tab moves out to another window (workspace plan 2,
     * Rulings 5).
     *
     * @return the new layout, or this one when no group is maximized
     */
    public Layout unmaximize() {
        return maximized.isEmpty() ? this : new Layout(root, activeGroup, Optional.empty(), recent, tabOrders);
    }

    /**
     * Where {@code group} sits (parent tabs spec 4's origin; workspace plan 7b Rulings 2): the group beside it, across its
     * edge with the sibling its space goes to when it leaves (the one before it, or after it when it is first, as
     * {@link #remove(TabId)} gives it); the side of that group it is on; and its weight's share of the two siblings'. Of the
     * groups across that edge, the one {@link #neighbour} finds is beside it. A layout's only group sits on the right of
     * itself, at half.
     *
     * @param parentTab the parent tab this layout is
     * @param group a group of the layout
     * @return the origin
     * @throws IllegalArgumentException when the group is not in the layout
     */
    public Origin origin(ParentTabId parentTab, GroupId group) {
        Objects.requireNonNull(parentTab, "parentTab");
        requireGroup(group);
        Optional<Parent> parent = parent(root, group);
        if (parent.isEmpty()) return new Origin(parentTab, group, Side.RIGHT, 0.5);
        Split split = parent.get().split();
        int index = parent.get().index(), sibling = index > 0 ? index - 1 : 1;
        boolean horizontal = split.orientation() == Orientation.HORIZONTAL;
        Side side = index > 0 ? (horizontal ? Side.RIGHT : Side.DOWN) : (horizontal ? Side.LEFT : Side.UP);
        Side toward = index > 0 ? (horizontal ? Side.LEFT : Side.UP) : (horizontal ? Side.RIGHT : Side.DOWN);
        var near = new ArrayList<Group>();
        collect(split.children().get(sibling).node(), near);
        GroupId beside = neighbour(group, toward).filter(found -> near.stream().anyMatch(candidate -> candidate.id().equals(found)))
            .orElse(near.getFirst().id());
        double own = split.children().get(index).weight(), other = split.children().get(sibling).weight();
        return new Origin(parentTab, beside, side, own / (own + other));
    }

    /**
     * Bring Back's layout (parent tabs spec 5): {@code panes} beside {@code beside}, on {@code side} with {@code share} of
     * the space the two share, nested as they were; their active group becomes the active one and a maximize ends (spec
     * 11). The empty workspace takes {@code panes} whole.
     *
     * @param panes the layout coming back: at least one tab, and no id this layout has
     * @param beside a group of this layout
     * @param side where the panes go
     * @param share their part of the space they and {@code beside} share: above 0 and below 1
     * @return the new layout
     * @throws IllegalArgumentException when {@code panes} is empty or shares an id with this layout, {@code beside} is not
     *     in it, or the share is out of range
     */
    public Layout graft(Layout panes, GroupId beside, Side side, double share) {
        Objects.requireNonNull(panes, "panes");
        Objects.requireNonNull(side, "side");
        if (panes.isEmpty()) throw new IllegalArgumentException("The empty workspace has no panes to bring back");
        if (!(share > 0 && share < 1)) throw new IllegalArgumentException("A share is above 0 and below 1: " + share);
        requireGroup(beside);
        if (isEmpty()) return panes.unmaximize();
        Node next = replace(root, beside, found -> new Split(side.orientation(), side.leading()
            ? List.of(new Child(panes.root, share), new Child(found, 1 - share))
            : List.of(new Child(found, 1 - share), new Child(panes.root, share))));
        var order = new ArrayList<>(panes.recent);
        order.addAll(recent);
        var orders = new HashMap<>(tabOrders);
        orders.putAll(panes.tabOrders);
        return new Layout(next, panes.activeGroup, Optional.empty(), order, orders);
    }

    /** The split whose child {@code group} is, and that child's index. */
    private record Parent(Split split, int index) { }

    /** The split under {@code node} whose child is the group {@code group}; empty when it is {@code node} itself. */
    private static Optional<Parent> parent(Node node, GroupId group) {
        if (!(node instanceof Split split)) return Optional.empty();
        for (int i = 0; i < split.children().size(); i++) {
            Node child = split.children().get(i).node();
            if (child instanceof Group found && found.id().equals(group)) return Optional.of(new Parent(split, i));
            Optional<Parent> deeper = parent(child, group);
            if (deeper.isPresent()) return deeper;
        }
        return Optional.empty();
    }

    /**
     * The group in {@code direction} from {@code group} over the unit square:
     * of the groups wholly on that side that overlap it across the direction, the nearest, then the one whose centre is
     * nearest across; a tie goes to the first in tree order. The maximize is ignored.
     *
     * @param group a group of the layout
     * @param direction the direction
     * @return the neighbour, or empty at the workspace's edge (navigation does not wrap)
     * @throws IllegalArgumentException when the group is not in the layout
     */
    public Optional<GroupId> neighbour(GroupId group, Side direction) {
        Objects.requireNonNull(direction, "direction");
        requireGroup(group);
        Map<GroupId, Region> regions = bounds();
        Region current = regions.get(group);
        GroupId best = null;
        double bestForward = Double.POSITIVE_INFINITY;
        double bestAcross = Double.POSITIVE_INFINITY;
        for (var entry : regions.entrySet()) {
            Region candidate = entry.getValue();
            if (entry.getKey().equals(group) || !ahead(current, candidate, direction) || !overlaps(current, candidate, direction)) continue;
            double forward = forward(current, candidate, direction);
            double across = across(current, candidate, direction);
            if (forward < bestForward - EPSILON || (Math.abs(forward - bestForward) <= EPSILON && across < bestAcross - EPSILON)) {
                best = entry.getKey();
                bestForward = forward;
                bestAcross = across;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Each group's region of the unit square; a maximized group is placed where it sits in the tree.
     *
     * @return the regions by group, in tree order
     */
    public Map<GroupId, Region> bounds() {
        var regions = new LinkedHashMap<GroupId, Region>();
        place(root, new Region(0, 0, 1, 1), regions);
        return Collections.unmodifiableMap(regions);
    }

    /** Equal when the trees, the active groups and the maximized groups are equal; the order of use is ignored. */
    @Override public boolean equals(Object other) {
        return other instanceof Layout layout && root.equals(layout.root) && activeGroup.equals(layout.activeGroup)
            && maximized.equals(layout.maximized);
    }

    @Override public int hashCode() {
        return Objects.hash(root, activeGroup, maximized);
    }

    @Override public String toString() {
        return "Layout[root=" + root + ", activeGroup=" + activeGroup + ", maximized=" + maximized + "]";
    }

    /**
     * A layout over {@code next} with {@code group} active and first in the order of use; a structural change clears the
     * maximize, anything else moves it to {@code group} (workspace plan 2, Rulings 5).
     */
    private Layout activated(Node next, GroupId group, boolean structural) {
        return new Layout(next, group, structural ? Optional.empty() : maximized.map(ignored -> group), recent, tabOrders);
    }

    private Group requireGroup(GroupId id) {
        Objects.requireNonNull(id, "group");
        return group(id).orElseThrow(() -> new IllegalArgumentException("Group " + id + " is not in the layout"));
    }

    private GroupId requireGroupOf(TabId tab) {
        Objects.requireNonNull(tab, "tab");
        return groupOf(tab).orElseThrow(() -> new IllegalArgumentException("Tab " + tab + " is not in the layout"));
    }

    private static void checkIndex(int index, int size) {
        if (index < 0 || index > size) throw new IllegalArgumentException("Index " + index + " is outside 0 to " + size);
    }

    /** {@code node} with the group {@code id} replaced by what {@code change} makes of it; Split flattens the result. */
    private static Node replace(Node node, GroupId id, Function<Group, Node> change) {
        return switch (node) {
            case Group group -> group.id().equals(id) ? change.apply(group) : group;
            case Split split -> {
                var children = new ArrayList<Child>(split.children().size());
                boolean changed = false;
                for (Child child : split.children()) {
                    Node next = replace(child.node(), id, change);
                    changed |= next != child.node();
                    children.add(next == child.node() ? child : new Child(next, child.weight()));
                }
                yield changed ? new Split(split.orientation(), children) : split;
            }
        };
    }

    /**
     * {@code node} without the group {@code id}: empty when the node is that group. Its weight goes to the sibling before
     * it, or after it when it was first (workspace plan 2, Rulings 8), and a split left with one child collapses into that child.
     */
    private static Optional<Node> without(Node node, GroupId id) {
        return switch (node) {
            case Group group -> group.id().equals(id) ? Optional.empty() : Optional.of(group);
            case Split split -> {
                var children = new ArrayList<>(split.children());
                for (int i = 0; i < children.size(); i++) {
                    Child child = children.get(i);
                    if (!holds(child.node(), id)) continue;
                    Optional<Node> rest = without(child.node(), id);
                    if (rest.isPresent()) {
                        children.set(i, new Child(rest.get(), child.weight()));
                    } else {
                        children.remove(i);
                        int heir = i == 0 ? 0 : i - 1;
                        Child taker = children.get(heir);
                        children.set(heir, new Child(taker.node(), taker.weight() + child.weight()));
                    }
                    break;
                }
                yield Optional.of(children.size() == 1 ? children.getFirst().node() : new Split(split.orientation(), children));
            }
        };
    }

    /** Whether the group {@code id} is under {@code node}. */
    private static boolean holds(Node node, GroupId id) {
        return switch (node) {
            case Group group -> group.id().equals(id);
            case Split split -> split.children().stream().anyMatch(child -> holds(child.node(), id));
        };
    }

    /** Distances this close are equal. */
    private static final double EPSILON = 1.0e-12;

    /** {@code tab} in a new group on {@code side} of {@code group}, selected and active; clears the maximize. */
    private Layout beside(TabId tab, GroupId group, Side side) {
        Group created = new Group(GroupId.random(), List.of(tab), Optional.of(tab));
        Node next = replace(root, group, found -> new Split(side.orientation(), side.leading()
            ? List.of(new Child(created, 0.5), new Child(found, 0.5))
            : List.of(new Child(found, 0.5), new Child(created, 0.5))));
        return activated(next, created.id(), true);
    }

    /** {@code node} with the split at {@code path} (from {@code depth} on) given {@code weights}. */
    private static Node resized(Node node, List<Integer> path, int depth, List<Double> weights) {
        if (!(node instanceof Split split)) throw new IllegalArgumentException("No split at " + path);
        List<Child> children = split.children();
        if (depth == path.size()) {
            if (weights.size() != children.size())
                throw new IllegalArgumentException("The split at " + path + " has " + children.size() + " children, not " + weights.size() + " weights");
            var sized = new ArrayList<Child>();
            for (int i = 0; i < children.size(); i++) sized.add(new Child(children.get(i).node(), weights.get(i)));
            return new Split(split.orientation(), sized);
        }
        int index = path.get(depth);
        if (index < 0 || index >= children.size()) throw new IllegalArgumentException("No split at " + path);
        var next = new ArrayList<>(children);
        next.set(index, new Child(resized(children.get(index).node(), path, depth + 1, weights), children.get(index).weight()));
        return new Split(split.orientation(), next);
    }

    /** Puts each group under {@code node} into {@code into} with its share of {@code region}; the last child takes the rest. */
    private static void place(Node node, Region region, Map<GroupId, Region> into) {
        switch (node) {
            case Group group -> into.put(group.id(), region);
            case Split split -> {
                boolean horizontal = split.orientation() == Orientation.HORIZONTAL;
                double start = horizontal ? region.x() : region.y();
                double length = horizontal ? region.width() : region.height();
                double offset = 0;
                List<Child> children = split.children();
                for (int i = 0; i < children.size(); i++) {
                    double size = i == children.size() - 1 ? length - offset : length * children.get(i).weight();
                    place(children.get(i).node(), horizontal
                        ? new Region(start + offset, region.y(), size, region.height())
                        : new Region(region.x(), start + offset, region.width(), size), into);
                    offset += size;
                }
            }
        }
    }

    private static boolean ahead(Region current, Region candidate, Side direction) {
        return switch (direction) {
            case LEFT -> candidate.right() <= current.x() + EPSILON;
            case RIGHT -> candidate.x() >= current.right() - EPSILON;
            case UP -> candidate.bottom() <= current.y() + EPSILON;
            case DOWN -> candidate.y() >= current.bottom() - EPSILON;
        };
    }

    private static boolean overlaps(Region current, Region candidate, Side direction) {
        return direction.orientation() == Orientation.HORIZONTAL
            ? Math.min(current.bottom(), candidate.bottom()) - Math.max(current.y(), candidate.y()) > EPSILON
            : Math.min(current.right(), candidate.right()) - Math.max(current.x(), candidate.x()) > EPSILON;
    }

    private static double forward(Region current, Region candidate, Side direction) {
        return switch (direction) {
            case LEFT -> Math.max(0.0, current.x() - candidate.right());
            case RIGHT -> Math.max(0.0, candidate.x() - current.right());
            case UP -> Math.max(0.0, current.y() - candidate.bottom());
            case DOWN -> Math.max(0.0, candidate.y() - current.bottom());
        };
    }

    private static double across(Region current, Region candidate, Side direction) {
        return direction.orientation() == Orientation.HORIZONTAL
            ? Math.abs(current.centerY() - candidate.centerY())
            : Math.abs(current.centerX() - candidate.centerX());
    }

    /** Adds the groups under {@code node} to {@code into}, in tree order. */
    private static void collect(Node node, List<Group> into) {
        switch (node) {
            case Group group -> into.add(group);
            case Split split -> split.children().forEach(child -> collect(child.node(), into));
        }
    }

    /**
     * A group's tabs in order of use (workspace plan 2, Rulings 7): its selected tab, then the others in
     * {@code previous}'s order, then any tab {@code previous} lacks: those after the selected tab in strip order, then
     * those before it, nearest first, so that closing tabs never shown behaves as {@code JTabbedPane.removeTabAt}.
     */
    private static List<TabId> tabOrder(Group group, List<TabId> previous) {
        var order = new LinkedHashSet<TabId>();
        group.selected().ifPresent(order::add);
        for (TabId tab : previous) if (group.tabs().contains(tab)) order.add(tab);
        int at = group.selected().map(group.tabs()::indexOf).orElse(0);
        for (int i = at + 1; i < group.tabs().size(); i++) order.add(group.tabs().get(i));
        for (int i = at - 1; i >= 0; i--) order.add(group.tabs().get(i));
        return List.copyOf(order);
    }
}
