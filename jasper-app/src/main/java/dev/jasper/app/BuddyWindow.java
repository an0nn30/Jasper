package dev.jasper.app;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.swing.JComponent;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** The floating translucent utility window; every rule lives in the Buddy* helpers it drives. */
final class BuddyWindow {
    private static final System.Logger LOG = System.getLogger(BuddyWindow.class.getName());
    private static final int SCALE = 2;
    private static final int DRAG_THRESHOLD = 3;

    private final JWindow window = new JWindow();
    private final BuddySprite sprite;
    private final BuddyAnimator animator = new BuddyAnimator(new Random());
    private final Path stateFile;
    private final Runnable raiseTerminal;
    private final Runnable toggle;
    private final Timer timer = new Timer(1, event -> tick());
    private BuddyBubble bubble;
    private BuddyDeckWindow drawer;
    private Point pressScreen;
    private Point pressOrigin;
    private boolean dragged;
    private boolean disposed;

    private BuddyWindow(BuddySprite sprite, Path stateFile, Runnable raiseTerminal, Runnable toggle) {
        this.sprite = sprite; this.stateFile = stateFile; this.raiseTerminal = raiseTerminal; this.toggle = toggle;
        timer.setRepeats(false);
        window.setType(Window.Type.UTILITY);
        window.setAlwaysOnTop(true);
        window.setFocusableWindowState(false);
        window.setBackground(new Color(0, 0, 0, 0));
        JComponent canvas = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setComposite(AlphaComposite.Clear);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.setComposite(AlphaComposite.SrcOver);
                    paintBody(g2, animator.opacity());
                    // The spawn sparkles stay solid while the body behind them fades in.
                    animator.overlay().ifPresent(overlay -> sprite.paint(g2, overlay, SCALE, 0, 0));
                } finally { g2.dispose(); }
            }

            private void paintBody(Graphics2D g2, float opacity) {
                if (opacity >= 1f) { sprite.paint(g2, animator.frame(), SCALE, 0, 0); return; }
                Graphics2D faded = (Graphics2D) g2.create();
                try {
                    faded.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
                    sprite.paint(faded, animator.frame(), SCALE, 0, 0);
                } finally { faded.dispose(); }
            }
        };
        canvas.setOpaque(false);
        Dimension size = BuddySprite.size(SCALE);
        canvas.setPreferredSize(size);
        window.setContentPane(canvas);
        window.pack();
        window.setSize(size);
        window.setLocation(initialLocation(size));
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent event) { animator.hoverEntered(System.nanoTime()); paintAndSchedule(); }
            @Override public void mousePressed(MouseEvent event) {
                if (event.isPopupTrigger()) { popup(); return; }
                if (bubble != null) bubble.hide();
                pressScreen = event.getLocationOnScreen(); pressOrigin = window.getLocation(); dragged = false;
            }
            @Override public void mouseDragged(MouseEvent event) {
                if (pressScreen == null) return;
                Point now = event.getLocationOnScreen();
                int dx = now.x - pressScreen.x, dy = now.y - pressScreen.y;
                if (!dragged && Math.abs(dx) < DRAG_THRESHOLD && Math.abs(dy) < DRAG_THRESHOLD) return;
                dragged = true;
                window.setLocation(pressOrigin.x + dx, pressOrigin.y + dy);
                refreshDeck();
            }
            @Override public void mouseReleased(MouseEvent event) {
                if (event.isPopupTrigger()) { popup(); pressScreen = null; return; }
                if (pressScreen == null) return;
                pressScreen = null;
                if (dragged) save(window.getLocation());
            }
            @Override public void mouseClicked(MouseEvent event) {
                // A macOS control-click is the popup trigger yet reports the left button; it must not raise.
                if (SwingUtilities.isLeftMouseButton(event) && !event.isControlDown()
                        && event.getClickCount() == 2 && !dragged) raiseTerminal.run();
            }
        };
        canvas.addMouseListener(mouse);
        canvas.addMouseMotionListener(mouse);
    }

    /** Null when headless or the toolkit lacks always-on-top or per-pixel translucency; logs once. */
    static BuddyWindow create(Path stateFile, Runnable raiseTerminal, Runnable toggle) {
        if (GraphicsEnvironment.isHeadless()) return null;
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        if (!Toolkit.getDefaultToolkit().isAlwaysOnTopSupported()
                || !device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)) {
            LOG.log(System.Logger.Level.WARNING, "Desk buddy disabled: the toolkit lacks always-on-top or per-pixel translucency");
            return null;
        }
        BuddySprite sprite;
        try { sprite = BuddySprite.load(); }
        catch (IllegalStateException failure) {
            LOG.log(System.Logger.Level.WARNING, "Desk buddy disabled: sprite unavailable", failure);
            return null;
        }
        return new BuddyWindow(sprite, stateFile, raiseTerminal, toggle);
    }

    /** The application owns the drawer's contents; the buddy only gives it somewhere to sit. */
    void attachDeck(BuddyDeck deck) {
        if (disposed || drawer != null) return;
        drawer = new BuddyDeckWindow(deck);
        refreshDeck();
    }

    /** A notice was posted, dismissed or cleared: re-place the drawer beside him. */
    void refreshDeck() {
        if (drawer == null) return;
        if (window.isVisible()) drawer.showBeside(window.getBounds()); else drawer.hide();
    }

    /** A long command is in flight: he sits down with the laptop until it finishes. */
    void setWorking(boolean working) {
        animator.setWorking(working);
        window.repaint();
    }

    void show() {
        if (disposed || window.isVisible()) return;
        animator.shown(System.nanoTime());
        window.setVisible(true);
        paintAndSchedule();
        refreshDeck();
    }

    /** The user came back to a Jasper window: wave, waking him out of his shell first if need be. */
    void greet() {
        if (disposed || !window.isVisible()) return;
        animator.greet(System.nanoTime());
        paintAndSchedule();
    }

    /** The user is typing in a Jasper window: keep him awake, without a wave for every keystroke. */
    void poke() {
        if (disposed || !window.isVisible()) return;
        animator.poke(System.nanoTime());
        paintAndSchedule();
    }

    void hide() {
        if (disposed) return;
        timer.stop();
        if (bubble != null) bubble.hide();
        if (drawer != null) drawer.hide();
        animator.hidden();
        window.setVisible(false);
    }

    void dispose() {
        if (disposed) return;
        disposed = true;
        timer.stop();
        if (bubble != null) bubble.dispose();
        if (drawer != null) drawer.dispose();
        window.dispose();
    }

    private void tick() {
        if (disposed || !window.isVisible()) return;
        animator.tick(System.nanoTime());
        paintAndSchedule();
    }

    private void paintAndSchedule() {
        window.getContentPane().repaint();
        timer.stop();
        animator.nextDueNanos().ifPresent(due -> {
            long millis = Math.max(1, (due - System.nanoTime()) / 1_000_000);
            timer.setInitialDelay((int) Math.min(Integer.MAX_VALUE, millis));
            timer.start();
        });
    }

    /** The right-click menu is one bubble entry beside Jasper, not a platform popup menu. */
    private void popup() {
        if (bubble == null) bubble = new BuddyBubble(toggle);
        bubble.showBeside(window.getBounds(), BuddyBubbleContent.menu("Hide Jasper"));
    }

    private Point initialLocation(Dimension size) {
        List<Rectangle> screens = usableScreens();
        Rectangle primary = screens.isEmpty() ? new Rectangle(0, 0, 1280, 800) : screens.getFirst();
        Point fallback = BuddyPlacement.defaultLocation(primary, size);
        if (stateFile == null) return fallback;
        try {
            return BuddyStateFile.read(stateFile).map(saved -> BuddyPlacement.clamp(saved, screens, size)).orElse(fallback);
        } catch (IOException failure) {
            LOG.log(System.Logger.Level.WARNING, "Ignoring unreadable buddy state " + stateFile, failure);
            return fallback;
        }
    }

    private void save(Point location) {
        if (stateFile == null) return;
        try { BuddyStateFile.write(stateFile, location); }
        catch (IOException failure) { LOG.log(System.Logger.Level.WARNING, "Could not save buddy position to " + stateFile, failure); }
    }

    /** Default screen first, each reduced by its Dock/menu/taskbar insets. */
    static List<Rectangle> usableScreens() {
        var environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        var screens = new ArrayList<Rectangle>();
        GraphicsDevice primary = environment.getDefaultScreenDevice();
        for (GraphicsDevice device : environment.getScreenDevices()) {
            var configuration = device.getDefaultConfiguration();
            Rectangle bounds = new Rectangle(configuration.getBounds());
            Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
            bounds.x += insets.left; bounds.y += insets.top;
            bounds.width -= insets.left + insets.right; bounds.height -= insets.top + insets.bottom;
            if (device == primary) screens.addFirst(bounds); else screens.add(bounds);
        }
        return screens;
    }
}
