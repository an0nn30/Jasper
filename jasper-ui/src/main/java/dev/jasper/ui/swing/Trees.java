package dev.jasper.ui.swing;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes;

import java.util.*;
import java.util.List;

/** Flatten only expanded branches; expansion intent carries both owner and local item id. */
final class Trees {
    private Trees() {}

    record Visible(TreeItem item, int depth) {}

    static final class TreeControl extends InteractiveRows.RowsControl {
        final List<Visible> visible = new ArrayList<>();

        TreeControl(KitView view, Nodes.Tree node) {
            super(view, node);
            refresh();
            InteractiveRows.bind(canvas, "RIGHT", () -> expandFocused(true));
            InteractiveRows.bind(canvas, "LEFT", () -> expandFocused(false));
        }

        Nodes.Tree value() {
            return (Nodes.Tree) node;
        }

        @Override
        int size() {
            return visible.size();
        }

        @Override
        String itemId(int i) {
            return visible.get(i).item().id();
        }

        @Override
        Set<String> specifiedSelection() {
            return value().selectedIds();
        }

        @Override
        boolean multiple() {
            return false;
        }

        @Override
        String activation() {
            return value().activateAction();
        }

        @Override
        List<MenuItem> contextActions() {
            return List.of();
        }

        @Override
        void data() {
            visible.clear();
            value().roots().forEach(root -> flatten(root, 0));
        }

        void flatten(TreeItem item, int depth) {
            visible.add(new Visible(item, depth));
            if (value().expandedIds().contains(item.id()))
                item.children().forEach(child -> flatten(child, depth + 1));
        }

        void expandFocused(boolean expanded) {
            int i = indexOf(focusItem);
            if (i >= 0 && !visible.get(i).item().leaf())
                emit(new UiEvent.Expanded(node.id(), focusItem, expanded));
        }

        @Override
        void populate(InteractiveRows.Row row, int index) {
            row.begin();
            var entry = visible.get(index);
            var item = entry.item();
            row.indent = entry.depth() * com.formdev.flatlaf.util.UIScale.scale(16);
            var leading = new ArrayList<Nodes.Node>();
            if (!item.leaf())
                leading.add(
                        new Nodes.Button(
                                "@tree/" + node.id() + "/" + item.id() + "/expand",
                                ControlState.READY,
                                "",
                                value().expandedIds().contains(item.id())
                                        ? "chevron.down"
                                        : "chevron.right",
                                Nodes.ButtonKind.GHOST,
                                "expand",
                                "Expand or collapse " + item.title()));
            item.icon()
                    .ifPresent(
                            icon ->
                                    leading.add(
                                            new Nodes.Icon(
                                                    "@tree/" + node.id() + "/" + item.id()
                                                            + "/icon",
                                                    ControlState.READY,
                                                    icon,
                                                    Nodes.Hue.NONE,
                                                    Nodes.IconSize.CONTROL,
                                                    item.title())));
            row.slot(row.leading, leading);
            row.slot(
                    row.title,
                    List.of(
                            new Nodes.Text(
                                    "@tree/" + node.id() + "/" + item.id() + "/title",
                                    ControlState.READY,
                                    item.title(),
                                    Nodes.TextRole.BODY)));
            row.slot(row.subtitle, List.of());
            row.slot(row.trailing, List.of());
            if (!item.leaf()) {
                var c = (Buttons.ButtonControl) row.controls.get(leading.getFirst().id());
                if (c.button.getClientProperty("Jasper.treeCommand") == null) {
                    for (var listener : c.button.getActionListeners())
                        c.button.removeActionListener(listener);
                    java.awt.event.ActionListener listener =
                            e ->
                                    emit(
                                            new UiEvent.Expanded(
                                                    node.id(),
                                                    row.id,
                                                    !value().expandedIds().contains(row.id)));
                    c.button.addActionListener(listener);
                    c.own(() -> c.button.removeActionListener(listener));
                    c.button.putClientProperty("Jasper.treeCommand", true);
                }
            }
        }
    }
}
