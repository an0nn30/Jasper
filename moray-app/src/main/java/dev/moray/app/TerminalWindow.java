package dev.moray.app;

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
        @Override public void windowActivated(WindowEvent event) { content.setActive(true); }
        @Override public void windowDeactivated(WindowEvent event) { content.setActive(false); }
    };

    TerminalWindow(MorayApplication application, ShellLauncher launcher, Path directory) {
        this.application = application;
        content = new WindowContent(launcher, directory, application::newWindow, application::quit, this::close);
        content.onTitle = title -> frame.setTitle(Main.windowTitle(title));
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setContentPane(content); frame.setJMenuBar(content.menuBar());
        frame.addWindowListener(events); frame.pack(); frame.setLocationByPlatform(true);
        content.update();
    }

    void show() { frame.setVisible(true); if (content.currentTab() != null) content.currentTab().focusTerminal(); }

    @Override public void close() {
        if (closed) return;
        closed = true; content.close(); frame.removeWindowListener(events); frame.dispose();
        application.windowClosed(this);
    }
}
