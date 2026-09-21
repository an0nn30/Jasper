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
import java.awt.event.MouseWheelEvent;
import java.util.List;
import java.util.Objects;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * The drawer's window: translucent, always on top and never focused, like the bubble it replaces.
 * Unlike the bubble it has no auto-hide timer — the drawer is a standing list, so it goes away only
 * when it is emptied, when a card is activated, or when the buddy does.
 */
final class BuddyDeckWindow {
    private final JWindow window = new JWindow();
    private final BuddyDeck deck;
    private final BuddyDeckPanel panel;
    /** Animates hover and running-text shimmer; other live detail ticks once per second. */
    private final Timer frames = new Timer(16, event -> tick());
    private Rectangle anchor;
    private boolean disposed;

    BuddyDeckWindow(BuddyOptions options, BuddyDeck deck, Runnable onNoticesChanged) {
        this.deck = Objects.requireNonNull(deck, "deck");
        this.panel = new BuddyDeckPanel(options, deck, this::onPanelChanged, onNoticesChanged);
        panel.setMaxListHeight(this::usableHeight);
        try {
            window.setType(Window.Type.UTILITY);
            window.setAlwaysOnTop(true);
            window.setFocusableWindowState(false);
            window.setBackground(new Color(0, 0, 0, 0));
            window.setContentPane(panel);
            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent event) { panel.handleMove(event.getPoint()); }
                @Override public void mouseExited(MouseEvent event) {
                    // Moving within the panel still reports an exit for child bounds; only a real one counts.
                    if (panel.contains(event.getPoint())) return;
                    panel.handleExit();
                    hide();
                }
                // A macOS control-click is the popup trigger yet reports the left button; it must not activate.
                @Override public void mouseClicked(MouseEvent event) {
                    if (!SwingUtilities.isLeftMouseButton(event) || event.isControlDown()) return;
                    if (!panel.handleClick(event.getPoint())) hide();
                }
                @Override public void mouseWheelMoved(MouseWheelEvent event) {
                    panel.handleWheel(event.getWheelRotation());
                }
            };
            panel.addMouseListener(mouse);
            panel.addMouseMotionListener(mouse);
            panel.addMouseWheelListener(mouse);
        } catch (RuntimeException | Error failure) {
            try { window.dispose(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private void tick() {
        if (disposed || !window.isVisible()) { frames.stop(); return; }
        panel.repaint();
        if (!panel.needsAnimationFrames() && !panel.needsDetailUpdates()) frames.stop();
        else frames.setDelay(panel.needsAnimationFrames() ? 16 : 1000);
    }

    private void onPanelChanged() {
        layout();
        if (!disposed && window.isVisible() && (panel.needsAnimationFrames() || panel.needsDetailUpdates())) {
            int delay = panel.needsAnimationFrames() ? 16 : 1000;
            boolean speedUp = delay < frames.getDelay();
            frames.setDelay(delay);
            if (!frames.isRunning() || speedUp) frames.restart();
        }
    }

    /** Remembers where the buddy is and shows the drawer there, if there is anything in it. */
    void showBeside(Rectangle anchorOnScreen) {
        if (disposed) return;
        anchor = new Rectangle(anchorOnScreen);
        layout();
    }

    /** The drawer changed: re-size and re-place it, or take it away when it is now empty. */
    void refresh() { layout(); }

    void hide() {
        if (disposed) return;
        frames.stop();
        panel.reset();
        window.setVisible(false);
    }

    void applyOptions(BuddyOptions options) {
        panel.applyOptions(options);
        window.pack();
    }

    void dispose() {
        if (disposed) return;
        disposed = true;
        frames.stop();
        window.dispose();
    }

    boolean isShowing() { return !disposed && window.isVisible(); }

    /**
     * Whether the drawer has anything to show. Extracted because the test JVM is headless and a
     * {@link JWindow} cannot be built there at all — a test that only skips is not coverage.
     */
    static boolean shows(boolean disposed, Rectangle anchor, boolean deckEmpty, Dimension size) {
        return !disposed && anchor != null && !deckEmpty && size.width > 0 && size.height > 0;
    }

    private void layout() {
        if (disposed || anchor == null) return;
        Dimension size = panel.getPreferredSize();
        if (!shows(false, anchor, deck.isEmpty(), size)) { frames.stop(); window.setVisible(false); return; }
        if (!size.equals(window.getSize())) window.setSize(size);
        Point next = BuddyBubblePlacement.beside(anchor, size, screenFor(anchor));
        if (!next.equals(window.getLocation())) window.setLocation(next);
        if (!window.isVisible()) window.setVisible(true);
        panel.repaint();
        if (panel.needsDetailUpdates() && !frames.isRunning()) {
            frames.setDelay(panel.needsAnimationFrames() ? 16 : 1000);
            frames.start();
        }
    }

    /** How tall the expanded list may grow: the screen it sits on, less room to breathe. */
    private int usableHeight() {
        Rectangle screen = anchor == null ? null : screenFor(anchor);
        return screen == null ? 800 : Math.max(120, screen.height - 4 * BuddyDeckPanel.MOTION_MARGIN);
    }

    /** The usable screen holding the anchor's centre, else the default one, else a plausible fallback. */
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
