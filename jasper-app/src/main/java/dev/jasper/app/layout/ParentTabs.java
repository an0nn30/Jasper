package dev.jasper.app.layout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A window's parent tabs (parent tabs spec 4), as the main window and full windows hold them: the parent tabs in bar
 * order, the one that shows and their order of use. Immutable: each operation returns new parent tabs and rejects
 * arguments that would break an invariant with an {@link IllegalArgumentException}: a window holds at least one parent
 * tab, parent tab, group and tab ids are unique across its parent tabs, and only a window's only parent tab may be
 * empty, as the main window's empty workspace is (a child window closes instead). Equality covers the parent tabs and the
 * active one; the order of use only decides which parent tab shows after the shown one closes, and is not saved.
 */
public final class ParentTabs implements WindowLayout {
    private final List<ParentTab> parentTabs;
    private final ParentTabId active;
    private final List<ParentTabId> recent;

    /**
     * Parent tabs in bar order with {@code active} showing. Their order of use starts with {@code active}, then the
     * parent tabs after it in the bar, then those before it, nearest first, as a group's tabs do (workspace plan 2,
     * Rulings 7).
     *
     * @param parentTabs at least one, in bar order
     * @param active one of them
     * @throws IllegalArgumentException when an invariant does not hold
     */
    public ParentTabs(List<ParentTab> parentTabs, ParentTabId active) {
        this(parentTabs, active, List.of());
    }

    private ParentTabs(List<ParentTab> parentTabs, ParentTabId active, List<ParentTabId> previous) {
        this.parentTabs = List.copyOf(parentTabs);
        this.active = Objects.requireNonNull(active, "active");
        if (this.parentTabs.isEmpty()) throw new IllegalArgumentException("A window holds at least one parent tab");
        var ids = new HashSet<ParentTabId>();
        var groups = new HashSet<GroupId>();
        var tabs = new HashSet<TabId>();
        for (ParentTab parent : this.parentTabs) {
            if (!ids.add(parent.id())) throw new IllegalArgumentException("Parent tab " + parent.id() + " is in the window twice");
            if (parent.layout().isEmpty() && this.parentTabs.size() > 1)
                throw new IllegalArgumentException("Only a window's only parent tab may be empty: " + parent.id());
            for (Group group : parent.layout().groups())
                if (!groups.add(group.id())) throw new IllegalArgumentException("Group " + group.id() + " is in two parent tabs");
            for (TabId tab : parent.layout().tabs())
                if (!tabs.add(tab)) throw new IllegalArgumentException("Tab " + tab + " is in two parent tabs");
        }
        if (!ids.contains(active)) throw new IllegalArgumentException("The active parent tab " + active + " is not in the window");
        var order = new LinkedHashSet<ParentTabId>();
        order.add(active);
        for (ParentTabId id : previous) if (ids.contains(id)) order.add(id);
        int at = indexOf(active);
        for (int i = at + 1; i < this.parentTabs.size(); i++) order.add(this.parentTabs.get(i).id());
        for (int i = at - 1; i >= 0; i--) order.add(this.parentTabs.get(i).id());
        this.recent = List.copyOf(order);
    }

    /**
     * A window's one parent tab, showing.
     *
     * @param parentTab the parent tab; its layout may be empty
     * @return the parent tabs
     */
    public static ParentTabs of(ParentTab parentTab) {
        return new ParentTabs(List.of(parentTab), parentTab.id());
    }

    /**
     * The parent tabs in bar order.
     *
     * @return the parent tabs
     */
    public List<ParentTab> parentTabs() {
        return parentTabs;
    }

    /**
     * The parent tab that shows.
     *
     * @return its id
     */
    public ParentTabId active() {
        return active;
    }

    /**
     * The parent tab that shows.
     *
     * @return the parent tab
     */
    public ParentTab activeParentTab() {
        return parentTabs.get(indexOf(active));
    }

    /**
     * The parent tabs' order of use, most recent first; not part of equality or the file.
     *
     * @return every parent tab's id, the active one first
     */
    public List<ParentTabId> recent() {
        return recent;
    }

    /**
     * The parent tab with this id.
     *
     * @param id the parent tab's id
     * @return the parent tab, or empty when the window does not have it
     */
    public Optional<ParentTab> parentTab(ParentTabId id) {
        int at = indexOf(id);
        return at < 0 ? Optional.empty() : Optional.of(parentTabs.get(at));
    }

    /**
     * Where a parent tab is in the bar.
     *
     * @param id the parent tab's id
     * @return its index, or -1 when the window does not have it
     */
    public int indexOf(ParentTabId id) {
        Objects.requireNonNull(id, "id");
        for (int i = 0; i < parentTabs.size(); i++) if (parentTabs.get(i).id().equals(id)) return i;
        return -1;
    }

