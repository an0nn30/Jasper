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
    static Font font(KitContext context, Typography.Role role) { return SwingTypography.effective(context.typography().font(role)); }
    static int height(KitContext context, DesignMetrics.Role role, Typography.Role text) {
        DesignMetrics.Role padding = switch (role) {
            case PICKER_COMPACT_HEIGHT,PICKER_DEFAULT_HEIGHT,PICKER_SEARCH_HEIGHT,PICKER_ROW_HEIGHT,PICKER_UTILITY_HEIGHT -> DesignMetrics.Role.PICKER_VERTICAL_PADDING;
            case PANEL_DETAIL_ACTION -> DesignMetrics.Role.PANEL_DETAIL_ACTION_VERTICAL;
            case PANEL_SEARCH_HEIGHT -> DesignMetrics.Role.PANEL_SEARCH_VERTICAL_PADDING;
            case PANEL_HEADER_ACTION -> DesignMetrics.Role.PANEL_HEADER_ACTION_VERTICAL;
            case RESULTS_ROW_HEIGHT, RESULTS_HEADING_HEIGHT -> DesignMetrics.Role.RESULTS_VERTICAL_PADDING;
            case FIELD_HEIGHT, HERO_FIELD_HEIGHT -> DesignMetrics.Role.FIELD_VERTICAL_PADDING;
            case PANEL_EMPTY_ACTION_HEIGHT, SETTINGS_NAV_HEADING, SETTINGS_NAV_ROW, KEYCAP_TREE_ROW_HEIGHT, IDENTITY_ROW_HEIGHT, IDENTITY_HEADING_HEIGHT, MENU_ITEM_HEIGHT, BUTTON_HEIGHT, HERO_BUTTON_HEIGHT, FULL_FOOTER_BUTTON_HEIGHT, ORDERED_ADD_HEIGHT, PANEL_ACTION, INLINE_ACTION -> DesignMetrics.Role.BUTTON_VERTICAL_PADDING;
            default -> throw new IllegalArgumentException("Not a content height role: " + role);
        };
        int line = (int)Math.ceil(font(context, text).getLineMetrics("Ag", new FontRenderContext(null, true, true)).getHeight());
        return Math.max(metric(context, role), line + metric(context, padding));
    }
    static Color color(KitContext context, SemanticColor role) { return context.theme().semantic().color(role); }
    static void antialias(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }
    static void rounded(Graphics2D g, JComponent component, KitContext context, Color fill, Color border, boolean focus, boolean error) {
        rounded(g,component,context,fill,border,focus,error,DesignMetrics.Role.CONTROL_RADIUS);
    }
    static void rounded(Graphics2D g,JComponent component,KitContext context,Color fill,Color border,boolean focus,boolean error,DesignMetrics.Role radiusRole) {
        rounded(g, component, context, fill, border, focus, error, radiusRole, 0);
    }
    static void rounded(Graphics2D g,JComponent component,KitContext context,Color fill,Color border,boolean focus,boolean error,DesignMetrics.Role radiusRole,int inset) {
        rounded(g, component, context, fill, border, focus, error, metric(context, radiusRole), inset, 0);
    }
    /** Painted face can be smaller than its interactive/focus reservation without moving its content. */
    static void rounded(Graphics2D g,JComponent component,KitContext context,Color fill,Color border,boolean focus,boolean error,double radius,int inset,double bottomTrim) {
        antialias(g);
        double w = component.getWidth(), h = component.getHeight() - bottomTrim;
        component.putClientProperty("Jasper.kit.halo", focus || error ? new Halo(context, error,radius,inset,bottomTrim) : null);
        if (w <= 0 || h <= 0) return;
        var shape = new RoundRectangle2D.Double(inset, inset, w-2*inset, h-2*inset, radius * 2.0, radius * 2.0);
        if (fill != null) { g.setColor(fill); g.fill(shape); }
        if (border != null) {
            g.setColor(border); g.setStroke(new BasicStroke(UIScale.scale(1f)));
            g.draw(new RoundRectangle2D.Double(inset+.5, inset+.5, w - 2*inset - 1, h - 2*inset - 1, radius * 2.0, radius * 2.0));
        }
    }
    /** A transparent field reserves no outer halo; its receiving focus ring stays inside its existing allocation. */
    static void transparentFocus(Graphics2D g, JComponent component, KitContext context, DesignMetrics.Role radiusRole) {
        antialias(g);
        component.putClientProperty("Jasper.kit.halo", null);
        double width = component.getWidth(), height = component.getHeight();
        double radius = metric(context, radiusRole);
        double stroke = UIScale.scale(DesignMetrics.TRANSPARENT_FIELD_FOCUS_WIDTH);
        var ring = new java.awt.geom.Area(new RoundRectangle2D.Double(0, 0, width, height, 2*radius, 2*radius));
        double inner = Math.max(0, radius-stroke);
        ring.subtract(new java.awt.geom.Area(new RoundRectangle2D.Double(stroke, stroke,
            Math.max(0,width-2*stroke), Math.max(0,height-2*stroke), 2*inner, 2*inner)));
        g.setColor(color(context, SemanticColor.BORDER_FOCUS));
        g.fill(ring);
    }

    private record Halo(KitContext context, boolean error,double radius,int inset,double bottomTrim) {}
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
                    bounds.grow(-halo.inset(),-halo.inset());
                    int ring = metric(halo.context(), DesignMetrics.Role.FOCUS_RING);
                    double radius = halo.radius(), height = bounds.height - halo.bottomTrim();
                    var outer = new java.awt.geom.Area(new RoundRectangle2D.Double(bounds.x - ring, bounds.y - ring, bounds.width + 2.0 * ring, height + 2.0 * ring, 2.0 * (radius + ring), 2.0 * (radius + ring)));
                    outer.subtract(new java.awt.geom.Area(new RoundRectangle2D.Double(bounds.x, bounds.y, bounds.width, height, 2.0 * radius, 2.0 * radius)));
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
                // Native traversal must reveal the same retained control in any enclosing viewport.
                target.scrollRectToVisible(new Rectangle(0,0,target.getWidth(),target.getHeight()));
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
