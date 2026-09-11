package dev.moray.terminal;

import com.jediterm.terminal.CursorShape;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ShellIntegrationSessionTest {
    private FakeConnector connector;
    private TerminalSession session;

    @BeforeEach
    void start() throws Exception {
        connector = new FakeConnector();
        session = new TerminalSession(connector, 20, 4, 100);
        session.startReading();
    }

    @AfterEach
    void stop() {
        session.close();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS) // POSIX path in the URI
    void osc7SetsTheWorkingDirectory() throws Exception {
        AtomicReference<Path> reported = new AtomicReference<>();
        session.addListener(new TerminalSession.Listener() {
            @Override
            public void workingDirectoryChanged(Path directory) {
                reported.set(directory);
            }
        });

        connector.feed("\033]7;file://host/Users/me/My%20Dir\007");

        Await.until(() -> session.workingDirectory().isPresent(), "working directory from OSC 7");
        assertThat(session.workingDirectory()).contains(Path.of("/Users/me/My Dir"));
        assertThat(reported.get()).isEqualTo(Path.of("/Users/me/My Dir"));
    }

    @Test
    void onlyFileUrisWithAPathAreWorkingDirectories() {
        assertThat(TerminalSession.directoryFromUri("https://example.com/x")).isEmpty();
        assertThat(TerminalSession.directoryFromUri("not a uri")).isEmpty();
        assertThat(TerminalSession.directoryFromUri("file://host")).isEmpty();
    }

    @Test
    void onlyPromptStartMarksAreRecordedWithTheirRows() throws Exception {
        connector.feed("\033]133;A\007$ \033]133;B\007ls\r\n\033]133;C\007out\r\n\033]133;D;0\007\033]133;A\007$ ");

        Await.until(() -> session.promptRows().size() == 2, "two prompt marks");
        assertThat(session.promptRows()).containsExactly(0L, 2L);
    }

    @Test
    void promptRowsStayWithTheirLinesAsOutputScrolls() throws Exception {
        connector.feed("\033]133;A\007p0\r\n" + "l\r\n".repeat(9) + "end");

        Await.until(() -> "end".equals(session.snapshot().lineText(3)), "scrolled output");
        long prompt = session.promptRows().getFirst();
        assertThat(prompt).isZero();
        assertThat(session.lineText(prompt)).isEqualTo("p0");
    }

    @Test
    void cursorStyleResetRestoresTheConfiguredCursor() throws Exception {
        connector.feed("\033[6 q");
        Await.until(() -> session.display().cursorShape() == CursorShape.STEADY_VERTICAL_BAR, "beam requested");

        connector.feed("\033[0 q");

        Await.until(() -> session.display().cursorShape() == null, "back to the configured cursor");
    }

    @Test
    void customCommandsFromOtherToolsAreIgnored() throws Exception {
        connector.feed("\033]1341;other;cwd;file:///tmp\007done");

        Await.until(() -> "done".equals(session.snapshot().lineText(0)), "text after the command");
        assertThat(session.workingDirectory()).isEmpty();
    }
}
