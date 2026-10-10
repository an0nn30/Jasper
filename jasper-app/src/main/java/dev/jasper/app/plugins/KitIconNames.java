package dev.jasper.app.plugins;

import dev.jasper.sdk.ui.*;
import java.util.List;
import java.util.function.Consumer;

/** Registry-dependent validation of every descriptor position, including lazy menus and collapsed tree children.
 * Mirrors the other SDK runtime; the shared KitContractTest owns parity. No view state changes during this walk.
 */
final class KitIconNames {
    private KitIconNames() {}
    static void validate(ViewSpec spec, Consumer<String> require) { node(spec.root(), require); }
    private static void optional(String name, Consumer<String> require) {
        if (!name.isEmpty()) require.accept(name);
    }
    private static void menus(List<MenuItem> items, Consumer<String> require) {
        // Menu descriptors use blank for no artwork; button/choice cues use only the empty string.
        for (var item : items) if (!item.icon().isBlank()) require.accept(item.icon());
    }
    private static void tree(TreeItem item, Consumer<String> require) {
        item.icon().ifPresent(require);
        item.contextActions().ifPresent(items -> menus(items, require));
        for (var child : item.children()) tree(child, require);
    }
    private static void node(Nodes.Node node, Consumer<String> require) {
        switch (node) {
            case Nodes.Icon icon -> require.accept(icon.name());
            case Nodes.Button button -> optional(button.icon(), require);
            case Nodes.MenuButton menu -> { optional(menu.icon(), require); menus(menu.items(), require); }
            case Nodes.Choice choice -> optional(choice.icon(), require);
            case Nodes.Header header -> menus(header.overflow(), require);
            case Nodes.ItemList list -> {
                menus(list.contextActions(), require);
                for (var item : list.items()) item.contextActions().ifPresent(items -> menus(items, require));
            }
            case Nodes.Table table -> {
                menus(table.contextActions(), require);
                for (var row : table.rows()) {
                    row.icon().ifPresent(require);
                    row.contextActions().ifPresent(items -> menus(items, require));
                }
            }
            case Nodes.Tree tree -> { for (var item : tree.roots()) tree(item, require); }
            case Nodes.Breadcrumbs crumbs -> { for (var crumb : crumbs.crumbs()) menus(crumb.contextActions(), require); }
            default -> { }
        }
        for (var child : KitContent.children(node)) node(child, require);
    }
}
