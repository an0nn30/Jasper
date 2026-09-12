package dev.moray.app;

import java.nio.file.Path;
import javax.swing.SwingUtilities;

public final class Main {
    private static final System.Logger LOG = System.getLogger(Main.class.getName());

    private Main() {}

    public static void main(String[] args) {
        int result = start(args, System.out, System.err, service -> {
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
            Thread shutdown = Thread.ofPlatform().name("moray-log-shutdown").unstarted(() -> {
                exceptions.close();
                log.close();
            });
            try {
                Runtime.getRuntime().addShutdownHook(shutdown);
                System.setProperty("apple.awt.application.appearance", "system");
                System.setProperty("apple.laf.useScreenMenuBar", "true");
                SystemAppearance source = SystemAppearance.production();
                SwingUtilities.invokeLater(() -> {
                    try { new MorayApplication(service, source).newWindow(Path.of(System.getProperty("user.home"))); }
                    catch (RuntimeException failure) {
                        LOG.log(System.Logger.Level.ERROR, "Application startup failed", failure);
                        source.close(); service.close();
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
        Thread.ofPlatform().name("moray-startup-cleanup").daemon().start(() -> {
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

    /** Startup boundary: parsing and the first read finish before the desktop callback runs. */
    static int start(String[] args, java.io.PrintStream out, java.io.PrintStream error,
                     java.util.function.Consumer<ConfigService> launch) {
        AppArguments options;
        try { options = AppArguments.parse(args, Path.of(System.getProperty("user.dir"))); }
        catch (IllegalArgumentException failure) { error.println(failure.getMessage()); return 2; }
        if (options.help()) { out.println(AppArguments.USAGE); return 0; }
        String os = System.getProperty("os.name");
        Path home = Path.of(System.getProperty("user.home"));
        AppDirs dirs = AppDirs.resolve(os, System.getenv(), home);
        Path file = options.configOverride() == null ? dirs.configFile() : options.configOverride();
        ConfigService service = new ConfigService(file, dirs.themes(), os.startsWith("Mac"));
        try { launch.accept(service); }
        catch (RuntimeException failure) { service.close(); throw failure; }
        return 0;
    }

    /** The window title for a shell-reported title; "Moray" when the shell has not set one. */
    static String windowTitle(String shellTitle) {
        return shellTitle == null || shellTitle.isBlank() ? "Moray" : shellTitle;
    }
}
