package dev.jasper.app.splash;

import dev.jasper.app.startup.StartupSteps;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The boot splash's window (spec 10.11): undecorated, unfocusable and centred on the main screen, carrying the look's
 * window icons. It appears and closes at once, with no motion, and paints each step at once through the window's own
 * graphics, because the event thread is busy starting Jasper and would not get to a queued repaint. Every paint is a copy
 * from {@code SplashBuffer}, so a step changes only the step's line and the bar on screen. It is a plain AWT window, not
 * a Swing one, so opening it initializes no look and feel ahead of the application's. A window that cannot be made is
 * logged and the splash stays closed; start-up goes on. EDT.
 */
public final class Splash {
    private static final System.Logger LOG = System.getLogger(Splash.class.getName());
    private final SplashBuffer buffer;
    private final List<Image> icons;
    private Window window;
    private StartupSteps.Step step;
    private boolean closed;

    /**
     * A splash that draws with {@code painter}; nothing is shown until {@link #open}.
     *
     * @param painter the decoded splash
     * @param icons the window's icons, the look's (spec 10.11), so a taskbar or window switcher shows them from the
     *     splash on
     */
    public Splash(SplashPainter painter, List<Image> icons) {
        buffer = new SplashBuffer(Objects.requireNonNull(painter, "painter"));
        this.icons = List.copyOf(icons);
    }

    /**
     * Shows the splash on its first step. Does nothing once closed or open.
     *
     * @param first the step to show
     */
    public void open(StartupSteps.Step first) {
        if (closed || window != null) return;
        step = Objects.requireNonNull(first, "first");
        try {
            window = new Window((Frame) null) {
                @Override public void paint(Graphics g) { buffer.paint((Graphics2D) g, step); }
                // A paint copies its whole area from the buffer, so nothing is cleared first.
                @Override public void update(Graphics g) { paint(g); }
            };
            window.setFocusableWindowState(false);
            // The native window's first fill, before the splash paints, matches the splash: no light flash.
            window.setBackground(SplashColors.TOP);
            window.setIconImages(icons);
            window.setSize(SplashPainter.WIDTH, SplashPainter.HEIGHT);
            window.setLocation(centred(GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                .getDefaultConfiguration().getBounds()));
            window.setVisible(true);
            paintNow(g -> buffer.paint(g, step));
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "The splash could not be shown", failure);
            close();
        }
    }

    /**
     * Shows {@code next} at once.
     *
     * @param next the step start-up reached
     */
    public void show(StartupSteps.Step next) {
        step = Objects.requireNonNull(next, "next");
        if (window != null) paintNow(g -> buffer.show(g, step));
    }

    /** Closes the splash at once; idempotent. */
    public void close() {
        closed = true;
        if (window == null) return;
        window.setVisible(false);
        window.dispose();
        window = null;
    }

    private void paintNow(Consumer<Graphics2D> paint) {
        Graphics g = window.getGraphics();
        if (g == null) return;
        try { paint.accept((Graphics2D) g); }
        finally { g.dispose(); }
        Toolkit.getDefaultToolkit().sync();
    }

    /** Where a splash centred on {@code screen} goes. */
    static Point centred(Rectangle screen) {
        return new Point(screen.x + (screen.width - SplashPainter.WIDTH) / 2, screen.y + (screen.height - SplashPainter.HEIGHT) / 2);
    }
}
