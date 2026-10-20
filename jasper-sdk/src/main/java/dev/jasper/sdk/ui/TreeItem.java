package dev.jasper.sdk.ui;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.List;
import java.util.Objects;

/**
 * Immutable treeitem descriptor. A present {@code count} shows at the row's end (for example search matches);
 * {@code emphasized} marks the item for attention (for example unsaved changes). {@code tooltip} is the row's literal
 * tooltip and accessible description (blank for none); a present {@code status} shows a status dot after the icon;
 * {@code trailing} texts show as right-aligned columns that line up across the tree's rows; a present
 * {@code contextActions} is the row's context menu.
 */
public record TreeItem(String id, String title, Optional<String> icon, boolean leaf, List<TreeItem> children, OptionalInt count, boolean emphasized,
                       String tooltip, Optional<Nodes.StatusKind> status, List<String> trailing, Optional<List<MenuItem>> contextActions) {
    public TreeItem {
        ModelChecks.id(id);
        ModelChecks.label(title, "title"); Objects.requireNonNull(icon); icon.ifPresent(name -> ModelChecks.label(name, "icon")); children = List.copyOf(children); if (leaf && !children.isEmpty()) throw new IllegalArgumentException("Leaf cannot have children");
        Objects.requireNonNull(count); if (count.isPresent() && count.getAsInt() < 0) throw new IllegalArgumentException("Negative count");
        Objects.requireNonNull(tooltip); Objects.requireNonNull(status); trailing = List.copyOf(trailing);
        Objects.requireNonNull(contextActions); contextActions = contextActions.map(List::copyOf); contextActions.ifPresent(items -> ModelChecks.unique(items, MenuItem::id));
    }
    public TreeItem(String id, String title, Optional<String> icon, boolean leaf, List<TreeItem> children, OptionalInt count, boolean emphasized) {
        this(id, title, icon, leaf, children, count, emphasized, "", Optional.empty(), List.of(), Optional.empty());
    }
    public TreeItem(String id, String title, Optional<String> icon, boolean leaf, List<TreeItem> children) {
        this(id, title, icon, leaf, children, OptionalInt.empty(), false);
    }
}
