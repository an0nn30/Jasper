package dev.moray.app;

import java.nio.file.Path;
import javax.swing.SwingUtilities;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        SwingUtilities.invokeLater(() -> {
            new MorayApplication().newWindow(Path.of(System.getProperty("user.home")));
        });
    }

    /** The window title for a shell-reported title; "Moray" when the shell has not set one. */
    static String windowTitle(String shellTitle) {
        return shellTitle == null || shellTitle.isBlank() ? "Moray" : shellTitle;
    }
}
