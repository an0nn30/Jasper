package dev.jasper.app.plugins;

import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.ViewSpec;

/**
 * What a toolbar view may hold (spec 10.3): an INLINE stack of LABEL text, DROPDOWN, SEARCHABLE or PICKER choices, at
 * most one PRIMARY button and menu buttons. The testkit keeps the same rule; the shared contracts check both.
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
                case Nodes.Button button -> button.kind() == Nodes.ButtonKind.PRIMARY;
                case Nodes.MenuButton ignored -> true;
                default -> false;
            };
            if (!admitted) throw new IllegalArgumentException("A toolbar view does not admit " + describe(child));
            if (child instanceof Nodes.Button && ++primaries > 1)
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
