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
 * Focusable activity drawer, opened deliberately and closed on Escape, focus loss or activation.
 */
final class BuddyDeckWindow {
    private Window window;
    private Window owner;
    private final BuddyDrawerLifetime lifetime = new BuddyDrawerLifetime();
    private final BuddyDeck deck;
    private final BuddyDeckPanel panel;
    /** Animates brief hover feedback; live detail ticks once per second. */
    private final Timer frames = new Timer(16, event -> tick());
    private Rectangle anchor() { return lifetime.anchor(); }
    private boolean disposed;

    BuddyDeckWindow(BuddyOptions options, BuddyDeck deck, Runnable onNoticesChanged) {
        this.deck = Objects.requireNonNull(deck, "deck");
        this.panel = new BuddyDeckPanel(options, deck, this::onPanelChanged, onNoticesChanged);
        panel.setMaxListHeight(this::usableHeight);
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent event) { panel.handleMove(event.getPoint()); }
            @Override public void mouseExited(MouseEvent event) {
                // Moving within the panel still reports an exit for child bounds; only a real one counts.
                if (panel.contains(event.getPoint())) return;
                panel.handleExit();
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
        panel.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).put(
            javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "close");
        panel.getActionMap().put("close", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { hide(); }
        });
    }

    /** Owner selection happens only on explicit opening, preserving workspace activation. */
    private Window eligibleOwner() {
        Window active = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        for (Window candidate = active; candidate != null; candidate = candidate.getOwner())
            if (eligible(candidate)) return candidate;
        for (Window candidate : Window.getWindows()) if (eligible(candidate)) return candidate;
        return null;
    }

    private boolean eligible(Window candidate) {
        return candidate != window && candidate.isShowing() && candidate.isFocusableWindow()
            && (candidate instanceof java.awt.Frame || candidate instanceof java.awt.Dialog)
            && (!(candidate instanceof java.awt.Frame frame) || (frame.getExtendedState() & java.awt.Frame.ICONIFIED) == 0);
    }

    private void ensureWindow() {
        Window nextOwner = eligibleOwner();
        if (window != null && owner == nextOwner) return;
        if (window != null) { window.removeWindowFocusListener(focus); window.dispose(); }
        owner = nextOwner;
        window = owner == null ? new BuddySurfaceDialog() : new BuddySurfaceWindow(owner);
        window.setType(Window.Type.UTILITY);
        window.setAlwaysOnTop(true);
        window.setFocusableWindowState(true);
        window.setBackground(new Color(0, 0, 0, 0));
        ((javax.swing.RootPaneContainer) window).setContentPane(panel);
    }

    private final java.awt.event.WindowFocusListener focus = new java.awt.event.WindowAdapter() {
        @Override public void windowLostFocus(java.awt.event.WindowEvent event) { hide(); }
    };

    private void tick() {
        if (disposed || (window == null || !window.isVisible())) { frames.stop(); return; }
        panel.repaint();
        if (!panel.needsAnimationFrames() && !panel.needsDetailUpdates()) frames.stop();
        else frames.setDelay(panel.needsAnimationFrames() ? 16 : 1000);
    }

    private void onPanelChanged() {
        layout();
        if (!disposed && window != null && window.isVisible() && (panel.needsAnimationFrames() || panel.needsDetailUpdates())) {
            int delay = panel.needsAnimationFrames() ? 16 : 1000;
            boolean speedUp = delay < frames.getDelay();
            frames.setDelay(delay);
            if (!frames.isRunning() || speedUp) frames.restart();
        }
    }

    /** Explicit user opening is the only path which can request focus or choose an owner. */
    void showBeside(Rectangle anchorOnScreen) {
        if (disposed || deck.isEmpty()) return;
        ensureWindow();
        lifetime.open(anchorOnScreen);
        window.removeWindowFocusListener(focus);
        window.addWindowFocusListener(focus);
        layout();
        if (lifetime.takeFocusRequest() && window.isVisible()) {
            window.toFront(); window.requestFocus(); panel.requestFocusInWindow();
        }
    }

    void moveBeside(Rectangle anchorOnScreen) { lifetime.move(anchorOnScreen); layout(); }
    void refresh() { panel.refresh(); }

    void hide() {
        if (disposed) return;
        lifetime.close();
        frames.stop();
        if (window != null) {
            window.removeWindowFocusListener(focus);
            window.setVisible(false);
        }
        panel.reset();
    }

    void applyOptions(BuddyOptions options) {
        panel.applyOptions(options);
        layout();
    }

    void dispose() {
        if (disposed) return;
        disposed = true;
        frames.stop();
        lifetime.dispose();
        if (window != null) { window.removeWindowFocusListener(focus); window.dispose(); }
    }

    boolean isShowing() { return !disposed && window != null && window.isVisible(); }

    /**
     * Whether the drawer has anything to show. Extracted because the test JVM is headless and a
     * {@link JWindow} cannot be built there at all — a test that only skips is not coverage.
     */
    static boolean shows(boolean disposed, Rectangle anchor, boolean deckEmpty, Dimension size) {
        return !disposed && anchor != null && !deckEmpty && size.width > 0 && size.height > 0;
    }

    /** Shared by model refresh and anchor placement; headless-testable without constructing a window. */
    static Dimension reconcilePanel(BuddyDeckPanel panel) { return panel.reconcileSize(); }

    private void layout() {
        Rectangle anchor = anchor();
        if (disposed || anchor == null || window == null) return;
        Dimension size = reconcilePanel(panel);
        if (!shows(false, anchor, panel.displayed().isEmpty(), size)) { hide(); return; }
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
        Rectangle anchor = anchor();
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
