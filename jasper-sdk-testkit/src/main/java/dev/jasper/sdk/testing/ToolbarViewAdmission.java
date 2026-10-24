package dev.jasper.sdk.testing;

import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.ViewSpec;

/**
 * The fake's copy of the host's toolbar view rule (spec 10.3): an INLINE stack of LABEL text, DROPDOWN, SEARCHABLE or
 * PICKER choices, at most one PRIMARY button, icon-only GHOST buttons (a blank label, with an icon and a tooltip), menu
 * buttons and separators (placed as {@link ViewSpec} requires: between two items, never first, last or beside another).
 * The shared contracts keep the two in step.
 */
final class ToolbarViewAdmission {
    private ToolbarViewAdmission() { }

    /** Throws {@link IllegalArgumentException} naming the first node a toolbar does not admit. */
    static void require(ViewSpec spec) {
        if (!(spec.root() instanceof Nodes.Stack stack) || stack.role() != Nodes.LayoutRole.INLINE)
            throw new IllegalArgumentException("A toolbar view's root must be an INLINE Stack, not " + describe(spec.root()));
        int primaries = 0;
        for (Nodes.Node child : stack.children()) {
            boolean admitted = switch (child) {
                case Nodes.Text text -> text.role() == Nodes.TextRole.LABEL;
                case Nodes.Choice choice -> choice.kind() == Nodes.ChoiceKind.DROPDOWN || choice.kind() == Nodes.ChoiceKind.SEARCHABLE
                    || choice.kind() == Nodes.ChoiceKind.PICKER;
                case Nodes.Button button -> button.kind() == Nodes.ButtonKind.PRIMARY
                    || button.kind() == Nodes.ButtonKind.GHOST && button.label().isBlank();
                case Nodes.MenuButton ignored -> true;
                case Nodes.Separator ignored -> true;
                default -> false;
            };
            if (!admitted) throw new IllegalArgumentException("A toolbar view does not admit " + describe(child));
            if (child instanceof Nodes.Button button && button.kind() == Nodes.ButtonKind.PRIMARY && ++primaries > 1)
                throw new IllegalArgumentException("A toolbar view has at most one PRIMARY Button: " + describe(child));
        }
    }

    private static String describe(Nodes.Node node) {
        String kind = switch (node) {
            case Nodes.Text text -> "a " + text.role() + " Text";
            case Nodes.Choice choice -> "a " + choice.kind() + " Choice";
            case Nodes.Button button -> "a " + button.kind() + " Button";
            case Nodes.Stack stack -> "a " + stack.role() + " Stack";
            default -> "a " + node.getClass().getSimpleName();
        };
        return kind + " (" + node.id() + ")";
    }
}
