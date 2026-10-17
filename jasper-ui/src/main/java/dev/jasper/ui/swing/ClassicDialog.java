package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * The classic in-window dialog (Enterprise presentation spec 10.8; the Credential Vault mocks): a still scrim over the
 * window below its title bar and, above it, a card framed 1 px in {@code border.hover} with the catalog's popover shadow,
 * a header strip on {@code surface.header.active} holding the dialog's swatch and title, and the host's content on the
 * kit's dialog surface. CENTERED sits centred on the whole window (its title bar included, as the mocks place it); SHEET
 * joins the top edge with no top frame. It never moves. The host adds it to a layered pane, keeps it the pane's size and
 * owns keyboard routing and focus; the scrim takes every pointer event outside the card. EDT only.
 */
public final class ClassicDialog extends JComponent implements AutoCloseable {
    /** Where the card sits in the window. */
    public enum Placement { CENTERED, SHEET }

    private final KitContext context;
    private final JComponent content;
    private final Placement placement;
    private final Nodes.Hue swatch;
    private final Card card = new Card();
    private final Strip strip = new Strip();
    private final AutoCloseable appearance;
    private String title;
    private int windowTitle;
    private boolean closed;

    /**
     * A dialog around {@code content}.
     *
     * @param context the kit context the dialog paints with
     * @param content the host's content, a kit view's form as a rule
     * @param title the title the header strip shows
     * @param swatch the square drawn before the title; {@link Nodes.Hue#NONE} draws none
     * @param placement centred, or joined to the top as a sheet
     */
    public ClassicDialog(KitContext context, JComponent content, String title, Nodes.Hue swatch, Placement placement) {
        this.context = Objects.requireNonNull(context);
        this.content = Objects.requireNonNull(content);
        this.title = Objects.requireNonNull(title);
        this.swatch = Objects.requireNonNull(swatch);
        this.placement = Objects.requireNonNull(placement);
        setOpaque(false);
        setLayout(null);
        card.add(strip, BorderLayout.NORTH);
        card.add(content, BorderLayout.CENTER);
        add(card);
        var blocker = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) { event.consume(); }
            @Override public void mouseReleased(MouseEvent event) { event.consume(); }
            @Override public void mouseClicked(MouseEvent event) { event.consume(); }
            @Override public void mouseMoved(MouseEvent event) { event.consume(); }
            @Override public void mouseDragged(MouseEvent event) { event.consume(); }
        };
        addMouseListener(blocker);
        addMouseMotionListener(blocker);
        addMouseWheelListener(MouseEvent::consume);
        appearance = context.binding().subscribe(pair -> { card.restyle(); repaint(); });
        card.restyle();
    }

    /** The card around the strip and the content; its focus policy and cycle are the host's. */
    public JComponent card() { return card; }
    /** The host's content. */
    public JComponent content() { return content; }
    /** Where the card is, in this layer. */
    public Rectangle cardBounds() { return card.getBounds(); }
    /** Centred, or a sheet. */
    public Placement placement() { return placement; }
    /** The title the strip shows. */
    public String title() { return title; }

    /** Shows {@code value} in the strip. */
    public void setTitle(String value) {
        title = Objects.requireNonNull(value);
        strip.getAccessibleContext().setAccessibleName(value);
        strip.repaint();
    }

    /** The window's own title bar above this layer, in device pixels: a centred card is centred on the whole window. */
    public void windowTitleHeight(int pixels) { windowTitle = Math.max(0, pixels); placeCard(); }

    /** Puts the card in place, at once. */
    public void open() { placeCard(); repaint(); }

    /** Removes the dialog at once and then runs {@code removed}. */
    public void dismiss(Runnable removed) {
        Objects.requireNonNull(removed);
        remove();
        removed.run();
    }

    /** Removes the dialog and releases its theme subscription. Idempotent. */
    @Override public void close() {
        if (closed) return;
        closed = true;
        remove();
        try { appearance.close(); } catch (Exception ignored) { }
    }

    private void remove() {
        Container parent = getParent();
        if (parent == null) return;
        Rectangle bounds = getBounds();
        parent.remove(this);
        parent.repaint(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    @Override public void doLayout() { placeCard(); }

    private void placeCard() {
        Dimension preferred = card.getPreferredSize();
        int width = Math.min(preferred.width, getWidth()), height = Math.min(preferred.height, getHeight());
        int x = (getWidth() - width) / 2;
        int y = placement == Placement.SHEET ? 0 : Math.max(0, Math.min(getHeight() - height, (getHeight() - windowTitle - height) / 2));
        var bounds = new Rectangle(x, y, width, height);
        if (!bounds.equals(card.getBounds())) { card.setBounds(bounds); card.doLayout(); }
    }

    @Override protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        try {
            ThemePainting.antialias(g);
            g.setColor(ThemePainting.color(context, SemanticColor.SURFACE_SCRIM));
            g.fillRect(0, 0, getWidth(), getHeight());
            Rectangle at = card.getBounds();
            if (at.isEmpty()) return;
            ThemePainting.softShadow(g, new Rectangle2D.Double(at.x, at.y, at.width, at.height), ThemePainting.metric(context, DesignMetrics.Role.DIALOG_RADIUS),
                ThemePainting.metric(context, DesignMetrics.Role.POPOVER_SHADOW_OFFSET), ThemePainting.metric(context, DesignMetrics.Role.POPOVER_SHADOW_BLUR),
                context.metrics().value(context.theme().dark() ? DesignMetrics.Role.POPOVER_SHADOW_PERCENT_DARK : DesignMetrics.Role.POPOVER_SHADOW_PERCENT_LIGHT) / 100.0, true);
        } finally { g.dispose(); }
    }

    /** The card: the dialog surface, framed and clipped; a sheet's top is square and unframed. */
    private final class Card extends JPanel {
        Card() { super(new BorderLayout()); setOpaque(false); }

        void restyle() {
            int line = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
            setBorder(new EmptyBorder(placement == Placement.SHEET ? 0 : line, line, line, line));
        }

        /** A rounded rectangle; a sheet's top corners are square. */
        private Area outline(double x, double y, double w, double h, double r) {
            var shape = new Area(new RoundRectangle2D.Double(x, y, w, h, 2 * r, 2 * r));
            if (placement == Placement.SHEET) shape.add(new Area(new Rectangle2D.Double(x, y, w, Math.min(h, r + 1))));
            return shape;
        }

        private Shape surface() { return outline(0, 0, getWidth(), getHeight(), ThemePainting.metric(context, DesignMetrics.Role.DIALOG_RADIUS)); }

        /**
         * The 1 px frame as a filled ring, so it covers whole device pixels at every scale (a stroked line on the half
         * pixel lands on one device pixel and two half-covered ones at 2x); a sheet's ring has no top edge.
         */
        private Shape frame() {
            double line = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE), r = ThemePainting.metric(context, DesignMetrics.Role.DIALOG_RADIUS);
            double w = getWidth(), h = getHeight();
            boolean sheet = placement == Placement.SHEET;
            var ring = outline(0, 0, w, h, r);
            ring.subtract(outline(line, sheet ? -1 : line, w - 2 * line, sheet ? h - line + 1 : h - 2 * line, Math.max(0, r - line)));
            return ring;
        }

        @Override protected boolean isPaintingOrigin() { return true; }

        @Override protected void paintChildren(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                ThemePainting.antialias(g);
                g.setColor(ThemePainting.color(context, context.kit().colors().dialogSurface()));
                g.fill(surface());
                g.clip(surface());
                super.paintChildren(g);
            } finally { g.dispose(); }
            g = (Graphics2D) graphics.create();
            try {
                ThemePainting.antialias(g);
                g.setColor(DerivedColor.BORDER_HOVER.color(context.theme()));
                g.fill(frame());
            } finally { g.dispose(); }
        }
    }

    /**
     * The header strip: the swatch and the title above a {@code border.window} rule. It takes no focus, so the host's
     * focus lands in the content, where typing and Escape belong.
     */
    private final class Strip extends JComponent {
        Strip() { setFocusable(false); }

        // A bare JComponent has no accessible context; this one names its role.
        @Override public javax.accessibility.AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) accessibleContext = new AccessibleJComponent() {
                @Override public javax.accessibility.AccessibleRole getAccessibleRole() { return javax.accessibility.AccessibleRole.LABEL; }
            };
            return accessibleContext;
        }

        @Override public Dimension getPreferredSize() {
            return new Dimension(0, ThemePainting.metric(context, DesignMetrics.Role.DIALOG_HEADER_HEIGHT) + ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE));
        }

        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                ThemePainting.antialias(g);
                var theme = context.theme();
                int line = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE), height = getHeight() - line;
                g.setColor(DerivedColor.SURFACE_HEADER_ACTIVE.color(theme));
                g.fillRect(0, 0, getWidth(), height);
                // The rule parts the strip from the content: held apart from the dialog surface below it (Execution notes R31).
                g.setColor(SurfaceContrast.painted(theme, new PaintPair(SemanticColor.BORDER_WINDOW, context.kit().colors().dialogSurface(), SurfaceContrast.Guard.STRUCTURAL)));
                g.fillRect(0, height, getWidth(), line);
                int x = ThemePainting.metric(context, DesignMetrics.Role.DIALOG_HEADER_INSET);
                SemanticColor hue = switch (swatch) {
                    case BLUE -> SemanticColor.HUE_BLUE; case GREEN -> SemanticColor.HUE_GREEN; case AMBER -> SemanticColor.HUE_AMBER;
                    case PURPLE -> SemanticColor.HUE_PURPLE; case NEUTRAL -> SemanticColor.HUE_NEUTRAL; case NONE -> null;
                };
                if (hue != null) {
                    int size = ThemePainting.metric(context, DesignMetrics.Role.DIALOG_SWATCH);
                    g.setColor(SurfaceContrast.painted(theme, new PaintPair(hue, DerivedColor.SURFACE_HEADER_ACTIVE, SurfaceContrast.Guard.INDICATOR)));
                    g.fillRect(x, (height - size) / 2, size, size);
                    x += size + ThemePainting.metric(context, DesignMetrics.Role.DIALOG_SWATCH_GAP);
                }
                g.setFont(ThemePainting.font(context, Typography.Role.WINDOW_TITLE));
                FontMetrics metrics = g.getFontMetrics();
                g.setColor(SurfaceContrast.painted(theme, new PaintPair(SemanticColor.TEXT_PRIMARY, DerivedColor.SURFACE_HEADER_ACTIVE, SurfaceContrast.Guard.TEXT)));
                g.drawString(title, x, (height - metrics.getHeight()) / 2 + metrics.getAscent());
            } finally { g.dispose(); }
        }
    }
}