    /** Every tab: the parent tabs in bar order, each parent tab's tabs in workspace order. */
    @Override public List<TabId> tabs() {
        var tabs = new ArrayList<TabId>();
        for (ParentTab parent : parentTabs) tabs.addAll(parent.layout().tabs());
        return List.copyOf(tabs);
    }

    /**
     * Whether the window holds no tab: its only parent tab is empty, the main window's empty workspace.
     *
     * @return true for the empty workspace
     */
    public boolean isEmpty() {
        return parentTabs.size() == 1 && parentTabs.getFirst().layout().isEmpty();
    }

    /**
     * The parent tab holding a tab.
     *
     * @param tab the tab
     * @return its parent tab's id, or empty when the window does not hold it
     */
    public Optional<ParentTabId> holding(TabId tab) {
        Objects.requireNonNull(tab, "tab");
        for (ParentTab parent : parentTabs) if (parent.layout().contains(tab)) return Optional.of(parent.id());
        return Optional.empty();
    }

    /**
     * The parent tab holding a group.
     *
     * @param group the group
     * @return its parent tab's id, or empty when the window does not hold it
     */
    public Optional<ParentTabId> holding(GroupId group) {
        Objects.requireNonNull(group, "group");
        for (ParentTab parent : parentTabs) if (parent.layout().group(group).isPresent()) return Optional.of(parent.id());
        return Optional.empty();
    }

    /**
     * Puts {@code parentTab} at {@code index} of the bar and shows it.
     *
     * @param parentTab a parent tab holding at least one tab, none of whose ids the window has
     * @param index 0 to the number of parent tabs
     * @return the new parent tabs
     * @throws IllegalArgumentException when an id repeats, the index is outside the bar or a parent tab would be empty
     *     beside another
     */
    public ParentTabs open(ParentTab parentTab, int index) {
        Objects.requireNonNull(parentTab, "parentTab");
        if (index < 0 || index > parentTabs.size())
            throw new IllegalArgumentException("Index " + index + " is outside 0 to " + parentTabs.size());
        var next = new ArrayList<>(parentTabs);
        next.add(index, parentTab);
        return new ParentTabs(next, parentTab.id(), recent);
    }

    /**
     * Shows a parent tab and puts it first in the order of use.
     *
     * @param id a parent tab of the window
     * @return the new parent tabs
     * @throws IllegalArgumentException when the window does not have it
     */
    public ParentTabs select(ParentTabId id) {
        requireParentTab(id);
        return new ParentTabs(parentTabs, id, recent);
    }

    /**
     * Gives a parent tab a new layout, in its place.
     *
     * @param parentTab the parent tab with its new layout; its id is one of the window's
     * @return the new parent tabs
     * @throws IllegalArgumentException when the window does not have it, an id repeats or it would be empty beside another
     */
    public ParentTabs replace(ParentTab parentTab) {
        Objects.requireNonNull(parentTab, "parentTab");
        int at = requireParentTab(parentTab.id());
        var next = new ArrayList<>(parentTabs);
        next.set(at, parentTab);
        return new ParentTabs(next, active, recent);
    }

    /**
     * Removes a parent tab. When it showed, the one used most recently before it shows (Rulings 2 of plan 7a).
     *
     * @param id a parent tab of the window, not its only one
     * @return the new parent tabs
     * @throws IllegalArgumentException when the window does not have it, or it is the window's only parent tab
     */
    public ParentTabs remove(ParentTabId id) {
        int at = requireParentTab(id);
        if (parentTabs.size() == 1)
            throw new IllegalArgumentException("The window's only parent tab cannot be removed: the window closes instead");
        var next = new ArrayList<>(parentTabs);
        next.remove(at);
        ParentTabId heir = active.equals(id) ? recent.stream().filter(other -> !other.equals(id)).findFirst().orElseThrow() : active;
        return new ParentTabs(next, heir, recent);
    }

    /**
     * The parent tab {@code delta} places along the bar from the one that shows, wrapping around (Next and Previous
     * Parent Tab).
     *
     * @param delta how far, negative for back
     * @return its id
     */
    public ParentTabId step(int delta) {
        return parentTabs.get(Math.floorMod(indexOf(active) + delta, parentTabs.size())).id();
    }

    /** Equal when the parent tabs, in order, and the active one are equal; the order of use is ignored. */
    @Override public boolean equals(Object other) {
        return other instanceof ParentTabs tabs && parentTabs.equals(tabs.parentTabs) && active.equals(tabs.active);
    }

    @Override public int hashCode() {
        return Objects.hash(parentTabs, active);
    }

    @Override public String toString() {
        return "ParentTabs[parentTabs=" + parentTabs + ", active=" + active + "]";
    }

    private int requireParentTab(ParentTabId id) {
        int at = indexOf(id);
        if (at < 0) throw new IllegalArgumentException("Parent tab " + id + " is not in the window");
        return at;
    }
}
