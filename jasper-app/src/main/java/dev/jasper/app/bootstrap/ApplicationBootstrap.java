package dev.jasper.app.bootstrap;

import dev.jasper.app.application.JasperApplication;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.history.CommandHistory;
import dev.jasper.app.history.ShellHistoryIndex;
import dev.jasper.app.launch.ShellIntegrationScripts;
import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.platform.AppLog;
import dev.jasper.app.platform.ApplicationIcon;
import dev.jasper.app.platform.ConfigEditor;
import dev.jasper.app.platform.LoginItem;
import dev.jasper.app.residency.HandoffSocket;
import dev.jasper.app.residency.LaunchRequest;
import dev.jasper.app.snippets.SnippetStore;
import dev.jasper.app.bootstrap.StartupResources;
import java.nio.file.Path;
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
        int result = start(args, System.out, System.err, ApplicationBootstrap::prepareDesktop);
        if (result != 0) System.exit(result);
    }

    private static void prepareDesktop(ConfigService service, AppArguments options) {
        var acquired = new StartupResources();
        acquired.own(service);
        try {
            AppDirs dirs = AppDirs.resolve(System.getProperty("os.name"), System.getenv(),
                Path.of(System.getProperty("user.home")));
            AppLog log = acquired.own(AppLog.open(dirs.logs()));
            UnexpectedExceptions exceptions = acquired.own(installUnexpectedExceptionHandler());
            var endpoint = new AtomicReference<HandoffSocket>();
            Thread shutdown = Thread.ofPlatform().name("jasper-log-shutdown").unstarted(() -> {
                try { if (endpoint.get() != null) endpoint.get().close(); }
                finally { exceptions.close(); log.close(); }
            });
            Runtime.getRuntime().addShutdownHook(shutdown);
            acquired.own((AutoCloseable) () -> removeShutdownHook(shutdown));
            System.setProperty("apple.awt.application.appearance", "system");
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            SwingUtilities.invokeLater(() -> startDesktop(service, options, dirs, log, exceptions, shutdown, endpoint));
            // The posted EDT composition owns service; the process hook owns logging and the endpoint.
            acquired.transfer();
        } catch (RuntimeException | Error failure) {
            acquired.rollback(failure);
            LOG.log(System.Logger.Level.ERROR, "Application startup failed", failure);
        }
    }

    private static void startDesktop(ConfigService service, AppArguments options, AppDirs dirs, AppLog log,
                                     UnexpectedExceptions exceptions, Thread shutdown, AtomicReference<HandoffSocket> endpoint) {
        try {
            boolean resident = service.initialState().snapshot().backgroundEnabled();
            Path home = Path.of(System.getProperty("user.home"));
            String appPath = System.getProperty("jpackage.app-path");
            boolean standalone = options.configOverride() != null;
            if (!standalone && options.background() && !resident) {
                reconcileLoginItem(false, appPath, home);
                LOG.log(System.Logger.Level.INFO,
                    "Started with --background while background.enabled is off; exiting");
                service.close(); System.exit(0); return;
            }
            compose(service, () -> new CommandHistory(dirs.commandHistory()),
                history -> createApplication(service, history, dirs), application -> {
                    if (!standalone)
                        application.loginItems(loginItemReconciler(enabled -> reconcileLoginItem(enabled, appPath, home)));
                    if (!residentRole(options, resident)) return null;
                    Path source = HandoffSocket.codeSource();
                    var bound = HandoffSocket.bind(dirs.daemonSocket(), dirs.daemonToken(), dirs.daemonLock(),
                        handoffHandler(application, source, HandoffSocket.lastModified(source), home));
                    endpoint.set(bound);
                    return bound;
                }, application -> {
                    if (!options.background()) { application.newWindow(home); return; }
                    if (!application.resident()) {
                        LOG.log(System.Logger.Level.WARNING, standalone
                            ? "Started with --config and --background, but a --config launch is always standalone and cannot be resident; exiting"
                            : "Started with --background but the handoff endpoint is unavailable; exiting");
                        application.quit();
                    } else application.warmUp();
                });
        } catch (RuntimeException | Error failure) {
            // Also covers failure before composition took ownership (for example initial role resolution).
            var remaining = new StartupResources(); remaining.own(service); remaining.rollback(failure);
            LOG.log(System.Logger.Level.ERROR, "Application startup failed", failure);
            closeLogAfterStartupFailure(log, () -> { exceptions.close(); removeShutdownHook(shutdown); });
        }
    }

    private static JasperApplication createApplication(ConfigService service, CommandHistory history, AppDirs dirs) {
        var acquired = new StartupResources();
        try {
            ApplicationIcon.installTaskbarIcon();
            Path integrationDir = null;
            try { integrationDir = ShellIntegrationScripts.install(dirs.shellIntegration()); }
            catch (java.io.IOException failure) {
                LOG.log(System.Logger.Level.WARNING,
                    "Shell integration scripts could not be installed; integration is off", failure);
            }
            var shellHistory = acquired.own(ShellHistoryIndex.discovered());
            var snippets = acquired.own(new SnippetStore(dirs.snippets(), new ConfigEditor()::open));
            var application = new JasperApplication(service, null, history, dirs.buddyState(),
                () -> System.exit(0), shellHistory, snippets, integrationDir);
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
            acquired.own((AutoCloseable) application::quit);
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
            Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
            Thread.UncaughtExceptionHandler installed = (thread, failure) ->
                LOG.log(System.Logger.Level.ERROR, "Unexpected application failure", failure);
            Thread.setDefaultUncaughtExceptionHandler(installed);
            return new UnexpectedExceptions(previous, installed);
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
        private final Thread.UncaughtExceptionHandler installed;
        private boolean closed;

        private UnexpectedExceptions(Thread.UncaughtExceptionHandler previous,
                                     Thread.UncaughtExceptionHandler installed) {
            this.previous = previous;
            this.installed = installed;
        }

        @Override public void close() {
            synchronized (ApplicationBootstrap.class) {
                if (closed) return;
                closed = true;
                if (Thread.getDefaultUncaughtExceptionHandler() == installed) {
                    Thread.setDefaultUncaughtExceptionHandler(previous);
                }
            }
        }
    }

    /** Startup boundary: parsing, the handoff attempt and the first read finish before the desktop callback runs. */
    static int start(String[] args, java.io.PrintStream out, java.io.PrintStream error,
                     java.util.function.BiConsumer<ConfigService, AppArguments> launch) {
        AppArguments options;
        try { options = AppArguments.parse(args, Path.of(System.getProperty("user.dir"))); }
        catch (IllegalArgumentException failure) { error.println(failure.getMessage()); return 2; }
        if (options.help()) { out.println(AppArguments.USAGE); return 0; }
        String os = System.getProperty("os.name");
        Path home = Path.of(System.getProperty("user.home"));
        AppDirs dirs = AppDirs.resolve(os, System.getenv(), home);
        // Before any toolkit initialization: a handed-off launch must cost almost nothing and must
        // not settle a second icon into the Dock on its way out.
        if (handsOff(options, dirs)) return 0;
        Path file = options.configOverride() == null ? dirs.configFile() : options.configOverride();
        var acquired = new StartupResources();
        ConfigService service = acquired.own(new ConfigService(file, os.startsWith("Mac")));
        try {
            launch.accept(service, options);
            acquired.transfer();
            return 0;
        } catch (RuntimeException | Error failure) { acquired.rollback(failure); throw failure; }
    }

    /**
     * True when a resident process accepted this launch and there is nothing left to do. A launch
     * pointed at another configuration file never hands off, because the resident process is
     * holding a different one; neither does a resident process starting up.
     */
    static boolean handsOff(AppArguments options, AppDirs dirs) {
        if (options.background() || options.configOverride() != null) return false;
        Path source = HandoffSocket.codeSource();
        return HandoffSocket.handOff(dirs.daemonSocket(), dirs.daemonToken(), source, HandoffSocket.lastModified(source));
    }

    /** Residency needs the shared endpoint, so a launch pointed at another configuration never
     *  claims it: a later plain launch would otherwise be handed a window built from that file. */
    static boolean residentRole(AppArguments options, boolean enabled) {
        return enabled && options.configOverride() == null;
    }

    /** True when a request comes from a different build than this one. A resident process must not
     *  serve windows built from code it is not running. */
    static boolean stale(LaunchRequest request, Path source, long modified) {
        return !request.codeSource().equals(source == null ? Path.of("") : source)
            || request.codeSourceModified() != modified;
    }

    /**
     * The resident process's side of a handoff. A stale request also ends residency: the endpoint
     * goes away with the reply, so the last window closing must run the normal bounded shutdown
     * rather than leaving a process with nothing to reach it and no cleanup on the way out.
     */
    static java.util.function.Function<LaunchRequest, LaunchRequest.Response> handoffHandler(
            JasperApplication application, Path source, long modified, Path home) {
        return request -> {
            if (stale(request, source, modified)) {
                SwingUtilities.invokeLater(() -> application.endpointReleased());
                return LaunchRequest.Response.STALE;
            }
            SwingUtilities.invokeLater(() -> application.openOrRaise(home));
            return LaunchRequest.Response.OK;
        };
    }

    /**
     * Wraps a login-item reconcile action so a run of equal values collapses to one call, and so
     * the call itself never runs on the EDT. {@code ConfigurationController.accept} publishes on
     * every saved config, not only when {@code background.enabled} changed, and on a packaged
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
