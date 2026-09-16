package dev.jasper.terminal;

import com.jediterm.terminal.CursorShape;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ShellIntegrationSessionTest {
    private FakeConnector connector;
    private TerminalSession session;
    private CopyOnWriteArrayList<String> captured;
    private CopyOnWriteArrayList<OptionalInt> statuses;

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

    private void listenForCommands() {
        captured = new CopyOnWriteArrayList<>();
        statuses = new CopyOnWriteArrayList<>();
        session.addListener(new TerminalSession.Listener() {
            @Override public void commandExecuted(String command, OptionalInt exitStatus, Optional<Path> workingDirectory) {
                captured.add(command);
                statuses.add(exitStatus);
            }
        });
    }

    @Test void commandsAreCapturedBetweenTheirMarksWithTheirExitStatus() throws Exception {
        listenForCommands();
        connector.feed("\033]133;A\007$ \033]133;B\007ls -la\r\n\033]133;C\007out\r\n\033]133;D;2\007\033]133;A\007$ ");
        Await.until(() -> captured.size() == 1, "one captured command");
        assertThat(captured).containsExactly("ls -la");
        assertThat(statuses.getFirst()).hasValue(2);
    }

    @Test void aPromptWithoutACommandStartMarkCapturesNothing() throws Exception {
        listenForCommands();
        connector.feed("\033]133;A\007$ ls\r\n\033]133;C\007out\r\n\033]133;D;0\007\033]133;A\007$ ");
        Await.until(() -> session.promptRows().size() == 2, "two prompts");
        assertThat(captured).isEmpty();
    }

    /**
     * A prompt (mark A) must reset the pending command-start row, or a stray B without a matching C
     * leaves {@code commandStartRow} pointing at stale content; a later C with no B of its own would
     * then wrongly capture text between that stale row and wherever the cursor now is.
     */
    @Test void aPromptResetsTheCommandStartSoALeftoverMarkCapturesNothing() throws Exception {
        listenForCommands();
        connector.feed("\033]133;A\007$ \033]133;B\007partial text\r\n"
            + "\033]133;A\007$ \033]133;C\007\033]133;D;0\007\r\n"
            + "\033]133;A\007$ ");
        Await.until(() -> session.promptRows().size() == 3, "three prompt marks");
        assertThat(captured).isEmpty();
    }

    @Test void aMissingExitMarkStillDeliversTheCommandAtTheNextPrompt() throws Exception {
        listenForCommands();
        connector.feed("\033]133;A\007$ \033]133;B\007pwd\r\n\033]133;C\007/tmp\r\n\033]133;A\007$ ");
        Await.until(() -> captured.size() == 1, "captured at the next prompt");
        assertThat(captured).containsExactly("pwd");
        assertThat(statuses.getFirst()).isEmpty();
    }

    @Test void wrappedRowsJoinWithoutNewlinesAndContinuationRowsKeepThem() throws Exception {
        listenForCommands();
        // Width is 20: "$ " plus 25 characters wraps once.
        connector.feed("\033]133;A\007$ \033]133;B\007echo aaaaaaaaaaaaaaaaaaaa\r\n\033]133;C\007\033]133;D;0\007");
        Await.until(() -> captured.size() == 1, "wrapped command");
        assertThat(captured.getFirst()).isEqualTo("echo aaaaaaaaaaaaaaaaaaaa");
        connector.feed("\033]133;A\007$ \033]133;B\007echo 'a\r\n> b'\r\n\033]133;C\007\033]133;D;0\007");
        Await.until(() -> captured.size() == 2, "continuation command");
        assertThat(captured.get(1)).isEqualTo("echo 'a\n> b'");
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
    void aWidthChangeForgetsPromptRowsBecauseLinesReflow() throws Exception {
        // jediterm-core reflows soft-wrapped lines on a width change: at 30 columns the wrapped line above the
        // prompt becomes one row, so the prompt's recorded absolute row would then name the line above it.
        String wrapped = "w".repeat(30) + "\r\n";
        connector.feed(wrapped + "\033]133;A\007$ mark\r\n" + wrapped.repeat(4) + "end");
        Await.until(() -> "end".equals(session.snapshot().lineText(3)), "scrolled output");
        assertThat(session.lineText(session.promptRows().getFirst())).isEqualTo("$ mark");

        session.resize(20, 6); // height only: no reflow, the mark stays
        assertThat(session.lineText(session.promptRows().getFirst())).isEqualTo("$ mark");

        session.resize(30, 6);
        assertThat(session.promptRows()).isEmpty();
    }

    @Test
    void erasingTheScrollbackForgetsPromptsAndTellsListeners() throws Exception {
        AtomicBoolean reset = new AtomicBoolean();
        session.addListener(new TerminalSession.Listener() {
            @Override
            public void scrollbackReset() {
                reset.set(true);
            }
        });
        connector.feed("\033]133;A\007$ one\r\n" + "x\r\n".repeat(6) + "end");
        Await.until(() -> session.promptRows().size() == 1, "one prompt mark");

        connector.feed("\033[3J");

        Await.until(reset::get, "scrollback reset");
        assertThat(session.promptRows()).isEmpty();
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

    @Test void theCmdPayloadIsPreferredOverTheScreenAndMalformedPayloadsAreIgnored() throws Exception {
        listenForCommands();
        String encoded = java.util.Base64.getEncoder().encodeToString("echo \"one\ntwo\"".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        connector.feed("\033]133;A\007$ \033]133;B\007echo \"one\r\ndquote> two\"\r\n\033]1341;jasper;cmd;" + encoded
            + "\007\033]133;C\007one\r\ntwo\r\n\033]133;D;0\007\033]133;A\007$ ");
        Await.until(() -> captured.size() == 1, "captured through cmd");
        assertThat(captured).containsExactly("echo \"one\ntwo\"");
        assertThat(session.shellIntegrationDetected()).isTrue();
        connector.feed("\033]133;B\007ls\r\n\033]1341;jasper;cmd;***not base64***\007\033]133;C\007\033]133;D;0\007");
        Await.until(() -> captured.size() == 2, "fell back to the screen");
        assertThat(captured.get(1)).isEqualTo("ls");
    }

    @Test void aCmdPayloadWithoutABMarkStillCapturesTheCommand() throws Exception {
        listenForCommands();
        assertThat(session.shellIntegrationDetected()).isFalse();
        String encoded = java.util.Base64.getEncoder().encodeToString("pwd".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        connector.feed("\033]133;A\007$ pwd\r\n\033]1341;jasper;cmd;" + encoded + "\007\033]133;C\007/tmp\r\n\033]133;D;0\007");
        Await.until(() -> captured.size() == 1, "captured from cmd alone");
        assertThat(captured).containsExactly("pwd");
    }

    /** fish 4 emits its own A marks; a repeat must not flush the cycle and lose the exit status. */
    @Test void aRepeatedPromptMarkDoesNotStealTheExitStatus() throws Exception {
        listenForCommands();
        connector.feed("\033]133;A\007$ \033]133;B\007\033]1341;jasper;cmd;ZmFsc2U=\007\033]133;C\007");
        connector.feed("\033]133;A\007\033]133;A\007\033]133;D;1\007");

        Await.until(() -> captured.size() == 1, "one captured command");
        assertThat(captured).containsExactly("false");
        assertThat(statuses).containsExactly(OptionalInt.of(1));
    }

    @Test void aCommandTextThatWasNeverUsedDoesNotLeakIntoTheNextCommand() throws Exception {
        listenForCommands();
        connector.feed("\033]1341;jasper;cmd;U1RBTEU=\007\033]133;D;0\007");
        connector.feed("\033]133;B\007typed\033]133;C\007\033]133;D;0\007");

        Await.until(() -> captured.size() == 1, "one captured command");
        assertThat(captured).containsExactly("typed");
    }

    @Test void aResetDiscardsAnUnusedCommandText() throws Exception {
        listenForCommands();
        connector.feed("\033]1341;jasper;cmd;QkVGT1JF\007\033c");
        connector.feed("\033]133;B\007typed\033]133;C\007\033]133;D;0\007");

        Await.until(() -> captured.size() == 1, "one captured command");
        assertThat(captured).containsExactly("typed");
    }

    @Test void anOverlongCommandPayloadFallsBackToTheScreen() throws Exception {
        listenForCommands();
        String huge = Base64.getEncoder().encodeToString("x".repeat(64 * 1024).getBytes(StandardCharsets.UTF_8));
        connector.feed("\033]133;A\007$ \033]133;B\007typed\033]1341;jasper;cmd;" + huge
            + "\007\033]133;C\007\033]133;D;0\007");

        Await.until(() -> captured.size() == 1, "one captured command");
        assertThat(captured).containsExactly("typed");
    }

    @Test void aPayloadThatIsNotUtf8FallsBackToTheScreen() throws Exception {
        listenForCommands();
        String invalid = Base64.getEncoder().encodeToString(new byte[] {(byte) 0xC3, (byte) 0x28});
        connector.feed("\033]133;A\007$ \033]133;B\007typed\033]1341;jasper;cmd;" + invalid
            + "\007\033]133;C\007\033]133;D;0\007");

        Await.until(() -> captured.size() == 1, "one captured command");
        assertThat(captured).containsExactly("typed");
    }

    /** A prompt never sits inside a cycle, even when it repeats the row Jasper already marked. */
    @Test void aRepeatedPromptMarkStillDiscardsAHalfStartedCapture() throws Exception {
        listenForCommands();
        connector.feed("\033]133;A\007$ \033]133;B\007typed");
        connector.feed("\r\033]133;A\007$ \033]133;C\007\033]133;D;0\007");

        Await.until(() -> session.promptRows().size() == 1, "the redrawn prompt did not add a row");
        assertThat(captured).isEmpty();
    }

    @Test void aRepeatedPromptMarkStillDiscardsAnUnusedCommandText() throws Exception {
        listenForCommands();
        connector.feed("\033]133;A\007$ \033]1341;jasper;cmd;U1RBTEU=\007");
        connector.feed("\r\033]133;A\007$ \033]133;B\007typed\033]133;C\007\033]133;D;0\007");

        Await.until(() -> captured.size() == 1, "one captured command");
        assertThat(captured).containsExactly("typed");
    }

    @Test void theAlternateScreenDiscardsAnUnusedCommandText() throws Exception {
        listenForCommands();
        connector.feed("\033]1341;jasper;cmd;U1RBTEU=\007\033[?1049h");
        connector.feed("\033]133;B\007typed\033]133;C\007\033]133;D;0\007");

        Await.until(() -> captured.size() == 1, "one captured command");
        assertThat(captured).containsExactly("typed");
    }
}
