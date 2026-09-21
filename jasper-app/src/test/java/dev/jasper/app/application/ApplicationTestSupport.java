package dev.jasper.app.application;

import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.launch.*;
import dev.jasper.app.workspace.TerminalWindow;
import dev.jasper.terminal.session.TerminalSession;
import java.nio.file.Path;
import java.util.function.*;
import java.util.concurrent.Executor;

/** Package-local test access, excluded from production artifacts. */
public final class ApplicationTestSupport {
    public static ShellLauncher windowLauncher(Executor executor, Supplier<ConfigSnapshot> snapshots, BiFunction<Path, LaunchSettings, TerminalSession> start) { return JasperApplication.windowLauncher(executor, snapshots, start); }
    public static void windowClosed(JasperApplication application, TerminalWindow window) { application.windowClosed(window); }
    public static ShellLauncher windowLauncher(Executor executor, Supplier<ConfigSnapshot> snapshots, BiFunction<Path, LaunchSettings, TerminalSession> start, Path integration) { return JasperApplication.windowLauncher(executor, snapshots, start, integration); }
}
