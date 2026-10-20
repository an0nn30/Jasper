package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.ColorRole;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.border.EmptyBorder;

/**
 * The classic Properties of the Enterprise mocks (plan 10a Rulings 4). Titled: the Workstation grid, a {@code ROW_HEIGHT}
 * title band on {@code surface.panel} between a {@code border.window} rule and a panel rule, then rows of a label column
 * ({@code PROPERTY_LABEL_WIDTH}, {@code surface.panel}) and a value cell ({@code surface.card}), each row and the column
 * closed by a panel rule. Untitled: label-colon lines on the dialog surface, the values at
 * {@code PROPERTY_PLAIN_LABEL_WIDTH}.
 */
final class ClassicPropertiesFace implements PropertiesFace {
    private final SemanticColor surface;

    ClassicPropertiesFace(SemanticColor dialogSurface) { this.surface = dialogSurface; }

    private PaintPair text(ColorRole slot, ColorRole against) { return new PaintPair(slot, against, SurfaceContrast.Guard.TEXT); }

    @Override public List<PaintPair> pairs() {
        return List.of(text(SemanticColor.TEXT_STRONG_SECONDARY, SemanticColor.SURFACE_PANEL), text(SemanticColor.TEXT_SECONDARY, SemanticColor.SURFACE_PANEL),
            text(SemanticColor.TEXT_PRIMARY, SemanticColor.SURFACE_CARD),
            text(SemanticColor.TEXT_SECONDARY, surface), text(SemanticColor.TEXT_PRIMARY, surface));
    }

    /**
     * The rule above the title band: {@code border.window} as the theme gives it, unguarded, as plan 6b Rulings 34 paints
     * the tab strip's dividers; Enterprise Dark's {@code #2A2C30} on {@code surface.panel} is under the structural floor,
     * and the guard would lift it to a rule lighter than the panel (plan 10a Execution deviations).
     */
    private static Color windowRule(KitContext context) { return ThemePainting.role(context, SemanticColor.BORDER_WINDOW); }

    /**
     * The grid's rules under the title, under each row and after the label column: the panel rule
     * ({@link ThemePainting#panelRule}), which matches the mocks' faint rules in Light and Dark; they sit under the
     * structural floor.
     */
    private static Color rule(KitContext context) { return ThemePainting.panelRule(context); }

    static boolean titled(PropertyLists.PropertiesControl control) { return !((Nodes.Properties) control.node()).title().isBlank(); }

    @Override public JLabel title() { return new PropertyLists.TitleLabel(); }

    @Override public JComponent container(PropertyLists.PropertiesControl control) { return new PropertyLists.GridSurface(control); }

    @Override public String labelText(Nodes.Properties node, String label) { return node.title().isBlank() ? label + ":" : label; }

    /** The titled grid stands flush in a panel's footer; label-colon lines keep the footer's padding. */
    @Override public boolean flush(Nodes.Properties node) { return !node.title().isBlank(); }

    private int metric(PropertyLists.PropertiesControl control, DesignMetrics.Role role) { return ThemePainting.metric(control.context(), role); }

