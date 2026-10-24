package dev.jasper.app.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * One tab's layout (spec 7; tabs and panes spec 4): a tree of splits whose leaves are panes, each holding one tab, the
 * active pane, the maximized pane if any and the panes' order of use. Immutable: each operation returns a new layout and
 * rejects arguments that would break an invariant of spec 7.2 with an {@link IllegalArgumentException}. Equality covers
 * the tree, the active pane and the maximized pane; the order of use only decides focus after a removal and is not
 * saved. The code calls a pane a group (workspace plan 8a Rulings 1).
 */
public final class Layout {
    private final Node root;
    private final GroupId activeGroup;
    private final Optional<GroupId> maximized;
    private final List<GroupId> recent;
    private final List<Group> groups;

    /**
     * A layout over {@code root}. Its order of use starts with {@code activeGroup}, then the other groups in tree order.
     *
     * @param root the tree: no group or tab twice, and no empty group unless it is the root (the empty workspace)
     * @param activeGroup a group of the tree
     * @param maximized the active group while it fills the workspace, which needs at least two groups; else empty
     * @throws IllegalArgumentException when an invariant does not hold
     */
    public Layout(Node root, GroupId activeGroup, Optional<GroupId> maximized) {
        this(root, activeGroup, maximized, List.of());
    }

    private Layout(Node root, GroupId activeGroup, Optional<GroupId> maximized, List<GroupId> recent) {
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
     * Every tab in workspace order: the panes' tabs in tree order.
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
     * Puts {@code tab} into the empty workspace's pane, selected (tabs and panes spec 4: a pane holds one tab, so a tab
     * joins a layout that holds one by {@link #split} alone).
     *
     * @param tab a tab
     * @return the new layout
     * @throws IllegalArgumentException when this is not the empty workspace
     */
    public Layout open(TabId tab) {
        Objects.requireNonNull(tab, "tab");
        if (!isEmpty()) throw new IllegalArgumentException("Only the empty workspace's pane takes a tab, not " + tab);
        return new Layout(new Group(activeGroup, List.of(tab), Optional.of(tab)), activeGroup, Optional.empty(), recent);
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
     * Puts {@code by} in {@code tab}'s pane in its place: the pane keeps its id and its weight, and the active pane and
     * the maximize stay (a placeholder's Retry, workspace plan 6b Rulings 6).
     *
     * @param tab a tab of the layout
     * @param by a tab not in the layout
     * @return the new layout
     * @throws IllegalArgumentException when {@code tab} is not in the layout or {@code by} is
     */
    public Layout replaceTab(TabId tab, TabId by) {
        Objects.requireNonNull(by, "by");
        GroupId group = requireGroupOf(tab);
        if (contains(by)) throw new IllegalArgumentException("Tab " + by + " is already in the layout");
        return new Layout(replace(root, group, found -> new Group(found.id(), List.of(by), Optional.of(by))), activeGroup, maximized, recent);
    }

    /**
     * Removes {@code tab}, closing it or moving it out, and its pane with it: the pane's weight goes to the sibling before
     * it (after it when it was first), a split left with one child collapses into that child and nesting is flattened
     * again, and the active pane becomes the most recently used remaining pane. The maximize stays while the maximized
     * pane stays and two panes remain (Rulings 5). Removing the last tab leaves the empty workspace, keeping its pane.
     *
     * @param tab a tab of the layout
     * @return the new layout
     * @throws IllegalArgumentException when the tab is not in the layout
     */
    public Layout remove(TabId tab) {
        GroupId id = requireGroupOf(tab);
        if (root instanceof Group) return new Layout(new Group(id, List.of(), Optional.empty()), id, Optional.empty(), recent);
        GroupId heir = recent.stream().filter(other -> !other.equals(id)).findFirst().orElseThrow();
        Node next = without(root, id).orElseThrow();
        Optional<GroupId> kept = maximized.filter(max -> !max.equals(id) && next instanceof Split);
        return new Layout(next, heir, kept, recent);
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
     * Moves {@code tab}'s pane to {@code side} of {@code group}, as {@link #split(TabId, GroupId, Side)} opens one; its own
     * pane goes first, as {@link #remove(TabId)} removes it (plan 8c's pane moves).
     *
     * @param tab a tab of the layout
     * @param group another pane of the layout
     * @param side where the pane goes
     * @return the new layout
     * @throws IllegalArgumentException when the tab or the group is not in the layout, or the group is the tab's own
     */
    public Layout moveToNewGroup(TabId tab, GroupId group, Side side) {
        Objects.requireNonNull(side, "side");
        GroupId source = requireGroupOf(tab);
        requireGroup(group);
        if (source.equals(group)) throw new IllegalArgumentException("Tab " + tab + " cannot move beside its own pane " + group);
        return remove(tab).beside(tab, group, side);
    }

    /**
     * Puts every pane of {@code other}, another tab's layout, on {@code side} of {@code group}, as one nested split that
     * keeps their arrangement and sizes (pane flexibility spec 5; workspace plan 9a Rulings 10): the two take half of
     * {@code group}'s space each, as {@link #split(TabId, GroupId, Side)} shares it, and a split of {@code side}'s
     * orientation joins its parent's, as {@link Split} flattens one, its panes keeping their shares of the moved half.
     * {@code other}'s active pane is active and first in the order of use, its other panes next; the maximize clears.
     *
     * @param other a layout holding tabs, none of whose panes or tabs this layout holds
     * @param group a pane of this layout holding a tab
     * @param side where {@code other}'s panes go
     * @return the new layout
     * @throws IllegalArgumentException when {@code other} is empty or shares a pane or a tab with this layout, or when the
     *     group is not in this layout or holds no tab
     */
    public Layout insertBeside(Layout other, GroupId group, Side side) {
        Objects.requireNonNull(other, "other");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(side, "side");
        if (other.isEmpty()) throw new IllegalArgumentException("An empty layout has no panes to put beside " + group);
        if (requireGroup(group).tabs().isEmpty()) throw new IllegalArgumentException("The empty workspace's group cannot be split");
        Node next = replace(root, group, found -> new Split(side.orientation(), side.leading()
            ? List.of(new Child(other.root, 0.5), new Child(found, 0.5))
            : List.of(new Child(found, 0.5), new Child(other.root, 0.5))));
        var order = new ArrayList<GroupId>(other.recent);
        order.addAll(recent);
        return new Layout(next, other.activeGroup, Optional.empty(), order);
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
        return new Layout(resized(root, List.copyOf(path), 0, List.copyOf(weights)), activeGroup, maximized, recent);
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
        return new Layout(root, group, Optional.of(group), recent);
    }

    /**
     * The layout with no group maximized; plans 4 and 5 call it when a tab moves out to another window (workspace plan 2,
     * Rulings 5).
     *
     * @return the new layout, or this one when no group is maximized
     */
    public Layout unmaximize() {
        return maximized.isEmpty() ? this : new Layout(root, activeGroup, Optional.empty(), recent);
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
        return new Layout(next, group, structural ? Optional.empty() : maximized.map(ignored -> group), recent);
    }

    private Group requireGroup(GroupId id) {
        Objects.requireNonNull(id, "group");
        return group(id).orElseThrow(() -> new IllegalArgumentException("Group " + id + " is not in the layout"));
    }

    private GroupId requireGroupOf(TabId tab) {
        Objects.requireNonNull(tab, "tab");
        return groupOf(tab).orElseThrow(() -> new IllegalArgumentException("Tab " + tab + " is not in the layout"));
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
}
