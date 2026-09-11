package dev.moray.app;

import dev.moray.terminal.TerminalOptions;
import dev.moray.terminal.TerminalSession;
import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.UncheckedIOException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.SwingUtilities;

/** Application-level window and shell ownership; closing a window never exits sibling windows. */
final class MorayApplication {
    private final ThemeController themes = new ThemeController();
    private final Set<TerminalWindow> windows = new LinkedHashSet<>();
    private final ExecutorService launches = Executors.newThreadPerTaskExecutor(
        Thread.ofPlatform().name("moray-shell-launch-", 0).factory());
    private final ShellLauncher launcher;
    private boolean quitting;

    MorayApplication() {
        var command = DefaultShell.command(System.getProperty("os.name"), System.getenv());
        launcher = new ShellLauncher(launches, directory -> {
            try {
                return TerminalSession.start(command, System.getenv(), directory, 120, 36,
                    TerminalOptions.defaults().scrollback());
            } catch (IOException e) { throw new UncheckedIOException(e); }
        }, Path.of(command.getFirst()).getFileName().toString());
        if (supportsNativeQuit()) Desktop.getDesktop().setQuitHandler((event, response) -> {
            // Cancel the native immediate JVM exit; pane close owns bounded child cleanup.
            response.cancelQuit(); SwingUtilities.invokeLater(this::quit);
        });
    }

    void newWindow(Path directory) {
        if (quitting) return;
        TerminalWindow window = new TerminalWindow(this, launcher, directory, themes);
        windows.add(window); window.show();
    }

    void windowClosed(TerminalWindow window) {
        windows.remove(window);
        if (windows.isEmpty()) shutdown();
    }

    void quit() {
        quitting = true;
        for (TerminalWindow window : List.copyOf(windows)) window.close();
        shutdown();
    }

    private void shutdown() {
        quitting = true;
        launches.shutdown();
        if (supportsNativeQuit()) Desktop.getDesktop().setQuitHandler(null);
    }

    private static boolean supportsNativeQuit() {
        return !GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported()
            && Desktop.getDesktop().isSupported(Desktop.Action.APP_QUIT_HANDLER);
    }
}
