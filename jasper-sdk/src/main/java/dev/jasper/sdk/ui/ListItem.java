package dev.jasper.sdk.ui;

import java.util.Optional;
import java.util.List;
import java.util.Objects;

/**
 * Immutable list item descriptor: one row of a {@link Nodes.ItemList}. Its slots hold {@link Nodes.Text},
 * {@link Nodes.Icon}, {@link Nodes.Chip}, {@link Nodes.StatusDot}, {@link Nodes.Progress}, {@link Nodes.Strength},
 * {@link Nodes.Button}, {@link Nodes.MenuButton}, {@link Nodes.Toggle} and {@link Nodes.Choice} nodes, or INLINE
 * {@link Nodes.Stack}s of them; slot nodes are view nodes, so their ids are unique in the view.
 *
 * @param id the item's id, unique in its list; reported in {@link UiEvent.Selected}, {@link UiEvent.Clicked},
 *     {@link UiEvent.Removed} and {@link UiEvent.Reordered}, and named by the list's {@code selectedIds}
 * @param leading the node before the title, such as an icon or a status dot; absent for none. An ORDERED list draws
 *     the row's number before it
 * @param title the nodes of the row's first line, in order
 * @param subtitle the nodes of a second line below the title; empty for none
 * @param trailing the nodes at the row's end, such as chips, buttons or a toggle; empty for none
 * @param heading true makes the row a kit-styled section heading: its title only, which must not be empty, with a
 *     separator above it unless it is the first row; it is never selected, activated, hovered or offered a menu, and an
 *     ORDERED list has none
 * @param contextActions present is the row's own menu with unique ids, offered on it whether or not it is selected,
 *     and empty means the row has none; absent, the list's menu, which acts on the selection, is offered while the row
 *     is selected. Always absent on a heading
 * @param tooltip the row's literal tooltip and accessible description, shown over its passive content (interactive
 *     controls keep their own); blank for none, and always blank on a heading
 */
public record ListItem(String id, Optional<Nodes.Node> leading, List<Nodes.Node> title, List<Nodes.Node> subtitle, List<Nodes.Node> trailing, boolean heading,
                       Optional<List<MenuItem>> contextActions, String tooltip) {
    public ListItem {
        ModelChecks.id(id);
        Objects.requireNonNull(leading); title = List.copyOf(title); subtitle = List.copyOf(subtitle); trailing = List.copyOf(trailing);
        leading.ifPresent(ListItem::inline); title.forEach(ListItem::inline); subtitle.forEach(ListItem::inline); trailing.forEach(ListItem::inline);
        if (heading && (leading.isPresent() || !subtitle.isEmpty() || !trailing.isEmpty() || title.isEmpty()))
            throw new IllegalArgumentException("A heading has a title only");
        Objects.requireNonNull(contextActions); contextActions = contextActions.map(List::copyOf); contextActions.ifPresent(items -> ModelChecks.unique(items, MenuItem::id));
        if (heading && contextActions.isPresent()) throw new IllegalArgumentException("A heading has no menu");
        Objects.requireNonNull(tooltip);
        if (heading && !tooltip.isBlank()) throw new IllegalArgumentException("A heading has no tooltip");
    }
    /**
     * An item without a tooltip.
     *
     * @param id the item's id, unique in its list
     * @param leading the node before the title; absent for none
     * @param title the nodes of the row's first line
     * @param subtitle the nodes of a second line; empty for none
     * @param trailing the nodes at the row's end; empty for none
     * @param heading true makes the row a section heading
     * @param contextActions the row's own menu, empty for none; absent for the list's menu
     */
    public ListItem(String id, Optional<Nodes.Node> leading, List<Nodes.Node> title, List<Nodes.Node> subtitle, List<Nodes.Node> trailing, boolean heading,
                    Optional<List<MenuItem>> contextActions) {
        this(id, leading, title, subtitle, trailing, heading, contextActions, "");
    }
    /**
     * An item without a menu of its own.
     *
     * @param id the item's id, unique in its list
     * @param leading the node before the title; absent for none
     * @param title the nodes of the row's first line
     * @param subtitle the nodes of a second line; empty for none
     * @param trailing the nodes at the row's end; empty for none
     * @param heading true makes the row a section heading
     */
    public ListItem(String id, Optional<Nodes.Node> leading, List<Nodes.Node> title, List<Nodes.Node> subtitle, List<Nodes.Node> trailing, boolean heading) {
        this(id, leading, title, subtitle, trailing, heading, Optional.empty());
    }
    /**
     * An ordinary, selectable item.
     *
     * @param id the item's id, unique in its list
     * @param leading the node before the title; absent for none
     * @param title the nodes of the row's first line
     * @param subtitle the nodes of a second line; empty for none
     * @param trailing the nodes at the row's end; empty for none
     */
    public ListItem(String id, Optional<Nodes.Node> leading, List<Nodes.Node> title, List<Nodes.Node> subtitle, List<Nodes.Node> trailing) {
        this(id, leading, title, subtitle, trailing, false);
    }
    /** Row slots are controlled presentation/actions, never mutable editor or secret storage. */
    private static void inline(Nodes.Node node) {
        switch (node) {
            case Nodes.Text _, Nodes.Icon _, Nodes.Chip _, Nodes.StatusDot _, Nodes.Progress _, Nodes.Strength _, Nodes.Button _, Nodes.MenuButton _, Nodes.Toggle _, Nodes.Choice _ -> {}
            case Nodes.Stack stack -> {
                if (stack.role() != Nodes.LayoutRole.INLINE) throw new IllegalArgumentException("Row stacks must be INLINE");
                stack.children().forEach(ListItem::inline);
            }
            default -> throw new IllegalArgumentException("Unsupported inline row node: " + node.getClass().getSimpleName());
        }
    }
}
