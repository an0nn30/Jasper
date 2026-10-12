package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.MotionPolicy;
import dev.jasper.ui.theme.ColorMath;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.function.LongSupplier;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

/**
 * The kit's modal overlay: a scrim that dims a window's whole content and, above it, a centred card holding the host's
 * content. Opening fades the scrim in while the card fades up into place from {@code OVERLAY_RISE} below, easing out
 * over {@code OVERLAY_ENTER_MILLIS}; closing is its opposite over {@code OVERLAY_EXIT_MILLIS}: the card lifts off at
 * once and rises another {@code OVERLAY_RISE} (easing out) while it fades (easing in) and the scrim lifts.
 * With motion off both are instant. While it moves the card paints a snapshot of its content, so the card fades as one
 * surface; at rest its content paints live. The scrim takes every pointer event outside the card.
 * <p>The host adds the overlay to a layered pane, keeps it the pane's size, calls {@link #open()}, and owns keyboard
 * routing and focus. EDT only.
 */
public final class ModalOverlay extends JComponent implements AutoCloseable {
    enum Phase { NEW, OPENING, OPEN, CLOSING, CLOSED }

    private final KitContext context;
    private final JComponent content;
    private final Card card = new Card();
    private final MotionPolicy motion;
    private final LongSupplier clock;
    private final Timer frames;
    private final AutoCloseable appearance;
    private AutoCloseable effect;
    private Phase phase = Phase.NEW;
    private long started;
    /** How far in the card is, 0 (gone) to 1 (in place): the eased share of the running transition. */
    private double shown;
    /** Where the exit started from, so a close during the entrance fades out from there. */
    private double from;
    /** Where the card is against its place, in rises: 1 a rise below, 0 in place, -1 a rise above. */
    private double offset = 1;
    /** Where the exit started moving from, so a close during the entrance carries on upward from there. */
    private double fromOffset;
    /** The closing card's last picture; the card itself is hidden while it leaves. */
    private BufferedImage leaving;
    private Runnable removed = () -> { };
    private boolean disposed;

    public ModalOverlay(KitContext context, JComponent content) { this(context, content, System::nanoTime); }

    ModalOverlay(KitContext context, JComponent content, LongSupplier clock) {
        this.context = Objects.requireNonNull(context);
        this.content = Objects.requireNonNull(content);
        this.clock = Objects.requireNonNull(clock);
        motion = context.motionPolicy();
        setOpaque(false);
        setLayout(null);
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
        frames = new Timer(1000 / 60, event -> tick());
        appearance = context.binding().subscribe(pair -> { card.restyle(); repaint(); });
        card.restyle();
    }

    /** The card around the content; its focus policy and cycle are the host's. */
    public JComponent card() { return card; }

    /** The content shown in the card. */
    public JComponent content() { return content; }

    /** Where the card is, in this overlay. */
    public Rectangle cardBounds() { return card.getBounds(); }

    /** Whether the overlay is still entering or open, not leaving or gone. */
    public boolean active() { return phase == Phase.OPENING || phase == Phase.OPEN; }

    /** Starts the entrance (in place at once with motion off). Once only. */
    public void open() {
        if (phase != Phase.NEW) return;
        phase = Phase.OPENING;
        shown = 0;
        offset = 1;
        begin(context.metrics().value(DesignMetrics.Role.OVERLAY_ENTER_MILLIS));
    }

    /**
     * Starts the exit (at once with motion off); {@code removed} runs when it has finished and the overlay has left its
     * parent. A second call only replaces nothing: the first exit's callback stands.
     */
    public void dismiss(Runnable removed) {
        Objects.requireNonNull(removed);
        if (phase == Phase.CLOSING || phase == Phase.CLOSED) return;
        this.removed = removed;
        from = phase == Phase.NEW ? 0 : shown;
        fromOffset = offset;
        phase = Phase.CLOSING;
        leaving = from > 0 && motion.enabled() ? card.picture() : null;
        card.setVisible(false);
        begin(context.metrics().value(DesignMetrics.Role.OVERLAY_EXIT_MILLIS));
    }

    private void begin(int millis) {
        started = clock.getAsLong();
        stopFrames();
        if (millis <= 0 || !motion.enabled() || phase == Phase.CLOSING && from == 0) { finish(); return; }
        effect = motion.register(this::finish);
        frames.start();
        tick();
    }

