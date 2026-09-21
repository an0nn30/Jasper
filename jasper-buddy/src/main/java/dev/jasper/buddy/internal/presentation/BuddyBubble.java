package dev.jasper.buddy.internal.presentation;

import dev.jasper.buddy.config.BuddyOptions;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Objects;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** The bubble's window: translucent, always on top and never focused, so it dismisses on pointer exit or a timeout. */
final class BuddyBubble {
    static final int AUTO_HIDE_MILLIS = 5_000;

    private final JWindow window = new JWindow();
    private final BuddyBubblePanel panel;
    private final Timer autoHide;
    private final Runnable onActivate;
    private boolean disposed;

    BuddyBubble(BuddyOptions options, Runnable onActivate) {
        panel = new BuddyBubblePanel(options);
        this.onActivate = Objects.requireNonNull(onActivate, "onActivate");
        autoHide = new Timer(AUTO_HIDE_MILLIS, event -> hide());
        autoHide.setRepeats(false);
        try {
            window.setType(Window.Type.UTILITY);
            window.setAlwaysOnTop(true);
            window.setFocusableWindowState(false);
            window.setBackground(new Color(0, 0, 0, 0));
            window.setContentPane(panel);
            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent event) { panel.setHighlighted(true); }
                @Override public void mouseExited(MouseEvent event) { hide(); }
                // A macOS control-click is the popup trigger yet reports the left button; it must not activate.
                @Override public void mouseClicked(MouseEvent event) {
                    if (!SwingUtilities.isLeftMouseButton(event) || event.isControlDown()) return;
                    hide();
                    onActivate.run();
                }
            };
            panel.addMouseListener(mouse);
        } catch (RuntimeException | Error failure) {
            try { window.dispose(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    void showBeside(Rectangle anchorOnScreen, BuddyBubbleContent content) {
        if (disposed) return;
        panel.setContent(content);
        panel.setHighlighted(false);
        Dimension size = panel.getPreferredSize();
        window.setSize(size);
        window.setLocation(BuddyBubblePlacement.beside(anchorOnScreen, size, screenFor(anchorOnScreen)));
        window.setVisible(true);
        autoHide.restart();
    }

    void hide() {
        autoHide.stop();
        panel.setHighlighted(false);
        window.setVisible(false);
    }

    void applyOptions(BuddyOptions options) {
        panel.applyOptions(options);
        window.pack();
    }

    void dispose() {
        if (disposed) return;
        disposed = true;
        autoHide.stop();
        window.dispose();
    }

    boolean isShowing() { return window.isVisible(); }

    /** The usable screen holding the anchor's centre, else the default one, else a plausible fallback. */
    private static Rectangle screenFor(Rectangle anchor) {
        List<Rectangle> screens = BuddyWindow.usableScreens();
        if (screens.isEmpty()) return new Rectangle(0, 0, 1280, 800);
        for (Rectangle screen : screens) {
            if (screen.contains((int) anchor.getCenterX(), (int) anchor.getCenterY())) return screen;
        }
        return screens.getFirst();
    }
}
