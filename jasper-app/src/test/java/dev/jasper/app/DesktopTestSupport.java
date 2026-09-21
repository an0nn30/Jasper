package dev.jasper.app;

import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.session.SessionLaunchOptions;

import dev.jasper.terminal.session.TerminalSession;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;

final class DesktopTestSupport {
    private static final List<WindowContent> OWNERS = new ArrayList<>();
    static final Path HOME = Path.of(System.getProperty("user.home"));
    static TerminalSession shell(Path directory) {
        try {
            return TerminalSession.start(SessionLaunchOptions.builder().command(List.of("/bin/sh", "-c", "printf 'alpha alpha\\n'; read answer")).environment(System.getenv()).workingDirectory(directory).grid(new GridSize(80, 24)).scrollback(100).build());
        } catch (Exception e) { throw new CompletionException(e); }
    }
    static void edt(Runnable task) throws Exception { SwingUtilities.invokeAndWait(task); }
    static void until(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < end) {
            boolean[] done = {false};
            edt(() -> done[0] = condition.getAsBoolean());
            if (done[0]) return;
            Thread.sleep(10);
        }
        throw new AssertionError("Condition did not complete within 5 seconds");
    }
    static ShellLauncher launcher(Queue<Runnable> pending) {
        return new ShellLauncher(pending::add, DesktopTestSupport::shell, "sh");
    }
    static WindowContent content(ShellLauncher launcher) {
        WindowContent owner = new WindowContent(launcher, HOME, path -> {}, () -> {}, () -> {});
        OWNERS.add(owner);
        return owner;
    }
    static WindowContent content(ShellLauncher launcher, ThemeController themes) {
        WindowContent owner = new WindowContent(launcher, HOME, path -> {}, () -> {}, () -> {}, themes);
        OWNERS.add(owner);
        return owner;
    }
    static void closeOwners() throws Exception {
        edt(() -> { OWNERS.forEach(WindowContent::close); OWNERS.clear(); });
    }
}