    /** One frame: the eased progress at the clock's time, the card's place and a repaint. */
    void tick() {
        if (phase != Phase.OPENING && phase != Phase.CLOSING) return;
        // Motion turned off mid-way settles the transition (the policy runs finish).
        if (!motion.enabled()) return;
        int millis = context.metrics().value(phase == Phase.OPENING ? DesignMetrics.Role.OVERLAY_ENTER_MILLIS : DesignMetrics.Role.OVERLAY_EXIT_MILLIS);
        double t = Math.min(1, Math.max(0, (clock.getAsLong() - started) / 1e6 / millis));
        if (phase == Phase.OPENING) { shown = easeOutCubic(t); offset = 1 - shown; }
        // The exit lifts off at once and rises while still clearly visible (ease-out), fading as it goes (ease-in):
        // on one shared ease-in it barely moved before it had faded.
        else { shown = from * (1 - easeInQuad(t)); offset = fromOffset + (-1 - fromOffset) * easeOutCubic(t); }
        if (t >= 1) { finish(); return; }
        placeCard();
        repaint();
    }

    private void finish() {
        stopFrames();
        if (phase == Phase.OPENING) {
            phase = Phase.OPEN;
            shown = 1;
            offset = 0;
            card.forget();
            placeCard();
            repaint();
        } else if (phase == Phase.CLOSING) {
            phase = Phase.CLOSED;
            shown = 0;
            offset = -1;
            leaving = null;
            remove();
            Runnable after = removed;
            removed = () -> { };
            after.run();
        }
    }

    private void stopFrames() {
        frames.stop();
        AutoCloseable running = effect;
        effect = null;
        if (running != null) try { running.close(); } catch (Exception ignored) { }
    }

