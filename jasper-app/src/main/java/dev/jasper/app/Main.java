package dev.jasper.app;

import java.nio.file.Path;
import javax.swing.SwingUtilities;

public final class Main {
    private static final System.Logger LOG = System.getLogger(Main.class.getName());

    private Main() {}

    public static void main(String[] args) {
        int result = start(args, System.out, System.err, (service, options) -> {
            AppDirs dirs = AppDirs.resolve(System.getProperty("os.name"), System.getenv(),
                Path.of(System.getProperty("user.home")));
            AppLog log = AppLog.open(dirs.logs());
            UnexpectedExceptions exceptions;
            try { exceptions = installUnexpectedExceptionHandler(); }
            catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.ERROR, "Application startup failed", failure);
                service.close();
                log.close();
                return;
            }
            Thread shutdown = Thread.ofPlatform().name("jasper-log-shutdown").unstarted(() -> {
                exceptions.close();
                log.close();
            });
            try {
                Runtime.getRuntime().addShutdownHook(shutdown);
                System.setProperty("apple.awt.application.appearance", "system");
                System.setProperty("apple.laf.useScreenMenuBar", "true");
                SwingUtilities.invokeLater(() -> {
                    CommandHistory history = null;
                    JasperApplication application = null;
                    HandoffSocket endpoint = null;
                    try {
                        boolean resident = service.initialState().snapshot().backgroundEnabled();
                        Path home = Path.of(System.getProperty("user.home"));
                        String appPath = System.getProperty("jpackage.app-path");
                        reconcileLoginItem(resident, appPath, home);
                        // A login item that outlived the setting: leave rather than sit resident.
                        if (options.background() && !resident) {
                            LOG.log(System.Logger.Level.INFO,
                                "Started with --background while background.enabled is off; exiting");
                            service.close();
                            System.exit(0);
                            return;
                        }
                        history = new CommandHistory(dirs.commandHistory());
                        ApplicationIcon.installTaskbarIcon();
                        Path integrationDir = null;
                        try {
                            integrationDir = ShellIntegrationScripts.install(dirs.shellIntegration());
                        } catch (java.io.IOException failure) {
                            LOG.log(System.Logger.Level.WARNING,
                                "Shell integration scripts could not be installed; integration is off", failure);
                        }
                        application = new JasperApplication(service, null, history, dirs.buddyState(),
                            () -> System.exit(0), ShellHistoryIndex.discovered(),
                            new SnippetStore(dirs.snippets(), new ConfigEditor()::open), integrationDir);
                        // The login item tracks the setting while Jasper runs; residency does not.
                        application.loginItems(enabled -> reconcileLoginItem(enabled, appPath, home));
                        if (resident) {
                            JasperApplication owner = application;
                            Path source = HandoffSocket.codeSource();
                            long modified = HandoffSocket.lastModified(source);
                            endpoint = HandoffSocket.bind(dirs.daemonSocket(), dirs.daemonToken(), dirs.daemonLock(),
                                request -> {
                                    // An older build must not serve windows built from newer code.
                                    if (!request.codeSource().equals(source == null ? Path.of("") : source)
                                            || request.codeSourceModified() != modified) {
                                        return LaunchRequest.Response.STALE;
                                    }
                                    SwingUtilities.invokeLater(() -> owner.openOrRaise(home));
                                    return LaunchRequest.Response.OK;
                                });
                            // Residency needs the endpoint: without it a windowless JVM has nothing
                            // holding it alive and nothing to be reached through.
                            application.residency(endpoint != null);
                        }
                        if (options.background()) {
                            if (!application.resident()) {
                                // Unreachable without an endpoint: exiting beats popping a window
                                // onto the screen of someone who asked for a background process.
                                LOG.log(System.Logger.Level.WARNING,
                                    "Started with --background but the handoff endpoint is unavailable; exiting");
                                application.quit();
                                return;
                            }
                            application.warmUp();
                        } else application.newWindow(Path.of(System.getProperty("user.home")));
                    }
                    catch (RuntimeException failure) {
                        LOG.log(System.Logger.Level.ERROR, "Application startup failed", failure);
                        if (endpoint != null) endpoint.close();
                        if (application != null) application.quit();
                        else if (history != null) history.close();
                        service.close();
                        closeLogAfterStartupFailure(log, () -> {
                            exceptions.close();
                            removeShutdownHook(shutdown);
                        });
                    }
                });
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.ERROR, "Application startup failed", failure);
                service.close();
                exceptions.close();
                log.close();
                removeShutdownHook(shutdown);
            }
        });
        if (result != 0) System.exit(result);
    }

    static UnexpectedExceptions installUnexpectedExceptionHandler() {
        synchronized (Main.class) {
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
            synchronized (Main.class) {
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
        ConfigService service = new ConfigService(file, os.startsWith("Mac"));
        try { launch.accept(service, options); }
        catch (RuntimeException failure) { service.close(); throw failure; }
        return 0;
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

    /** Makes the user's login items match the setting. Never throws; autostart is not worth a failed launch. */
    static void reconcileLoginItem(boolean enabled, String appPath, Path home) {
        if (enabled && (appPath == null || appPath.isBlank())) {
            LOG.log(System.Logger.Level.INFO,
                "Background residency is on, but this Jasper is not an installed package, so it will not start at login");
        }
        LoginItem.apply(LoginItem.plan(System.getProperty("os.name"), enabled, appPath, home));
    }

    /** The window title for a shell-reported title; "Jasper" when the shell has not set one. */
    static String windowTitle(String shellTitle) {
        return shellTitle == null || shellTitle.isBlank() ? "Jasper" : shellTitle;
    }
}
