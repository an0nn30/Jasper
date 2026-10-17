package dev.jasper.app.bootstrap;

import dev.jasper.app.application.DockControl;
import dev.jasper.app.application.JasperApplication;
import dev.jasper.app.commandline.CommandDispatcher;
import dev.jasper.app.commandline.CommandListener;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.launch.ShellIntegrationScripts;
import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.platform.AppLog;
import dev.jasper.app.platform.ApplicationIcon;
import dev.jasper.app.platform.LoginItem;
import dev.jasper.app.platform.MacDock;
import dev.jasper.app.platform.ResidentTray;
import dev.jasper.app.platform.SystemNotifier;
import dev.jasper.app.platform.WindowsForeground;
import dev.jasper.app.platform.WindowsProcess;
import dev.jasper.app.residency.HandoffSocket;
import dev.jasper.app.residency.LaunchRequest;
import dev.jasper.app.startup.MemoryReading;
import dev.jasper.app.startup.StartupTrace;
import dev.jasper.app.bootstrap.StartupResources;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import dev.jasper.app.restart.ResidentControl;

/** Resolves desktop-free launch roles, then assembles the application's resource owners. */
public final class ApplicationBootstrap {
    private static final System.Logger LOG = System.getLogger(ApplicationBootstrap.class.getName());
    private ApplicationBootstrap() {}

    public static void main(String[] args) {
        StartupTrace.current().mark(StartupTrace.Milestone.MAIN_ENTERED);
        int result = start(args, System.out, System.err, ApplicationBootstrap::prepare, ApplicationBootstrap::prepareDesktop);
        if (result != 0) System.exit(result);
    }

    /** What the boundary hands the desktop: the service, the arguments, the home, the log (null without the pre-pass) and the selection. */
    record Startup(ConfigService service, AppArguments options, AppDirs dirs, AppLog log,
                   dev.jasper.app.plugins.PluginSelection selection) { }

    /** What the pre-pass made: the log, open from here on, and the plugin selection. */
    record Prepared(AppLog log, dev.jasper.app.plugins.PluginSelection selection) { }

    /** The pre-pass {@link #start} runs after the hand-off checks and before the configuration is read (spec 4.8). */
    @FunctionalInterface interface Preparation {
        /** No log, no maintenance and no plugins: what tests of the boundary run. */
        Preparation NONE = (dirs, options) -> new Prepared(null, dev.jasper.app.plugins.PluginSelection.none());
        Prepared prepare(AppDirs dirs, AppArguments options);
    }

    /**
     * The production pre-pass: the Jasper log, pending plugin removals and installs, then the plugin selection, all on
     * the bootstrap thread and before AWT, so maintenance and selection log to the log file.
     */
    static Prepared prepare(AppDirs dirs, AppArguments options) {
        AppLog log = AppLog.open(dirs.logs());
        try {
            long began = System.nanoTime();
            JasperApplication.preparePlugins(dirs);
            Path source = HandoffSocket.codeLocation();
            String build = source == null ? "" : source + "@" + HandoffSocket.lastModified(source);
            var selection = dev.jasper.app.plugins.PluginSelection.of(dirs, source, build, options.pluginDir(), options.safeMode());
            StartupTrace.current().phase("plugin selection", began);
            return new Prepared(log, selection);
        } catch (RuntimeException | Error failure) { log.close(); throw failure; }
    }
    private static void prepareDesktop(Startup startup) {
        ConfigService service = startup.service(); AppArguments options = startup.options();
        var acquired = new StartupResources();
        acquired.own(service);
        try {
            AppDirs dirs = startup.dirs();
            AppLog log = acquired.own(Objects.requireNonNull(startup.log(), "the desktop needs the pre-pass's log"));
            UnexpectedExceptions exceptions = acquired.own(installUnexpectedExceptionHandler());
            var endpoint = new AtomicReference<HandoffSocket>();
            var commands = new AtomicReference<CommandListener>();
            var attempt = startup.selection().attempt();
            Thread shutdown = Thread.ofPlatform().name("jasper-log-shutdown").unstarted(() -> {
                try {
                    // An orderly quit before the look proved itself clears its pending mark; any other exit leaves it (spec 4.6).
                    try { attempt.exiting(); } catch (RuntimeException unrecorded) { LOG.log(System.Logger.Level.WARNING, "Could not update the presentation attempt record", unrecorded); }
                    if (endpoint.get() != null) endpoint.get().close();
                    if (commands.get() != null) commands.get().close();
                }
                finally { exceptions.close(); log.close(); }
            });
            Runtime.getRuntime().addShutdownHook(shutdown);
            acquired.own((AutoCloseable) () -> removeShutdownHook(shutdown));
            configureDesktopProperties();
            // Before the look and feel and any window: a restart-only choice, like residency.
            dev.jasper.app.platform.TitleBarStyle.choose(System.getProperty("os.name").startsWith("Mac"),
                service.initialState().snapshot().nativeDecorations(),
                service.initialState().snapshot().viewMode() == dev.jasper.app.config.ViewMode.COMPACT).configure();
            // Before any plugin is discovered, and here rather than on the EDT: it may wait for the state lock.
            dev.jasper.ui.icons.IconRegistry.prepareBuiltIns();
            SwingUtilities.invokeLater(() -> startDesktop(startup, dirs, log, exceptions, shutdown, endpoint, commands));
            // The posted EDT composition owns service; the process hook owns logging, the endpoint and the command
            // listener.
            acquired.transfer();
        } catch (RuntimeException | Error failure) {
            acquired.rollback(failure);
            LOG.log(System.Logger.Level.ERROR, "Application startup failed", failure);
        }
    }