    private void remove() {
        Container parent = getParent();
        if (parent == null) return;
        Rectangle bounds = getBounds();
        parent.remove(this);
        parent.repaint(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    /**
     * Removes the overlay at once, without an exit, drops a pending exit's callback and releases its theme and motion
     * registrations; a host calls it from the exit's callback too. Idempotent.
     */
    @Override public void close() {
        if (disposed) return;
        disposed = true;
        phase = Phase.CLOSED;
        stopFrames();
        leaving = null;
        removed = () -> { };
        remove();
        try { appearance.close(); } catch (Exception ignored) { }
        motion.close();
    }

    /** Ease-out cubic: fast at first, settling gently. */
    static double easeOutCubic(double t) { double u = 1 - t; return 1 - u * u * u; }

    /** Ease-in cubic: gentle at first, leaving quickly. */
    static double easeInCubic(double t) { return t * t * t; }

    /** Ease-in quadratic: the exit's fade, holding a little before it goes. */
    static double easeInQuad(double t) { return t * t; }

    /** The eased progress of the running transition, 0 to 1; for tests. */
    double shown() { return shown; }

    Phase phase() { return phase; }

    @Override public void doLayout() { placeCard(); }

    /** Centred within the margin, moved by its offset: lowered while it rises in, raised as it rises out. */
    private void placeCard() {
        int margin = Math.min(ThemePainting.metric(context, DesignMetrics.Role.OVERLAY_MARGIN), Math.min(getWidth(), getHeight()) / 2);
        Dimension preferred = card.getPreferredSize();
        int width = Math.min(Math.max(0, preferred.width), Math.max(0, getWidth() - 2 * margin));
        int height = Math.min(Math.max(0, preferred.height), Math.max(0, getHeight() - 2 * margin));
        int lower = (int) Math.round(ThemePainting.metric(context, DesignMetrics.Role.OVERLAY_RISE) * offset);
        var bounds = new Rectangle(margin + (getWidth() - 2 * margin - width) / 2, margin + (getHeight() - 2 * margin - height) / 2 + lower, width, height);
        if (!bounds.equals(card.getBounds())) { card.setBounds(bounds); card.doLayout(); }
    }

    @Override protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        try {
            ThemePainting.antialias(g);
            Color scrim = ThemePainting.color(context, SemanticColor.SURFACE_SCRIM);
            g.setColor(ColorMath.opacity(scrim, context.metrics().value(DesignMetrics.Role.OVERLAY_SCRIM_PERCENT) / 100.0 * shown));
            g.fillRect(0, 0, getWidth(), getHeight());
            Rectangle at = card.getBounds();
            if (at.isEmpty() || shown <= 0) return;
            // A soft shadow: nested rounded fills, one step wider each, together reaching the shadow's strength.
            int reach = ThemePainting.metric(context, DesignMetrics.Role.OVERLAY_SHADOW), radius = ThemePainting.metric(context, DesignMetrics.Role.WINDOW_RADIUS);
            double strength = context.metrics().value(DesignMetrics.Role.OVERLAY_SHADOW_PERCENT) / 100.0 * shown;
            if (reach > 0) {
                g.setColor(ColorMath.opacity(scrim, strength / reach));
                int drop = Math.max(1, reach / 6);
                for (int step = reach; step >= 1; step--)
                    g.fill(new RoundRectangle2D.Float(at.x - step, at.y - step + drop, at.width + 2 * step, at.height + 2 * step,
                        2 * (radius + step), 2 * (radius + step)));
            }
            if (leaving != null) {
                g.setComposite(AlphaComposite.SrcOver.derive((float) Math.min(1, Math.max(0, shown))));
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(leaving, at.x, at.y, at.width, at.height, null);
            }
        } finally { g.dispose(); }
    }

    /**
     * The card: the dialog surface, rounded and outlined, clipping its content. Children repaint through it, so a
     * repaint they start while the card moves still fades with it.
     */
    private final class Card extends JPanel {
        private BufferedImage snapshot;
        private boolean capturing;

        Card() {
            super(new BorderLayout());
            setOpaque(false);
        }

        void restyle() {
            int line = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
            setBorder(new EmptyBorder(line, line, line, line));
            forget();
        }

        void forget() { snapshot = null; }

        private Shape surface(double inset) {
            double radius = Math.max(0, ThemePainting.metric(context, DesignMetrics.Role.WINDOW_RADIUS) - inset);
            return new RoundRectangle2D.Double(inset, inset, Math.max(0, getWidth() - 2 * inset), Math.max(0, getHeight() - 2 * inset),
                2 * radius, 2 * radius);
        }

        @Override protected boolean isPaintingOrigin() { return true; }

        @Override public void setBounds(int x, int y, int width, int height) {
            if (width != getWidth() || height != getHeight()) forget();
            super.setBounds(x, y, width, height);
        }

        @Override public void paint(Graphics graphics) {
            if (capturing || phase != Phase.OPENING) { super.paint(graphics); return; }
            var g = (Graphics2D) graphics.create();
            try {
                double scale = Math.max(1, g.getTransform().getScaleX());
                if (snapshot == null || snapshot.getWidth() != (int) Math.ceil(getWidth() * scale)) snapshot = picture(scale);
                if (snapshot == null) return;
                g.setComposite(AlphaComposite.SrcOver.derive((float) Math.min(1, Math.max(0, shown))));
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(snapshot, 0, 0, getWidth(), getHeight(), null);
            } finally { g.dispose(); }
        }

        /** The card as it paints now, at the default display's scale. */
        BufferedImage picture() {
            var configuration = getGraphicsConfiguration();
            return picture(configuration == null ? 1 : Math.max(1, configuration.getDefaultTransform().getScaleX()));
        }

        private BufferedImage picture(double scale) {
            if (getWidth() <= 0 || getHeight() <= 0) return null;
            var image = new BufferedImage((int) Math.ceil(getWidth() * scale), (int) Math.ceil(getHeight() * scale), BufferedImage.TYPE_INT_ARGB);
            var g = image.createGraphics();
            capturing = true;
            try { g.scale(scale, scale); printAll(g); } finally { capturing = false; g.dispose(); }
            return image;
        }

        @Override protected void paintChildren(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                // Swing may skip paintComponent when an opaque child covers the dirty rectangle.
                // Fill here: rounding that child exposes the card underneath even in that case.
                ThemePainting.antialias(g);
                g.setColor(ThemePainting.color(context, SemanticColor.SURFACE_DIALOG));
                g.fill(surface(0));
                g.clip(surface(ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE)));
                super.paintChildren(g);
            } finally { g.dispose(); }
            // Last, so child backgrounds cannot cover the antialiased outline.
            g = (Graphics2D) graphics.create();
            try {
                ThemePainting.antialias(g);
                g.setColor(ThemePainting.color(context, SemanticColor.BORDER_WINDOW));
                g.draw(surface(ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE) / 2.0));
            } finally { g.dispose(); }
        }
    }
}
