package dev.jasper.app;

import dev.jasper.terminal.TerminalSession;
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
            return TerminalSession.start(List.of("/bin/sh", "-c", "printf 'alpha alpha\\n'; read answer"),
                System.getenv(), directory, 80, 24, 100);
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
