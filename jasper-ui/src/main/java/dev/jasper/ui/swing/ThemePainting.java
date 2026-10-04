package dev.jasper.ui.swing;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.RoundRectangle2D;
import javax.swing.*;

/** Shared semantic paint treatments and exactly-once Swing user scaling. */
final class ThemePainting {
    private ThemePainting() {}
    static int metric(KitContext context, DesignMetrics.Role role) { return UIScale.scale(context.metrics().value(role)); }
    static Font font(KitContext context, Typography.Role role) { Font font = context.typography().font(role); return font.deriveFont(UIScale.scale(font.getSize2D())); }
    static int height(KitContext context, DesignMetrics.Role role, Typography.Role text) {
        return UIScale.scale(context.metrics().contentHeight(role, context.typography().font(text), new FontRenderContext(null, true, true)));
    }
    static Color color(KitContext context, SemanticColor role) { return context.theme().semantic().color(role); }
    static void antialias(Graphics2D g) { g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); }
    static void rounded(Graphics2D g, JComponent component, KitContext context, Color fill, Color border, boolean focus, boolean error) {
        antialias(g);
        int ring = metric(context, DesignMetrics.Role.FOCUS_RING);
        int radius = metric(context, DesignMetrics.Role.CONTROL_RADIUS);
        double w = component.getWidth() - 2.0 * ring, h = component.getHeight() - 2.0 * ring;
        if (w <= 0 || h <= 0) return;
        if (focus || error) {
            g.setColor(ColorMath.opacity(color(context, error ? SemanticColor.BORDER_ERROR : SemanticColor.BORDER_FOCUS), error ? .22 : .25));
            g.fill(new RoundRectangle2D.Double(0, 0, component.getWidth(), component.getHeight(), radius + 2.0 * ring, radius + 2.0 * ring));
        }
        var shape = new RoundRectangle2D.Double(ring, ring, w, h, radius, radius);
        if (fill != null) { g.setColor(fill); g.fill(shape); }
        if (border != null) {
            g.setColor(border); g.setStroke(new BasicStroke(UIScale.scale(1f)));
            g.draw(new RoundRectangle2D.Double(ring + .5, ring + .5, w - 1, h - 1, radius, radius));
        }
    }
    /** Per-control focus cause; mouse presses hide keyboard rings until keyboard focus returns. */
    static final class Focus implements AutoCloseable {
        private final JComponent target;
        private boolean keyboard;
        private final MouseListener mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { keyboard = false; target.repaint(); repaintParent(); }
        };
        private final KeyListener key = new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { keyboard = true; target.repaint(); repaintParent(); }
        };
        private final FocusListener focus = new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                keyboard = switch (e.getCause()) { case MOUSE_EVENT -> false; default -> true; };
                target.repaint(); repaintParent();
            }
            @Override public void focusLost(FocusEvent e) { target.repaint(); repaintParent(); }
        };
        Focus(JComponent target) { this.target = target; target.addMouseListener(mouse); target.addKeyListener(key); target.addFocusListener(focus); }
        boolean visible() { return target.isEnabled() && target.isFocusOwner() && keyboard; }
        private void repaintParent() { if (target.getParent() != null) target.getParent().repaint(); }
        @Override public void close() { target.removeMouseListener(mouse); target.removeKeyListener(key); target.removeFocusListener(focus); }
    }
}
