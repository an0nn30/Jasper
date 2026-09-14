package dev.jasper.app;

import dev.jasper.terminal.TerminalSession;
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
final class JasperApplication {
    private static final System.Logger LOG = System.getLogger(JasperApplication.class.getName());
    private final ThemeController themes = new ThemeController();
    private final Set<TerminalWindow> windows = new LinkedHashSet<>();
    private final ExecutorService launches = Executors.newThreadPerTaskExecutor(
        Thread.ofPlatform().name("jasper-shell-launch-", 0).factory());
    private final ConfigurationController configuration;
    private boolean quitting;
    private boolean shutdownQueued;
    private boolean stopped;
    private final CommandHistory history;
    private final ShellLauncher suppliedLauncher;

    JasperApplication() { this(null); }

    JasperApplication(ConfigService service) { this(service, SystemAppearance.fixed(BuiltinTheme.DARK)); }

    JasperApplication(ConfigService service, SystemAppearance source) { this(service, source, null); }

    /** Isolated launch ownership for controlled tools; production still resolves captured settings. */
    JasperApplication(ConfigService service, SystemAppearance source, ShellLauncher suppliedLauncher) {
        this(service, source, suppliedLauncher, new CommandHistory());
    }

    JasperApplication(ConfigService service, SystemAppearance source, ShellLauncher suppliedLauncher, CommandHistory history) {
        this.history = history;
        this.suppliedLauncher = suppliedLauncher;
        configuration = service == null ? null : new ConfigurationController(themes, service, source);
        if (service == null) source.close();
        if (supportsNativeQuit()) Desktop.getDesktop().setQuitHandler((event, response) -> {
            // Cancel the native immediate JVM exit; pane close owns bounded child cleanup.
            response.cancelQuit(); SwingUtilities.invokeLater(this::quit);
        });
    }

    TerminalWindow newWindow(Path directory) {
        if (quitting) return null;
        ShellLauncher launcher = suppliedLauncher != null ? suppliedLauncher : windowLauncher(launches,
            configuration == null ? ConfigSnapshot::defaults : configuration::snapshot, JasperApplication::startSession);
        TerminalWindow window = new TerminalWindow(this, launcher, directory, themes, configuration, history);
        windows.add(window); window.show();
        return window;
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
        } catch (IOException failure) {
            LOG.log(System.Logger.Level.ERROR, "Shell launch failed", failure);
            throw new UncheckedIOException(failure);
        }
    }

    void windowClosed(TerminalWindow window) {
        windows.remove(window);
        if (windows.isEmpty()) requestShutdown();
    }

    void quit() {
        quitting = true;
        for (TerminalWindow window : List.copyOf(windows)) window.close();
        requestShutdown();
    }

    private void requestShutdown() {
        quitting = true;
        if (shutdownQueued || stopped) return;
        shutdownQueued = true;
        // Accepted palette actions record only after dispatch, including Quit/last-tab close.
        SwingUtilities.invokeLater(this::shutdown);
    }

    private void shutdown() {
        if (stopped) return;
        stopped = true;
        quitting = true;
        launches.shutdown();
        history.close();
        if (configuration != null) configuration.close();
        if (supportsNativeQuit()) Desktop.getDesktop().setQuitHandler(null);
    }

    private static boolean supportsNativeQuit() {
        return !GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported()
            && Desktop.getDesktop().isSupported(Desktop.Action.APP_QUIT_HANDLER);
    }
}
