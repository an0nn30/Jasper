package dev.jasper.buddy.internal.presentation;

import dev.jasper.buddy.internal.animation.BuddyAnimator;
import dev.jasper.buddy.internal.model.BuddyDeck;
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
import dev.jasper.buddy.config.BuddyOptions;
import dev.jasper.buddy.config.BuddyPosition;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.swing.JComponent;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** The floating translucent utility window; every rule lives in the Buddy* helpers it drives. */
public final class BuddyWindow {
    private static final System.Logger LOG = System.getLogger(BuddyWindow.class.getName());
    private static final int SCALE = 2;
    private static final int DRAG_THRESHOLD = 3;

    private final JWindow window = new JWindow();
    private final BuddySprite sprite;
    private final BuddyAnimator animator = new BuddyAnimator(new Random());
    private BuddyOptions options;
    private final java.beans.PropertyChangeListener appearanceListener = event -> {
        if ("lookAndFeel".equals(event.getPropertyName())) SwingUtilities.invokeLater(() -> {
            if (!this.disposed) refreshDeck();
        });
    };
    private final Timer timer = new Timer(1, event -> tick());
    private BuddyBubble bubble;
    private BuddyDeckWindow drawer;
    private BuddyColumnWindow column;
    private final BuddyDragFrames dragFrames = new BuddyDragFrames(this::presentDrag);
    private Point pressScreen;
    private Point pressOrigin;
    private boolean dragged;
    private boolean disposed;

    private BuddyWindow(BuddySprite sprite, BuddyOptions options) {
        this.sprite = sprite; this.options = options;
    }

    private void initialize(BuddyDeck deck) {
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
                if (!dragged && column != null) column.beginDrag();
                dragged = true;
                dragFrames.offer(new Point(pressOrigin.x + dx, pressOrigin.y + dy));
            }
            @Override public void mouseReleased(MouseEvent event) {
                if (event.isPopupTrigger()) { endDrag(); popup(); pressScreen = null; return; }
                if (pressScreen == null) return;
                pressScreen = null;
                if (dragged) { endDrag(); options.positionChanged().accept(new BuddyPosition(window.getX(), window.getY())); }
            }
            @Override public void mouseClicked(MouseEvent event) {
                // A macOS control-click is the popup trigger yet reports the left button; it must not raise.
                if (!SwingUtilities.isLeftMouseButton(event) || event.isControlDown() || dragged) return;
                if (event.getClickCount() == 2) options.activateHost().run();
                else if (event.getClickCount() == 1) openDrawer();
            }
        };
        canvas.addMouseListener(mouse);
        canvas.addMouseMotionListener(mouse);
        attachDeck(deck);
        javax.swing.UIManager.addPropertyChangeListener(appearanceListener);
    }

    private void presentDrag(Point location) {
        if (disposed || !window.isVisible()) return;
        if (!location.equals(window.getLocation())) window.setLocation(location);
        Rectangle bounds = new Rectangle(location, window.getSize());
        if (column != null) column.moveBeside(bounds);
        if (drawer != null && drawer.isShowing()) drawer.showBeside(bounds);
    }

    private void endDrag() {
        dragFrames.finish();
        if (column != null) column.endDrag();
    }

    /** Returns null when headless, unsupported or the sprite is unavailable. Unsupported/resource failures log per attempt; the host decides whether to retry. Other initialization failures are rethrown after rollback. */
    public static BuddyWindow create(BuddyOptions options, BuddyDeck deck) {
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
        BuddyWindow result = new BuddyWindow(sprite, options);
        try { result.initialize(deck); return result; }
        catch (RuntimeException | Error failure) {
            try { result.dispose(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    /** Attaches the companion-owned model to the drawer and column owned by this presentation. */
    void attachDeck(BuddyDeck deck) {
        if (disposed || drawer != null) return;
        drawer = new BuddyDeckWindow(options, deck, () -> column.refresh());
        column = new BuddyColumnWindow(options, deck, this::openDrawer);
        refreshDeck();
    }

    /** A notice was posted, dismissed or cleared: re-place both surfaces around him. */
    public void refreshDeck() {
        if (column == null) return;
        if (window.isVisible()) {
            column.showBeside(window.getBounds());
            if (drawer.isShowing()) drawer.showBeside(window.getBounds());
        } else {
            column.hide();
            drawer.hide();
        }
    }

    /** Single click: double-click already raises the terminal and right-click opens his menu. */
    private void openDrawer() {
        if (drawer == null || !window.isVisible()) return;
        drawer.showBeside(window.getBounds());
    }

    /** Applies host working intent to the animator without acquiring presentation or deciding policy. */
    public void setWorking(boolean working) {
        animator.setWorking(working);
        window.repaint();
    }

    public void show() {
        if (disposed || window.isVisible()) return;
        animator.shown(System.nanoTime());
        window.setVisible(true);
        paintAndSchedule();
        refreshDeck();
    }

    /** The user came back to a Jasper window: wave, waking him out of his shell first if need be. */
    public void greet() {
        if (disposed || !window.isVisible()) return;
        animator.greet(System.nanoTime());
        paintAndSchedule();
    }

    /** The user is typing in a Jasper window: keep him awake, without a wave for every keystroke. */
    public void poke() {
        if (disposed || !window.isVisible()) return;
        animator.poke(System.nanoTime());
        paintAndSchedule();
    }

    public void hide() {
        if (disposed) return;
        timer.stop();
        dragFrames.cancel();
        pressScreen = null;
        if (bubble != null) bubble.hide();
        if (drawer != null) drawer.hide();
        if (column != null) column.hide();
        animator.hidden();
        window.setVisible(false);
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        javax.swing.UIManager.removePropertyChangeListener(appearanceListener);
        timer.stop();
        dragFrames.cancel();
        pressScreen = null;
        if (bubble != null) bubble.dispose();
        if (drawer != null) drawer.dispose();
        if (column != null) column.dispose();
        window.dispose();
        options = null;
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
        if (bubble == null) bubble = new BuddyBubble(options, () -> options.toggleRequested().run());
        bubble.showBeside(window.getBounds(), BuddyBubbleContent.menu("Hide Jasper"));
    }

    private Point initialLocation(Dimension size) {
        List<Rectangle> screens = usableScreens();
        Rectangle primary = screens.isEmpty() ? new Rectangle(0, 0, 1280, 800) : screens.getFirst();
        Point fallback = BuddyPlacement.defaultLocation(primary, size);
        return options.initialPosition()
            .map(saved -> BuddyPlacement.clamp(new Point(saved.x(), saved.y()), screens, size))
            .orElse(fallback);
    }

    /** Updates appearance and callbacks without resetting the user's dragged position. */
    public void applyOptions(BuddyOptions value) {
        if (disposed) return;
        options = java.util.Objects.requireNonNull(value);
        if (bubble != null) bubble.applyOptions(value);
        if (drawer != null) drawer.applyOptions(value);
        if (column != null) column.applyOptions(value);
        refreshDeck();
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
