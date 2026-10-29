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
 * window icons. It appears and closes at once, and paints each step at once through the window's own
 * graphics, because the event thread is busy starting Jasper and would not get to a queued repaint. Every paint is a copy
 * from {@code SplashBuffer}, which also preserves fractional-scale illustration sampling between steps. It is a plain AWT window, not
 * a Swing one, so opening it initializes no look and feel ahead of the application's. A window that cannot be made is
 * logged and the splash stays closed; start-up goes on. Window lifecycle and step updates are EDT-owned. The queue
 * animation paints on a daemon while startup occupies the EDT; all buffer and live-window paint access is
 * serialized by a private lock, and closing cancels the worker before disposing the window outside that lock.
 */
public final class Splash {
    private static final System.Logger LOG = System.getLogger(Splash.class.getName());
    private final Object paintLock = new Object();
    private final SplashPainter painter;
    private final SplashAnimation animation;
    private final boolean animations;
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
        this(painter, icons, true);
    }

    /**
     * A splash honoring the startup animation preference.
     * @param painter decoded artwork
     * @param icons window and taskbar icons
     * @param animations whether the user's app-wide preference permits motion
     */
    public Splash(SplashPainter painter, List<Image> icons, boolean animations) {
        this.painter = Objects.requireNonNull(painter, "painter");
        this.animations = animations;
        buffer = new SplashBuffer(painter);
        animation = new SplashAnimation(this::animate, painter.animated() && animations);
        this.icons = List.copyOf(icons);
    }

    /**
     * Shows the splash on its first step. Does nothing once closed or open.
     *
     * @param first the step to show
     */
    public void open(StartupSteps.Step first) {
        if (closed || window != null) return;
        synchronized (paintLock) {
            step = Objects.requireNonNull(first, "first");
            painter.showStep(step, System.nanoTime(), animations);
        }
        try {
            window = new Window((Frame) null) {
                @Override public void paint(Graphics g) {
                    synchronized (paintLock) {
                        if (!closed) buffer.paint((Graphics2D) g, step);
                    }
                }
                // A paint copies its whole area from the buffer, so nothing is cleared first.
                @Override public void update(Graphics g) { paint(g); }
            };
            window.setFocusableWindowState(false);
            // The native window's first fill, before the splash paints, matches the splash: no light flash.
            window.setBackground(painter.background());
            window.setIconImages(icons);
            window.setSize(painter.width(), painter.height());
            window.setLocation(centred(GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                .getDefaultConfiguration().getBounds(), painter.width(), painter.height()));
            window.setVisible(true);
            synchronized (paintLock) { paintNow(g -> buffer.paint(g, step)); }
            animation.start();
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
        synchronized (paintLock) {
            if (closed) return;
            step = Objects.requireNonNull(next, "next");
            painter.showStep(step, System.nanoTime(), animations);
            if (window != null) paintNow(g -> buffer.show(g, step));
        }
    }

    /** Closes the splash at once; idempotent. */
    public void close() {
        animation.close();
        Window closing;
        synchronized (paintLock) {
            closed = true;
            closing = window;
            window = null;
        }
        // Never hold the painting lock across native window disposal.
        if (closing != null) {
            closing.setVisible(false);
            closing.dispose();
        }
    }

    private void animate(long nowNanos) {
        synchronized (paintLock) {
            if (closed || window == null) return;
            if (!painter.animationTime(nowNanos)) return;
            paintNow(g -> buffer.show(g, step));
        }
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
        return centred(screen, SplashPainter.WIDTH, SplashPainter.HEIGHT);
    }

    static Point centred(Rectangle screen, int width, int height) {
        return new Point(screen.x + (screen.width - width) / 2, screen.y + (screen.height - height) / 2);
    }
}
