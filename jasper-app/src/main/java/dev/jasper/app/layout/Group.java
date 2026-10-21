package dev.jasper.app.layout;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A pane, a leaf of the tree (tabs and panes spec 4): the one tab it holds and shows, or none in the empty workspace.
 * The record keeps a list and a selection (workspace plan 8a Rulings 2); {@code workspace.toml} saves its one tab
 * (workspace plan 6a Rulings 32).
 *
 * @param id the pane's id
 * @param tabs its one tab, or none in the empty workspace's pane
 * @param selected the tab it shows, {@code tabs}' one; empty exactly when there is none
 */
public record Group(GroupId id, List<TabId> tabs, Optional<TabId> selected) implements Node {
    /** Copies the tab list and checks that it holds at most one tab, the one selected. */
    public Group {
        Objects.requireNonNull(id, "id");
        tabs = List.copyOf(tabs);
        Objects.requireNonNull(selected, "selected");
        if (tabs.size() > 1) throw new IllegalArgumentException("Pane " + id + " holds more than one tab");
        if (tabs.isEmpty() != selected.isEmpty())
            throw new IllegalArgumentException(tabs.isEmpty() ? "The empty pane " + id + " selects nothing" : "Pane " + id + " selects its tab");
        if (selected.isPresent() && !tabs.contains(selected.get()))
            throw new IllegalArgumentException("Pane " + id + " does not hold its selected tab " + selected.get());
    }
}
