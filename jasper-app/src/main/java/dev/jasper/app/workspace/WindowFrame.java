package dev.jasper.app.workspace;

import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.platform.ApplicationIcon;
import dev.jasper.app.platform.MacTitleBar;
import dev.jasper.app.platform.WindowGlass;
import dev.jasper.app.persistence.UiState;
import com.formdev.flatlaf.util.SystemInfo;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.*;

/**
 * The native frame of one Jasper window (workspace tabs spec 6.2 and 6.3), around its {@link WindowContent}: the main
 * window's with the look's title bar, header and menus; a child window's with a title bar and the active tab's title, the
 * screen menu bar's menus on macOS while the look shows them there, and no menu row anywhere else. Both take the Keymap
 * on their own root, the command palette and workspace glass. Everything testable lives in {@link JasperWindow} and
 * {@link WindowManager}; this is never constructed in headless tests.
 */
public final class WindowFrame {
    private final JFrame frame = new JFrame("Jasper");
    private final WindowContent content;
    private final JasperWindow.Events events;
    private final MacTitleBar titleBar;
    private final WindowGlass glass;
    private boolean closed;
    private WindowPlacement placement;
    private final java.awt.event.ComponentAdapter geometry = new java.awt.event.ComponentAdapter() {
        @Override public void componentMoved(java.awt.event.ComponentEvent event) { rememberBounds(); }
        @Override public void componentResized(java.awt.event.ComponentEvent event) { rememberBounds(); }
    };
    private final WindowAdapter listener = new WindowAdapter() {
        @Override public void windowClosing(WindowEvent event) { events.closing().run(); }
        @Override public void windowActivated(WindowEvent event) { setActive(true); }
        @Override public void windowDeactivated(WindowEvent event) { setActive(false); }
        @Override public void windowOpened(WindowEvent event) { reportState(); }
        @Override public void windowIconified(WindowEvent event) { reportState(); }
        @Override public void windowDeiconified(WindowEvent event) { reportState(); }
    };

    /**
     * The frame around {@code content}, sized for {@code initial}'s grid when it is given, not yet shown. When building
     * it fails, what was built goes (the title bar, the glass and the frame) and the failure propagates, the cleanup's
     * under it.
     *
     * @param content the window's content; its role decides the frame's chrome
     * @param look the look's chrome and title facts
     * @param initial the configuration a new window takes, or null
     * @param events what the frame tells its window
     */
    public WindowFrame(WindowContent content, WindowLook look, ConfigSnapshot initial, JasperWindow.Events events) {
        this.content = java.util.Objects.requireNonNull(content, "content");
        this.events = java.util.Objects.requireNonNull(events, "events");
        MacTitleBar bar = null;
        WindowGlass made = null;
        try {
            frame.setIconImages(ApplicationIcon.images());
            if (initial != null) {
                // An empty window (a moved tab's) gives up its stand-in size, so its workspace takes
                // the grid's (Rulings 18).
                if (content.currentPane() == null) content.setPreferredSize(null);
                JComponent sized = content.currentPane() != null ? content.currentPane() : content.workspace();
                sized.setPreferredSize(InitialWindowSize.terminalArea(initial));
            }
            bar = content.isMain() ? WindowContent.mount(frame.getRootPane(), content, look, frame::setTitle)
                : WindowContent.mountChild(frame.getRootPane(), content, look, screenMenuBar(), frame::setTitle);
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            content.installRootBindings(frame.getRootPane());
            // Native initial focus must not fall through to the first restored tool-panel control.
            frame.setFocusTraversalPolicy(content.getFocusTraversalPolicy());
            content.onMinimumSizeChanged = this::updateMinimumSize;
            if (bar != null) bar.attach(frame);
            // Workspace glass exists only on macOS: elsewhere WindowGlass is never loaded and its
            // requests are never made.
            if (SystemInfo.isMacOS && !GraphicsEnvironment.isHeadless()) {
                made = new WindowGlass(frame.getRootPane(), WindowGlass.Platform.current(),
                    content::applyBackgroundOpacity);
                WindowGlass requested = made;
                content.setGlassRequests((levels, dark) -> requested.request(levels.opacity(), levels.blur(), dark));
            }
            frame.addWindowListener(listener); frame.pack();
            if (initial != null) {
                updateMinimumSize();
                frame.setSize(InitialWindowSize.fit(frame.getSize(), frame.getMinimumSize(), usableBounds()));
            }
            frame.setLocationByPlatform(true);
            content.update();
        } catch (RuntimeException | Error failure) {
            // No window gets this frame's shell to dispose it through: it goes now, with what it made.
            if (made != null) {
                try { made.close(); } catch (RuntimeException | Error closing) { failure.addSuppressed(closing); }
            }
            if (bar != null) {
                try { bar.close(); } catch (RuntimeException | Error closing) { failure.addSuppressed(closing); }
            }
            try {
                frame.removeWindowListener(listener);
                frame.dispose();
            } catch (RuntimeException | Error disposing) {
                failure.addSuppressed(disposing);
            }
            throw failure;
        }
        titleBar = bar;
        glass = made;
    }