    /**
     * Whether the launch is about to show a window or rehearse one, so the look's attempt is marked pending (spec 4.6):
     * the first window, or a resident {@code --background} warm-up, never headless and never {@code --no-window}.
     */
    static boolean attemptBeforePresenting(AppArguments options, boolean resident, boolean headless) {
        if (headless || options.noWindow()) return false;
        return !options.background() || resident;
    }

    /** macOS reads these settings during toolkit/LAF initialization; never change them on reload. */
    static void configureDesktopProperties() {
        System.setProperty("apple.awt.application.appearance", "system");
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        // Read once when the first tray icon is created: the macOS menu bar glyph is a template image.
        System.setProperty("apple.awt.enableTemplateImages", "true");
    }

    /**
     * Properties a launch sets before anything touches AWT. A packaged macOS {@code --background} launch (the login item)
     * starts as an accessory application, so no Dock icon appears at login; the bridge brings Jasper back when a window
     * opens. A development run, or a packaged image without its bridge, has nothing to bring it back, so it never starts
     * hidden.
     */
    static Map<String, String> launchProperties(boolean background, String osName, String appPath) {
        return background && MacDock.available(osName, appPath) ? Map.of("apple.awt.UIElement", "true") : Map.of();
    }

    private static void startDesktop(Startup startup, AppDirs dirs, AppLog log,
                                     UnexpectedExceptions exceptions, Thread shutdown, AtomicReference<HandoffSocket> endpoint,
                                     AtomicReference<CommandListener> commands) {
        ConfigService service = startup.service(); AppArguments options = startup.options();
        try {
            StartupTrace.current().mark(StartupTrace.Milestone.TOOLKIT_READY);
            boolean resident = service.initialState().snapshot().backgroundEnabled();
            Path home = Path.of(System.getProperty("user.home"));
            String appPath = System.getProperty("jpackage.app-path");
            boolean standalone = options.standaloneLaunch();
            if (!standalone && options.background() && !resident) {
                reconcileLoginItem(false, appPath, home);
                LOG.log(System.Logger.Level.INFO,
                    "Started with --background while system.run_in_background is off; exiting");
                service.close(); System.exit(0); return;
            }
            compose(service, () -> new CommandHistory(dirs.commandHistory()),
                history -> {
                    JasperApplication application = createApplication(service, history, dirs, options, commands, startup.selection());
                    exceptions.forwardTo(application::unexpected);
                    return application;
                }, application -> {
                    if (!standalone)
                        application.loginItems(loginItemReconciler(enabled -> reconcileLoginItem(enabled, appPath, home)));
                    if (!residentRole(options, resident)) return null;
                    Path source = HandoffSocket.codeLocation();
                    var bound = HandoffSocket.bind(dirs.daemonSocket(), dirs.daemonToken(), dirs.daemonLock(),
                        handoffHandler(application, source, HandoffSocket.lastModified(source)));
                    endpoint.set(bound);
                    return bound;
                }, application -> {
                    if (options.noWindow()) {
                        serveWithoutWindow(application, commands.get());
                        return;
                    }
                    if (!options.background()) {
                        if (attemptBeforePresenting(options, application.resident(), GraphicsEnvironment.isHeadless()))
                            application.presentationAttempt().pending();
                        long window = System.nanoTime();
                        application.newWindow();
                        StartupTrace.current().phase("first window", window);
                        return;
                    }
                    if (!application.resident()) {
                        LOG.log(System.Logger.Level.WARNING, standalone
                            ? "Started with --background, but a --config, --safe-mode, --plugin-dir or --standalone launch is always standalone and cannot be resident; exiting"
                            : "Started with --background but the handoff endpoint is unavailable; exiting");
                        application.quitNow();
                    } else {
                        if (attemptBeforePresenting(options, application.resident(), GraphicsEnvironment.isHeadless()))
                            application.presentationAttempt().pending();
                        application.warmUp(() -> StartupTrace.current().mark(StartupTrace.Milestone.WARMED_UP));
                    }
                });
        } catch (RuntimeException | Error failure) {
            // Also covers failure before composition took ownership (for example initial role resolution).
            var remaining = new StartupResources(); remaining.own(service); remaining.rollback(failure);
            LOG.log(System.Logger.Level.ERROR, "Application startup failed", failure);
            closeLogAfterStartupFailure(log, () -> { exceptions.close(); removeShutdownHook(shutdown); });
        }
    }

