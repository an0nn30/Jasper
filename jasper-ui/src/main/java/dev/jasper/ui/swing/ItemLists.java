package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes;

import java.awt.Container;
import java.util.*;
import java.util.List;

import javax.swing.*;

/** Interactive semantic item rows with a footer outside the selectable data set. */
final class ItemLists {
    private ItemLists() {}

    static final class ListControl extends InteractiveRows.RowsControl {
        final Map<String, Integer> nodeRows = new HashMap<>();
        final EnumMap<Typography.Role, Integer> measuredFonts =
                new EnumMap<>(Typography.Role.class);
        final EnumMap<DesignMetrics.Role, Integer> measuredInputs =
                new EnumMap<>(DesignMetrics.Role.class);

        ListControl(KitView view, Nodes.ItemList node) {
            super(view, node);
            refresh();
        }

        Nodes.ItemList value() {
            return (Nodes.ItemList) node;
        }

        @Override
        int size() {
            return value().items().size();
        }

        @Override
        String itemId(int i) {
            return value().items().get(i).id();
        }

        @Override
        Set<String> specifiedSelection() {
            return value().selectedIds();
        }

        @Override
        boolean multiple() {
            return value().selectionMode() == Nodes.SelectionMode.MULTIPLE;
        }

        @Override
        String activation() {
            return value().activateAction();
        }

        @Override
        List<MenuItem> contextActions() {
            return value().contextActions();
        }

        @Override
        int lines() {
            return value().kind() == Nodes.ListKind.DOUBLE ? 2 : 1;
        }

        /** Descriptor-only measurement keeps offscreen choices from allocating Swing widgets. */
        @Override
        int minimumRowHeight() {
            measuredFonts.clear();
            measuredInputs.clear();
            int height = 0;
            for (var item : value().items()) {
                int middle = slotHeight(item.title()) + slotHeight(item.subtitle());
                if (middle > 0)
                    middle += ThemePainting.metric(context(), DesignMetrics.Role.ROW_GAP);
                int side =
                        Math.max(
                                item.leading().map(this::inlineHeight).orElse(0),
                                slotHeight(item.trailing()));
                height = Math.max(height, Math.max(middle, side));
            }
            return height;
        }

        int slotHeight(List<Nodes.Node> nodes) {
            return nodes.stream().mapToInt(this::inlineHeight).max().orElse(0);
        }

        int fontHeight(Typography.Role role) {
            return measuredFonts.computeIfAbsent(
                    role,
                    key -> canvas.getFontMetrics(ThemePainting.font(context(), key)).getHeight());
        }

        int inputHeight(DesignMetrics.Role metric, Typography.Role font) {
            return measuredInputs.computeIfAbsent(
                    metric, key -> ThemePainting.height(context(), key, font));
        }

        int inlineHeight(Nodes.Node node) {
            if (!node.state().visible()) return 0;
            return switch (node) {
                case Nodes.Choice choice -> {
                    int input =
                            switch (choice.kind()) {
                                case DROPDOWN ->
                                        inputHeight(
                                                DesignMetrics.Role.FIELD_HEIGHT,
                                                Typography.Role.BODY);
                                case PICKER ->
                                        inputHeight(
                                                DesignMetrics.Role.BUTTON_HEIGHT,
                                                Typography.Role.LABEL);
                                case SEGMENTED ->
                                        inputHeight(
                                                        DesignMetrics.Role.PANEL_ACTION,
                                                        Typography.Role.LABEL)
                                                + 2
                                                        * ThemePainting.metric(
                                                                context(),
                                                                DesignMetrics.Role.SEGMENT_PADDING);
                            };
                    yield input
                            + (choice.state().error().isEmpty()
                                    ? 0
                                    : fontHeight(Typography.Role.CAPTION)
                                            + ThemePainting.metric(
                                                    context(), DesignMetrics.Role.ICON_GAP));
                }
                case Nodes.Stack stack -> slotHeight(stack.children());
                case Nodes.Text text -> fontHeight(Typography.Role.valueOf(text.role().name()));
                case Nodes.Icon icon ->
                        com.formdev.flatlaf.util.UIScale.scale(context().icons().size(icon.size()));
                case Nodes.Button _, Nodes.MenuButton _ ->
                        inputHeight(DesignMetrics.Role.BUTTON_HEIGHT, Typography.Role.LABEL);
                case Nodes.Toggle toggle ->
                        ThemePainting.metric(
                                context(),
                                toggle.compact()
                                        ? DesignMetrics.Role.COMPACT_SWITCH_HEIGHT
                                        : DesignMetrics.Role.SWITCH_HEIGHT);
                default -> fontHeight(Typography.Role.BODY);
            };
        }

