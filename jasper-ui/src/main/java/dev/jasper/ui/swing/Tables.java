package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.SemanticColor;

import java.awt.*;
import java.util.List;
import java.util.Set;

import javax.swing.*;

/** Column-aligned virtual rows; numeric cells align to the trailing column edge. */
final class Tables {
    private Tables() {}

    static final class TableControl extends InteractiveRows.RowsControl {
        final JPanel header = new JPanel();

        TableControl(KitView view, Nodes.Table node) {
            super(view, node);
            header.setOpaque(false);
            scroll.setColumnHeaderView(header);
            refresh();
        }

        Nodes.Table value() {
            return (Nodes.Table) node;
        }

        @Override
        int size() {
            return value().rows().size();
        }

        @Override
        String itemId(int i) {
            return value().rows().get(i).id();
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
        void data() {
            header.removeAll();
            header.setLayout(new GridLayout(1, Math.max(1, value().columns().size())));
            for (var column : value().columns()) {
                var label = Labels.literal();
                label.setText(column.title());
                label.setFont(ThemePainting.font(context(), Typography.Role.CAPTION));
                label.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_SECONDARY));
                label.setHorizontalAlignment(
                        column.numeric() ? SwingConstants.RIGHT : SwingConstants.LEFT);
                header.add(label);
            }
            int pad =
                    ThemePainting.metric(
                            context(), dev.jasper.ui.metrics.DesignMetrics.Role.ICON_GAP);
            header.setBorder(
                    BorderFactory.createEmptyBorder(
                            pad,
                            pad + (multiple() ? new JCheckBox().getPreferredSize().width : 0),
                            pad,
                            pad));
        }

        @Override
        void populate(InteractiveRows.Row row, int index) {
            row.begin();
            row.slot(row.leading, List.of());
            row.slot(row.subtitle, List.of());
            row.slot(row.trailing, List.of());
            var data = value().rows().get(index);
            var nodes = new java.util.ArrayList<Nodes.Node>();
            for (var column : value().columns())
                nodes.add(
                        new Nodes.Text(
                                "@table/" + node.id() + "/" + data.id() + "/" + column.id(),
                                ControlState.READY,
                                data.cells().getOrDefault(column.id(), ""),
                                column.role()));
            row.slot(row.title, nodes);
            row.title.setLayout(new GridLayout(1, Math.max(1, nodes.size())));
            for (int i = 0; i < nodes.size(); i++) {
                var label = (JLabel) row.title.getComponent(i);
                label.setHorizontalAlignment(
                        value().columns().get(i).numeric()
                                ? SwingConstants.RIGHT
                                : SwingConstants.LEFT);
                label.setIcon(
                        i == 0 && data.icon().isPresent()
                                ? context()
                                        .icons()
                                        .icon(
                                                data.icon().get(),
                                                context().theme().semantic(),
                                                Nodes.Hue.NONE,
                                                com.formdev.flatlaf.util.UIScale.scale(14))
                                : null);
                row.cells.add(label);
            }
        }
    }
}
