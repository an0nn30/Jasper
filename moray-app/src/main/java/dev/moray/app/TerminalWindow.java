package dev.moray.app;

import com.formdev.flatlaf.util.SystemInfo;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import javax.swing.*;

/** Native window boundary; all terminal behavior lives in the actual WindowContent component. */
final class TerminalWindow implements AutoCloseable {
    private final MorayApplication application;
    private final JFrame frame = new JFrame("Moray");
    private final WindowContent content;
    private final MacTitleBar titleBar;
    private boolean closed;
    private final WindowAdapter events = new WindowAdapter() {
        @Override public void windowClosing(WindowEvent event) { close(); }
        @Override public void windowActivated(WindowEvent event) { setActive(true); }
        @Override public void windowDeactivated(WindowEvent event) { setActive(false); }
    };

    TerminalWindow(MorayApplication application, ShellLauncher launcher, Path directory, ThemeController themes) {
        this.application = application;
        content = new WindowContent(launcher, directory, application::newWindow, application::quit, this::close, themes);
        titleBar = MacTitleBar.install(frame.getRootPane(), content, SystemInfo.isMacFullWindowContentSupported, frame::setTitle);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setJMenuBar(content.menuBar());
        content.installRootBindings(frame.getRootPane());
        content.onMinimumSizeChanged = this::updateMinimumSize;
        if (titleBar != null) titleBar.attach(frame);
        frame.addWindowListener(events); frame.pack(); frame.setLocationByPlatform(true);
        content.update();
    }

    static Dimension minimumSize(JRootPane root, Insets decorations) {
        Dimension contentMinimum = root.getMinimumSize();
        return new Dimension(contentMinimum.width + decorations.left + decorations.right,
            contentMinimum.height + decorations.top + decorations.bottom);
    }

    private void updateMinimumSize() {
        Dimension minimum = minimumSize(frame.getRootPane(), frame.getInsets());
        if (!frame.isMinimumSizeSet() || !minimum.equals(frame.getMinimumSize())) frame.setMinimumSize(minimum);
    }

    private void setActive(boolean active) {
        content.setActive(active);
        if (titleBar != null) titleBar.setActive(active);
    }

    void show() { frame.setVisible(true); if (content.currentTab() != null) content.currentTab().focusTerminal(); }

    @Override public void close() {
        if (closed) return;
        closed = true; content.close();
        if (titleBar != null) titleBar.close();
        frame.removeWindowListener(events); frame.dispose();
        application.windowClosed(this);
    }
}
