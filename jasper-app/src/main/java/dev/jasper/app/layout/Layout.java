package dev.jasper.app.layout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
