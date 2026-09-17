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
    static final int BUBBLE_GAP = 30;
    private final JWindow window = new JWindow();
    private final BuddyDeck deck;
    private final BuddyColumnPanel panel;
    /** Animate placement and running-text shimmer at 60 Hz; other live detail ticks once a second. */
    private final Timer frames = new Timer(16, event -> tick());
    private Rectangle anchor;
    private final BuddyColumnPlacement placement = new BuddyColumnPlacement();
    private boolean disposed, dragging;
    private List<Rectangle> screens = List.of();

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
        if (disposed || dragging || !window.isVisible()) { frames.stop(); return; }
        Point next = placement.at(System.nanoTime());
        if (!next.equals(window.getLocation())) window.setLocation(next);
        panel.repaintFrame();
        if (!moving() && !panel.needsDetailUpdates()) frames.stop();
        else frames.setDelay(moving() ? 16 : 1000);
    }

    private boolean moving() { return placement.moving(System.nanoTime()) || panel.needsAnimationFrames(); }

    /** Called whenever the panel starts something moving; harmless when it is already running. */
    private void animate() {
        if (disposed || dragging || !window.isVisible() || (!moving() && !panel.needsDetailUpdates())) return;
        int delay = moving() ? 16 : 1000;
        boolean speedUp = delay < frames.getDelay();
        frames.setDelay(delay);
        // setDelay alone does not cancel the already queued one-second detail tick.
        if (!frames.isRunning() || speedUp) frames.restart();
    }

    private void onPanelChanged() {
        layout();
        animate();
    }

    /** Whether the column fits between the top of the screen and the top of his head. */
    static boolean fitsAbove(Rectangle anchor, int columnHeight, Rectangle screen) {
        return anchor.y - BUBBLE_GAP - columnHeight + BuddyColumnPanel.MARGIN >= screen.y;
    }

    void showBeside(Rectangle anchorOnScreen) {
        if (disposed) return;
        anchor = new Rectangle(anchorOnScreen);
        refreshScreens();
        panel.refresh();
    }

    void beginDrag() {
        if (disposed) return;
        dragging = true;
        frames.stop();
        refreshScreens();
    }

    /** Geometry only: no notice rebuild, revalidation or monitor queries on pointer frames. */
    void moveBeside(Rectangle anchorOnScreen) {
        if (disposed) return;
        anchor = new Rectangle(anchorOnScreen);
        layout();
        if (window.isVisible()) panel.repaintFrame();
    }

    void endDrag() { dragging = false; animate(); }

    void refresh() { panel.refresh(); }

    void hide() {
        if (disposed) return;
        frames.stop();
        dragging = false;
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
        if (deck.column().isEmpty()) { frames.stop(); window.setVisible(false); return; }
        Rectangle screen = screenFor(anchor);
        // The stack keeps the same window size while its direction and anchor-relative offset animate.
        Dimension size = panel.getPreferredSize();
        long now = System.nanoTime();
        boolean snap = !window.isVisible();
        boolean below = placement.update(anchor, size, screen, now, snap);
        panel.setBelow(below, snap);
        if (size.width <= 0 || size.height <= 0) { window.setVisible(false); return; }
        if (!size.equals(window.getSize())) window.setSize(size);
        Point next = placement.at(now);
        if (!next.equals(window.getLocation())) window.setLocation(next);
        if (!window.isVisible()) window.setVisible(true);
        animate();
    }

    /** Account for transparent shadow/motion room: the visible body is 30px from his head. */
    static Point place(Rectangle anchor, Dimension size, Rectangle screen, boolean below) {
        int x = anchor.x + (anchor.width - size.width) / 2;
        int y = below ? anchor.y + anchor.height + BUBBLE_GAP - BuddyColumnPanel.MARGIN
            : anchor.y - BUBBLE_GAP - size.height + BuddyColumnPanel.MARGIN;
        return new Point(Math.max(screen.x, Math.min(x, screen.x + screen.width - size.width)),
            Math.max(screen.y, Math.min(y, screen.y + screen.height - size.height)));
    }

    private void refreshScreens() {
        screens = GraphicsEnvironment.isHeadless() ? List.of() : BuddyWindow.usableScreens();
        if (screens.isEmpty()) screens = List.of(new Rectangle(0, 0, 1280, 800));
    }

    private Rectangle screenFor(Rectangle anchor) {
        if (screens.isEmpty()) refreshScreens();
        for (Rectangle screen : screens) {
            if (screen.contains((int) anchor.getCenterX(), (int) anchor.getCenterY())) return screen;
        }
        return screens.getFirst();
    }
}
