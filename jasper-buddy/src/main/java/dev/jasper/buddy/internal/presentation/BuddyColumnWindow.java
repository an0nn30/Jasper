package dev.jasper.buddy.internal.presentation;

import dev.jasper.buddy.config.BuddyOptions;
import dev.jasper.buddy.internal.model.BuddyDeck;
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

/** The window the thought column lives in: translucent, always on top, never focused. */
final class BuddyColumnWindow {
    static final int BUBBLE_GAP = 30;
    private final JWindow window = new BuddySurfaceWindow();
    private final BuddyDeck deck;
    private final BuddyColumnPanel panel;
    private Rectangle anchor;
    private final BuddyColumnPlacement placement = new BuddyColumnPlacement();
    private boolean disposed;
    private List<Rectangle> screens = List.of();

    BuddyColumnWindow(BuddyOptions options, BuddyDeck deck, Runnable onOpenDrawer) {
        this.deck = Objects.requireNonNull(deck, "deck");
        this.panel = new BuddyColumnPanel(options, deck, this::onPanelChanged, Objects.requireNonNull(onOpenDrawer, "onOpenDrawer"));
        try {
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
        } catch (RuntimeException | Error failure) {
            try { window.dispose(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private void onPanelChanged() { layout(); }

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
        refreshScreens();
    }

    /** Geometry only: no notice rebuild, revalidation or monitor queries on pointer frames. */
    void moveBeside(Rectangle anchorOnScreen) {
        if (disposed) return;
        anchor = new Rectangle(anchorOnScreen);
        layout();
        if (window.isVisible()) panel.repaintFrame();
    }

    void endDrag() { }

    void refresh() { panel.refresh(); }

    void hide() {
        if (disposed) return;
        anchor = null;
        window.setVisible(false);
    }

    void applyOptions(BuddyOptions options) {
        panel.applyOptions(options);
        window.pack();
    }

    void dispose() {
        if (disposed) return;
        disposed = true;
        window.dispose();
    }

    boolean isShowing() { return !disposed && window.isVisible(); }

    private void layout() {
        if (disposed || anchor == null) return;
        if (deck.isEmpty()) { window.setVisible(false); return; }
        Rectangle screen = screenFor(anchor);
        // Position the single capsule directly; no activity surface owns an idle timer.
        Dimension size = panel.getPreferredSize();
        long now = System.nanoTime();
        boolean snap = true;
        boolean below = placement.update(anchor, size, screen, now, snap);
        panel.setBelow(below, snap);
        if (size.width <= 0 || size.height <= 0) { window.setVisible(false); return; }
        if (!size.equals(window.getSize())) window.setSize(size);
        Point next = placement.at(now);
        if (!next.equals(window.getLocation())) window.setLocation(next);
        if (!window.isVisible()) window.setVisible(true);
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
