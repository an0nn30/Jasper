package dev.jasper.app.application;

import dev.jasper.app.appearance.BuiltinTheme;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.FontConfig;
import dev.jasper.app.history.CommandHistory;
import dev.jasper.app.history.ShellHistoryIndex;
import dev.jasper.app.launch.LaunchSettings;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.notifications.ActivityNotifier;
import dev.jasper.app.notifications.CommandNotice;
import dev.jasper.app.notifications.CommandNotifier;
import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.platform.NativeNotifier;
import dev.jasper.app.plugins.PluginRuntime;
import dev.jasper.app.platform.SystemFonts;
import dev.jasper.app.snippets.SnippetStore;
import dev.jasper.app.workspace.TerminalWindow;
import dev.jasper.app.workspace.WindowCallbacks;
import dev.jasper.app.workspace.WorkspaceActivity;
import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.rendering.FontSet;
import dev.jasper.terminal.session.SessionLaunchOptions;

import dev.jasper.terminal.session.TerminalSession;
import java.awt.Component;
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
public final class JasperApplication {
    private static final System.Logger LOG = System.getLogger(JasperApplication.class.getName());
    private final ThemeController themes = new ThemeController();
    private final Set<TerminalWindow> windows = new LinkedHashSet<>();
    private final SessionLaunchCoordinator launches = new SessionLaunchCoordinator(Executors.newThreadPerTaskExecutor(
        Thread.ofPlatform().name("jasper-shell-launch-", 0).factory()));
    private final ConfigurationController configuration;
    private boolean quitting;
    private boolean resident;
    private java.util.function.Consumer<Boolean> loginItems = enabled -> { };
    private boolean shutdownQueued;
    private boolean stopped;
    private final java.util.List<Runnable> shutdownActions = new java.util.ArrayList<>();
    private java.awt.desktop.AppReopenedListener reopenListener;
    private boolean quitHandlerInstalled;
    private final CommandHistory history;
    private final ShellLauncher suppliedLauncher;
    private final ApplicationShutdown shutdown;
    private final ShellHistoryIndex shellHistory;
    private final SnippetStore snippets;
    private final Path shellIntegrationDir;
    private final BuddyIntegration buddy;
    private final dev.jasper.app.lifecycle.Subscription buddyAppearance;
    private final NativeNotifier nativeNotifier = new NativeNotifier();
    private final CommandNotifier notifications;
    private PluginRuntime plugins;
    private ActivityNotifier activityNotifier;
    private dev.jasper.app.lifecycle.Subscription pluginTheme;

    private int configuredLongCommandSeconds() {
        return configuration == null ? 10 : configuration.snapshot().longCommandSeconds();
    }
    private TerminalWindow lastActive;

    public JasperApplication() { this(null); }

    public JasperApplication(ConfigService service) { this(service, null); }

    /** Isolated launch ownership for controlled tools; production still resolves captured settings. */
    public JasperApplication(ConfigService service, ShellLauncher suppliedLauncher) {
        this(service, suppliedLauncher, new CommandHistory());
    }

    public JasperApplication(ConfigService service, ShellLauncher suppliedLauncher, CommandHistory history) {
        this(service, suppliedLauncher, history, null);
    }

    public JasperApplication(ConfigService service, ShellLauncher suppliedLauncher, CommandHistory history, Path buddyStateFile) {
        this(service, suppliedLauncher, history, buddyStateFile, () -> {});
    }

    public JasperApplication(ConfigService service, ShellLauncher suppliedLauncher, CommandHistory history, Path buddyStateFile,
                      Runnable terminate) {
        this(service, suppliedLauncher, history, buddyStateFile, terminate, new ShellHistoryIndex(List.of()));
    }

    public JasperApplication(ConfigService service, ShellLauncher suppliedLauncher, CommandHistory history, Path buddyStateFile,
                      Runnable terminate, ShellHistoryIndex shellHistory) {
        this(service, suppliedLauncher, history, buddyStateFile, terminate, shellHistory, null);
    }

    public JasperApplication(ConfigService service, ShellLauncher suppliedLauncher, CommandHistory history, Path buddyStateFile,
                      Runnable terminate, ShellHistoryIndex shellHistory, SnippetStore snippets) {
        this(service, suppliedLauncher, history, buddyStateFile, terminate, shellHistory, snippets, null);
    }

