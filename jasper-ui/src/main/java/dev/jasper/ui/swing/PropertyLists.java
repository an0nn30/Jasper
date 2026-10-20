package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

/** SDK 0.18's Properties (spec 8.2): a heading over its rows, drawn by the kit's {@link PropertiesFace}. */
final class PropertyLists {
    private PropertyLists() {}

    /** A value coloured by its severity on the card, held to the text floor. */
    static PaintPair valuePair(Nodes.Severity severity) {
        return new PaintPair(Labels.tint(severity), SemanticColor.SURFACE_CARD, SurfaceContrast.Guard.TEXT);
    }

    static final class PropertiesControl extends ControlRegistry.Control {
        final PropertiesFace face;
        final JLabel title;
        final JComponent rows;
        final ArrayList<JLabel> labels = new ArrayList<>(), values = new ArrayList<>();

        PropertiesControl(KitView view, Nodes.Properties node) {
            super(view, node, new JPanel());
            component.setOpaque(false);
            face = view.context().faces().properties;
            title = face.title();
            rows = face.container(this);
            KitOwnership.mark(title, node.id() + ".title");
            KitOwnership.mark(rows, node.id() + ".rows");
            rows.setLayout(face.grid(this));
            Stacks.mount(component, List.<JComponent>of(title, rows));
            refresh();
        }

        @Override void refresh() {
            state();
            var properties = (Nodes.Properties) node;
            int count = properties.rows().size();
            while (labels.size() > count) { rows.remove(labels.removeLast()); rows.remove(values.removeLast()); }
            while (labels.size() < count) {
                int index = labels.size();
                var label = Labels.literal(); var value = Labels.literal();
                KitOwnership.mark(label, node.id() + ".label." + index);
                KitOwnership.mark(value, node.id() + ".value." + index);
                label.setLabelFor(value);
                labels.add(label); values.add(value); rows.add(label); rows.add(value);
            }
            for (int i = 0; i < count; i++) {
                var row = properties.rows().get(i); var label = labels.get(i); var value = values.get(i);
                label.setText(face.labelText(properties, row.label()));
                value.setText(row.value());
                value.setToolTipText(row.value().isEmpty() ? null : row.value());
                // A data value keeps its end in view when it is shortened, as monospaced text does.
                value.putClientProperty("Jasper.leadingEllipsis", row.technical());
                label.setEnabled(component.isEnabled()); value.setEnabled(component.isEnabled());
            }
            title.setText(properties.title());
            title.setVisible(!properties.title().isBlank());
            title.setEnabled(component.isEnabled());
            face.style(this);
            component.revalidate(); component.repaint();
        }

        Nodes.Node node() { return node; }
        JComponent component() { return component; }

        /** Whether {@code next}, leading a panel's footer stack, stands flush there (its face decides). */
        boolean flush(Nodes.Properties next) { return face.flush(next); }
    }

    /** The classic heading: a literal label that fills its background when the face makes it opaque (the title band). */
    static final class TitleLabel extends Labels.LiteralLabel {
        TitleLabel() { putClientProperty("html.disable", true); }
        @Override protected void paintComponent(Graphics g) {
            if (isOpaque()) { g.setColor(getBackground()); g.fillRect(0, 0, getWidth(), getHeight()); }
            super.paintComponent(g);
        }
    }

    /** The classic rows' container: the face paints its bands and rules under the labels and values. */
    static final class GridSurface extends JPanel {
        final PropertiesControl owner;
        GridSurface(PropertiesControl owner) { this.owner = owner; setOpaque(false); }
        @Override protected void paintComponent(Graphics g) {
            var graphics = (Graphics2D) g.create();
            try { owner.face.paint(owner, graphics, getWidth(), getHeight()); } finally { graphics.dispose(); }
        }
    }

    /** A label column as wide as the widest label, then one value column; one line per row (the default face). */
    static final class Grid implements Composition.WidthLayout {
        final PropertiesControl owner;

        Grid(PropertiesControl owner) { this.owner = owner; }

        private int labelWidth() { return owner.labels.stream().mapToInt(label -> label.getPreferredSize().width).max().orElse(0); }
        private int columnGap() { return ThemePainting.metric(owner.context(), DesignMetrics.Role.COLUMN_GAP); }
        private int rowGap() { return ThemePainting.metric(owner.context(), DesignMetrics.Role.ROW_GAP); }
        private int rowHeight(int index) {
            return Math.max(owner.labels.get(index).getPreferredSize().height, owner.values.get(index).getPreferredSize().height);
        }

        @Override public int height(Container parent, int width) {
            Insets insets = parent.getInsets();
            int height = insets.top + insets.bottom;
            for (int i = 0; i < owner.labels.size(); i++) height += rowHeight(i) + (i == 0 ? 0 : rowGap());
            return height;
        }

        @Override public Dimension preferredLayoutSize(Container parent) {
            Insets insets = parent.getInsets();
            int values = owner.values.stream().mapToInt(value -> value.getPreferredSize().width).max().orElse(0);
            int width = insets.left + insets.right + labelWidth() + columnGap() + values;
            return new Dimension(width, height(parent, width));
        }

        @Override public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            int labelWidth = labelWidth(), x = insets.left + labelWidth + columnGap(), y = insets.top;
            int valueWidth = Math.max(0, parent.getWidth() - insets.right - x);
            for (int i = 0; i < owner.labels.size(); i++) {
                int height = rowHeight(i);
                owner.labels.get(i).setBounds(insets.left, y, labelWidth, height);
                owner.values.get(i).setBounds(x, y, valueWidth, height);
                y += height + rowGap();
            }
        }
    }
}
