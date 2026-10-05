package dev.jasper.sdk.ui;

import java.util.Optional;
import java.util.List;
import java.util.Objects;

/** Immutable treeitem descriptor. */
public record TreeItem(String id, String title, Optional<String> icon, boolean leaf, List<TreeItem> children) {
    public TreeItem {
        ModelChecks.id(id);
        ModelChecks.label(title, "title"); Objects.requireNonNull(icon); icon.ifPresent(name -> ModelChecks.label(name, "icon")); children = List.copyOf(children); if (leaf && !children.isEmpty()) throw new IllegalArgumentException("Leaf cannot have children");
    }
}
