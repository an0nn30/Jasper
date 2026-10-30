package dev.jasper.browser.item;

import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.ui.Nodes;
import java.util.List;

/** Finds a browser item's nodes by id, in a view or a node tree. */
final class Views {
    private Views() { }

    static Nodes.Node node(Nodes.Node root, String id) {
        if (root.id().equals(id)) return root;
        List<Nodes.Node> children = switch (root) {
            case Nodes.Split split -> split.children();
            case Nodes.Stack stack -> stack.children();
            default -> List.of();
        };
        for (Nodes.Node child : children) {
            Nodes.Node found = node(child, id);
            if (found != null) return found;
        }
        return null;
    }

    static Nodes.Button button(FakeKitView view, String id) { return button(view.spec().root(), id); }

    static Nodes.Button button(Nodes.Node root, String id) { return (Nodes.Button) require(root, id); }

    static Nodes.Field field(FakeKitView view, String id) { return (Nodes.Field) require(view.spec().root(), id); }

    private static Nodes.Node require(Nodes.Node root, String id) {
        Nodes.Node found = node(root, id);
        if (found == null) throw new AssertionError("no node " + id);
        return found;
    }
}
