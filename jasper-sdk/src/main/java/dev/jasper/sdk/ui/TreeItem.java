package dev.jasper.sdk.ui;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.List;
import java.util.Objects;

/**
 * Immutable tree item descriptor: one row of a {@link Nodes.Tree}, with the items below it.
 *
 * @param id the item's id, unique in its tree; reported in {@link UiEvent.Selected}, {@link UiEvent.Expanded} and
 *     {@link UiEvent.Clicked}
 * @param title the row's literal text and accessible name; not blank
 * @param icon the name of an icon drawn before the title; absent for none
 * @param leaf true for an item without a disclosure; false for a branch, which shows one even before it has children
 *     and may be named in the tree's {@code expandedIds}
 * @param children the items shown below this one while the tree's {@code expandedIds} names it; a leaf has none
 * @param count a number shown at the row's end in the count role, such as search matches; not negative, and absent
 *     for none
 * @param emphasized true marks the item for attention, such as unsaved changes, drawing the title in strong text in the
 *     link colour
 * @param tooltip the row's literal tooltip and accessible description; blank for none
 * @param status present draws a status dot of that kind after the icon
 * @param trailing texts shown right-aligned after the title as columns that line up across the tree's rows; under the
 *     tree's columns there is at most one per column after the first
 * @param contextActions present is the menu a right click on the row opens, whether or not the row is selected, with
 *     unique ids; choosing an item reports only its {@link UiEvent.Action}; empty or absent for none
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
    /**
     * An item without a tooltip, status, trailing texts or context menu.
     *
     * @param id the item's id, unique in its tree
     * @param title the row's literal text; not blank
     * @param icon the name of an icon drawn before the title; absent for none
     * @param leaf true for an item without a disclosure
     * @param children the items shown below this one while it is expanded
     * @param count a number shown at the row's end, not negative; absent for none
     * @param emphasized true draws the title in strong text in the link colour
     */
    public TreeItem(String id, String title, Optional<String> icon, boolean leaf, List<TreeItem> children, OptionalInt count, boolean emphasized) {
        this(id, title, icon, leaf, children, count, emphasized, "", Optional.empty(), List.of(), Optional.empty());
    }
    /**
     * An item without a count or emphasis, tooltip, status, trailing texts or context menu.
     *
     * @param id the item's id, unique in its tree
     * @param title the row's literal text; not blank
     * @param icon the name of an icon drawn before the title; absent for none
     * @param leaf true for an item without a disclosure
     * @param children the items shown below this one while it is expanded
     */
    public TreeItem(String id, String title, Optional<String> icon, boolean leaf, List<TreeItem> children) {
        this(id, title, icon, leaf, children, OptionalInt.empty(), false);
    }
}
