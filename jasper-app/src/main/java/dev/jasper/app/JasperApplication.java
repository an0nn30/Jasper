package dev.jasper.app;

import dev.jasper.terminal.TerminalSession;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
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
    /** At most one buddy poke per second, however fast the user types. */
    private static final long POKE_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(1);
    private final ThemeController themes = new ThemeController();
    private final Set<TerminalWindow> windows = new LinkedHashSet<>();
    private final ExecutorService launches = Executors.newThreadPerTaskExecutor(
        Thread.ofPlatform().name("jasper-shell-launch-", 0).factory());
    private final ConfigurationController configuration;
    private boolean quitting;
    private boolean resident;
    private java.util.function.Consumer<Boolean> loginItems = enabled -> { };
    private boolean shutdownQueued;
    private boolean stopped;
    private final CommandHistory history;
    private final ShellLauncher suppliedLauncher;
    private final Runnable terminate;
    private final ShellHistoryIndex shellHistory;
    private final SnippetStore snippets;
    private final Path shellIntegrationDir;
    private final Set<TerminalSession> sessions = ConcurrentHashMap.newKeySet();
    private final BuddyVisibility buddyVisibility = new BuddyVisibility();
    private final Path buddyStateFile;
    private BuddyWindow buddy;
    private final NativeNotifier nativeNotifier = new NativeNotifier();
    /** The drawer outlives the buddy's window: hiding him must not throw away what you kept. */
    private final BuddyDeck deck = new BuddyDeck();
    private final CommandNotifier notifications = new CommandNotifier(
        () -> java.time.Duration.ofSeconds(configuredLongCommandSeconds()),
        deck,
        () -> { if (buddy != null) buddy.refreshDeck(); },
        nativeNotifier::send,
        working -> { if (buddy != null) buddy.setWorking(working); },
        JasperApplication::afterDelay);
    private boolean buddyUnavailable;

    private int configuredLongCommandSeconds() {
        return configuration == null ? 10 : configuration.snapshot().longCommandSeconds();
    }
    private AWTEventListener keyWatch;
    private long lastPokeNanos;
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

    JasperApplication(ConfigService service, ShellLauncher suppliedLauncher, CommandHistory history, Path buddyStateFile,
                      Runnable terminate) {
        this(service, suppliedLauncher, history, buddyStateFile, terminate, new ShellHistoryIndex(List.of()));
    }

    JasperApplication(ConfigService service, ShellLauncher suppliedLauncher, CommandHistory history, Path buddyStateFile,
                      Runnable terminate, ShellHistoryIndex shellHistory) {
        this(service, suppliedLauncher, history, buddyStateFile, terminate, shellHistory, null);
    }

    JasperApplication(ConfigService service, ShellLauncher suppliedLauncher, CommandHistory history, Path buddyStateFile,
                      Runnable terminate, ShellHistoryIndex shellHistory, SnippetStore snippets) {
        this(service, suppliedLauncher, history, buddyStateFile, terminate, shellHistory, snippets, null);
    }

    /**
     * {@code buddyStateFile} may be null: the buddy then starts in the default corner and forgets drags.
     * {@code terminate} runs once, off the EDT, after shutdown's bounded cleanup; production passes the JVM exit.
     * {@code shellIntegrationDir} is the extracted script directory, or null when extraction failed or tests want none.
     */
    JasperApplication(ConfigService service, ShellLauncher suppliedLauncher, CommandHistory history, Path buddyStateFile,
                      Runnable terminate, ShellHistoryIndex shellHistory, SnippetStore snippets, Path shellIntegrationDir) {
        this.history = history;
        this.suppliedLauncher = suppliedLauncher;
        this.buddyStateFile = buddyStateFile;
        this.terminate = terminate;
        this.shellHistory = shellHistory;
        this.snippets = snippets;
        this.shellIntegrationDir = shellIntegrationDir;
        configuration = service == null ? null : new ConfigurationController(themes, service);
        if (configuration != null) configuration.onSnapshot(snapshot -> {
            buddyVisibility.configure(snapshot.buddyEnabled()); syncBuddy();
            if (snippets != null) snippets.reload();
            loginItems.accept(snapshot.backgroundEnabled());
        });
        if (supports(Desktop.Action.APP_QUIT_HANDLER)) Desktop.getDesktop().setQuitHandler((event, response) -> {
            // Cancel the native immediate JVM exit; pane close owns bounded child cleanup.
            response.cancelQuit(); SwingUtilities.invokeLater(this::quit);
        });
        // Clicking the Dock icon of a running Jasper creates no process: AppKit sends this instead.
        // Registered whether or not residency is on, because a window that is merely minimized
        // should come back the same way.
        if (supports(Desktop.Action.APP_EVENT_REOPENED)) {
            Desktop.getDesktop().addAppEventListener((java.awt.desktop.AppReopenedListener) event ->
                SwingUtilities.invokeLater(() -> openOrRaise(Path.of(System.getProperty("user.home")))));
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

    TerminalWindow newWindow(Path directory) {
        if (quitting) return null;
        boolean first = windows.isEmpty();
        ShellLauncher launcher = suppliedLauncher != null ? suppliedLauncher : windowLauncher(launches,
            configuration == null ? ConfigSnapshot::defaults : configuration::snapshot,
            (path, settings) -> track(startSession(path, settings)), shellIntegrationDir);
        TerminalWindow window = new TerminalWindow(this, launcher, directory, themes, configuration, history, shellHistory, snippets);
        window.content().anyWindowActive = () -> windows.stream().anyMatch(open -> open.content().isActiveAndOpen());
        window.content().onCommandStarted = (command, pane, elapsed, focus, watched) ->
            notifications.started(pane, command, elapsed, focus, watched);
        window.content().onCommandFinished = (command, exitStatus, duration, origin, pane, focus) ->
            notifications.finished(pane, command, exitStatus, duration, origin, focus);
        window.content().onPaneTitleChanged = notifications::titleChanged;
        window.content().onPaneClosed = notifications::closed;
        window.content().onPaneFocused = notifications::looked;
        window.content().onPaneBlurred = notifications::hidden;
        windows.add(window); window.show();
        if (first) shellHistory.refresh();
        if (first && configuration == null && snippets != null) snippets.reload();
        return window;
    }

    /**
     * Whether this process outlives its windows. Decided once at startup: which process owns the
     * handoff endpoint is not something to renegotiate while running.
     */
    void residency(boolean resident) { this.resident = resident; }

    boolean resident() { return resident; }

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
    void endpointReleased() {
        resident = false;
        if (windows.isEmpty()) requestShutdown();
    }

    /**
     * Who reconciles the user's login items with the setting. Injected, because reconciling from
     * here would have these tests write into the developer's real login items. Setting it replays
     * the current value at once: the constructor's own listener registration has already fired by
     * the time production can install this.
     */
    void loginItems(java.util.function.Consumer<Boolean> reconcile) {
        loginItems = Objects.requireNonNull(reconcile, "reconcile");
        if (configuration != null) loginItems.accept(configuration.snapshot().backgroundEnabled());
    }

    /** The handoff and the macOS reopen event share this: raise what is open, or open the first window. */
    void openOrRaise(Path directory) {
        if (quitting || stopped) return;
        if (windows.isEmpty()) newWindow(directory);
        else raiseTerminal();
    }

    /**
     * Pays the first window's one-time costs with no window on screen. Constructing this
     * application already installed the look and feel; the font set is the remaining expensive
     * piece, and building one realizes the toolkit's font machinery and the cell metrics.
     */
    void warmUp() {
        ConfigSnapshot snapshot = configuration == null ? ConfigSnapshot.defaults() : configuration.snapshot();
        FontConfig font = snapshot.font();
        try {
            new dev.jasper.terminal.FontSet(font.family(), font.size(), font.fallback(),
                font.ligatures(), font.lineHeight());
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Font warm-up failed; the first window will pay for it", failure);
        }
        shellHistory.refresh();
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
        // Residency keeps the warm process: the command history, the shell-history index, the
        // snippets and the configuration watcher are precisely what makes the next window fast,
        // and shutdown would close all of them. Quit still terminates.
        if (windows.isEmpty() && !resident) requestShutdown();
        else syncBuddy();
    }

    void windowStateChanged(TerminalWindow window, boolean showing, boolean iconified) {
        if (!windows.contains(window)) return;
        buddyVisibility.window(window, showing, iconified);
        syncBuddy();
    }

    /** Coming back to a Jasper window is attention: the buddy wakes and waves. */
    void windowActivated(TerminalWindow window) {
        if (!windows.contains(window)) return;
        lastActive = window;
        if (buddy != null && !quitting && !stopped) buddy.greet();
    }

    /**
     * One toolkit listener for the whole app, installed with the buddy: typing in a Jasper window keeps
     * him awake without a wave. Key events from the buddy's own bubble or any other window are ignored.
     */
    private void installKeyWatch() {
        if (keyWatch != null) return;
        lastPokeNanos = System.nanoTime() - POKE_INTERVAL_NANOS;
        keyWatch = event -> {
            if (event.getID() != KeyEvent.KEY_PRESSED || quitting || stopped || buddy == null) return;
            if (!(event.getSource() instanceof Component source) || !owns(source)) return;
            long now = System.nanoTime();
            if (now - lastPokeNanos < POKE_INTERVAL_NANOS) return;
            lastPokeNanos = now;
            buddy.poke();
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(keyWatch, AWTEvent.KEY_EVENT_MASK);
    }

    private void removeKeyWatch() {
        if (keyWatch == null) return;
        Toolkit.getDefaultToolkit().removeAWTEventListener(keyWatch);
        keyWatch = null;
    }

    private boolean owns(Component component) {
        for (TerminalWindow window : windows) if (window.owns(component)) return true;
        return false;
    }

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
                        if (buddy == null) buddyUnavailable = true; else installKeyWatch();
                    }
                    if (buddy != null) { buddy.show(); buddy.attachDeck(deck); }
                } else if (buddy != null) buddy.hide();
            } catch (RuntimeException failure) {
                buddyUnavailable = true;
                if (buddy != null) {
                    try { buddy.dispose(); } catch (RuntimeException ignored) { }
                }
                buddy = null;
                removeKeyWatch();
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
        removeKeyWatch();
        if (buddy != null) buddy.dispose();
        history.close();
        shellHistory.close();
        if (snippets != null) snippets.close();
        if (configuration != null) configuration.close();
        if (supports(Desktop.Action.APP_QUIT_HANDLER)) Desktop.getDesktop().setQuitHandler(null);
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

    private static boolean supports(Desktop.Action action) {
        return !GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported()
            && Desktop.getDesktop().isSupported(action);
    }
}
