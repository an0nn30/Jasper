package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * The classic toggle (spec 5.2, DESIGN_LANGUAGE §6): a 13 px box leading its label. Checked, the accent inside an
 * {@code accent.edge} edge with an on-accent check; unchecked, the input surface inside a {@code border.hover} edge;
 * pending, {@code status.warning} with an on-accent dash; disabled, flat {@code surface.tableHeader} inside
 * {@code border.divider}; on a selected row, an outline in the row's ink, faded when disabled. It never animates.
 */
final class ClassicToggleFace implements ToggleFace {
    /** The share a disabled box on a selected row keeps, the disabled switch's. */
    static final double DISABLED_OPACITY = .42;
    private final PaintPair checkedEdge, mark, edge, pendingMark;

    /** A face whose box stands on {@code surface}, the kit's dialog surface. */
    ClassicToggleFace(SemanticColor surface) {
        checkedEdge = new PaintPair(DerivedColor.ACCENT_EDGE, surface, SurfaceContrast.Guard.INDICATOR);
        mark = new PaintPair(SemanticColor.TEXT_ONACCENT, SemanticColor.ACCENT, SurfaceContrast.Guard.INDICATOR);
        edge = new PaintPair(DerivedColor.BORDER_HOVER, SemanticColor.SURFACE_INPUT, SurfaceContrast.Guard.CONTROL);
        pendingMark = new PaintPair(SemanticColor.TEXT_ONACCENT, SemanticColor.STATUS_WARNING, SurfaceContrast.Guard.INDICATOR);
    }

    @Override public List<PaintPair> pairs() { return List.of(checkedEdge, mark, edge, pendingMark); }
    @Override public boolean leads() { return true; }
    @Override public boolean moves() { return false; }
    @Override public int rowHeight(KitContext context, boolean compact) { return ThemePainting.metric(context, DesignMetrics.Role.CHECKBOX_BOX); }

    @Override public Dimension preferredSize(Switches.SwitchButton button) {
        int ring = ThemePainting.metric(button.context, DesignMetrics.Role.FOCUS_RING), box = ThemePainting.metric(button.context, DesignMetrics.Role.CHECKBOX_BOX);
        return new Dimension(box + 2 * ring, box + 2 * ring);
    }

    @Override public void paint(Switches.SwitchButton button, Graphics2D g) {
        var c = button.context;
        ThemePainting.antialias(g);
        int box = ThemePainting.metric(c, DesignMetrics.Role.CHECKBOX_BOX), ring = ThemePainting.metric(c, DesignMetrics.Role.FOCUS_RING);
        int radius = ThemePainting.metric(c, DesignMetrics.Role.CHECKBOX_RADIUS), line = ThemePainting.metric(c, DesignMetrics.Role.HAIRLINE);
        int x = (button.getWidth() - box) / 2, y = (button.getHeight() - box) / 2;
        boolean enabled = button.isEnabled(), on = button.isSelected() || button.pending;
        if (enabled && (button.error || button.focus != null && button.focus.visible())) {
            g.setColor(ThemePainting.focusHaloColor(c, button.error));
            g.fill(new RoundRectangle2D.Double(x - ring, y - ring, box + 2.0 * ring, box + 2.0 * ring, 2.0 * (radius + ring), 2.0 * (radius + ring)));
        }
        Color fill, border, ink;
        if (button.onSelection) {
            // A disabled box on a selected row fades its outline and mark as a disabled switch does there.
            fill = null; border = ink = ThemePainting.color(c, button.ink.text);
            if (!enabled) border = ink = ColorMath.opacity(border, DISABLED_OPACITY);
        }
        else if (!enabled) {
            fill = ThemePainting.role(c, DerivedColor.SURFACE_TABLE_HEADER); border = ThemePainting.role(c, DerivedColor.BORDER_DIVIDER);
            ink = ThemePainting.color(c, SemanticColor.TEXT_PLACEHOLDER);
        } else if (on) {
            fill = ThemePainting.color(c, button.pending ? SemanticColor.STATUS_WARNING : SemanticColor.ACCENT);
            border = ThemePainting.painted(c, checkedEdge); ink = ThemePainting.painted(c, button.pending ? pendingMark : mark);
        } else { fill = ThemePainting.color(c, SemanticColor.SURFACE_INPUT); border = ThemePainting.painted(c, edge); ink = null; }
        // The edge is the box minus its inside, and the fill the inside: both stay crisp at every scale.
        var outer = new RoundRectangle2D.Double(x, y, box, box, 2.0 * radius, 2.0 * radius);
        var inner = new RoundRectangle2D.Double(x + line, y + line, box - 2.0 * line, box - 2.0 * line,
            2.0 * Math.max(0, radius - line), 2.0 * Math.max(0, radius - line));
        var ring2 = new Area(outer); ring2.subtract(new Area(inner));
        g.setColor(border); g.fill(ring2);
        if (fill != null) { g.setColor(fill); g.fill(inner); }
        if (on && ink != null) {
            if (button.pending) ClassicGlyphs.dash(g, x, y, box, ink);
            else ClassicGlyphs.check(g, x, y, box, ink);
        }
    }
}
