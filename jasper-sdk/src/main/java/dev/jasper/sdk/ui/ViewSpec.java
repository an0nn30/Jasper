package dev.jasper.sdk.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import static dev.jasper.sdk.ui.Nodes.*;

/** A validated immutable view tree. Node ids are global; item ids are local to their owning control. */
public record ViewSpec(Node root) {
    public ViewSpec {
        Objects.requireNonNull(root);
        // One traversal validates ids and collects the ItemLists that fields name with their arrow keys.
        var lists = new HashSet<String>(); var listRefs = new HashSet<String>();
        validate(root, new HashSet<>(), new HashSet<>(), lists, listRefs, 0, new int[]{0});
        if (!lists.containsAll(listRefs)) throw new IllegalArgumentException("A field's list must be an ItemList in the same view");
    }
    private static void references(Set<String> selected, Set<String> ids) {
        if (!ids.containsAll(selected)) throw new IllegalArgumentException("Unknown item reference");
    }
    private static void tree(TreeItem item, Set<String> ids, Set<String> branches, int depth) {
        if (depth > 128) throw new IllegalArgumentException("Tree too deep");
        if (!ids.add(item.id())) throw new IllegalArgumentException("Duplicate tree item: " + item.id());
        if (!item.leaf()) branches.add(item.id());
        for (var child : item.children()) tree(child, ids, branches, depth + 1);
    }
    private static void validate(Node node, Set<String> ids, Set<String> contentIds, Set<String> lists, Set<String> listRefs, int depth, int[] count) {
        if (depth > 128 || ++count[0] > 100_000) throw new IllegalArgumentException("View too deep or large");
        if (!ids.add(node.id())) throw new IllegalArgumentException("Duplicate node: " + node.id());
        if (node instanceof Custom custom && !contentIds.add(custom.contentId())) throw new IllegalArgumentException("Duplicate custom content id");
        if (node instanceof ItemList list) lists.add(list.id());
        if (node instanceof Field field && !field.listId().isBlank()) listRefs.add(field.listId());
        var children = new ArrayList<Node>();
        switch (node) {
            case Stack n -> children.addAll(n.children());
            case Card n -> children.addAll(n.children());
            case Split n -> children.addAll(n.children());
            case FormRow n -> { children.add(n.control()); n.trailing().ifPresent(children::add); }
            case SettingRow n -> children.add(n.control());
            case Section n -> children.add(n.body());
            case Empty n -> { children.add(n.icon()); children.addAll(n.actions()); }
            case Header n -> { n.context().ifPresent(children::add); n.primary().ifPresent(children::add); }
            case Panel n -> { children.add(n.header()); n.searchOrNavigation().ifPresent(children::add); children.add(n.body()); n.footer().ifPresent(children::add); }
            case Form n -> { n.hero().ifPresent(children::add); children.addAll(n.sections()); n.preview().ifPresent(children::add); children.addAll(n.actions()); }
            case ItemList n -> {
                n.addAction().ifPresent(children::add);
                references(n.selectedIds(), new HashSet<>(n.items().stream().map(ListItem::id).toList()));
                for (var item : n.items()) { item.leading().ifPresent(children::add); children.addAll(item.title()); children.addAll(item.subtitle()); children.addAll(item.trailing()); }
            }
            case PickerMenu ignored -> {}
            case Choice n -> {
                if (!n.selectedId().isEmpty()) references(Set.of(n.selectedId()), new HashSet<>(n.options().stream().map(Option::id).toList()));
            }
            case Table n -> {
                var columns = new HashSet<>(n.columns().stream().map(Column::id).toList());
                for (var row : n.rows()) references(row.cells().keySet(), columns);
                references(n.selectedIds(), new HashSet<>(n.rows().stream().map(TableRow::id).toList()));
            }
            case Tree n -> {
                var itemIds = new HashSet<String>(); var branches = new HashSet<String>();
                for (var item : n.roots()) tree(item, itemIds, branches, 0);
                references(n.selectedIds(), itemIds); references(n.expandedIds(), branches);
            }
            case Text _, Icon _, Button _, MenuButton _, StatusDot _, Field _, Secret _, Toggle _, Chip _, Progress _, Strength _, Breadcrumbs _, Custom _ -> { }
        }
        for (var child : children) validate(child, ids, contentIds, lists, listRefs, depth + 1, count);
    }
}