    private static JasperApplication createApplication(ConfigService service, CommandHistory history, AppDirs dirs,
                                                       AppArguments options, AtomicReference<CommandListener> commands,
                                                       dev.jasper.app.plugins.PluginSelection selection) {
        long began = System.nanoTime();
        var acquired = new StartupResources();
        try {
            ApplicationIcon.installTaskbarIcon();
            Path integrationDir = null;
            try { integrationDir = ShellIntegrationScripts.install(dirs.shellIntegration()); }
            catch (java.io.IOException failure) {
                LOG.log(System.Logger.Level.WARNING,
                    "Shell integration scripts could not be installed; integration is off", failure);
            }
            var application = new JasperApplication(service, null, history, dirs.buddyState(),
                () -> System.exit(0), integrationDir, dirs.themes(), selection);
            Path source = HandoffSocket.codeLocation();
            application.residentControl(new ResidentControl(() -> HandoffSocket.live(dirs.daemonSocket()),
                () -> HandoffSocket.retire(dirs.daemonSocket(), dirs.daemonToken(), source, HandoffSocket.lastModified(source))),
                replacementHandsOff(options), standaloneNotice(options));
            application.staleBuild(replacedOnDisk(source, HandoffSocket.lastModified(source)));
            // The macOS menu bar item and the Windows notification-area icon, once start-up has settled. On Windows the
            // same icon carries the system notifications' balloons, and one is added for them when Jasper is not resident.
            ResidentTray tray = ResidentTray.forThisDesktop();
            boolean onWindows = WindowsProcess.supported(System.getProperty("os.name"));
            application.residentIndicator(actions -> {
                // Resident and settled: from here this process may sit for hours with no window. On Windows, keep the
                // next one from being built throttled and out of a trimmed working set. Off the EDT: the first foreign
                // calls take tens of milliseconds to prepare, which this also pays before any window needs a reading.
                if (onWindows) Thread.ofPlatform().daemon().name("jasper-keep-responsive").start(WindowsProcess::keepResponsive);
                return tray.install(actions);
            });
            if (onWindows) {
                // A window opened after a spell with none then says whether its memory had to be paged back in.
                StartupTrace.current().readMemoryWith(() -> {
                    WindowsProcess.Sample sample = WindowsProcess.sample();
                    return sample == null ? null : new MemoryReading(sample.pageFaults(), sample.workingSetBytes());
                });
            }
            application.systemNotifier(SystemNotifier.forThisPlatform(System.getProperty("os.name"), dirs, tray));
            // A packaged macOS Jasper keeps running on Quit and leaves the Dock while it has no window.
            if (MacDock.available())
                application.backgroundOnQuit(new DockControl(MacDock::show, MacDock::prepare, MacDock::poweringOff), !options.background());
            if (!options.privateCommands()) application.subcommandList(dirs.subcommandList());
            application.startPlugins(dirs);
            // After the plugins started, so a call that arrives first finds its handler.
            CommandListener listener = serveCommands(application, options, dirs, acquired);
            if (listener != null) commands.set(listener);
            StartupTrace.current().phase("application", began);
            acquired.transfer();
            return application;
        } catch (RuntimeException | Error failure) { acquired.rollback(failure); throw failure; }
    }

