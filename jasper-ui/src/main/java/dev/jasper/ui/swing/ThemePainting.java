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
            case PICKER_COMPACT_HEIGHT,PICKER_DEFAULT_HEIGHT,PICKER_SEARCH_HEIGHT,PICKER_ROW_HEIGHT,PICKER_UTILITY_HEIGHT,FORM_SEGMENT_HEIGHT,FULL_SETTING_CHOICE_HEIGHT -> DesignMetrics.Role.PICKER_VERTICAL_PADDING;
            case PANEL_DETAIL_ACTION -> DesignMetrics.Role.PANEL_DETAIL_ACTION_VERTICAL;
            case PANEL_SEARCH_HEIGHT -> DesignMetrics.Role.PANEL_SEARCH_VERTICAL_PADDING;
            case PANEL_HEADER_ACTION -> DesignMetrics.Role.PANEL_HEADER_ACTION_VERTICAL;
            case RESULTS_ROW_HEIGHT, RESULTS_HEADING_HEIGHT -> DesignMetrics.Role.RESULTS_VERTICAL_PADDING;
            case FIELD_HEIGHT, HERO_FIELD_HEIGHT, COLUMN_SEARCH_HEIGHT -> DesignMetrics.Role.FIELD_VERTICAL_PADDING;
            case PANEL_EMPTY_ACTION_HEIGHT, SETTINGS_NAV_HEADING, SETTINGS_NAV_ROW, KEYCAP_TREE_ROW_HEIGHT, IDENTITY_ROW_HEIGHT, IDENTITY_HEADING_HEIGHT, MENU_ITEM_HEIGHT, BUTTON_HEIGHT, HERO_BUTTON_HEIGHT, FULL_FOOTER_BUTTON_HEIGHT, ORDERED_ADD_HEIGHT, PANEL_ACTION, INLINE_ACTION -> DesignMetrics.Role.BUTTON_VERTICAL_PADDING;
            default -> throw new IllegalArgumentException("Not a content height role: " + role);
        };
        int line = (int)Math.ceil(font(context, text).getLineMetrics("Ag", new FontRenderContext(null, true, true)).getHeight());
        return Math.max(metric(context, role), line + metric(context, padding));
    }
    static Color color(KitContext context, SemanticColor role) { return context.theme().semantic().color(role); }
    private record ShadowKey(int width, int height, double radius, int blur, double strength) { }
    /** Blurred shadow masks by face size and treatment; a few faces are on screen at once. EDT only. */
    private static final java.util.Map<ShadowKey, java.awt.image.BufferedImage> SHADOWS = new java.util.LinkedHashMap<>(16, .75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<ShadowKey, java.awt.image.BufferedImage> eldest) { return size() > 16; }
    };

    /**
     * A soft black shadow under {@code face}, like CSS {@code box-shadow: 0 offset blur rgba(0,0,0,strength)}: the
     * rounded face at {@code strength}, lowered by {@code offset} and blurred by {@code blur} (three box passes each way,
     * a Gaussian of sigma {@code blur / 2}). Paint it before the face; the graphics' composite scales it (a fading card).
     */
    static void softShadow(Graphics2D graphics, java.awt.geom.RectangularShape face, double radius, int offset, int blur, double strength) {
        if (blur <= 0 || strength <= 0 || face.isEmpty()) return;
        int width = (int) Math.ceil(face.getWidth()), height = (int) Math.ceil(face.getHeight()), pad = blur + blur / 2;
        var mask = SHADOWS.computeIfAbsent(new ShadowKey(width, height, radius, blur, Math.min(1, strength)), key -> shadow(key, pad));
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(mask, (int) Math.floor(face.getX()) - pad, (int) Math.floor(face.getY()) - pad + offset, null);
        } finally { g.dispose(); }
    }

    private static java.awt.image.BufferedImage shadow(ShadowKey key, int pad) {
        int w = key.width() + 2 * pad, h = key.height() + 2 * pad;
        var mask = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var g = mask.createGraphics();
        try {
            antialias(g);
            g.setColor(ColorMath.opacity(Color.BLACK, key.strength()));
            g.fill(new RoundRectangle2D.Double(pad, pad, key.width(), key.height(), 2 * key.radius(), 2 * key.radius()));
        } finally { g.dispose(); }
        int[] pixels = mask.getRGB(0, 0, w, h, null, 0, w), alpha = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) alpha[i] = pixels[i] >>> 24;
        int box = Math.max(1, key.blur() / 2);
        for (int pass = 0; pass < 3; pass++) { boxBlur(alpha, w, h, box, true); boxBlur(alpha, w, h, box, false); }
        for (int i = 0; i < pixels.length; i++) pixels[i] = alpha[i] << 24;
        mask.setRGB(0, 0, w, h, pixels, 0, w);
        return mask;
    }

    /** One running-sum box blur of {@code radius} along each row ({@code horizontal}) or column, in place. */
    private static void boxBlur(int[] alpha, int width, int height, int radius, boolean horizontal) {
        int lines = horizontal ? height : width, length = horizontal ? width : height, span = 2 * radius + 1;
        int[] line = new int[length];
        for (int l = 0; l < lines; l++) {
            for (int i = 0; i < length; i++) line[i] = alpha[horizontal ? l * width + i : i * width + l];
            int sum = 0;
            for (int i = 0; i <= Math.min(radius, length - 1); i++) sum += line[i];
            for (int i = 0; i < length; i++) {
                alpha[horizontal ? l * width + i : i * width + l] = (sum + span / 2) / span;
                if (i - radius >= 0) sum -= line[i - radius];
                if (i + radius + 1 < length) sum += line[i + radius + 1];
            }
        }
    }

    /** The focus ring's share of {@code border.focus} (about 32% in Dark, 25% in Light), or the error ring's of {@code border.error}. */
    static double focusHaloShare(KitContext context, boolean error) {
        return context.metrics().value(error ? DesignMetrics.Role.ERROR_HALO_PERCENT
            : context.theme().dark() ? DesignMetrics.Role.FOCUS_HALO_PERCENT_DARK : DesignMetrics.Role.FOCUS_HALO_PERCENT_LIGHT) / 100.0;
    }
    /** A category tile's or chip's share of its {@code hue.<name>.surface}: 17% in Dark, 13% in Light. */
    static double tileShare(KitContext context) {
        return context.metrics().value(context.theme().dark() ? DesignMetrics.Role.TILE_PERCENT_DARK : DesignMetrics.Role.TILE_PERCENT_LIGHT) / 100.0;
    }
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
        // Control faces are exact logical geometry: normalization would move a hairline half a device
        // pixel and blur it over three rows at 2x. Later artwork keeps the caller's stroke policy.
        Object control = g.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        try {
            var shape = new RoundRectangle2D.Double(inset, inset, w-2*inset, h-2*inset, radius * 2.0, radius * 2.0);
            if (fill != null) { g.setColor(fill); g.fill(shape); }
            if (border != null) {
                g.setColor(border); g.setStroke(new BasicStroke(UIScale.scale(1f)));
                g.draw(new RoundRectangle2D.Double(inset+.5, inset+.5, w - 2*inset - 1, h - 2*inset - 1, radius * 2.0, radius * 2.0));
            }
        } finally {
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, control == null ? RenderingHints.VALUE_STROKE_DEFAULT : control);
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
    /** The client property holding a primary button's {@link Drop}. */
    static final String DROP = "Jasper.kit.drop";
    /** A primary button's face, in its own coordinates, whose soft drop the kit view paints outside it. */
    record Drop(KitContext context, java.awt.geom.RoundRectangle2D face) {}
    /** View-owned final overlay: ring lies outside nominal bounds, clipped only by viewports/root. */
    static void halos(KitView root, Graphics graphics) { halos(root, root, graphics); }

    /** theme.md's primary button drop, {@code 0 1px 2px} black, painted outside the face only. */
    private static void drop(KitView root, JComponent component, Drop drop, Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        try {
            for (Container ancestor = component.getParent(); ancestor != null && ancestor != root; ancestor = ancestor.getParent())
                if (ancestor instanceof JViewport) g.clip(SwingUtilities.convertRectangle(ancestor, new Rectangle(0, 0, ancestor.getWidth(), ancestor.getHeight()), root));
            var origin = SwingUtilities.convertPoint(component, 0, 0, root);
            var at = drop.face();
            var face = new RoundRectangle2D.Double(origin.x + at.getX(), origin.y + at.getY(), at.getWidth(), at.getHeight(), at.getArcWidth(), at.getArcHeight());
            var outside = new java.awt.geom.Area(new Rectangle(0, 0, root.getWidth(), root.getHeight()));
            outside.subtract(new java.awt.geom.Area(face));
            g.clip(outside);
            if (!component.isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f));
            var context = drop.context();
            softShadow(g, face, at.getArcWidth() / 2, UIScale.scale(1), UIScale.scale(2), context.metrics().value(context.theme().dark()
                ? DesignMetrics.Role.BUTTON_DROP_PERCENT_DARK : DesignMetrics.Role.BUTTON_DROP_PERCENT_LIGHT) / 100.0);
        } finally { g.dispose(); }
    }
    private static void halos(KitView root, Container container, Graphics graphics) {
        for (Component child : container.getComponents()) {
            if (!child.isVisible()) continue;
            if (child instanceof JComponent component && component.getClientProperty(DROP) instanceof Drop drop) drop(root, component, drop, graphics);
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
                    g.setColor(ColorMath.opacity(color(halo.context(), halo.error() ? SemanticColor.BORDER_ERROR : SemanticColor.BORDER_FOCUS), focusHaloShare(halo.context(), halo.error())));
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