    /** Whether macOS shows this process's menus in the screen menu bar, as the look committed before AWT started. */
    private static boolean screenMenuBar() {
        return SystemInfo.isMacOS && Boolean.getBoolean("apple.laf.useScreenMenuBar");
    }

    /** The main window's bounds from {@code state}, checked against the current displays; before the frame shows. */
    public void restoreLayout(UiState state) {
        placement = new WindowPlacement(state);
        updateMinimumSize();
        var screens = new java.util.ArrayList<Rectangle>();
        var environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        screens.add(usableBounds(environment.getDefaultScreenDevice().getDefaultConfiguration()));
        for (var device : environment.getScreenDevices()) {
            Rectangle bounds = usableBounds(device.getDefaultConfiguration());
            if (!screens.contains(bounds)) screens.add(bounds);
        }
        placement.restore(frame.getMinimumSize(), screens).ifPresent(frame::setBounds);
        placement.observe(frame.getBounds(), java.awt.Frame.NORMAL);
        frame.setExtendedState(placement.extendedState());
        frame.addComponentListener(geometry);
    }

    /** @return what this frame does for its window */
    public WindowShell shell() {
        return new WindowShell(this::show, this::toFront, this::dispose, this::discard, () -> frame, this::owns, frame::setBounds);
    }

    /** @return the native window, for painting the warm-up rehearsal */
    public java.awt.Window nativeWindow() { return frame; }

    private void rememberBounds() {
        if (!closed && placement != null && frame.isShowing()) placement.observe(frame.getBounds(), frame.getExtendedState());
    }

    static Dimension minimumSize(JRootPane root, Insets decorations) {
        Dimension contentMinimum = root.getMinimumSize();
        return new Dimension(contentMinimum.width + decorations.left + decorations.right,
            contentMinimum.height + decorations.top + decorations.bottom);
    }

    private void updateMinimumSize() {
        Dimension minimum = minimumSize(frame.getRootPane(), frame.getInsets());
        minimum = InitialWindowSize.fit(minimum, minimum, usableBounds());
        if (!frame.isMinimumSizeSet() || !minimum.equals(frame.getMinimumSize())) frame.setMinimumSize(minimum);
    }

    private Rectangle usableBounds() {
        return usableBounds(frame.getGraphicsConfiguration());
    }

    private static Rectangle usableBounds(java.awt.GraphicsConfiguration graphics) {
        Rectangle bounds = new Rectangle(graphics.getBounds());
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(graphics);
        bounds.x += insets.left; bounds.y += insets.top;
        bounds.width -= insets.left + insets.right;
        bounds.height -= insets.top + insets.bottom;
        return bounds;
    }

    private void setActive(boolean active) {
        if (titleBar != null) titleBar.setActive(active);
        events.activated().accept(active);
    }

    private void reportState() {
        events.state().accept(frame.isShowing(), (frame.getExtendedState() & java.awt.Frame.ICONIFIED) != 0);
    }

    private void toFront() {
        if ((frame.getExtendedState() & java.awt.Frame.ICONIFIED) != 0) frame.setExtendedState(frame.getExtendedState() & ~java.awt.Frame.ICONIFIED);
        frame.toFront(); frame.requestFocus();
    }

    private boolean owns(Component component) {
        return component == frame || SwingUtilities.getWindowAncestor(component) == frame;
    }

    private void show() { frame.setVisible(true); reportState(); content.focusSelected(); }

    /** Disposes the frame without remembering its bounds: its window failed to open, or it is the rehearsal's. */
    public void discard() {
        placement = null;
        dispose();
    }

    /** After the content closed: remembers the main window's bounds, then disposes the frame. */
    private void dispose() {
        if (closed) return;
        rememberBounds();
        closed = true;
        frame.removeComponentListener(geometry);
        if (glass != null) glass.close();
        if (placement != null) placement.save(frame.getExtendedState());
        if (titleBar != null) titleBar.close();
        frame.removeWindowListener(listener); frame.dispose();
    }
}
