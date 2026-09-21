package dev.jasper.app.testsupport;

import dev.jasper.app.workspace.DesktopTestSupport;
import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.session.SessionLaunchOptions;
import dev.jasper.terminal.session.TerminalSession;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Test-only child JVM: no shell startup files, GUI or production test seam. */
public final class ControlledSessionChild {
    private ControlledSessionChild() {}
    public static final class Process {
        public static void main(String[] args) throws Exception {
            System.out.print("\033]2;controlled-ready\007"); System.out.flush();
            new CountDownLatch(1).await();
        }
    }
    public static TerminalSession start() throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", executable);
        Path classes = Path.of(ControlledSessionChild.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        TerminalSession session = TerminalSession.start(SessionLaunchOptions.builder()
            .command(List.of(java.toString(), "-cp", classes.toString(), Process.class.getName()))
            .environment(System.getenv()).workingDirectory(DesktopTestSupport.HOME)
            .grid(new GridSize(40, 5)).scrollback(10).build());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!session.title().equals("controlled-ready") && System.nanoTime() < deadline && !session.exitFuture().isDone())
            Thread.sleep(5);
        if (!session.title().equals("controlled-ready")) { session.close(); throw new AssertionError("Controlled child did not start"); }
        return session;
    }
}
