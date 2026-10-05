package dev.jasper.app.shortcuthelp;

import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.TreeItem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * The grouped shortcut sections as a kit tree. Section disclosure belongs to this window; searching shows every
 * section temporarily, and collapsing one while searching is not remembered. Item ids are {@code group/<name>} for a
 * section and {@code action/<id>} for a shortcut. EDT only.
 */
final class ShortcutTree {
    static final String ID = "shortcuts";
    private static final String GROUP = "group/";
    private static final String ACTION = "action/";

    private final boolean macOs;
    private final Set<String> expanded = new HashSet<>();
    private final Set<String> collapsedWhileSearching = new HashSet<>();
    private final LinkedHashMap<String, List<ShortcutEntry>> groups = new LinkedHashMap<>();
    private boolean searching;
    private String selected;

    ShortcutTree(boolean macOs) { this.macOs = macOs; }

    static String groupId(String group) { return GROUP + group; }
    static String actionId(ShortcutEntry entry) { return ACTION + entry.id(); }

    /** Shows {@code matches}; a search reveals every section until it ends. */
    void showRows(List<ShortcutEntry> matches, boolean searching) {
        this.searching = searching;
        collapsedWhileSearching.clear();
        groups.clear();
        for (var item : matches) groups.computeIfAbsent(item.group(), name -> new ArrayList<>()).add(item);
        if (selected != null && !visibleIds().contains(selected)) selected = null;
    }

    /** The user opened or closed a section. */
    void expanded(String itemId, boolean open) {
        if (!isGroup(itemId)) return;
        String group = itemId.substring(GROUP.length());
        if (searching) { if (open) collapsedWhileSearching.remove(group); else collapsedWhileSearching.add(group); }
        else if (open) expanded.add(group); else expanded.remove(group);
        if (selected != null && !visibleIds().contains(selected)) selected = itemId;
    }

    /** Opens or closes every section, remembered unless searching. */
    void expandSections(boolean open) { for (String group : List.copyOf(groups.keySet())) expanded(groupId(group), open); }

    boolean isOpen(String group) {
        return searching ? !collapsedWhileSearching.contains(group) : expanded.contains(group);
    }

    boolean isOpenItem(String groupId) { return isGroup(groupId) && isOpen(groupId.substring(GROUP.length())); }

    void select(String itemId) { selected = itemId; }
    String selected() { return selected; }
    boolean isGroup(String itemId) { return itemId != null && itemId.startsWith(GROUP); }

    /** The shortcut an item id names, if it is one that is shown. */
    Optional<ShortcutEntry> entry(String itemId) {
        if (itemId == null || !itemId.startsWith(ACTION)) return Optional.empty();
        for (var rows : groups.values()) for (var row : rows) if (actionId(row).equals(itemId)) return Optional.of(row);
        return Optional.empty();
    }

    /** The ids of the rows on screen, top to bottom. */
    List<String> visibleIds() {
        var ids = new ArrayList<String>();
        groups.forEach((group, rows) -> {
            ids.add(groupId(group));
            if (isOpen(group)) for (var row : rows) ids.add(actionId(row));
        });
        return ids;
    }

    int rowCount() { return visibleIds().size(); }

    /** The shortcut on screen row {@code row}, or null for a section. */
    ShortcutEntry entryAt(int row) {
        var ids = visibleIds();
        return row < 0 || row >= ids.size() ? null : entry(ids.get(row)).orElse(null);
    }

    Nodes.Tree descriptor() {
        var roots = new ArrayList<TreeItem>();
        var open = new HashSet<String>();
        groups.forEach((group, rows) -> {
            var children = new ArrayList<TreeItem>();
            boolean customized = false;
            for (var row : rows) {
                customized |= row.customized(macOs);
                children.add(new TreeItem(actionId(row), row.name(), Optional.empty(), true, List.of(), OptionalInt.empty(),
                    row.customized(macOs), row.context() + (row.editable() ? " · Click to edit" : " · Read-only contextual key"),
                    Optional.empty(), List.of(row.where(), ShortcutText.format(row.stroke())), Optional.empty()));
            }
            boolean shown = isOpen(group);
            if (shown) open.add(groupId(group));
            // A section holding a shortcut that differs from its default is emphasized, as IntelliJ shows it.
            roots.add(new TreeItem(groupId(group), group, Optional.of("folder"), false, children, OptionalInt.of(rows.size()),
                customized, shown ? "Collapse section" : "Expand section", Optional.empty(), List.of(), Optional.empty()));
        });
        return new Nodes.Tree(ID, ControlState.READY, roots, open, selected == null ? Set.of() : Set.of(selected), "edit");
    }
}
