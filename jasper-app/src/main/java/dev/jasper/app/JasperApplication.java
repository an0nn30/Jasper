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
    /** Upper bound on waiting for closed shells and the history file before the JVM is terminated. */
    private static final long EXIT_GRACE_MILLIS = 2_000;
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
    private final Runnable terminate;
    private final Set<TerminalSession> sessions = ConcurrentHashMap.newKeySet();
    private final BuddyVisibility buddyVisibility = new BuddyVisibility();
    private final Path buddyStateFile;
    private BuddyWindow buddy;
    private boolean buddyUnavailable;
    private TerminalWindow lastActive;

    JasperApplication() { this(null); }

    JasperApplication(ConfigService service) { this(service, null); }

    /** Isolated launch ownership for controlled tools; production still resolves captured settings. */
    JasperApplication(ConfigService service, ShellLauncher suppliedLauncher) {
        this(service, suppliedLauncher, new CommandHistory());
    }

    JasperApplication(ConfigService service, ShellLauncher suppliedLauncher, CommandHistory history) {
        this(service, suppliedLauncher, history, null);
    }

    JasperApplication(ConfigService service, ShellLauncher suppliedLauncher, CommandHistory history, Path buddyStateFile) {
        this(service, suppliedLauncher, history, buddyStateFile, () -> {});
    }

    /**
     * {@code buddyStateFile} may be null: the buddy then starts in the default corner and forgets drags.
     * {@code terminate} runs once, off the EDT, after shutdown's bounded cleanup; production passes the JVM exit.
     */
    JasperApplication(ConfigService service, ShellLauncher suppliedLauncher, CommandHistory history, Path buddyStateFile,
                      Runnable terminate) {
        this.history = history;
        this.suppliedLauncher = suppliedLauncher;
        this.buddyStateFile = buddyStateFile;
        this.terminate = terminate;
        configuration = service == null ? null : new ConfigurationController(themes, service);
        if (configuration != null) configuration.onSnapshot(snapshot -> {
            buddyVisibility.configure(snapshot.buddyEnabled()); syncBuddy();
        });
        if (supportsNativeQuit()) Desktop.getDesktop().setQuitHandler((event, response) -> {
            // Cancel the native immediate JVM exit; pane close owns bounded child cleanup.
            response.cancelQuit(); SwingUtilities.invokeLater(this::quit);
        });
    }

    TerminalWindow newWindow(Path directory) {
        if (quitting) return null;
        ShellLauncher launcher = suppliedLauncher != null ? suppliedLauncher : windowLauncher(launches,
            configuration == null ? ConfigSnapshot::defaults : configuration::snapshot,
            (path, settings) -> track(startSession(path, settings)));
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

    /** Remembers a shell so shutdown can wait for it to leave before terminating the JVM. */
    TerminalSession track(TerminalSession session) {
        sessions.add(session);
        session.exitFuture().whenComplete((code, error) -> sessions.remove(session));
        return session;
    }

    void windowClosed(TerminalWindow window) {
        windows.remove(window);
        buddyVisibility.remove(window);
        if (lastActive == window) lastActive = null;
        if (windows.isEmpty()) requestShutdown();
        else syncBuddy();
    }

    void windowStateChanged(TerminalWindow window, boolean showing, boolean iconified) {
        if (!windows.contains(window)) return;
        buddyVisibility.window(window, showing, iconified);
        syncBuddy();
    }

    void windowActivated(TerminalWindow window) { if (windows.contains(window)) lastActive = window; }

    void toggleBuddy() { buddyVisibility.toggle(); syncBuddy(); }

    boolean buddyEnabled() { return buddyVisibility.enabled(); }

    private void raiseTerminal() {
        TerminalWindow target = lastActive != null && windows.contains(lastActive) ? lastActive
            : windows.isEmpty() ? null : windows.iterator().next();
        if (target != null) target.toFront();
    }

    private void syncBuddy() {
        if (quitting || stopped) return;
        if (!buddyUnavailable) {
            try {
                if (buddyVisibility.shown()) {
                    if (buddy == null) {
                        buddy = BuddyWindow.create(buddyStateFile, this::raiseTerminal, this::toggleBuddy);
                        if (buddy == null) buddyUnavailable = true;
                    }
                    if (buddy != null) buddy.show();
                } else if (buddy != null) buddy.hide();
            } catch (RuntimeException failure) {
                buddyUnavailable = true;
                if (buddy != null) {
                    try { buddy.dispose(); } catch (RuntimeException ignored) { }
                }
                buddy = null;
                LOG.log(System.Logger.Level.WARNING, "Desk buddy disabled for this session", failure);
            }
        }
        for (TerminalWindow window : List.copyOf(windows)) window.content().updateActions();
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
        if (buddy != null) buddy.dispose();
        history.close();
        if (configuration != null) configuration.close();
        if (supportsNativeQuit()) Desktop.getDesktop().setQuitHandler(null);
        // Nothing else ends the JVM: without an explicit exit, AWT waits a full quiet second before it lets go.
        long started = System.nanoTime();
        List<CompletableFuture<?>> pending = new ArrayList<>();
        pending.add(history.closedFuture());
        for (TerminalSession session : List.copyOf(sessions)) pending.add(session.exitFuture());
        CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new))
            .orTimeout(EXIT_GRACE_MILLIS, TimeUnit.MILLISECONDS)
            .whenComplete((ignored, failure) -> Thread.ofPlatform().name("jasper-exit").start(() -> {
                LOG.log(System.Logger.Level.INFO, "Shutdown finished in " + (System.nanoTime() - started) / 1_000_000
                    + " ms" + (failure == null ? "" : " (cleanup timed out)"));
                terminate.run();
            }));
    }

    private static boolean supportsNativeQuit() {
        return !GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported()
            && Desktop.getDesktop().isSupported(Desktop.Action.APP_QUIT_HANDLER);
    }
}
