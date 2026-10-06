package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.SemanticColor;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Set;

import javax.swing.*;

/**
 * Column-aligned virtual rows; numeric cells align to the trailing column edge. Columns share the width by weight,
 * the header shows the host's sort and reports clicks as {@link UiEvent.Sorted}, and a muted row dims every cell.
 */
final class Tables {
    private Tables() {}

    static JScrollPane pane(KitView view, Nodes.Table node) {
        return node.scroll() == Nodes.ScrollPolicy.OVERLAY ? new OverlayScrollPane(view.context()) : new JScrollPane();
    }

    static final class TableControl extends InteractiveRows.RowsControl {
        final JPanel header = new JPanel();

        TableControl(KitView view, Nodes.Table node) {
            super(view, node, pane(view, node));
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
        String itemText(int i) {
            var columns = value().columns();
            return columns.isEmpty() ? "" : value().rows().get(i).cells().getOrDefault(columns.getFirst().id(), "");
        }

        /** A table keeps Enter with no focused row, as the JTable it replaced did (its Enter moved the selection). */
        @Override
        boolean keepsEnterWithoutFocus() {
            return true;
        }

        @Override
        boolean reportsClicks() {
            return true;
        }

        /** A context press outside every row opens the table's shared menu, as the JTable's component popup did. */
        @Override
        boolean sharedMenuOutsideRows() {
            return true;
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
        List<MenuItem> contextActions(String id) {
            for (var row : value().rows())
                if (row.id().equals(id)) return row.contextActions().orElse(value().contextActions());
            return List.of();
        }

        @Override
        boolean ownsContextActions(String id) {
            return value().rows().stream().anyMatch(row -> row.id().equals(id) && row.contextActions().isPresent());
        }

        WeightLayout weights() {
            return new WeightLayout(value().columns().stream().mapToDouble(Column::weight).toArray());
        }

        @Override
        void data() {
            header.removeAll();
            header.setLayout(weights());
            for (var column : value().columns()) {
                var label = Labels.literal();
                KitOwnership.mark(label, "@table/" + node.id() + "/header/" + column.id());
                label.setText(column.title());
                label.setFont(ThemePainting.font(context(), Typography.Role.CAPTION));
                label.setForeground(ThemePainting.color(context(), SemanticColor.TEXT_SECONDARY));
                label.setHorizontalAlignment(
                        column.numeric() ? SwingConstants.RIGHT : SwingConstants.LEFT);
                label.setHorizontalTextPosition(SwingConstants.LEADING);
                label.setIcon(column.sorted().map(ascending -> context().icons().icon(ascending ? "chevron.up" : "chevron.down",
                        context().theme().semantic(), SemanticColor.TEXT_SECONDARY,
                        com.formdev.flatlaf.util.UIScale.scale(context().icons().size(Nodes.IconSize.CAPTION)))).orElse(null));
                label.getAccessibleContext().setAccessibleName(column.title()
                        + column.sorted().map(ascending -> ascending ? ", sorted ascending" : ", sorted descending").orElse(""));
                String columnId = column.id();
                label.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent event) {
                        if (SwingUtilities.isLeftMouseButton(event)) emit(new UiEvent.Sorted(node.id(), columnId));
                    }
                });
                header.add(label);
            }
            int pad =
                    ThemePainting.metric(
                            context(), DesignMetrics.Role.ICON_GAP);
            header.setBorder(
                    BorderFactory.createEmptyBorder(
                            pad,
                            pad + (multiple() ? new JCheckBox().getPreferredSize().width : 0),
                            pad,
                            pad));
            header.revalidate();
            header.repaint();
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
            row.title.setLayout(weights());
            Color muted = data.muted() ? ThemePainting.color(context(), SemanticColor.TEXT_PLACEHOLDER) : null;
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
                if (muted != null) label.setForeground(muted);
                row.cells.add(label);
            }
            row.tooltip(data.tooltip());
            // A table row is named by its first column, as its identity (a file's name, an entry's title).
            row.name(nodes.isEmpty() ? List.of() : List.of(nodes.getFirst()));
        }
    }

    /** Columns side by side, each taking its weight's share of the width; children beyond the weights get none. */
    static final class WeightLayout implements LayoutManager {
        final double[] weights;

        WeightLayout(double[] weights) {
            this.weights = weights;
        }

        @Override
        public void addLayoutComponent(String name, Component component) {}

        @Override
        public void removeLayoutComponent(Component component) {}

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            int width = 0, height = 0;
            for (var c : parent.getComponents()) {
                width += c.getPreferredSize().width;
                height = Math.max(height, c.getPreferredSize().height);
            }
            Insets insets = parent.getInsets();
            return new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            Insets insets = parent.getInsets();
            return new Dimension(insets.left + insets.right, preferredLayoutSize(parent).height);
        }

        @Override
        public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            int width = Math.max(0, parent.getWidth() - insets.left - insets.right),
                    height = Math.max(0, parent.getHeight() - insets.top - insets.bottom);
            double total = 0;
            for (double weight : weights) total += weight;
            double sum = 0;
            var children = parent.getComponents();
            for (int i = 0; i < children.length; i++) {
                if (i >= weights.length || total <= 0) {
                    children[i].setBounds(0, 0, 0, 0);
                    continue;
                }
                int start = (int) Math.round(width * sum / total);
                sum += weights[i];
                int end = (int) Math.round(width * sum / total);
                children[i].setBounds(insets.left + start, insets.top, end - start, height);
            }
        }
    }
}