    @Override public void style(PropertyLists.PropertiesControl control) {
        var context = control.context(); var properties = (Nodes.Properties) control.node();
        boolean grid = titled(control);
        ColorRole labelGround = grid ? SemanticColor.SURFACE_PANEL : surface, valueGround = grid ? SemanticColor.SURFACE_CARD : surface;
        for (int i = 0; i < control.labels.size(); i++) {
            var row = properties.rows().get(i); var label = control.labels.get(i); var value = control.values.get(i);
            label.setFont(ThemePainting.font(context, Typography.Role.BODY));
            label.setForeground(ThemePainting.painted(context, text(SemanticColor.TEXT_SECONDARY, labelGround)));
            value.setFont(ThemePainting.font(context, row.technical() ? Typography.Role.MONO : Typography.Role.BODY, row.value()));
            value.setForeground(row.severity().map(severity -> ThemePainting.painted(context, new PaintPair(Labels.tint(severity), valueGround, SurfaceContrast.Guard.TEXT)))
                .orElseGet(() -> ThemePainting.painted(context, text(SemanticColor.TEXT_PRIMARY, valueGround))));
            label.setOpaque(false); value.setOpaque(false);
        }
        var title = control.title;
        title.setFont(ThemePainting.font(context, Typography.Role.BODY_STRONG));
        title.setForeground(ThemePainting.painted(context, text(SemanticColor.TEXT_STRONG_SECONDARY, SemanticColor.SURFACE_PANEL)));
        int row = metric(control, DesignMetrics.Role.ROW_HEIGHT), hairline = metric(control, DesignMetrics.Role.HAIRLINE), gap = metric(control, DesignMetrics.Role.ICON_GAP);
        int text = title.getFontMetrics(title.getFont()).getHeight(), above = Math.max(0, (row - text) / 2), below = Math.max(0, row - text - above);
        title.setOpaque(grid);
        title.setBackground(ThemePainting.role(context, SemanticColor.SURFACE_PANEL));
        title.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(hairline, 0, 0, 0, windowRule(context)),
            BorderFactory.createMatteBorder(0, 0, hairline, 0, rule(context))),
            new EmptyBorder(above, hairline + gap, below, gap)));
        control.rows.setBorder(new EmptyBorder(0, 0, 0, 0));
        control.component().setLayout(new Composition.Vertical(grid ? 0 : metric(control, DesignMetrics.Role.SECTION_LABEL_GAP)));
    }

    /** The label column's width: at least the role, and wide enough for the widest label with its padding. */
    int labelColumn(PropertyLists.PropertiesControl control) {
        int widest = control.labels.stream().mapToInt(label -> label.getPreferredSize().width).max().orElse(0);
        int gap = metric(control, DesignMetrics.Role.ICON_GAP);
        return titled(control) ? Math.max(metric(control, DesignMetrics.Role.PROPERTY_LABEL_WIDTH), widest + 2 * gap + metric(control, DesignMetrics.Role.HAIRLINE))
            : Math.max(metric(control, DesignMetrics.Role.PROPERTY_PLAIN_LABEL_WIDTH), widest + gap);
    }

    @Override public Composition.WidthLayout grid(PropertyLists.PropertiesControl control) {
        return new Composition.WidthLayout() {
            int rowHeight(int index) {
                if (titled(control)) return metric(control, DesignMetrics.Role.ROW_HEIGHT);
                return Math.max(control.labels.get(index).getPreferredSize().height, control.values.get(index).getPreferredSize().height);
            }
            int gap() { return titled(control) ? metric(control, DesignMetrics.Role.HAIRLINE) : metric(control, DesignMetrics.Role.PROPERTY_PLAIN_ROW_GAP); }
            @Override public int height(Container parent, int width) {
                int height = 0, count = control.labels.size();
                for (int i = 0; i < count; i++) height += rowHeight(i) + (titled(control) || i < count - 1 ? gap() : 0);
                return height;
            }
            @Override public Dimension preferredLayoutSize(Container parent) {
                int values = control.values.stream().mapToInt(value -> value.getPreferredSize().width).max().orElse(0);
                int width = labelColumn(control) + metric(control, DesignMetrics.Role.HAIRLINE) + 2 * metric(control, DesignMetrics.Role.ICON_GAP) + values;
                return new Dimension(width, height(parent, width));
            }
            @Override public void layoutContainer(Container parent) {
                boolean grid = titled(control);
                int column = labelColumn(control), hairline = metric(control, DesignMetrics.Role.HAIRLINE), gap = metric(control, DesignMetrics.Role.ICON_GAP);
                int labelX = grid ? hairline + gap : 0, valueX = grid ? column + hairline + gap : column, y = 0;
                for (int i = 0; i < control.labels.size(); i++) {
                    int height = rowHeight(i);
                    control.labels.get(i).setBounds(labelX, y, Math.max(0, column - labelX - (grid ? gap : 0)), height);
                    control.values.get(i).setBounds(valueX, y, Math.max(0, parent.getWidth() - valueX - (grid ? gap : 0)), height);
                    y += height + gap();
                }
            }
        };
    }

    @Override public void paint(PropertyLists.PropertiesControl control, Graphics2D g, int width, int height) {
        if (!titled(control)) return;
        var context = control.context();
        int column = labelColumn(control), hairline = metric(control, DesignMetrics.Role.HAIRLINE), stride = metric(control, DesignMetrics.Role.ROW_HEIGHT) + hairline;
        g.setColor(ThemePainting.role(context, SemanticColor.SURFACE_PANEL)); g.fillRect(0, 0, column, height);
        g.setColor(ThemePainting.role(context, SemanticColor.SURFACE_CARD)); g.fillRect(column + hairline, 0, Math.max(0, width - column - hairline), height);
        g.setColor(rule(context));
        g.fillRect(column, 0, hairline, height);
        for (int i = 1; i <= control.labels.size(); i++) g.fillRect(0, i * stride - hairline, width, hairline);
    }
}
