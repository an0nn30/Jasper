package dev.jasper.terminal.session;

import dev.jasper.terminal.testsupport.Await;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

class SessionLocalPathsTest {
    /** POSIX printf; PtyConnectorTest's ConPTY test covers the default mapping through a real Windows child. */
    @Test @DisabledOnOs(OS.WINDOWS)
    void aStartedSessionReadsLocalReportsThroughItsMapper() throws Exception {
        var options = SessionLaunchOptions.builder()
            .command(List.of("/bin/sh", "-c", "printf '\\033]7;file://localhost/reported\\007'; read line"))
            .environment(System.getenv()).workingDirectory(Path.of(System.getProperty("user.home")))
            .localPathMapper(path -> path.equals("/reported") ? Optional.of(Path.of("/mapped")) : Optional.empty()).build();
        try (TerminalSession session = TerminalSession.start(options)) {
            Await.until(() -> session.workingDirectory().equals(Optional.of(Path.of("/mapped"))), "the mapped working directory");
        }
    }
}
