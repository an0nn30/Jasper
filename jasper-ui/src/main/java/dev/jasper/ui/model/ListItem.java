package dev.jasper.ui.model;

import java.util.Optional;
import java.util.List;
import java.util.Objects;

/**
 * Immutable listitem descriptor. A {@code heading} is a kit-styled section row (its title only, with a separator above
 * it unless it is the first row): it is never selected, activated, hovered or offered a menu.
 */
public record ListItem(String id, Optional<Nodes.Node> leading, List<Nodes.Node> title, List<Nodes.Node> subtitle, List<Nodes.Node> trailing, boolean heading) {
    public ListItem {
        ModelChecks.id(id);
        Objects.requireNonNull(leading); title = List.copyOf(title); subtitle = List.copyOf(subtitle); trailing = List.copyOf(trailing);
        leading.ifPresent(ListItem::inline); title.forEach(ListItem::inline); subtitle.forEach(ListItem::inline); trailing.forEach(ListItem::inline);
        if (heading && (leading.isPresent() || !subtitle.isEmpty() || !trailing.isEmpty() || title.isEmpty()))
            throw new IllegalArgumentException("A heading has a title only");
    }
    /** An ordinary, selectable item. */
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