    /**
     * {@code buddyStateFile} may be null: the buddy then starts in the default corner and forgets drags.
     * {@code terminate} runs once, off the EDT, after shutdown's bounded cleanup; production passes the JVM exit.
     * {@code shellIntegrationDir} is the extracted script directory, or null when extraction failed or tests want none.
     */
    public JasperApplication(ConfigService service, ShellLauncher suppliedLauncher, CommandHistory history, Path buddyStateFile,
                      Runnable terminate, ShellHistoryIndex shellHistory, SnippetStore snippets, Path shellIntegrationDir) {
        this.history = history;
        this.suppliedLauncher = suppliedLauncher;
        this.shutdown = new ApplicationShutdown(terminate);
        this.shellHistory = shellHistory;
        this.snippets = snippets;
        this.shellIntegrationDir = shellIntegrationDir;
        buddy = new BuddyIntegration(buddyStateFile, SystemFonts.system(java.awt.Font.PLAIN, 13f),
            themes.current().chrome() == BuiltinTheme.DARK, this::raiseTerminal, this::toggleBuddy, this::owns);
        buddyAppearance = themes.subscribe((theme, changed) -> buddy.appearance(SystemFonts.system(java.awt.Font.PLAIN, 13f), theme.chrome() == BuiltinTheme.DARK));
        notifications = new CommandNotifier(() -> java.time.Duration.ofSeconds(configuredLongCommandSeconds()),
            buddy.companion(), nativeNotifier::send, buddy.companion()::setWorking, JasperApplication::afterDelay);
        configuration = service == null ? null : new ConfigurationController(themes, service);
        if (configuration != null) configuration.onSnapshot(snapshot -> {
            buddy.configured(snapshot.buddyEnabled()); updateBuddyActions();
            if (snippets != null) snippets.reload();
            loginItems.accept(snapshot.backgroundEnabled());
            if (plugins != null) plugins.configurationChanged(snapshot.plugins());
        });
        if (supports(Desktop.Action.APP_QUIT_HANDLER)) {
            Desktop.getDesktop().setQuitHandler((event, response) -> {
                response.cancelQuit(); SwingUtilities.invokeLater(this::quit);
            });
            quitHandlerInstalled = true;
        }
        // Clicking the Dock icon of a running Jasper creates no process: AppKit sends this instead.
        // Registered whether or not residency is on, because a window that is merely minimized
        // should come back the same way.
        if (supports(Desktop.Action.APP_EVENT_REOPENED)) {
            reopenListener = event -> SwingUtilities.invokeLater(() -> openOrRaise(Path.of(System.getProperty("user.home"))));
            Desktop.getDesktop().addAppEventListener(reopenListener);
        }
    }

    /** Swing's timer, as a plain function: schedule a task, get back the way to cancel it. */
    private static Runnable afterDelay(java.time.Duration delay, Runnable task) {
        javax.swing.Timer timer = new javax.swing.Timer(
            (int) Math.max(1, Math.min(Integer.MAX_VALUE, delay.toMillis())), event -> task.run());
        timer.setRepeats(false);
        timer.start();
        return timer::stop;
    }

    public TerminalWindow newWindow(Path directory) {
        if (quitting) return null;
        boolean first = windows.isEmpty();
        ShellLauncher launcher = suppliedLauncher != null ? suppliedLauncher : windowLauncher(launches,
            configuration == null ? ConfigSnapshot::defaults : configuration::snapshot,
            (path, settings) -> track(startSession(path, settings)), shellIntegrationDir);
        TerminalWindow window = new TerminalWindow(new WindowCallbacks(this::newWindow, this::quit,
            this::windowActivated, this::windowClosed,
            state -> windowStateChanged(state.window(), state.showing(), state.iconified())),
            launcher, directory, themes, configuration == null ? null : configuration.snapshot(), history, shellHistory, snippets);
        if (configuration != null) configuration.register(window.content());
        window.content().onToggleBuddy = this::toggleBuddy;
        window.content().buddyEnabled = this::buddyEnabled;
        window.content().anyWindowActive = () -> windows.stream().anyMatch(open -> open.content().isActiveAndOpen());
        window.content().activity(event -> {
            switch (event) {
                case WorkspaceActivity.Started value -> notifications.started(value.id(), value.command(),
                    value.elapsedNanos(), value.activate(), value.watched());
                case WorkspaceActivity.Finished value -> notifications.finished(value.id(), value.command(),
                    value.exitStatus(), value.duration(), new CommandNotice.Origin(value.origin().anyWindowActive(),
                        value.origin().ownWindowActive(), value.origin().ownTabSelected(), value.origin().ownPaneFocused()), value.activate());
                case WorkspaceActivity.TitleChanged value -> notifications.titleChanged(value.id(), value.title());
                case WorkspaceActivity.PaneState value -> {
                    switch (value.state()) {
                        case OPENED -> notifications.opened(value.id());
                        case CLOSED -> notifications.closed(value.id());
                        case FOCUSED -> notifications.looked(value.id());
                        case BLURRED -> notifications.hidden(value.id());
                    }
                }
            }
        });
        windows.add(window); window.show();
        if (first) shellHistory.refresh();
        if (first && configuration == null && snippets != null) snippets.reload();
        return window;
    }

