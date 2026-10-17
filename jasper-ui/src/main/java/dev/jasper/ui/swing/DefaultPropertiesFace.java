package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.Graphics2D;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.border.EmptyBorder;

/** SDK 0.18's Properties as plan 3b drew them: a section-label heading over a card of label and value rows. */
final class DefaultPropertiesFace implements PropertiesFace {
    @Override public List<PaintPair> pairs() { return List.of(); }

    @Override public JLabel title() { return Labels.literal(); }

    @Override public JComponent container(PropertyLists.PropertiesControl control) { return new Cards.CardSurface(control.context()); }

    @Override public String labelText(Nodes.Properties node, String label) { return label; }

    @Override public void style(PropertyLists.PropertiesControl control) {
        var context = control.context(); var properties = (Nodes.Properties) control.node(); var theme = context.theme();
        for (int i = 0; i < control.labels.size(); i++) {
            var row = properties.rows().get(i); var label = control.labels.get(i); var value = control.values.get(i);
            Composition.style(label, context, Typography.Role.LABEL, SemanticColor.TEXT_SECONDARY);
            value.setFont(ThemePainting.font(context, row.technical() ? Typography.Role.MONO : Typography.Role.BODY, row.value()));
            value.setForeground(row.severity().map(severity -> SurfaceContrast.painted(theme, PropertyLists.valuePair(severity)))
                .orElseGet(() -> ThemePainting.color(context, SemanticColor.TEXT_PRIMARY)));
        }
        Composition.style(control.title, context, Typography.Role.SECTION_LABEL, SemanticColor.TEXT_TERTIARY);
        int pad = ThemePainting.metric(context, DesignMetrics.Role.CARD_PADDING) + ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
        control.rows.setBorder(new EmptyBorder(pad, pad, pad, pad));
        control.component().setLayout(new Composition.Vertical(ThemePainting.metric(context, DesignMetrics.Role.SECTION_LABEL_GAP)));
    }

    @Override public Composition.WidthLayout grid(PropertyLists.PropertiesControl control) { return new PropertyLists.Grid(control); }

    @Override public void paint(PropertyLists.PropertiesControl control, Graphics2D g, int width, int height) { }
}
