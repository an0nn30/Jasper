package dev.jasper.app.bootstrap;

import dev.jasper.app.platform.AppLog;
import dev.jasper.app.application.JasperApplication;
import dev.jasper.app.residency.LaunchRequest;
import java.nio.file.Path;

/** Package-local test access, excluded from production artifacts. */
public final class BootstrapTestSupport {
    public static AutoCloseable installUnexpectedExceptionHandler() { return ApplicationBootstrap.installUnexpectedExceptionHandler(); }
    public static void closeLogAfterStartupFailure(AppLog log, Runnable after) { ApplicationBootstrap.closeLogAfterStartupFailure(log, after); }
    public static java.util.function.Function<LaunchRequest, LaunchRequest.Response> handoffHandler(JasperApplication app, Path source, long modified, Path home) { return ApplicationBootstrap.handoffHandler(app, source, modified, home); }
}