    /**
     * Whether this process outlives its windows. Decided once at startup: which process owns the
     * handoff endpoint is not something to renegotiate while running.
     */
    public void residency(boolean resident) { this.resident = resident; }

    public boolean resident() { return resident; }

    /**
     * The handoff endpoint is gone, so nothing can reach this process any more. It stops being
     * resident, and with no windows left there is nothing to wait for: shut down properly rather
     * than letting the JVM starve once the accept thread ends, which skips every close.
     * Distinct from {@link #residency} because that is also how startup reports a failed bind,
     * before any window exists.
     *
     * <p>When windows are still open, this only clears residency and shutdown waits for the last
     * one to close, the same path {@link #windowClosed} already takes. That branch needs a live
     * window and is not covered by a headless test — a known gap, not an oversight.
     */
    public void endpointReleased() {
        resident = false;
        if (windows.isEmpty()) requestShutdown();
    }

    /**
     * Who reconciles the user's login items with the setting. Injected, because reconciling from
     * here would have these tests write into the developer's real login items. Setting it replays
     * the current value at once: the constructor's own listener registration has already fired by
     * the time production can install this.
     */
    public void loginItems(java.util.function.Consumer<Boolean> reconcile) {
        loginItems = Objects.requireNonNull(reconcile, "reconcile");
        if (configuration != null) loginItems.accept(configuration.snapshot().backgroundEnabled());
    }

    /** The handoff and the macOS reopen event share this: raise what is open, or open the first window. */
    public void openOrRaise(Path directory) {
        if (quitting || stopped) return;
        if (windows.isEmpty()) newWindow(directory);
        else raiseTerminal();
    }

    /**
     * Pays the first window's one-time costs with no window on screen. Constructing this
     * application already installed the look and feel; the font set is the remaining expensive
     * piece, and building one realizes the toolkit's font machinery and the cell metrics.
     */
    public void warmUp() {
        ConfigSnapshot snapshot = configuration == null ? ConfigSnapshot.defaults() : configuration.snapshot();
        FontConfig font = snapshot.font();
        try {
            new dev.jasper.terminal.rendering.FontSet(font.family(), font.size(), font.fallback(),
                font.ligatures(), font.lineHeight());
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Font warm-up failed; the first window will pay for it", failure);
        }
        shellHistory.refresh();
    }

    /**
     * Starts plugins once, before the first window, so their contributions are in place when windows
     * appear. {@code codeSource} locates bundled plugins beside the application jar and may be null;
     * {@code developmentDirectory} is the {@code --plugin-dir} value or null.
     */
    public void startPlugins(Path codeSource, Path developmentDirectory, boolean safeMode, AppDirs dirs) {
        if (plugins != null || quitting || stopped) return;
        activityNotifier = new ActivityNotifier(buddy.companion(), this::raiseTerminal);
        plugins = new PluginRuntime(new PluginRuntime.Options(PluginRuntime.bundledDirectory(codeSource), dirs.plugins(),
            developmentDirectory, safeMode, dirs.pluginState(), dirs.pluginLock(), dirs.pluginData()), activityNotifier,
            (key, message) -> { if (configuration != null) configuration.report(key, message); });
        plugins.start(configuration == null ? Map.of() : configuration.snapshot().plugins());
        boolean[] replayed = new boolean[1];
        // subscribe replays the current theme at once; plugins read the look on demand, so only later changes are events.
        pluginTheme = themes.subscribe((theme, chromeChanged) -> {
            if (replayed[0] && chromeChanged) plugins.themeChanged(theme.chrome() == BuiltinTheme.DARK);
            replayed[0] = true;
        });
    }

    static ShellLauncher windowLauncher(Executor executor, Supplier<ConfigSnapshot> snapshots,
                                        BiFunction<Path, LaunchSettings, TerminalSession> start) {
        return windowLauncher(executor, snapshots, start, null);
    }

    static ShellLauncher windowLauncher(Executor executor, Supplier<ConfigSnapshot> snapshots,
                                        BiFunction<Path, LaunchSettings, TerminalSession> start, Path integrationDir) {
        ConfigSnapshot initial = snapshots.get();
        return new ShellLauncher(executor, () -> LaunchSettings.resolve(snapshots.get(),
            System.getProperty("os.name"), System.getenv(), initial.columns(), initial.lines(), integrationDir), start);
    }

