package dev.moray.terminal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PtyConnectorTest {
    private static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");

    @Test
    void programOutputReachesTheScreenAndExitCodeIsReported() throws Exception {
        List<String> command = WINDOWS
            ? List.of("cmd.exe", "/c", "echo moray-pty-ok")
            : List.of("/bin/sh", "-c", "echo moray-pty-ok");
        try (TerminalSession session = start(command)) {
            Await.until(() -> screenText(session).contains("moray-pty-ok"), "echo output on screen");
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

    private static TerminalSession start(List<String> command) throws Exception {
        return TerminalSession.start(command, System.getenv(), Path.of(System.getProperty("user.home")), 80, 24, 100);
    }

    private static String screenText(TerminalSession session) {
        ScreenSnapshot snapshot = session.snapshot();
        StringBuilder text = new StringBuilder();
        for (int row = 0; row < snapshot.height(); row++) {
            text.append(snapshot.lineText(row)).append('\n');
        }
        return text.toString();
    }
}
