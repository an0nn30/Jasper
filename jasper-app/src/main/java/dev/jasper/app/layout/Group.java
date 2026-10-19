package dev.jasper.app.layout;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A tab group, a leaf of the tree: its tabs in strip order and the one it shows (spec 7.1).
 *
 * @param id the group's id
 * @param tabs its tabs in order, none twice
 * @param selected the tab it shows, one of {@code tabs}; empty exactly when there are none, which only the empty
 *     workspace's group may be
 */
public record Group(GroupId id, List<TabId> tabs, Optional<TabId> selected) implements Node {
    /** Copies the tabs and checks the selection. */
    public Group {
        Objects.requireNonNull(id, "id");
        tabs = List.copyOf(tabs);
        Objects.requireNonNull(selected, "selected");
        if (Set.copyOf(tabs).size() != tabs.size()) throw new IllegalArgumentException("Group " + id + " lists a tab twice");
        if (tabs.isEmpty() != selected.isEmpty())
            throw new IllegalArgumentException(tabs.isEmpty() ? "The empty group " + id + " selects nothing" : "Group " + id + " selects one of its tabs");
        if (selected.isPresent() && !tabs.contains(selected.get()))
            throw new IllegalArgumentException("Group " + id + " does not hold its selected tab " + selected.get());
    }
}
