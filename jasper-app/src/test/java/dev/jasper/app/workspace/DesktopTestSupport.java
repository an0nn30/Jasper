package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.session.SessionLaunchOptions;

import dev.jasper.terminal.session.TerminalSession;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;

public final class DesktopTestSupport {
    private static final List<WindowContent> OWNERS = new ArrayList<>();
    public static final Path HOME = Path.of(System.getProperty("user.home"));
    public static TerminalSession shell(Path directory) {
        try {
            return TerminalSession.start(SessionLaunchOptions.builder().command(List.of("/bin/sh", "-c", "printf 'alpha alpha\\n'; read answer")).environment(System.getenv()).workingDirectory(directory).grid(new GridSize(80, 24)).scrollback(100).build());
        } catch (Exception e) { throw new CompletionException(e); }
    }
    static void setStatusMetadata(WindowStatusBar status, String shell, String directory, String dimensions, boolean running, boolean integration) {
        // Treat hostile display text as a remote string, never as a platform filesystem path.
        boolean remote = directory.startsWith("<html>");
        var snapshot = new dev.jasper.app.terminals.PaneSnapshot("", remote ? Optional.empty() : Optional.of(Path.of(directory)),
            80, 24, integration, dev.jasper.app.terminals.PaneSnapshot.State.RUNNING, OptionalInt.empty(), Optional.empty(),
            remote ? Optional.of(new dev.jasper.app.terminals.RemoteLocation("host", directory)) : Optional.empty(), shell);
        status.setMetadata(snapshot, dimensions, running);
    }
    public static void edt(Runnable task) throws Exception { SwingUtilities.invokeAndWait(task); }
    public static void until(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < end) {
            boolean[] done = {false};
            edt(() -> done[0] = condition.getAsBoolean());
            if (done[0]) return;
            Thread.sleep(10);
        }
        throw new AssertionError("Condition did not complete within 5 seconds");
    }
    public static ShellLauncher launcher(Queue<Runnable> pending) {
        return new ShellLauncher(pending::add, DesktopTestSupport::shell, "sh");
    }
    public static WindowContent content(ShellLauncher launcher) {
        WindowContent owner = new WindowContent(launcher, HOME, path -> {}, () -> {}, () -> {});
        OWNERS.add(owner);
        return owner;
    }
    /** A window with one platform's keymap and shortcut text, whatever the host is. */
    public static WindowContent content(ShellLauncher launcher, boolean macOs) {
        WindowContent owner = new WindowContent(launcher, HOME, path -> {}, () -> {}, () -> {}, new ThemeController(),
            KeyBindings.defaults(macOs), new CommandHistory(), macOs);
        OWNERS.add(owner);
        return owner;
    }
    public static WindowContent content(ShellLauncher launcher, ThemeController themes) {
        WindowContent owner = new WindowContent(launcher, HOME, path -> {}, () -> {}, () -> {}, themes);
        OWNERS.add(owner);
        return owner;
    }
    public static void closeOwners() throws Exception {
        edt(() -> { OWNERS.forEach(WindowContent::close); OWNERS.clear(); });
    }
}
