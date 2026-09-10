package dev.moray.app;

import dev.moray.terminal.TerminalOptions;
import dev.moray.terminal.TerminalSession;
import dev.moray.terminal.TerminalView;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        TerminalOptions options = TerminalOptions.defaults();
        TerminalSession session = TerminalSession.start(
            DefaultShell.command(System.getProperty("os.name"), System.getenv()),
            System.getenv(), Path.of(System.getProperty("user.home")), 120, 36, options.scrollback());
        session.exitFuture().thenAccept(code -> SwingUtilities.invokeLater(() -> System.exit(0)));

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Moray");
            TerminalView view = new TerminalView(session, options);
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    session.close();
                    System.exit(0);
                }
            });
            session.addListener(new TerminalSession.Listener() {
                @Override
                public void titleChanged(String title) {
                    SwingUtilities.invokeLater(() -> frame.setTitle(title.isBlank() ? "Moray" : title));
                }
            });
            frame.add(view);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            view.requestFocusInWindow();
        });
    }
}
