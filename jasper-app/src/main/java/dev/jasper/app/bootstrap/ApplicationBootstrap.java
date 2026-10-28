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
import dev.jasper.app.startup.StartupSteps;
import dev.jasper.app.startup.StartupTrace;
import dev.jasper.app.splash.Splash;
import dev.jasper.app.splash.SplashPainter;
import dev.jasper.app.platform.BuildInfo;
import dev.jasper.app.presentation.Branding;
import dev.jasper.app.presentation.Presentation;
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

/** Resolves desktop-free launch roles, then assembles the application's resource owners. */
public final class ApplicationBootstrap {
    private static final System.Logger LOG = System.getLogger(ApplicationBootstrap.class.getName());
    private ApplicationBootstrap() {}

    public static void main(String[] args) {
        StartupTrace.current().mark(StartupTrace.Milestone.MAIN_ENTERED);
        int result = start(args, System.out, System.err, environmentDirs(), ApplicationBootstrap::prepare, ApplicationBootstrap::prepareDesktop);
        if (result != 0) System.exit(result);
    }

    /**
     * What the boundary hands the desktop: the service, the arguments, the home, the log (null without the pre-pass), the
     * selection and the home's owner, whose lock and endpoints this process holds (null only where a test builds one).
     */
    record Startup(ConfigService service, AppArguments options, AppDirs dirs, AppLog log,
                   dev.jasper.app.plugins.PluginSelection selection, Instance instance) { }

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
        var opened = new AtomicReference<StartupSteps>(StartupSteps.NONE);
        try {
            AppDirs dirs = startup.dirs();
            AppLog log = acquired.own(Objects.requireNonNull(startup.log(), "the desktop needs the pre-pass's log"));
            UnexpectedExceptions exceptions = acquired.own(installUnexpectedExceptionHandler());
            Instance instance = Objects.requireNonNull(startup.instance(), "the desktop needs the home's owner");
            // A failed start closes the endpoints and exits; the lock goes with the process, never before it (plan Ruling 24).
            acquired.own((AutoCloseable) instance::closeEndpoints);
            var attempt = startup.selection().attempt();
            Thread shutdown = Thread.ofPlatform().name("jasper-log-shutdown").unstarted(() -> {
                try {
                    // An orderly quit before the look proved itself clears its pending mark; any other exit leaves it (spec 4.6).
                    try { attempt.exiting(); } catch (RuntimeException unrecorded) { LOG.log(System.Logger.Level.WARNING, "Could not update the presentation attempt record", unrecorded); }
                    instance.closeEndpoints();
                }
                finally { exceptions.close(); log.close(); }
            });
            Runtime.getRuntime().addShutdownHook(shutdown);
            acquired.own((AutoCloseable) () -> removeShutdownHook(shutdown));
            applyDesktop(startup.selection().presentation(), service.initialState().snapshot(), System.getProperty("os.name").startsWith("Mac"));
            // Spec 10.11, as soon as AWT may start: the look's icons and, on a start that will open a window, the default or branded splash.
            opened.set(openSplash(startup, GraphicsEnvironment.isHeadless()));
            // Off the EDT and before the first window: an optional prewarm of the built-in icons.
            dev.jasper.ui.icons.IconRegistry.prepareBuiltIns();
            StartupSteps steps = opened.get();
            SwingUtilities.invokeLater(() -> startDesktop(startup, dirs, log, exceptions, shutdown, steps));
            // The posted EDT composition owns service; the process hook owns logging and the endpoints; the instance lock
            // lasts as long as the process.
            acquired.transfer();
        } catch (RuntimeException | Error failure) {
            acquired.rollback(failure);
            // A splash already posted opens first, then closes: start-up failed (spec 10.11). Nothing without one.
            abandon(opened.get(), SwingUtilities::invokeLater);
            LOG.log(System.Logger.Level.ERROR, "Application startup failed", failure);
            exitAfterFailedStart();
        }
    }

    /**
     * Spec 10.11, once the desktop properties and the title-bar style are committed: a branded look's icons become the
     * application's on every launch (so a window a resident opens later carries them too), and on a start that will
     * open a window the splash's files are decoded here, off the EDT, and its window is posted ahead of the composition,
     * with the Dock or taskbar icon. Returns the steps start-up reports to the splash, or {@link StartupSteps#NONE}. A
     * splash whose files cannot be drawn is skipped and logged; start-up goes on.
     */
    static StartupSteps openSplash(Startup startup, boolean headless) {
        return openSplash(startup, headless, config -> SplashSelection.theme(config, startup.selection()));
    }

    /** The artwork selector is lazy: launch exclusions and explicit branding precede any appearance observation. */
    static StartupSteps openSplash(Startup startup, boolean headless,
                                  Function<dev.jasper.app.config.ConfigSnapshot, String> selectTheme) {
        Presentation presentation = startup.selection().presentation();
        Branding branding = presentation.branding();
        String product = branding.present() ? branding.product() : "Jasper";
        long began = System.nanoTime();
        ApplicationIcon.useLook(branding.iconFiles());
        if (!splashWanted(startup.options(), headless, true)) return StartupSteps.NONE;
        SplashPainter painter;
        try { painter = branding.present()
            ? SplashPainter.of(splashContent(presentation, BuildInfo.current()), screenScale())
            : SplashPainter.themed(selectTheme.apply(startup.service() == null ? dev.jasper.app.config.ConfigSnapshot.defaults()
                : startup.service().initialState().snapshot()),
                BuildInfo.current().buildLine(), screenScale()); }
        catch (RuntimeException undrawable) {
            LOG.log(System.Logger.Level.WARNING, "The " + product + " splash cannot be drawn; starting without it", undrawable);
            return StartupSteps.NONE;
        }
        var splash = new Splash(painter, ApplicationIcon.images(), startup.service() == null
            || startup.service().initialState().snapshot().animations().enabled());
        var steps = StartupSteps.of(product, startup.selection().loading().size(), splash::show, splash::close);
        SwingUtilities.invokeLater(() -> {
            ApplicationIcon.installTaskbarIcon();
            splash.open(steps.current());
        });
        StartupTrace.current().phase("splash", began);
        return steps;
    }

    /**
     * Whether this start shows the default or branded splash: one that will open a window, so not headless,
     * {@code --background}, {@code --no-window} or {@code --safe-mode} (a handed-off launch never gets here).
     */
    static boolean splashWanted(AppArguments options, boolean headless, boolean branded) {
        return !headless && !options.background() && !options.noWindow() && !options.safeMode();
    }

    /** What the splash draws: the look's branding and faces, the version line and About's copyright line (spec 10.11). */
    static SplashPainter.Content splashContent(Presentation presentation, BuildInfo build) {
        Branding branding = presentation.branding();
        var faces = presentation.faces();
        boolean sharedIcon = branding.iconFiles().isEmpty();
        return new SplashPainter.Content(branding.product(), build.buildLine(), BuildInfo.COPYRIGHT, sharedIcon ? ApplicationIcon.defaultPng(64) : branding.icon(64),
            sharedIcon ? ApplicationIcon.defaultPng(128) : branding.icon(128),
            branding.splashArt(), faces.ui().map(ui -> ui.semibold().bytes()), faces.ui().map(ui -> ui.regular().bytes()),
            faces.data().map(data -> data.regular().bytes()));
    }

    /**
     * Ends the steps of a start that failed (spec 10.11) through {@code edt}, the event thread: a splash already posted
     * opens first, then closes. Without a splash nothing is posted, so a failure before anything touched AWT does not
     * start the toolkit. {@code active()} only ever turns false, so a stale read off the EDT at worst posts a second,
     * harmless {@code done()}.
     */
    static void abandon(StartupSteps steps, java.util.concurrent.Executor edt) {
        if (steps.active()) edt.execute(steps::done);
    }

    /** Transfers successful startup to the window paint callback; failed composition closes the splash. EDT. */
    static void presenting(StartupSteps steps, Runnable start) {
        try { start.run(); }
        catch (RuntimeException | Error failure) { steps.done(); throw failure; }
    }

    /** The main screen's device scale, for the splash art's first raster; 1 without a screen. */
    private static int screenScale() {
        try {
            return (int) Math.ceil(GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                .getDefaultConfiguration().getDefaultTransform().getScaleX() - 1e-6);
        } catch (RuntimeException | Error noScreen) {
            return 1;
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

    /**
     * The look's flags, committed before AWT (spec 9.1): the macOS menu bar, then, before the look and feel and any
     * window, the title bar style, a restart-only choice like residency, under the look's system title bar.
     *
     * @param presentation the process's presentation, whose committed flags apply
     * @param snapshot the configuration read at start
     * @param mac whether this is macOS
     * @return the title bar style configured
     */
    static dev.jasper.app.platform.TitleBarStyle applyDesktop(dev.jasper.app.presentation.Presentation presentation,
                                                              dev.jasper.app.config.ConfigSnapshot snapshot, boolean mac) {
        var desktop = presentation.committedDesktop();
        configureDesktopProperties(desktop);
        var style = dev.jasper.app.platform.TitleBarStyle.choose(mac, snapshot.nativeDecorations(),
            snapshot.viewMode() == dev.jasper.app.config.ViewMode.COMPACT, desktop.nativeTitle());
        style.configure();
        return style;
    }

    /** The default look's desktop properties. */
    static void configureDesktopProperties() { configureDesktopProperties(dev.jasper.app.presentation.DesktopFlags.STANDARD); }

    /**
     * macOS reads these settings during toolkit/LAF initialization; never change them on reload. {@code flags} decide
     * whether menus use the screen menu bar or a row in each window (spec 10.1).
     */
    static void configureDesktopProperties(dev.jasper.app.presentation.DesktopFlags flags) {
        System.setProperty("apple.awt.application.appearance", "system");
        System.setProperty("apple.laf.useScreenMenuBar", Boolean.toString(flags.screenMenuBar()));
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

    private static void startDesktop(Startup startup, AppDirs dirs, AppLog log, UnexpectedExceptions exceptions,
                                     Thread shutdown, StartupSteps steps) {
        ConfigService service = startup.service(); AppArguments options = startup.options(); Instance instance = startup.instance();
        try {
            StartupTrace.current().mark(StartupTrace.Milestone.TOOLKIT_READY);
            boolean resident = residentRole(options, service.initialState().snapshot().backgroundEnabled());
            Path home = Path.of(System.getProperty("user.home"));
            String appPath = System.getProperty("jpackage.app-path");
            if (options.background() && !resident) {
                if (options.residencyAllowed()) reconcileLoginItem(false, appPath, home);
                if (instance.declineIfIdle()) {
                    LOG.log(System.Logger.Level.INFO, "Started with --background, but this launch is not resident; exiting");
                    service.close(); System.exit(0); return;
                }
                // A launch already handed this process a request it answered: it starts as an ordinary launch and
                // carries the request out (plan Ruling 20). On macOS it began without a Dock icon; its first window
                // brings it back, as a resident's does.
                LOG.log(System.Logger.Level.INFO, "Started with --background, not resident, holding a request; starting as usual");
            }
            boolean warmUp = options.background() && resident;
            presenting(steps, () -> compose(service, () -> new CommandHistory(dirs.commandHistory()),
                history -> {
                    JasperApplication application = createApplication(service, history, dirs, options, instance, startup.selection(), steps);
                    exceptions.forwardTo(application::unexpected);
                    return application;
                }, application -> {
                    if (options.residencyAllowed())
                        application.loginItems(loginItemReconciler(enabled -> reconcileLoginItem(enabled, appPath, home)));
                    application.residency(resident);
                    // From here the application closes the endpoints at quit, as the process hook does at exit.
                    application.onShutdown(instance::closeEndpoints);
                    // The requests held since the lock was taken run now, in arrival order; later handoffs are answered
                    // once the event thread has taken them, and none once the application quits (spec 5.3).
                    instance.serve(handoffHandler(application), application::acceptsRequests, SwingUtilities::invokeLater,
                        application.commands());
                }, application -> {
                    if (options.noWindow()) {
                        serveWithoutWindow(application, instance.commands());
                        return;
                    }
                    // A --background start that is not resident gets here only to carry out a request: it presents as usual.
                    if (attemptBeforePresenting(options, application.resident() || !warmUp, GraphicsEnvironment.isHeadless()))
                        application.presentationAttempt().pending();
                    if (warmUp) {
                        application.warmUp(() -> StartupTrace.current().mark(StartupTrace.Milestone.WARMED_UP));
                        return;
                    }
                    steps.workspace();
                    long window = System.nanoTime();
                    // The first window takes the first directory the launch names; its other paths open in it (plan Ruling 13).
                    application.openFirstWindow(options.paths());
                    StartupTrace.current().phase("first window", window);
                }));
        } catch (RuntimeException | Error failure) {
            abandon(steps, Runnable::run);
            // Also covers failure before composition took ownership (for example initial role resolution). The lock goes
            // with the process, never before it (plan Ruling 24).
            var remaining = new StartupResources(); remaining.own(service); remaining.own((AutoCloseable) instance::closeEndpoints);
            remaining.rollback(failure);
            LOG.log(System.Logger.Level.ERROR, "Application startup failed", failure);
            closeLogAfterStartupFailure(log, () -> { exceptions.close(); removeShutdownHook(shutdown); exitAfterFailedStart(); });
        }
    }

    /**
     * Ends a process whose start failed after it claimed its home, once the event thread has run what was posted before
     * (the splash closing; at most 2 s): the instance lock is released with the process, so no later launch owns the home
     * while this one still shuts down (plan Ruling 24).
     */
    private static void exitAfterFailedStart() {
        Thread.ofPlatform().name("jasper-failed-start").start(() -> {
            var drained = new java.util.concurrent.CountDownLatch(1);
            SwingUtilities.invokeLater(drained::countDown);
            try { drained.await(2, java.util.concurrent.TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            System.exit(1);
        });
    }

    private static JasperApplication createApplication(ConfigService service, CommandHistory history, AppDirs dirs,
                                                       AppArguments options, Instance instance,
                                                       dev.jasper.app.plugins.PluginSelection selection, StartupSteps steps) {
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
            steps.configuration();
            var application = new JasperApplication(service, null, history, dirs.buddyState(),
                () -> System.exit(0), integrationDir, dirs.themes(), selection);
            // A look that fell back at start runs without its branding (spec 4.7 and 9.1): Jasper's icons and default Meadow splash.
            ApplicationIcon.useLook(application.presentation().branding().iconFiles());
            ApplicationIcon.installTaskbarIcon();
            application.startupSteps(steps);
            Path source = HandoffSocket.codeLocation();
            // A restart replays this launch's own arguments, without its paths (workspace-tabs spec 5.3).
            application.launchArguments(options.given());
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
                application.backgroundOnQuit(new DockControl(dockShow(MacDock::show, ApplicationIcon::installTaskbarIcon),
                    MacDock::prepare, MacDock::poweringOff), !options.background());
            application.subcommandList(dirs.subcommandList());
            // New local shells name the socket this owner serves (JASPER_COMMAND_SOCKET).
            if (instance.commandSocket() != null) application.commandSocket(instance.commandSocket());
            application.startPlugins(dirs);
            StartupTrace.current().phase("application", began);
            acquired.transfer();
            return application;
        } catch (RuntimeException | Error failure) { acquired.rollback(failure); throw failure; }
    }

    /**
     * Spec 10.11: switching the activation policy gives a packaged Jasper a new Dock item, which shows the bundle's icon,
     * so each switch into the Dock sets the look's icon again ({@code reapply}). A {@code --background} resident's first
     * window and a reopen after Quit both come back this way.
     */
    static Consumer<Boolean> dockShow(Consumer<Boolean> show, Runnable reapply) {
        Objects.requireNonNull(show, "show");
        Objects.requireNonNull(reapply, "reapply");
        return visible -> {
            show.accept(visible);
            if (visible) reapply.run();
        };
    }

    /**
     * EDT acquisition transaction; factories keep resource creation explicit and rollback testable. {@code ready} runs
     * once the application exists, before {@code present}: residency, endpoint cleanup and the requests the owner has
     * held since it took the lock.
     */
    static JasperApplication compose(ConfigService service, Supplier<CommandHistory> openHistory,
                                     Function<CommandHistory, JasperApplication> openApplication,
                                     Consumer<JasperApplication> ready,
                                     Consumer<JasperApplication> present) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Desktop composition requires the EDT");
        var acquired = new StartupResources();
        acquired.own(service);
        try {
            CommandHistory history = acquired.own(openHistory.get());
            JasperApplication application = openApplication.apply(history);
            acquired.own((AutoCloseable) application::quitNow);
            acquired.release(history); acquired.release(service);
            ready.accept(application);
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

    /** The home this process's environment names: {@code jasper.home}, else {@code JASPER_HOME}, else the OS default. */
    static AppDirs environmentDirs() {
        return AppDirs.resolve(System.getProperty("os.name"), System.getenv(), Path.of(System.getProperty("user.home")),
            System.getProperty("jasper.home"));
    }

    /** The test entry without the pre-pass: the owner's lock and endpoints are released once {@code launch} returns. */
    static int start(String[] args, java.io.PrintStream out, java.io.PrintStream error, AppDirs dirs,
                     java.util.function.BiConsumer<ConfigService, AppArguments> launch) {
        return start(args, out, error, dirs, startup -> {
            try { launch.accept(startup.service(), startup.options()); }
            finally { startup.instance().close(); }
        });
    }

    /** The test entry without the pre-pass; the caller closes the startup's instance. */
    static int start(String[] args, java.io.PrintStream out, java.io.PrintStream error, AppDirs dirs, Consumer<Startup> launch) {
        return start(args, out, error, dirs, Preparation.NONE, launch);
    }

    /**
     * Startup boundary: parsing, claiming {@code dirs} (workspace-tabs spec 5.1 to 5.3: the instance lock or a handoff,
     * before any toolkit initialization) and the first read finish before the desktop callback runs. Returns the exit code.
     */
    static int start(String[] args, java.io.PrintStream out, java.io.PrintStream error, AppDirs dirs, Preparation prepare,
                     Consumer<Startup> launch) {
        AppArguments options;
        try { options = AppArguments.parse(args, Path.of(System.getProperty("user.dir"))); }
        catch (IllegalArgumentException failure) { error.println(failure.getMessage()); return 2; }
        if (options.help()) { out.println(AppArguments.USAGE); return 0; }
        String os = System.getProperty("os.name");
        // AppKit reads this as the toolkit initialises.
        launchProperties(options.background(), os, System.getProperty("jpackage.app-path")).forEach(System::setProperty);
        options = options.withHomeOverride(dirs.overridden());
        // Before any toolkit initialization: a handed-off launch must cost almost nothing and must
        // not settle a second icon into the Dock on its way out.
        var claim = SingleInstance.claim(options, dirs, SingleInstance.Timing.STANDARD, LaunchFailure.reporter(error));
        if (claim instanceof SingleInstance.Claim.Done done) return done.code();
        Instance instance = ((SingleInstance.Claim.Owner) claim).instance();
        Path file = options.configOverride() == null ? dirs.configFile() : options.configOverride();
        var acquired = new StartupResources();
        acquired.own(instance);
        Prepared prepared;
        try { prepared = prepare.prepare(dirs, options); }
        catch (RuntimeException | Error failure) { acquired.rollback(failure); throw failure; }
        if (prepared.log() != null) acquired.own(prepared.log());
        StartupTrace.current().look(prepared.selection().presentation().look());
        long read = System.nanoTime();
        ConfigService service;
        try { service = acquired.own(new ConfigService(file, os.startsWith("Mac"), prepared.selection().presentation().defaults())); }
        catch (RuntimeException | Error failure) { acquired.rollback(failure); throw failure; }
        StartupTrace.current().phase("config", read);
        StartupTrace.current().mark(StartupTrace.Milestone.CONFIG_LOADED);
        try {
            launch.accept(new Startup(service, options, dirs, prepared.log(), prepared.selection(), instance));
            acquired.transfer();
            return 0;
        } catch (RuntimeException | Error failure) { acquired.rollback(failure); throw failure; }
    }

    /** Residency applies only to the default home launched without start-up flags, and only when it is on (spec 5.2). */
    static boolean residentRole(AppArguments options, boolean enabled) {
        return enabled && options.residencyAllowed();
    }

    /** How long a --no-window Jasper that is not resident waits for its first call. */
    static final int NO_CALL_GRACE_MILLIS = 30_000;

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
     * True once the application jar this process was loaded from has changed on disk (a new build was installed under
     * it) or gone. An unknown code location never counts. Any thread; one file stat per call.
     */
    static java.util.function.BooleanSupplier replacedOnDisk(Path source, long modified) {
        return () -> source != null && HandoffSocket.lastModified(source) != modified;
    }

    /** The owner's side of a handoff (workspace-tabs spec 5.3): carries the request out. EDT. */
    static BiConsumer<LaunchRequest, Boolean> handoffHandler(JasperApplication application) {
        return (request, updated) -> application.handedOff(request.paths(), request.ignoredFlags(), updated);
    }

    /**
     * Serializes login-item reconciliation off the calling thread, coalescing pending saved choices
     * to the latest value. {@code ConfigurationController.accept} publishes on every saved config,
     * not only when {@code system.run_in_background} changed, and on a packaged Windows install the
     * wrapped action can shell out to {@code reg add} with a ten-second timeout.
     */
    static java.util.function.Consumer<Boolean> loginItemReconciler(java.util.function.Consumer<Boolean> reconcile) {
        return new LoginItemUpdates(reconcile, task ->
            Thread.ofPlatform().name("jasper-login-item").daemon().start(task));
    }

    /** Makes the user's login items match the setting. Never throws; autostart is not worth a failed launch. */
    static void reconcileLoginItem(boolean enabled, String appPath, Path home) {
        if (enabled && (appPath == null || appPath.isBlank())) {
            LOG.log(System.Logger.Level.INFO,
                "Background residency is on, but this Jasper is not an installed package, so it will not start at login");
        }
        try {
            LoginItem.apply(LoginItem.plan(System.getProperty("os.name"), enabled, appPath, home, System.getenv()));
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Could not reconcile the login item", failure);
        }
    }

}