        @Override
        void data() {
            nodeRows.clear();
            for (int i = 0; i < size(); i++) {
                var item = value().items().get(i);
                final int index = i;
                item.leading().ifPresent(n -> index(n, index));
                item.title().forEach(n -> index(n, index));
                item.subtitle().forEach(n -> index(n, index));
                item.trailing().forEach(n -> index(n, index));
            }
            if (focusControl != null
                    && !nodeRows.containsKey(focusControl)
                    && !(value().kind() == Nodes.ListKind.ORDERED
                            && focusItem != null
                            && List.of("up", "down", "remove").stream()
                                    .anyMatch(
                                            suffix ->
                                                    focusControl.equals(
                                                            internalId(focusItem, suffix)))))
                focusControl = null;
        }

        void index(Nodes.Node n, int index) {
            nodeRows.put(n.id(), index);
            ControlRegistry.children(n).forEach(child -> index(child, index));
        }

        @Override
        ControlRegistry.Control inline(String id) {
            Integer index = nodeRows.get(id);
            if (index == null) return null;
            focusItem = itemId(index);
            focusControl = id;
            ensure(index);
            var row = mounted.get(focusItem);
            return row == null ? null : row.controls.get(id);
        }

        @Override
        Container childParent(Nodes.Node child) {
            return canvas;
        }

        @Override
        void children(List<ControlRegistry.Control> controls) {
            JComponent next = controls.isEmpty() ? null : controls.getFirst().component;
            if (footer != next && footer != null) canvas.remove(footer);
            footer = next;
            if (footer != null && footer.getParent() != canvas) canvas.add(footer);
            canvas.revalidate();
            sync();
        }

        @Override
        void populate(InteractiveRows.Row row, int index) {
            var item = value().items().get(index);
            row.begin();
            var leading = new ArrayList<Nodes.Node>(item.leading().stream().toList());
            var trailing = new ArrayList<Nodes.Node>(item.trailing());
            if (value().kind() == Nodes.ListKind.ORDERED) {
                leading.addFirst(
                        new Nodes.Text(
                                internalId(row.id, "index"),
                                ControlState.READY,
                                Integer.toString(index + 1),
                                Nodes.TextRole.CAPTION));
                trailing.add(commandNode(row, "up", "chevron.up", "Move up", index > 0));
                trailing.add(
                        commandNode(row, "down", "chevron.down", "Move down", index < size() - 1));
                trailing.add(commandNode(row, "remove", "close", "Remove", true));
            }
            row.slot(row.leading, leading);
            row.slot(row.title, item.title());
            row.slot(row.subtitle, item.subtitle());
            row.slot(row.trailing, trailing);
            if (value().kind() == Nodes.ListKind.ORDERED) {
                command(
                        row,
                        "up",
                        index > 0,
                        () -> emit(new UiEvent.Reordered(node.id(), row.id, row.index - 1)));
                command(
                        row,
                        "down",
                        index < size() - 1,
                        () -> emit(new UiEvent.Reordered(node.id(), row.id, row.index + 1)));
                command(row, "remove", true, () -> emit(new UiEvent.Removed(node.id(), row.id)));
            }
        }

        String internalId(String item, String suffix) {
            String id = "@ordered/" + node.id() + "/" + item + "/" + suffix;
            while (nodeRows.containsKey(id)) id = "@" + id;
            return id;
        }

        Nodes.Button commandNode(
                InteractiveRows.Row row,
                String suffix,
                String icon,
                String title,
                boolean possible) {
            return new Nodes.Button(
                    internalId(row.id, suffix),
                    new ControlState(true, possible, false, ""),
                    "",
                    icon,
                    Nodes.ButtonKind.GHOST,
                    suffix,
                    title);
        }

        void command(InteractiveRows.Row row, String suffix, boolean possible, Runnable action) {
            var c = (Buttons.ButtonControl) row.controls.get(internalId(row.id, suffix));
            c.button.disabledOpacity = possible ? .42f : .30f;
            if (c.button.getClientProperty("Jasper.rowCommand") == null) {
                for (var listener : c.button.getActionListeners())
                    c.button.removeActionListener(listener);
                java.awt.event.ActionListener listener =
                        e -> {
                            if (interactive() && c.button.isEnabled())
                                ((Runnable) c.button.getClientProperty("Jasper.rowCommand")).run();
                        };
                c.button.addActionListener(listener);
                c.own(() -> c.button.removeActionListener(listener));
            }
            c.button.putClientProperty("Jasper.rowCommand", action);
        }
    }
}
