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
    /** {@code role}'s effective font for {@code text}: the platform face where the kit's UI face lacks one of its glyphs. */
    static Font font(KitContext context, Typography.Role role, String text) { return SwingTypography.effective(context.typography().font(role, text)); }
    /** The fractional antialiased context engine labels and native text measure in. */
    private static final FontRenderContext TEXT_CONTEXT = new FontRenderContext(null, true, true);
    /** Literal advance measured with the same fractional context used by engine labels and native text. */
    static int measureText(Font font, String text) { return (int) Math.ceil(font.getStringBounds(text, TEXT_CONTEXT).getWidth()); }
    /** Kerning, as the Enterprise mocks' browser sets text. */
    private static final java.util.Map<java.awt.font.TextAttribute, Object> KERNED =
        java.util.Map.of(java.awt.font.TextAttribute.KERNING, java.awt.font.TextAttribute.KERNING_ON);
    /** The text's kerned fractional advance in the same context, rounded: where the mocks set the text that follows it. */
    static int setAdvance(Font font, String text) { return (int) Math.round(font.deriveFont(KERNED).getStringBounds(text, TEXT_CONTEXT).getWidth()); }
    static int height(KitContext context, DesignMetrics.Role role, Typography.Role text) {
        DesignMetrics.Role padding = context.metrics().contentPadding(role);
        int line = (int)Math.ceil(font(context, text).getLineMetrics("Ag", new FontRenderContext(null, true, true)).getHeight());
        return Math.max(metric(context, role), line + metric(context, padding));
    }
    static Color color(KitContext context, SemanticColor role) { return context.theme().semantic().color(role); }
    /** {@code role}'s colour in the context's theme: a semantic role or a derived one. */
    static Color role(KitContext context, ColorRole role) { return role.color(context.theme()); }
    /** {@code pair}'s slot as its guard paints it in the context's theme (spec 7.3). */
    static Color painted(KitContext context, PaintPair pair) { return SurfaceContrast.painted(context.theme(), pair); }
    /**
     * A classic panel's faint rule, under its search row and in its Properties grid: {@code border.subtle} laid over
     * {@code surface.panel}, opaque, as the Workstation and Dark Mode mocks draw them (plan 10a).
     */
    static Color panelRule(KitContext context) { return subtleRule(context, SemanticColor.SURFACE_PANEL); }
    /** {@code border.subtle} laid over {@code ground}, opaque: a classic faint rule on that surface. */
    static Color subtleRule(KitContext context, SemanticColor ground) {
        Color subtle = color(context, SemanticColor.BORDER_SUBTLE);
        return dev.jasper.ui.theme.ColorMath.mix(subtle, color(context, ground), subtle.getAlpha() / 255.0);
    }
    /** A mask's face size and treatment; a hollow mask has the face, {@code cutout} above the shadow, cut out. */
    private record ShadowKey(int width, int height, double radius, int blur, double strength, boolean hollow, int cutout) { }
    /** Blurred shadow masks by face size and treatment; a few faces are on screen at once. EDT only. */
    private static final java.util.Map<ShadowKey, java.awt.image.BufferedImage> SHADOWS = new java.util.LinkedHashMap<>(16, .75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<ShadowKey, java.awt.image.BufferedImage> eldest) { return size() > 16; }
    };

    /** How many blurred shadow masks are cached. */
    static int shadowMasks() { return SHADOWS.size(); }

    /**
     * A soft black shadow under {@code face}, like CSS {@code box-shadow: 0 offset blur rgba(0,0,0,strength)}: the
     * rounded face at {@code strength}, lowered by {@code offset} and blurred by {@code blur} (three box passes each way,
     * a Gaussian of sigma {@code blur / 2}). Paint it before the face; the graphics' composite scales it (a fading card).
     * One mask per treatment serves every face large enough to slice: its corners as blurred, its middle row and column
     * stretched along the edges and its centre filled, so a face that changes size blurs nothing new.
     */
    static void softShadow(Graphics2D graphics, java.awt.geom.RectangularShape face, double radius, int offset, int blur, double strength) {
        softShadow(graphics, face, radius, offset, blur, strength, false);
    }

    /**
     * {@link #softShadow}; when {@code hollow}, none of it lies under the face (as {@code box-shadow} does), for a face
     * that is translucent (a fading card) or painted before its shadow. The face is cut out of the mask itself, so the
     * shadow paints the same whatever clip a repaint brings.
     */
    static void softShadow(Graphics2D graphics, java.awt.geom.RectangularShape face, double radius, int offset, int blur, double strength, boolean hollow) {
        if (blur <= 0 || strength <= 0 || face.isEmpty()) return;
        double share = Math.min(1, strength);
        int width = (int) Math.ceil(face.getWidth()), height = (int) Math.ceil(face.getHeight()), pad = blur + blur / 2;
        int x = (int) Math.floor(face.getX()) - pad, y = (int) Math.floor(face.getY()) - pad + offset;
        // A corner slice holds the radius plus the blur's reach, so the mask's middle row and column are straight edges.
        int corner = (int) Math.ceil(radius) + pad, core = 2 * corner + 1;
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            if (width < core || height < core) {
                // A face too small to slice has its own mask.
                g.drawImage(SHADOWS.computeIfAbsent(new ShadowKey(width, height, radius, blur, share, hollow, hollow ? offset : 0), key -> shadow(key, pad)), x, y, null);
                return;
            }
            var mask = SHADOWS.computeIfAbsent(new ShadowKey(core, core, radius, blur, share, hollow, hollow ? offset : 0), key -> shadow(key, pad));
            int slice = pad + corner, side = core + 2 * pad, across = width + 2 * pad, down = height + 2 * pad;
            int[] dx = {0, slice, across - slice, across}, dy = {0, slice, down - slice, down}, s = {0, slice, slice + 1, side};
            for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++)
                g.drawImage(mask, x + dx[i], y + dy[j], x + dx[i + 1], y + dy[j + 1], s[i], s[j], s[i + 1], s[j + 1], null);
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
        if (key.hollow()) {
            // The face sits the offset above its lowered shadow; clear it, antialiased like the face's own edge.
            var cut = mask.createGraphics();
            try {
                antialias(cut);
                cut.setComposite(AlphaComposite.Clear);
                cut.fill(new RoundRectangle2D.Double(pad, pad - key.cutout(), key.width(), key.height(), 2 * key.radius(), 2 * key.radius()));
            } finally { cut.dispose(); }
        }
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
    /** The resolved ring paint, retaining the kit opacity unless the theme supplies a final override. */
    static Color focusHaloColor(KitContext context, boolean error) {
        return dev.jasper.ui.theme.DerivedPalette.halo(context.theme(), error, focusHaloShare(context, error));
    }
    /** A category tile's or chip's share of its {@code hue.<name>.surface}: 17% in Dark, 13% in Light. */
    static double tileShare(KitContext context) {
        return context.metrics().value(context.theme().dark() ? DesignMetrics.Role.TILE_PERCENT_DARK : DesignMetrics.Role.TILE_PERCENT_LIGHT) / 100.0;
    }
    /**
     * {@code painter} drawn as one layer at {@code opacity}, as CSS {@code opacity} draws a control: the whole face at the
     * device's resolution first, then laid over what is beneath, so text over the control's own fill keeps the contrast
     * it has at rest rather than fading a second time over the faded fill.
     */
    static void faded(Graphics graphics, Component component, float opacity, java.util.function.Consumer<Graphics2D> painter) {
        var g = (Graphics2D) graphics.create();
        try {
            var transform = g.getTransform();
            double scaleX = transform.getScaleX(), scaleY = transform.getScaleY();
            int width = (int) Math.ceil(component.getWidth() * scaleX), height = (int) Math.ceil(component.getHeight() * scaleY);
            if (width <= 0 || height <= 0) return;
            var layer = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE);
            var face = layer.createGraphics();
            try {
                face.setRenderingHints(g.getRenderingHints());
                face.setFont(g.getFont());
                face.scale(scaleX, scaleY);
                painter.accept(face);
            } finally { face.dispose(); }
            g.setComposite(AlphaComposite.SrcOver.derive(opacity));
            g.scale(1 / scaleX, 1 / scaleY);
            g.drawImage(layer, 0, 0, null);
        } finally { g.dispose(); }
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

    /** theme.md's primary button drop, {@code 0 1px 2px} black, painted outside the face only (a hollow shadow). */
    private static void drop(KitView root, JComponent component, Drop drop, Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        try {
            for (Container ancestor = component.getParent(); ancestor != null && ancestor != root; ancestor = ancestor.getParent())
                if (ancestor instanceof JViewport) g.clip(SwingUtilities.convertRectangle(ancestor, new Rectangle(0, 0, ancestor.getWidth(), ancestor.getHeight()), root));
            var origin = SwingUtilities.convertPoint(component, 0, 0, root);
            var at = drop.face();
            var face = new RoundRectangle2D.Double(origin.x + at.getX(), origin.y + at.getY(), at.getWidth(), at.getHeight(), at.getArcWidth(), at.getArcHeight());
            if (!component.isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.42f));
            var context = drop.context();
            softShadow(g, face, at.getArcWidth() / 2, UIScale.scale(1), UIScale.scale(2), context.metrics().value(context.theme().dark()
                ? DesignMetrics.Role.BUTTON_DROP_PERCENT_DARK : DesignMetrics.Role.BUTTON_DROP_PERCENT_LIGHT) / 100.0, true);
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
                    g.setColor(focusHaloColor(halo.context(), halo.error()));
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
