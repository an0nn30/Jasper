package dev.moray.app;

import dev.moray.terminal.TerminalOptions;
import dev.moray.terminal.TerminalSession;
import dev.moray.terminal.TerminalView;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        TerminalOptions options = TerminalOptions.defaults();
        TerminalSession session = startShellOrExit(options);
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
                    SwingUtilities.invokeLater(() -> frame.setTitle(windowTitle(title)));
                }
            });
            frame.setTitle(windowTitle(session.title())); // a title the shell set before the listener existed
            frame.add(view);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            view.requestFocusInWindow();
        });
    }

    /** The window title for a shell-reported title; "Moray" when the shell has not set one. */
    static String windowTitle(String shellTitle) {
        return shellTitle == null || shellTitle.isBlank() ? "Moray" : shellTitle;
    }

    private static TerminalSession startShellOrExit(TerminalOptions options) throws Exception {
        try {
            return TerminalSession.start(
                DefaultShell.command(System.getProperty("os.name"), System.getenv()),
                System.getenv(), Path.of(System.getProperty("user.home")), 120, 36, options.scrollback());
        } catch (IOException e) {
            SwingUtilities.invokeAndWait(() -> JOptionPane.showMessageDialog(null,
                "Moray could not start your shell:\n" + e.getMessage(), "Moray", JOptionPane.ERROR_MESSAGE));
            System.exit(1);
            throw e; // not reached: System.exit does not return
        }
    }
}
