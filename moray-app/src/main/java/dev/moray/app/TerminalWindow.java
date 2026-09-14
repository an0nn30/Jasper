package dev.moray.app;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import javax.swing.*;

/** Native window boundary; all terminal behavior lives in the actual WindowContent component. */
final class TerminalWindow implements AutoCloseable {
    private final MorayApplication application;
    private final JFrame frame = new JFrame("Moray");
    private final WindowContent content;
    private boolean closed;
    private final WindowAdapter events = new WindowAdapter() {
        @Override public void windowClosing(WindowEvent event) { close(); }
        @Override public void windowActivated(WindowEvent event) { setActive(true); }
        @Override public void windowDeactivated(WindowEvent event) { setActive(false); }
    };

    TerminalWindow(MorayApplication application, ShellLauncher launcher, Path directory, ThemeController themes) {
        this(application, launcher, directory, themes, null);
    }

    TerminalWindow(MorayApplication application, ShellLauncher launcher, Path directory, ThemeController themes,
                   ConfigurationController configuration) {
        this(application, launcher, directory, themes, configuration, new CommandHistory());
    }

    TerminalWindow(MorayApplication application, ShellLauncher launcher, Path directory, ThemeController themes,
                   ConfigurationController configuration, CommandHistory history) {
        this.application = application;
        frame.setIconImages(ApplicationIcon.images(System.getProperty("os.name").startsWith("Mac")));
        content = new WindowContent(launcher, directory, application::newWindow, application::quit, this::close, themes,
            KeyBindings.defaults(System.getProperty("os.name").startsWith("Mac")), System::nanoTime, history, System.getProperty("os.name").startsWith("Mac"));
        if (configuration != null) {
            content.currentPane().setPreferredSize(InitialWindowSize.terminalArea(configuration.snapshot()));
            configuration.register(content);
        }
        frame.setContentPane(content);
        content.onTitle = title -> frame.setTitle(Main.windowTitle(title));
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setJMenuBar(content.menuBar());
        content.installRootBindings(frame.getRootPane());
        content.onMinimumSizeChanged = this::updateMinimumSize;
        frame.addWindowListener(events); frame.pack();
        if (configuration != null) {
            updateMinimumSize();
            frame.setSize(InitialWindowSize.fit(frame.getSize(), frame.getMinimumSize(), usableBounds()));
        }
        frame.setLocationByPlatform(true);
        content.update();
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
        var graphics = frame.getGraphicsConfiguration();
        Rectangle bounds = new Rectangle(graphics.getBounds());
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(graphics);
        bounds.x += insets.left; bounds.y += insets.top;
        bounds.width -= insets.left + insets.right;
        bounds.height -= insets.top + insets.bottom;
        return bounds;
    }

    private void setActive(boolean active) {
        content.setActive(active);
    }

    WindowContent content() { return content; }
    boolean closed() { return closed; }
    Dimension size() { return frame.getSize(); }
    void resize(Dimension size) { frame.setSize(size); }

    void show() { frame.setVisible(true); if (content.currentTab() != null) content.currentTab().focusTerminal(); }

    @Override public void close() {
        if (closed) return;
        closed = true; content.close();
        frame.removeWindowListener(events); frame.dispose();
        application.windowClosed(this);
    }
}
