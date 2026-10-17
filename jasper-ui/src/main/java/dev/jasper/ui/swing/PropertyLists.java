package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** SDK 0.18's Properties (spec 8.2): a heading over a card of label and value rows. */
final class PropertyLists {
    private PropertyLists() {}

    /** A value coloured by its severity on the card, held to the text floor. */
    static PaintPair valuePair(Nodes.Severity severity) {
        return new PaintPair(Labels.tint(severity), SemanticColor.SURFACE_CARD, SurfaceContrast.Guard.TEXT);
    }

    static final class PropertiesControl extends ControlRegistry.Control {
        final JLabel title = Labels.literal();
        final Cards.CardSurface card;
        final ArrayList<JLabel> labels = new ArrayList<>(), values = new ArrayList<>();

        PropertiesControl(KitView view, Nodes.Properties node) {
            super(view, node, new JPanel());
            component.setOpaque(false);
            card = new Cards.CardSurface(view.context());
            KitOwnership.mark(title, node.id() + ".title");
            KitOwnership.mark(card, node.id() + ".rows");
            card.setLayout(new Grid(this));
            Stacks.mount(component, List.<JComponent>of(title, card));
            refresh();
        }

        @Override void refresh() {
            state();
            var properties = (Nodes.Properties) node; var theme = context().theme();
            int count = properties.rows().size();
            while (labels.size() > count) { card.remove(labels.removeLast()); card.remove(values.removeLast()); }
            while (labels.size() < count) {
                int index = labels.size();
                var label = Labels.literal(); var value = Labels.literal();
                KitOwnership.mark(label, node.id() + ".label." + index);
                KitOwnership.mark(value, node.id() + ".value." + index);
                label.setLabelFor(value);
                labels.add(label); values.add(value); card.add(label); card.add(value);
            }
            for (int i = 0; i < count; i++) {
                var row = properties.rows().get(i); var label = labels.get(i); var value = values.get(i);
                label.setText(row.label());
                Composition.style(label, context(), Typography.Role.LABEL, SemanticColor.TEXT_SECONDARY);
                value.setText(row.value());
                value.setToolTipText(row.value().isEmpty() ? null : row.value());
                value.setFont(ThemePainting.font(context(), row.technical() ? Typography.Role.MONO : Typography.Role.BODY, row.value()));
                value.setForeground(row.severity().map(severity -> SurfaceContrast.painted(theme, valuePair(severity)))
                    .orElseGet(() -> ThemePainting.color(context(), SemanticColor.TEXT_PRIMARY)));
                // A data value keeps its end in view when it is shortened, as monospaced text does.
                value.putClientProperty("Jasper.leadingEllipsis", row.technical());
                label.setEnabled(component.isEnabled()); value.setEnabled(component.isEnabled());
            }
            title.setText(properties.title());
            title.setVisible(!properties.title().isBlank());
            title.setEnabled(component.isEnabled());
            Composition.style(title, context(), Typography.Role.SECTION_LABEL, SemanticColor.TEXT_TERTIARY);
            int pad = ThemePainting.metric(context(), DesignMetrics.Role.CARD_PADDING) + ThemePainting.metric(context(), DesignMetrics.Role.HAIRLINE);
            card.setBorder(new EmptyBorder(pad, pad, pad, pad));
            component.setLayout(new Composition.Vertical(ThemePainting.metric(context(), DesignMetrics.Role.SECTION_LABEL_GAP)));
            component.revalidate(); component.repaint();
        }
    }

    /** A label column as wide as the widest label, then one value column; one line per row. */
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
