package dev.jasper.ui.model;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.List;
import java.util.Objects;

/**
 * Immutable treeitem descriptor. A present {@code count} shows at the row's end (for example search matches);
 * {@code emphasized} marks the item for attention (for example unsaved changes).
 */
public record TreeItem(String id, String title, Optional<String> icon, boolean leaf, List<TreeItem> children, OptionalInt count, boolean emphasized) {
    public TreeItem {
        ModelChecks.id(id);
        ModelChecks.label(title, "title"); Objects.requireNonNull(icon); icon.ifPresent(name -> ModelChecks.label(name, "icon")); children = List.copyOf(children); if (leaf && !children.isEmpty()) throw new IllegalArgumentException("Leaf cannot have children");
        Objects.requireNonNull(count); if (count.isPresent() && count.getAsInt() < 0) throw new IllegalArgumentException("Negative count");
    }
    public TreeItem(String id, String title, Optional<String> icon, boolean leaf, List<TreeItem> children) {
        this(id, title, icon, leaf, children, OptionalInt.empty(), false);
    }
}