    private static TerminalSession startSession(Path directory, LaunchSettings settings) {
        try {
            return TerminalSession.start(SessionLaunchOptions.builder().command(settings.command()).environment(settings.environment()).workingDirectory(directory).grid(new GridSize(settings.columns(), settings.lines())).scrollback(settings.scrollback()).build());
        } catch (IOException failure) {
            LOG.log(System.Logger.Level.ERROR, "Shell launch failed", failure);
            throw new UncheckedIOException(failure);
        }
    }

    /** Remembers a shell so shutdown can wait for it to leave before terminating the JVM. */
    TerminalSession track(TerminalSession session) { return launches.track(session); }

    void windowClosed(TerminalWindow window) {
        windows.remove(window);
        buddy.removeWindow(window);
        if (lastActive == window) lastActive = null;
        // Residency keeps the warm process: the command history, the shell-history index, the
        // snippets and the configuration watcher are precisely what makes the next window fast,
        // and shutdown would close all of them. Quit still terminates.
        if (windows.isEmpty() && !resident) requestShutdown();
        else updateBuddyActions();
    }

    void windowStateChanged(TerminalWindow window, boolean showing, boolean iconified) {
        if (!windows.contains(window)) return;
        buddy.window(window, showing, iconified);
        updateBuddyActions();
    }

    /** Coming back to a Jasper window is attention: the buddy wakes and waves. */
    void windowActivated(TerminalWindow window) {
        if (!windows.contains(window)) return;
        lastActive = window;
        if (buddy != null && !quitting && !stopped) buddy.greet();
    }

    private boolean owns(Component component) {
        for (TerminalWindow window : windows) if (window.owns(component)) return true;
        return false;
    }

    void toggleBuddy() { buddy.toggle(); updateBuddyActions(); }

    boolean buddyEnabled() { return buddy.enabled(); }

    private void raiseTerminal() {
        TerminalWindow target = lastActive != null && windows.contains(lastActive) ? lastActive
            : windows.isEmpty() ? null : windows.iterator().next();
        if (target != null) target.toFront();
    }

    private void updateBuddyActions() {
        for (TerminalWindow window : List.copyOf(windows)) window.content().updateActions();
    }

    /**
     * Registers process cleanup before quit. Each action runs on a daemon worker and participates
     * in the bounded exit wait; callbacks must not access Swing. Registration is EDT-owned.
     */
    public void onShutdown(Runnable action) {
        if (stopped) processCleanup(java.util.Objects.requireNonNull(action));
        else shutdownActions.add(java.util.Objects.requireNonNull(action));
    }

    public void quit() {
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
        // Armed before anything else: a plugin's stop() that blocks the EDT cannot be abandoned, and
        // await() below is never reached in that case.
        shutdown.arm(() -> plugins == null ? null : plugins.executing());
        launches.close();
        // Application-owned state first, so none of it depends on plugins behaving.
        history.close();
        shellHistory.close();
        if (snippets != null) snippets.close();
        List<CompletableFuture<?>> pluginWork = plugins == null ? List.of() : plugins.stop();
        if (pluginTheme != null) pluginTheme.close();
        notifications.close();
        if (activityNotifier != null) activityNotifier.close();
        buddyAppearance.close();
        buddy.close();
        if (configuration != null) configuration.close();
        if (quitHandlerInstalled) { Desktop.getDesktop().setQuitHandler(null); quitHandlerInstalled = false; }
        if (reopenListener != null) { Desktop.getDesktop().removeAppEventListener(reopenListener); reopenListener = null; }
        // Cross-process locks and socket probes must not hold up Swing or the exit deadline.
        List<CompletableFuture<?>> pending = new ArrayList<>(launches.pendingExits());
        pending.addAll(pluginWork);
        for (Runnable action : java.util.List.copyOf(shutdownActions)) pending.add(processCleanup(action));
        shutdownActions.clear();
        // Nothing else ends the JVM: without an explicit exit, AWT waits a full quiet second before it lets go.
        pending.add(history.closedFuture());
        shutdown.await(pending);
    }

    private static CompletableFuture<Void> processCleanup(Runnable action) {
        return CompletableFuture.runAsync(action, work ->
            Thread.ofPlatform().name("jasper-process-cleanup").daemon().start(work));
    }

    private static boolean supports(Desktop.Action action) {
        return !GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported()
            && Desktop.getDesktop().isSupported(action);
    }
}
