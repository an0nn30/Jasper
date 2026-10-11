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
        boolean settingsNavigation;
        final List<Visible> visible = new ArrayList<>();
        final Set<String> itemIds = new HashSet<>();
        final Map<String, TreeItem> items = new HashMap<>();
        /** Each trailing column's width, measured over every item so the columns line up from row to row. */
        int[] trailingWidths = new int[0];

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
        boolean reportsClicks() {
            return true;
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
        List<MenuItem> contextActions(String id) {
            var item = items.get(id);
            return item == null ? List.of() : item.contextActions().orElse(List.of());
        }

        /** Tree menus are always the item's own. */
        @Override
        boolean ownsContextActions(String id) {
            return items.containsKey(id);
        }

        @Override
        String itemText(int i) {
            return visible.get(i).item().title();
        }

        @Override
        void data() {
            visible.clear();
            itemIds.clear();
            items.clear();
            value().roots().forEach(root -> flatten(root, 0, true));
            var metrics = canvas.getFontMetrics(ThemePainting.font(context(), dev.jasper.ui.metrics.Typography.Role.CAPTION));
            int columns = items.values().stream().mapToInt(item -> item.trailing().size()).max().orElse(0);
            trailingWidths = new int[columns];
            // Detail columns are at least TREE_DETAIL_COLUMN wide and the last (the value) at least TREE_VALUE_COLUMN.
            for (int i = 0; i < columns; i++)
                trailingWidths[i] = ThemePainting.metric(context(), i == columns - 1
                        ? dev.jasper.ui.metrics.DesignMetrics.Role.TREE_VALUE_COLUMN
                        : dev.jasper.ui.metrics.DesignMetrics.Role.TREE_DETAIL_COLUMN);
            for (var item : items.values())
                for (int i = 0; i < item.trailing().size(); i++)
                    trailingWidths[i] = Math.max(trailingWidths[i], metrics.stringWidth(item.trailing().get(i)));
        }

        @Override
        boolean containsItem(String id) {
            return itemIds.contains(id);
        }

        void flatten(TreeItem item, int depth, boolean shown) {
            itemIds.add(item.id());
            items.put(item.id(), item);
            if (shown) visible.add(new Visible(item, depth));
            boolean childrenShown = shown && value().expandedIds().contains(item.id());
            item.children().forEach(child -> flatten(child, depth + 1, childrenShown));
        }

        void expandFocused(boolean expanded) {
            int i = indexOf(focusItem);
            if (i >= 0 && !visible.get(i).item().leaf())
                emit(new UiEvent.Expanded(node.id(), focusItem, expanded));
        }

        private static Nodes.Hue settingsHue(String icon){return switch(icon){case "appearance","font"->Nodes.Hue.BLUE;case "bell"->Nodes.Hue.AMBER;case "user","plugin"->Nodes.Hue.PURPLE;case "terminal"->Nodes.Hue.GREEN;default->Nodes.Hue.NEUTRAL;};}

        @Override
        void populate(InteractiveRows.Row row, int index) {
            row.begin();
            var entry = visible.get(index);
            var item = entry.item();
            row.indent = settingsNavigation?0:entry.depth() * com.formdev.flatlaf.util.UIScale.scale(16);
            var leading = new ArrayList<Nodes.Node>();
            if (!item.leaf() && !settingsNavigation)
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
                                                    settingsNavigation ? settingsHue(icon) : Nodes.Hue.NONE,
                                                    Nodes.IconSize.CONTROL,
                                                    item.title())));
            item.status().ifPresent(status -> leading.add(new Nodes.StatusDot(
                    "@tree/" + node.id() + "/" + item.id() + "/status", ControlState.READY, status)));
            row.slot(row.leading, leading);
            if(settingsNavigation)for(var control:row.controls.values())if(control instanceof Labels.IconControl icon)icon.tile(context().metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.SETTINGS_ICON_TILE),context().metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.SETTINGS_ICON_TILE),context().icons().size(Nodes.IconSize.CONTROL),selected.contains(item.id()));
            String titleId = "@tree/" + node.id() + "/" + item.id() + "/title";
            row.slot(
                    row.title,
                    List.of(
                            new Nodes.Text(
                                    titleId,
                                    ControlState.READY,
                                    item.title(),
                                    settingsNavigation && !item.leaf()?Nodes.TextRole.SECTION_LABEL:item.emphasized() ? Nodes.TextRole.BODY_STRONG : Nodes.TextRole.BODY)));
            // Emphasis is strong text in the link color; the text control's own refresh picks its role color first.
            if (item.emphasized())
                row.controls.get(titleId).component.setForeground(ThemePainting.color(context(), dev.jasper.ui.theme.SemanticColor.TEXT_LINK));
            row.slot(row.subtitle, List.of());
            var trailing = new ArrayList<Nodes.Node>();
            if(settingsNavigation&&!item.leaf())trailing.add(new Nodes.Button("@tree/"+node.id()+"/"+item.id()+"/expand",ControlState.READY,"",value().expandedIds().contains(item.id())?"chevron.down":"chevron.right",Nodes.ButtonKind.GHOST,"expand","Expand or collapse "+item.title(),Nodes.ButtonPresentation.CONTEXTUAL_EMBEDDED));
            for (int i = 0; i < item.trailing().size(); i++)
                trailing.add(new Nodes.Text("@tree/" + node.id() + "/" + item.id() + "/trailing/" + i,
                        ControlState.READY, item.trailing().get(i), Nodes.TextRole.CAPTION));
            item.count().ifPresent(count -> trailing.add(new Nodes.Text(
                    "@tree/" + node.id() + "/" + item.id() + "/count", ControlState.READY, Integer.toString(count), Nodes.TextRole.COUNT)));
            row.slot(row.trailing, trailing);
            for (int i = 0; i < item.trailing().size(); i++) {
                var label = (javax.swing.JLabel) row.controls.get(trailing.get(i).id()).component;
                label.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
                label.setPreferredSize(null);
                int width = i < trailingWidths.length ? trailingWidths[i] : label.getPreferredSize().width;
                label.setPreferredSize(new java.awt.Dimension(width + label.getInsets().left + label.getInsets().right, label.getPreferredSize().height));
            }
            // The row, its slots and its passive texts (title, trailing columns, count) show the item's literal tooltip.
            row.tooltip(item.tooltip());
            row.name(List.of(new Nodes.Text("@tree/" + node.id() + "/" + item.id() + "/name", ControlState.READY, item.title(), Nodes.TextRole.BODY)));
            if (!item.leaf()) {
                var c = (Buttons.ButtonControl) row.controls.get("@tree/"+node.id()+"/"+item.id()+"/expand");
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
