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

/**
 * The boot splash's window (spec 10.11): undecorated, unfocusable and centred on the main screen, carrying the look's
 * window icons. It appears and closes at once, with no motion, and paints each step at once through the window's own
 * graphics, because the event thread is busy starting Jasper and would not get to a queued repaint. It is a plain AWT
 * window, not a Swing one, so opening it initializes no look and feel ahead of the application's. A window that cannot
 * be made is logged and the splash stays closed; start-up goes on. EDT.
 */
public final class Splash {
    private static final System.Logger LOG = System.getLogger(Splash.class.getName());
    private final SplashPainter painter;
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
        this.painter = Objects.requireNonNull(painter, "painter");
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
                @Override public void paint(Graphics g) { draw(g); }
                // The whole window is painted each time, so nothing is cleared first.
                @Override public void update(Graphics g) { draw(g); }
            };
            window.setFocusableWindowState(false);
            window.setIconImages(icons);
            window.setSize(SplashPainter.WIDTH, SplashPainter.HEIGHT);
            window.setLocation(centred(GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                .getDefaultConfiguration().getBounds()));
            window.setVisible(true);
            paintNow();
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
        if (window != null) paintNow();
    }

    /** Closes the splash at once; idempotent. */
    public void close() {
        closed = true;
        if (window == null) return;
        window.setVisible(false);
        window.dispose();
        window = null;
    }

    private void draw(Graphics g) {
        StartupSteps.Step shown = step;
        if (shown != null) painter.paint((Graphics2D) g, shown.text(), shown.fraction());
    }

    private void paintNow() {
        Graphics g = window.getGraphics();
        if (g == null) return;
        try { draw(g); }
        finally { g.dispose(); }
        Toolkit.getDefaultToolkit().sync();
    }

    /** Where a splash centred on {@code screen} goes. */
    static Point centred(Rectangle screen) {
        return new Point(screen.x + (screen.width - SplashPainter.WIDTH) / 2, screen.y + (screen.height - SplashPainter.HEIGHT) / 2);
    }
}
