package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.launch.ShellLauncher;
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
import java.nio.file.Path;
import javax.swing.*;

/** Native window boundary; all terminal behavior lives in the actual WindowContent component. */
public final class TerminalWindow implements AutoCloseable {
    private final WindowCallbacks callbacks;
    private final JFrame frame = new JFrame("Jasper");
    private final WindowContent content;
    private final MacTitleBar titleBar;
    private final WindowGlass glass;
    private boolean closed;
    private WindowPlacement placement;
    private final java.awt.event.ComponentAdapter geometry = new java.awt.event.ComponentAdapter() {
        @Override public void componentMoved(java.awt.event.ComponentEvent event) { rememberBounds(); }
        @Override public void componentResized(java.awt.event.ComponentEvent event) { rememberBounds(); }
    };
    private final WindowAdapter events = new WindowAdapter() {
        @Override public void windowClosing(WindowEvent event) { if (content.mayClose()) close(); }
        @Override public void windowActivated(WindowEvent event) { setActive(true); callbacks.activated().accept(TerminalWindow.this); }
        @Override public void windowDeactivated(WindowEvent event) { setActive(false); }
        @Override public void windowOpened(WindowEvent event) { reportState(); }
        @Override public void windowIconified(WindowEvent event) { reportState(); }
        @Override public void windowDeiconified(WindowEvent event) { reportState(); }
    };

    /** A window with one tab in {@code directory}, or a detached one with no tab when it is null. */
    public TerminalWindow(WindowCallbacks callbacks, ShellLauncher launcher, Path directory, ThemeController themes,
                   ConfigSnapshot initial, CommandHistory history) {
        this(callbacks, launcher, directory, directory == null, themes, initial, history);
    }

    /**
     * A {@code detached} window has no tab; any other starts one tab in {@code directory}, where null means where a
     * new shell starts (the user's home, which a WSL profile reads as its Linux home).
     */
    public TerminalWindow(WindowCallbacks callbacks, ShellLauncher launcher, Path directory, boolean detached,
                   ThemeController themes, ConfigSnapshot initial, CommandHistory history) {
        this(callbacks, launcher, directory, detached, themes, initial, history, WindowLook.standard());
    }

    /** As above, under {@code look}: its workspace chrome and its title bar (Enterprise presentation spec 9.1 and 10.1). */
    public TerminalWindow(WindowCallbacks callbacks, ShellLauncher launcher, Path directory, boolean detached,
                   ThemeController themes, ConfigSnapshot initial, CommandHistory history, WindowLook look) {
        this.callbacks = callbacks;
        frame.setIconImages(ApplicationIcon.images());
        content = new WindowContent(launcher, directory, detached, callbacks.newWindow(), callbacks.quit(), this::close, themes,
            KeyBindings.defaults(SystemInfo.isMacOS), history, SystemInfo.isMacOS, look.chrome());
        if (initial != null) {
            if (content.currentPane() != null) content.currentPane().setPreferredSize(InitialWindowSize.terminalArea(initial));
            content.applyConfiguration(initial, SystemInfo.isMacOS);
        }
        titleBar = WindowContent.mount(frame.getRootPane(), content, look, frame::setTitle);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        content.installRootBindings(frame.getRootPane());
        // Native initial focus must not fall through to the first restored tool-panel control.
        frame.setFocusTraversalPolicy(content.getFocusTraversalPolicy());
        content.onMinimumSizeChanged = this::updateMinimumSize;
        if (titleBar != null) titleBar.attach(frame);
        // Workspace glass exists only on macOS: elsewhere WindowGlass is never loaded and its requests are never made.
        if (SystemInfo.isMacOS && !GraphicsEnvironment.isHeadless()) {
            glass = new WindowGlass(frame.getRootPane(), WindowGlass.Platform.current(), content::applyBackgroundOpacity);
            content.setGlassRequests((levels, dark) -> glass.request(levels.opacity(), levels.blur(), dark));
        } else glass = null;
        frame.addWindowListener(events); frame.pack();
        if (initial != null) {
            updateMinimumSize();
            frame.setSize(InitialWindowSize.fit(frame.getSize(), frame.getMinimumSize(), usableBounds()));
        }
        frame.setLocationByPlatform(true);
        content.update();
    }

    /** Restore after configuration and tool panels are connected, before the native window is shown. */
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
        content.setActive(active);
        if (titleBar != null) titleBar.setActive(active);
    }

    private void reportState() {
        callbacks.stateChanged().accept(new WindowCallbacks.State(this, frame.isShowing(),
            (frame.getExtendedState() & java.awt.Frame.ICONIFIED) != 0));
    }

    public void toFront() {
        if ((frame.getExtendedState() & java.awt.Frame.ICONIFIED) != 0) frame.setExtendedState(frame.getExtendedState() & ~java.awt.Frame.ICONIFIED);
        frame.toFront(); frame.requestFocus();
        content.focusSelected();
    }

    /** Whether {@code component} is this window's frame or sits inside it; used to filter toolkit key events. */
    public boolean owns(Component component) {
        return component == frame || SwingUtilities.getWindowAncestor(component) == frame;
    }

    public WindowContent content() { return content; }
    public boolean closed() { return closed; }
    public Dimension size() { return frame.getSize(); }
    /** The native window, for parenting application-built dialogs; never exposed to extensions. */
    public java.awt.Window nativeWindow() { return frame; }
    public void resize(Dimension size) { frame.setSize(size); }

    public void show() { frame.setVisible(true); reportState(); content.focusSelected(); }

    /**
     * Closes this window without remembering it: neither its bounds nor its dock layout are saved, so a window that
     * failed to compose, or the warm-up rehearsal's (never shown, at the default size), never becomes the last window
     * closed, whose layout the next window restores. EDT.
     */
    public void discard() {
        if (closed) return;
        placement = null;
        content.forgetLayout();
        close();
    }

    @Override public void close() {
        if (closed) return;
        rememberBounds();
        closed = true;
        frame.removeComponentListener(geometry);
        // Save the intact dock first; plugin disposal callbacks must not change the closing window's snapshot.
        if (glass != null) glass.close();
        content.close();
        if (placement != null) placement.save(frame.getExtendedState());
        if (titleBar != null) titleBar.close();
        frame.removeWindowListener(events); frame.dispose();
        callbacks.closed().accept(this);
    }
}