    /** EDT acquisition transaction; factories keep resource creation explicit and rollback testable. */
    static JasperApplication compose(ConfigService service, Supplier<CommandHistory> openHistory,
                                     Function<CommandHistory, JasperApplication> openApplication,
                                     Function<JasperApplication, HandoffSocket> bindEndpoint,
                                     Consumer<JasperApplication> present) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Desktop composition requires the EDT");
        var acquired = new StartupResources();
        acquired.own(service);
        try {
            CommandHistory history = acquired.own(openHistory.get());
            JasperApplication application = openApplication.apply(history);
            acquired.own((AutoCloseable) application::quitNow);
            acquired.release(history); acquired.release(service);
            HandoffSocket endpoint = bindEndpoint.apply(application);
            if (endpoint != null) {
                // The application owns endpoint cleanup even if presentation fails below.
                // Rollback queues quit; it must never take the endpoint's process lock on EDT.
                application.onShutdown(endpoint::close);
            }
            application.residency(endpoint != null);
            present.accept(application);
            acquired.transfer();
            return application;
        } catch (RuntimeException | Error failure) { acquired.rollback(failure); throw failure; }
    }

    static UnexpectedExceptions installUnexpectedExceptionHandler() {
        synchronized (ApplicationBootstrap.class) {
            var exceptions = new UnexpectedExceptions(Thread.getDefaultUncaughtExceptionHandler());
            Thread.setDefaultUncaughtExceptionHandler(exceptions.installed);
            return exceptions;
        }
    }

    static void closeLogAfterStartupFailure(AppLog log, Runnable after) {
        Thread.ofPlatform().name("jasper-startup-cleanup").daemon().start(() -> {
            try { log.close(); }
            finally { after.run(); }
        });
    }

    private static void removeShutdownHook(Thread shutdown) {
        try { Runtime.getRuntime().removeShutdownHook(shutdown); }
        catch (IllegalStateException shutdownInProgress) { /* The hook owns the concurrent close. */ }
    }

    static final class UnexpectedExceptions implements AutoCloseable {
        private final Thread.UncaughtExceptionHandler previous;
        /** The handler this installed; it logs each failure, then forwards it. */
        final Thread.UncaughtExceptionHandler installed = this::uncaught;
        private volatile BiConsumer<Thread, Throwable> forward = (thread, failure) -> { };
        private boolean closed;

        private UnexpectedExceptions(Thread.UncaughtExceptionHandler previous) { this.previous = previous; }

        /** Where each failure goes after it is logged: the application records it as an incident. Any thread. */
        void forwardTo(BiConsumer<Thread, Throwable> sink) { forward = Objects.requireNonNull(sink, "sink"); }

        private void uncaught(Thread thread, Throwable failure) {
            LOG.log(System.Logger.Level.ERROR, "Unexpected application failure", failure);
            try { forward.accept(thread, failure); }
            catch (RuntimeException | Error unrecorded) {
                // Never back into this handler: a failure while recording is only logged.
                LOG.log(System.Logger.Level.ERROR, "An unexpected failure could not be recorded", unrecorded);
            }
        }

        @Override public void close() {
            synchronized (ApplicationBootstrap.class) {
                if (closed) return;
                closed = true;
                forward = (thread, failure) -> { };
                if (Thread.getDefaultUncaughtExceptionHandler() == installed) {
                    Thread.setDefaultUncaughtExceptionHandler(previous);
                }
            }
        }
    }

    /** Startup boundary: parsing, the handoff attempt and the first read finish before the desktop callback runs. */
    static int start(String[] args, java.io.PrintStream out, java.io.PrintStream error,
                     java.util.function.BiConsumer<ConfigService, AppArguments> launch) {
        return start(args, out, error, startup -> launch.accept(startup.service(), startup.options()));
    }
    static int start(String[] args, java.io.PrintStream out, java.io.PrintStream error, Consumer<Startup> launch) {
        return start(args, out, error, Preparation.NONE, launch);
    }
    static int start(String[] args, java.io.PrintStream out, java.io.PrintStream error, Preparation prepare, Consumer<Startup> launch) {
        AppArguments options;
        try { options = AppArguments.parse(args, Path.of(System.getProperty("user.dir"))); }
        catch (IllegalArgumentException failure) { error.println(failure.getMessage()); return 2; }
        if (options.help()) { out.println(AppArguments.USAGE); return 0; }
        String os = System.getProperty("os.name");
        // AppKit reads this as the toolkit initialises.
        launchProperties(options.background(), os, System.getProperty("jpackage.app-path")).forEach(System::setProperty);
        Path home = Path.of(System.getProperty("user.home"));
        AppDirs dirs = AppDirs.resolve(os, System.getenv(), home, System.getProperty("jasper.home"));
        options = options.withHomeOverride(dirs.overridden());
        // Before any toolkit initialization: a handed-off launch must cost almost nothing and must
        // not settle a second icon into the Dock on its way out.
        if (handsOff(options, dirs)) return 0;
        if (alreadyResident(options, dirs)) {
            LOG.log(System.Logger.Level.INFO, "Started with --background while a resident Jasper is running; exiting");
            return 0;
        }
        if (alreadyServing(options, dirs)) {
            LOG.log(System.Logger.Level.INFO, "Started with --no-window while a Jasper serves the jasper command; exiting");
            return 0;
        }
        Path file = options.configOverride() == null ? dirs.configFile() : options.configOverride();
        var acquired = new StartupResources();
        Prepared prepared = prepare.prepare(dirs, options);
        if (prepared.log() != null) acquired.own(prepared.log());
        StartupTrace.current().look(prepared.selection().presentation().look());
        long read = System.nanoTime();
        ConfigService service;
        try { service = acquired.own(new ConfigService(file, os.startsWith("Mac"), prepared.selection().presentation().defaults())); }
        catch (RuntimeException | Error failure) { acquired.rollback(failure); throw failure; }
        StartupTrace.current().phase("config", read);
        StartupTrace.current().mark(StartupTrace.Milestone.CONFIG_LOADED);
        try {
            launch.accept(new Startup(service, options, dirs, prepared.log(), prepared.selection()));
            acquired.transfer();
            return 0;
        } catch (RuntimeException | Error failure) { acquired.rollback(failure); throw failure; }
    }

    /**
     * True when a resident process accepted this launch and there is nothing left to do. A standalone
     * launch ({@code --config}, {@code --safe-mode}, {@code --plugin-dir}, {@code --standalone}, an overridden
     * home) never hands off, because the resident process holds a different configuration or plugin set, or is the very
     * process a recovery launch is escaping; neither does a resident process starting up, nor a {@code --no-window}
     * launch, which would be handed a window when it is there to serve the {@code jasper} command.
     */
    static boolean handsOff(AppArguments options, AppDirs dirs) {
        if (options.background() || options.noWindow() || options.standaloneLaunch()) return false;
        // Windows lets only this freshly launched process bring a window forward; the resident needs that right by the
        // time it shows its window. Grant before sending, including when an existing window can be raised immediately.
        WindowsForeground.allow(System.getProperty("os.name"), HandoffSocket.ownerPid(dirs.daemonToken()));
        Path source = HandoffSocket.codeLocation();
        boolean handed = HandoffSocket.handOff(dirs.daemonSocket(), dirs.daemonToken(), source, HandoffSocket.lastModified(source));
        return handed;
    }

    /**
     * True for a {@code --background} launch (the login item) while another process already owns the endpoint. That
     * launch could only lose the bind, and would first have spent seconds of the login starting plugins and rehearsing a
     * window, competing with the resident it would lose to. A standalone launch is left to its own refusal later.
     */
    static boolean alreadyResident(AppArguments options, AppDirs dirs) {
        return options.background() && !options.standaloneLaunch() && HandoffSocket.live(dirs.daemonSocket());
    }

    /** Residency needs the shared endpoint, so a standalone launch ({@code --config}, {@code --safe-mode},
     *  {@code --plugin-dir}, {@code --standalone}) never claims it: a later plain launch would otherwise be
     *  handed a window built from that configuration or plugin set. */
    static boolean residentRole(AppArguments options, boolean enabled) {
        return enabled && !options.standaloneLaunch();
    }

    /** How long a --no-window Jasper that is not resident waits for its first call. */
    static final int NO_CALL_GRACE_MILLIS = 30_000;

    /** The command socket a launch serves: its own for a private launch, otherwise its home's shared one. */
    static Path commandSocket(AppArguments options, AppDirs dirs) {
        return options.privateCommands() ? dirs.commandSocket(ProcessHandle.current().pid()) : dirs.commandSocket();
    }

    /**
     * Binds the command socket this launch serves ({@link #bindCommands}), owned by {@code acquired} until start-up
     * completes and closed at quit, and names it in the application's new local shells. Null when none binds. EDT.
     */
    static CommandListener serveCommands(JasperApplication application, AppArguments options, AppDirs dirs,
                                         StartupResources acquired) {
        CommandListener listener = bindCommands(options, dirs, application.commands());
        if (listener != null) {
            acquired.own(listener);
            application.onShutdown(listener::close);
            application.commandSocket(listener.socket());
        }
        return listener;
    }

    /**
     * Binds the command socket this launch serves ({@link #commandSocket}). A launch that finds another Jasper serving
     * its home's shared socket serves a private one instead, as a private launch does, so that its own shells reach it
     * rather than the other Jasper; a {@code --no-window} launch exists only to serve the shared socket and does not.
     * Null when nothing binds.
     */
    static CommandListener bindCommands(AppArguments options, AppDirs dirs, CommandDispatcher dispatcher) {
        CommandListener listener = CommandListener.bind(commandSocket(options, dirs), dispatcher);
        if (listener != null || options.noWindow() || options.privateCommands() || !CommandListener.live(dirs.commandSocket()))
            return listener;
        LOG.log(System.Logger.Level.INFO, "Another Jasper serves the jasper command for this home; this one serves its own shells");
        return CommandListener.bind(dirs.commandSocket(ProcessHandle.current().pid()), dispatcher);
    }

    /** True for a {@code --no-window} launch while another Jasper already serves the command: it would only lose the bind. */
    static boolean alreadyServing(AppArguments options, AppDirs dirs) {
        return options.noWindow() && !options.privateCommands() && CommandListener.live(dirs.commandSocket());
    }

    /**
     * A {@code --no-window} launch, which the {@code jasper} command makes to serve a call: no window. A Jasper that is
     * not resident quits once it has served its calls and has no window, or when no call arrives within
     * {@link #NO_CALL_GRACE_MILLIS}, so a client killed before it connected leaves nothing hidden behind. EDT.
     */
    static void serveWithoutWindow(JasperApplication application, CommandListener commands) {
        if (commands == null) {
            LOG.log(System.Logger.Level.WARNING, "Started with --no-window but the jasper command is unavailable; exiting");
            application.quitNow();
            return;
        }
        if (application.resident()) {
            application.warmUp(() -> StartupTrace.current().mark(StartupTrace.Milestone.WARMED_UP));
            return;
        }
        BooleanSupplier busy = commands::busy;
        commands.onIdle(() -> SwingUtilities.invokeLater(() -> application.quitIfUnused(busy)));
        var grace = new javax.swing.Timer(NO_CALL_GRACE_MILLIS, event -> application.quitIfUnused(busy));
        grace.setRepeats(false);
        grace.start();
    }

    /**
     * Whether leaving safe mode yields a plain launch. A replacement that keeps {@code --config},
     * {@code --plugin-dir} or {@code --standalone} is standalone anyway: it never hands off, so there is no
     * resident to ask, and a resident with another configuration must not be retired on its account.
     */
    static boolean replacementHandsOff(AppArguments options) {
        return options.configOverride() == null && options.pluginDir() == null && !options.standalone() && !options.homeOverride();
    }

    /** A launch that is standalone only because of {@code --standalone} is a "Launch anyway" replacement. */
    static boolean standaloneNotice(AppArguments options) {
        return options.standalone() && options.configOverride() == null && options.pluginDir() == null && !options.safeMode();
    }

    /** True when a request comes from a different build than this one. A resident process must not
     *  serve windows built from code it is not running. */
    static boolean stale(LaunchRequest request, Path source, long modified) {
        return !request.codeLocation().equals(source == null ? Path.of("") : source)
            || request.codeLocationModified() != modified;
    }

    /**
     * True once the application jar this process was loaded from has changed on disk (a new build was installed under
     * it) or gone. An unknown code location never counts. Any thread; one file stat per call.
     */
    static java.util.function.BooleanSupplier replacedOnDisk(Path source, long modified) {
        return () -> source != null && HandoffSocket.lastModified(source) != modified;
    }

    /**
     * The resident process's side of a handoff. A stale request also ends residency: the endpoint
     * goes away with the reply, so the last window closing must run the normal bounded shutdown
     * rather than leaving a process with nothing to reach it and no cleanup on the way out. A retire
     * request runs the normal quit path; the endpoint is released by the application's shutdown, not here.
     */
    static java.util.function.Function<LaunchRequest, LaunchRequest.Response> handoffHandler(
            JasperApplication application, Path source, long modified) {
        return request -> {
            // Before the staleness check: a recovery launch may well come from another build.
            if (request.kind() == LaunchRequest.Kind.RETIRE) {
                SwingUtilities.invokeLater(application::quit);
                return LaunchRequest.Response.OK;
            }
            if (stale(request, source, modified)) {
                SwingUtilities.invokeLater(() -> application.endpointReleased());
                return LaunchRequest.Response.STALE;
            }
            SwingUtilities.invokeLater(() -> application.openOrRaise(JasperApplication.HANDOFF));
            return LaunchRequest.Response.OK;
        };
    }

    /**
     * Wraps a login-item reconcile action so a run of equal values collapses to one call, and so
     * the call itself never runs on the EDT. {@code ConfigurationController.accept} publishes on
     * every saved config, not only when {@code system.run_in_background} changed, and on a packaged
     * Windows install the wrapped action can shell out to {@code reg add} with a ten-second
     * timeout -- long enough to freeze the UI if it ran inline.
     */
    static java.util.function.Consumer<Boolean> loginItemReconciler(java.util.function.Consumer<Boolean> reconcile) {
        Boolean[] lastApplied = {null};
        return enabled -> {
            if (enabled.equals(lastApplied[0])) return;
            lastApplied[0] = enabled;
            Thread.ofPlatform().name("jasper-login-item").daemon().start(() -> reconcile.accept(enabled));
        };
    }

    /** Makes the user's login items match the setting. Never throws; autostart is not worth a failed launch. */
    static void reconcileLoginItem(boolean enabled, String appPath, Path home) {
        if (enabled && (appPath == null || appPath.isBlank())) {
            LOG.log(System.Logger.Level.INFO,
                "Background residency is on, but this Jasper is not an installed package, so it will not start at login");
        }
        try {
            LoginItem.apply(LoginItem.plan(System.getProperty("os.name"), enabled, appPath, home));
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Could not reconcile the login item", failure);
        }
    }

}
