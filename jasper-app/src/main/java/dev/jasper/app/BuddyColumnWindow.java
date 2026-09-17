package dev.jasper.app;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Objects;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** The window the thought column lives in: translucent, always on top, never focused. */
final class BuddyColumnWindow {
    private final JWindow window = new JWindow();
    private final BuddyDeck deck;
    private final BuddyColumnPanel panel;
    /**
     * Drives the frames while anything is moving. The buddy's own timer repaints only his sprite
     * canvas, so without this the column painted exactly one frame per event — at elapsed zero,
     * which is scale 0.6 and opacity 0, leaving the bubble invisible until some unrelated repaint.
     */
    private final Timer frames = new Timer(16, event -> tick());
    private Rectangle anchor;
    private boolean disposed;

    BuddyColumnWindow(BuddyDeck deck, Runnable onOpenDrawer) {
        this.deck = Objects.requireNonNull(deck, "deck");
        this.panel = new BuddyColumnPanel(deck, this::onPanelChanged, Objects.requireNonNull(onOpenDrawer, "onOpenDrawer"));
        window.setType(Window.Type.UTILITY);
        window.setAlwaysOnTop(true);
        window.setFocusableWindowState(false);
        window.setBackground(new Color(0, 0, 0, 0));
        window.setContentPane(panel);
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent event) { panel.handleMove(event.getPoint()); }
            @Override public void mouseExited(MouseEvent event) { panel.handleExit(); }
            // A macOS control-click is the popup trigger yet reports the left button; it must not activate.
            @Override public void mouseClicked(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event) || event.isControlDown()) return;
                if (!panel.handleClick(event.getPoint())) hide();
            }
        };
        panel.addMouseListener(mouse);
        panel.addMouseMotionListener(mouse);
    }

    private void tick() {
        if (disposed || !window.isVisible()) { frames.stop(); return; }
        panel.repaint();
        if (!panel.animating()) frames.stop();
    }

    /** Called whenever the panel starts something moving; harmless when it is already running. */
    private void animate() {
        if (disposed || !window.isVisible() || !panel.animating()) return;
        if (!frames.isRunning()) frames.start();
    }

    private void onPanelChanged() {
        layout();
        animate();
    }

    /** Whether the column fits between the top of the screen and the top of his head. */
    static boolean fitsAbove(Rectangle anchor, int columnHeight, Rectangle screen) {
        return anchor.y - columnHeight >= screen.y;
    }

    void showBeside(Rectangle anchorOnScreen) {
        if (disposed) return;
        anchor = new Rectangle(anchorOnScreen);
        panel.refresh();
    }

    void refresh() { panel.refresh(); }

    void hide() {
        if (disposed) return;
        frames.stop();
        window.setVisible(false);
    }

    void dispose() {
        if (disposed) return;
        disposed = true;
        frames.stop();
        window.dispose();
    }

    boolean isShowing() { return !disposed && window.isVisible(); }

    private void layout() {
        if (disposed || anchor == null) return;
        if (deck.column().isEmpty()) { window.setVisible(false); return; }
        Rectangle screen = screenFor(anchor);
        // Decide the direction before measuring again: the panel lays its bubbles out the other way
        // round, and its height does not depend on the direction, so one measurement settles it.
        Dimension size = panel.getPreferredSize();
        panel.setBelow(!fitsAbove(anchor, size.height, screen));
        if (size.width <= 0 || size.height <= 0) { window.setVisible(false); return; }
        window.setSize(size);
        window.setLocation(place(size, screen));
        window.setVisible(true);
        animate();
    }

    /** Centred on him, above when there is room and below when there is not. */
    private Point place(Dimension size, Rectangle screen) {
        if (!panel.below()) return BuddyBubblePlacement.above(anchor, size, screen);
        int x = anchor.x + (anchor.width - size.width) / 2;
        int y = anchor.y + anchor.height + BuddyBubblePlacement.ABOVE_GAP;
        int clampedX = Math.max(screen.x, Math.min(x, screen.x + screen.width - size.width));
        int clampedY = Math.max(screen.y, Math.min(y, screen.y + screen.height - size.height));
        return new Point(clampedX, clampedY);
    }

    private static Rectangle screenFor(Rectangle anchor) {
        if (GraphicsEnvironment.isHeadless()) return new Rectangle(0, 0, 1280, 800);
        List<Rectangle> screens = BuddyWindow.usableScreens();
        if (screens.isEmpty()) return new Rectangle(0, 0, 1280, 800);
        for (Rectangle screen : screens) {
            if (screen.contains((int) anchor.getCenterX(), (int) anchor.getCenterY())) return screen;
        }
        return screens.getFirst();
    }
}
