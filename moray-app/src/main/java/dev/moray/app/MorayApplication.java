package dev.moray.app;

import dev.moray.terminal.TerminalSession;
import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.UncheckedIOException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/** Application-level window and shell ownership; closing a window never exits sibling windows. */
final class MorayApplication {
    private final ThemeController themes = new ThemeController();
    private final Set<TerminalWindow> windows = new LinkedHashSet<>();
    private final ExecutorService launches = Executors.newThreadPerTaskExecutor(
        Thread.ofPlatform().name("moray-shell-launch-", 0).factory());
    private final ConfigurationController configuration;
    private boolean quitting;

    MorayApplication() { this(null); }

    MorayApplication(ConfigService service) {
        configuration = service == null ? null : new ConfigurationController(themes, service);
        if (supportsNativeQuit()) Desktop.getDesktop().setQuitHandler((event, response) -> {
            // Cancel the native immediate JVM exit; pane close owns bounded child cleanup.
            response.cancelQuit(); SwingUtilities.invokeLater(this::quit);
        });
    }

    void newWindow(Path directory) {
        if (quitting) return;
        ShellLauncher launcher = windowLauncher(launches,
            configuration == null ? ConfigSnapshot::defaults : configuration::snapshot, MorayApplication::startSession);
        TerminalWindow window = new TerminalWindow(this, launcher, directory, themes, configuration);
        windows.add(window); window.show();
    }

    static ShellLauncher windowLauncher(Executor executor, Supplier<ConfigSnapshot> snapshots,
                                        BiFunction<Path, LaunchSettings, TerminalSession> start) {
        ConfigSnapshot initial = snapshots.get();
        return new ShellLauncher(executor, () -> LaunchSettings.resolve(snapshots.get(),
            System.getProperty("os.name"), System.getenv(), initial.columns(), initial.lines()), start);
    }

    private static TerminalSession startSession(Path directory, LaunchSettings settings) {
        try {
            return TerminalSession.start(settings.command(), settings.environment(), directory,
                settings.columns(), settings.lines(), settings.scrollback());
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
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
        if (configuration != null) configuration.close();
        if (supportsNativeQuit()) Desktop.getDesktop().setQuitHandler(null);
    }

    private static boolean supportsNativeQuit() {
        return !GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported()
            && Desktop.getDesktop().isSupported(Desktop.Action.APP_QUIT_HANDLER);
    }
}
