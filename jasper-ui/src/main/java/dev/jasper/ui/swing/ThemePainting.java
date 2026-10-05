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
        int radius = metric(context, DesignMetrics.Role.CONTROL_RADIUS);
        double w = component.getWidth(), h = component.getHeight();
        component.putClientProperty("Jasper.kit.halo", focus || error ? new Halo(context, error) : null);
        if (w <= 0 || h <= 0) return;
        var shape = new RoundRectangle2D.Double(0, 0, w, h, radius * 2.0, radius * 2.0);
        if (fill != null) { g.setColor(fill); g.fill(shape); }
        if (border != null) {
            g.setColor(border); g.setStroke(new BasicStroke(UIScale.scale(1f)));
            g.draw(new RoundRectangle2D.Double(.5, .5, w - 1, h - 1, radius * 2.0, radius * 2.0));
        }
    }
    private record Halo(KitContext context, boolean error) {}
    /** View-owned final overlay: ring lies outside nominal bounds, clipped only by viewports/root. */
    static void halos(KitView root, Graphics graphics) { halos(root, root, graphics); }
    private static void halos(KitView root, Container container, Graphics graphics) {
        for (Component child : container.getComponents()) {
            if (!child.isVisible()) continue;
            if (child instanceof JComponent component && component.getClientProperty("Jasper.kit.halo") instanceof Halo halo) {
                var g = (Graphics2D)graphics.create();
                try {
                    antialias(g);
                    for (Container ancestor = component.getParent(); ancestor != null && ancestor != root; ancestor = ancestor.getParent()) {
                        if (ancestor instanceof JViewport) g.clip(SwingUtilities.convertRectangle(ancestor, new Rectangle(0, 0, ancestor.getWidth(), ancestor.getHeight()), root));
                    }
                    var bounds = SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), root);
                    int ring = metric(halo.context(), DesignMetrics.Role.FOCUS_RING), radius = metric(halo.context(), DesignMetrics.Role.CONTROL_RADIUS);
                    var outer = new java.awt.geom.Area(new RoundRectangle2D.Double(bounds.x - ring, bounds.y - ring, bounds.width + 2.0 * ring, bounds.height + 2.0 * ring, 2.0 * (radius + ring), 2.0 * (radius + ring)));
                    outer.subtract(new java.awt.geom.Area(new RoundRectangle2D.Double(bounds.x, bounds.y, bounds.width, bounds.height, 2.0 * radius, 2.0 * radius)));
                    g.setColor(ColorMath.opacity(color(halo.context(), halo.error() ? SemanticColor.BORDER_ERROR : SemanticColor.BORDER_FOCUS), halo.error() ? .22 : .25));
                    if (!component.isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f));
                    g.fill(outer);
                } finally { g.dispose(); }
            }
            if (child instanceof Container nested) halos(root, nested, graphics);
        }
    }
    /** Per-control focus cause; mouse presses hide keyboard rings until keyboard focus returns. */
    static final class Focus implements AutoCloseable {
        private final JComponent target;
        private final JComponent paintOwner;
        private final KitView view;
        private Rectangle priorHaloBounds;
        private boolean keyboard;
        private final MouseListener mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { keyboard = false; target.repaint(); repaintHalo(); }
        };
        private final KeyListener key = new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { keyboard = true; target.repaint(); repaintHalo(); }
        };
        private final FocusListener focus = new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                keyboard = switch (e.getCause()) { case MOUSE_EVENT -> false; default -> true; };
                target.repaint(); repaintHalo();
            }
            @Override public void focusLost(FocusEvent e) { target.repaint(); repaintHalo(); }
        };
        Focus(JComponent target, JComponent paintOwner, KitView view) {
            this.target = target; this.paintOwner = paintOwner; this.view = view;
            target.addMouseListener(mouse); target.addKeyListener(key); target.addFocusListener(focus);
        }
        boolean visible() { return target.isEnabled() && target.isFocusOwner() && keyboard; }
        private void repaintHalo() {
            if (target.getParent() != null) target.getParent().repaint();
            Rectangle dirty = priorHaloBounds == null ? null : new Rectangle(priorHaloBounds);
            if (SwingUtilities.isDescendingFrom(paintOwner, view)) {
                var bounds = SwingUtilities.convertRectangle(paintOwner, new Rectangle(0, 0, paintOwner.getWidth(), paintOwner.getHeight()), view);
                int ring = metric(view.context(), DesignMetrics.Role.FOCUS_RING);
                bounds.grow(ring, ring);
                priorHaloBounds = bounds;
                dirty = dirty == null ? bounds : dirty.union(bounds);
            }
            // The view paints the external ring, so its dirty region must include the exterior.
            // Retain the previous rectangle until teardown to erase rings after detachment/moves.
            if (dirty != null) view.repaint(dirty.x, dirty.y, dirty.width, dirty.height);
        }
        @Override public void close() {
            paintOwner.putClientProperty("Jasper.kit.halo", null); repaintHalo();
            target.removeMouseListener(mouse); target.removeKeyListener(key); target.removeFocusListener(focus);
        }
    }
}
