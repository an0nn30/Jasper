package dev.jasper.terminal.internal.emulation;

import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.internal.rendering.ScreenSnapshot;
import dev.jasper.terminal.session.SessionLaunchOptions;
import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.testsupport.Await;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import static org.assertj.core.api.Assertions.assertThat;

class PtyConnectorTest {
    private static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");

    @Test
    void programOutputReachesTheScreenAndExitCodeIsReported() throws Exception {
        List<String> command = WINDOWS
            ? List.of("cmd.exe", "/c", "echo jasper-pty-ok")
            : List.of("/bin/sh", "-c", "echo jasper-pty-ok");
        try (TerminalSession session = start(command)) {
            Await.until(() -> screenText(session).contains("jasper-pty-ok"), "echo output on screen");
            assertThat(session.exitFuture().get(10, TimeUnit.SECONDS)).isZero();
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void childSeesTerminalEnvironment() throws Exception {
        try (TerminalSession session = start(List.of("/bin/sh", "-c", "printf '%s %s' \"$TERM\" \"$COLORTERM\""))) {
            Await.until(() -> screenText(session).contains("xterm-256color truecolor"), "TERM and COLORTERM");
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void childSeesTheInitialWindowSize() throws Exception {
        try (TerminalSession session = start(List.of("/bin/sh", "-c", "stty size"))) {
            Await.until(() -> screenText(session).contains("24 80"), "stty reports 24 rows, 80 columns");
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void inputReachesTheProgram() throws Exception {
        try (TerminalSession session = start(List.of("/bin/sh", "-c", "read line; echo \"got:$line\""))) {
            session.write("abc\r");
            Await.until(() -> screenText(session).contains("got:abc"), "program echoes its input");
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void closeReturnsOnTheEdtAndForciblyEndsAChildThatIgnoresHangup() throws Exception {
        // Keep the process handle so failed assertions can still kill this intentionally immortal fixture.
        var process = new com.pty4j.PtyProcessBuilder(new String[]{"/bin/sh", "-c",
            "trap '' HUP TERM; printf 'jasper-traps-ready\\n'; while :; do :; done"})
            .setEnvironment(System.getenv())
            .setDirectory(System.getProperty("user.home"))
            .setInitialColumns(80).setInitialRows(24)
            .setUnixOpenTtyToPreserveOutputAfterTermination(true).start();
        TerminalSession session = EmulationFixture.unstarted(new PtyConnector(dev.jasper.terminal.internal.process.ProcessFixture.child(process)), 80, 24, 100);
        try {
            session.internalAccess().startReading();
            Await.until(() -> screenText(session).contains("jasper-traps-ready"), "shell signal traps installed");
            AtomicLong closeMillis = new AtomicLong();

            SwingUtilities.invokeAndWait(() -> {
                long started = System.nanoTime();
                session.close();
                closeMillis.set(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            });

            assertThat(closeMillis.get()).isLessThan(100);
            AtomicReference<Thread> cleanup = new AtomicReference<>();
            Await.until(() -> Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.isAlive() && thread.getName().equals("jasper-pty-close"))
                .findFirst().map(thread -> {
                    cleanup.set(thread);
                    return true;
                }).orElse(false), "PTY close cleanup worker");
            assertThat(cleanup.get().isDaemon()).isFalse();
            assertThat(session.exitFuture().get(5, TimeUnit.SECONDS)).isNotZero();
        } finally {
            session.close();
            if (process.isAlive()) process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void foregroundJobFollowsJobControlWithoutOscOrShellIntegration() throws Exception {
        try (TerminalSession session = start(List.of("/bin/bash", "--noprofile", "--norc", "-i"))) {
            Await.until(() -> session.foregroundJob().orElse("").equals("bash"), "idle bash foreground job");
            session.write("sleep 30\r");
            Await.until(() -> session.foregroundJob().orElse("").equals("sleep"), "sleep foreground job");
            session.write(new byte[]{3});
            Await.until(() -> session.foregroundJob().orElse("").equals("bash"), "shell restored after interrupt");
            session.write("sleep 30 &\r");
            Await.until(() -> screenText(session).contains("[1]"), "background job started");
            assertThat(session.foregroundJob()).contains("bash");
            session.write("kill %1; exit\r");
            session.exitFuture().get(5, TimeUnit.SECONDS);
            assertThat(session.foregroundJob()).isEmpty();
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void loginShellGetsTheConventionalLeadingDash() throws Exception {
        try (TerminalSession session = start(List.of("/bin/bash", "--noprofile", "--norc", "-l", "-i"))) {
            Await.until(() -> session.foregroundJob().orElse("").equals("-bash"), "login shell name");
        }
    }

    private static TerminalSession start(List<String> command) throws Exception {
        return TerminalSession.start(SessionLaunchOptions.builder().command(command).environment(System.getenv()).workingDirectory(Path.of(System.getProperty("user.home"))).grid(new GridSize(80, 24)).scrollback(100).build());
    }

    private static String screenText(TerminalSession session) {
        ScreenSnapshot snapshot = session.internalAccess().snapshot();
        StringBuilder text = new StringBuilder();
        for (int row = 0; row < snapshot.height(); row++) {
            text.append(snapshot.lineText(row)).append('\n');
        }
        return text.toString();
    }
}
