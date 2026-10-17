package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.util.List;
import javax.swing.*;

/**
 * The classic spinner (DESIGN_LANGUAGE §6, Settings › Window mock): a right-aligned data-face value with its unit muted,
 * and a narrow column of stacked ▲ and ▼ step buttons on the button gradient, ruled off in {@code border.divider}.
 */
final class ClassicStepperFace implements StepperFace {
    private final PaintPair arrow = new PaintPair(SemanticColor.TEXT_SECONDARY, SemanticColor.BUTTON_SECONDARY_BG, SurfaceContrast.Guard.INDICATOR);
    private final PaintPair unit = new PaintPair(SemanticColor.TEXT_PLACEHOLDER, SemanticColor.SURFACE_INPUT, SurfaceContrast.Guard.TEXT);

    @Override public List<PaintPair> pairs() { return List.of(arrow, unit); }

    @Override public void arrange(JPanel surface, JButton decrease, JButton increase) {
        var column = new JPanel(new GridLayout(2, 1, 0, 0));
        column.setOpaque(false);
        column.add(increase); column.add(decrease);
        surface.add(column, BorderLayout.EAST);
    }

    @Override public int alignment() { return SwingConstants.RIGHT; }

    @Override public Dimension stepSize(KitContext context, int direction) {
        return new Dimension(ThemePainting.metric(context, DesignMetrics.Role.SPINNER_ARROW_WIDTH),
            ThemePainting.height(context, DesignMetrics.Role.FIELD_HEIGHT, Typography.Role.BODY) / 2);
    }

    @Override public void paintStep(JButton button, Graphics2D g, KitContext c, int direction, boolean focus, Runnable standard) {
        ThemePainting.antialias(g);
        int w = button.getWidth(), h = button.getHeight(), line = ThemePainting.metric(c, DesignMetrics.Role.HAIRLINE);
        Color top = ThemePainting.color(c, SemanticColor.BUTTON_SECONDARY_BG), bottom = ThemePainting.role(c, DerivedColor.BUTTON_END);
        g.setPaint(button.getModel().isPressed() ? new GradientPaint(0, 0, bottom, 0, h, top) : new GradientPaint(0, 0, top, 0, h, bottom));
        g.fillRect(0, 0, w, h);
        g.setColor(ThemePainting.role(c, DerivedColor.BORDER_DIVIDER));
        g.fillRect(0, 0, line, h);
        if (direction < 0) g.fillRect(0, 0, w, line);
        if (!button.isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f));
        int glyph = ThemePainting.metric(c, DesignMetrics.Role.SPINNER_GLYPH);
        double y = (h - glyph * .8) / 2;
        if (direction > 0) ClassicGlyphs.up(g, (w + line) / 2.0, y, glyph, ThemePainting.painted(c, arrow));
        else ClassicGlyphs.down(g, (w + line) / 2.0, y, glyph, ThemePainting.painted(c, arrow));
        if (focus) {
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(ThemePainting.color(c, SemanticColor.BORDER_FOCUS));
            g.drawRect(line, 0, w - line - 1, h - 1);
        }
    }

    @Override public void styleValue(KitContext context, JComponent value, JLabel label) {
        value.setFont(ThemePainting.font(context, Typography.Role.MONO));
        label.setFont(value.getFont()); label.setForeground(ThemePainting.painted(context, unit));
    }

    /**
     * The value's share of the spinner's {@code SPINNER_WIDTH}, which spans both outline strokes (the mocks' 82): the arrow
     * column and the trailing stroke of {@link #surfaceInsets} take the rest (plan 9b Task 7).
     */
    @Override public int valueWidth(KitContext context) {
        return ThemePainting.metric(context, DesignMetrics.Role.SPINNER_WIDTH) - ThemePainting.metric(context, DesignMetrics.Role.SPINNER_ARROW_WIDTH)
            - surfaceInsets(context).right;
    }

    /** The arrow column stays inside the field's 1 px outline on its top, bottom and trailing sides. */
    @Override public Insets surfaceInsets(KitContext context) {
        int line = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
        return new Insets(line, 0, line, line);
    }

    @Override public boolean slider() { return false; }
}
