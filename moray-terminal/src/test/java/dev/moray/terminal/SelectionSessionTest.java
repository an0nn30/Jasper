package dev.moray.terminal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SelectionSessionTest {
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
    void textOfASelectionOnScreen() throws Exception {
        show("hello world", 0, "hello world");

        assertThat(session.text(new Selection(0, 0, 0, 4, false))).isEqualTo("hello");
    }

    @Test
    void wordSelectionFindsTheWord() throws Exception {
        show("echo foo.bar baz", 0, "echo foo.bar baz");

        assertThat(session.text(session.wordSelection(0, 7))).isEqualTo("foo.bar");
    }

    @Test
    void lineSelectionIncludesSoftWrappedRows() throws Exception {
        show("x".repeat(25) + "\r\nnext", 2, "next");

        assertThat(session.text(session.lineSelection(1))).isEqualTo("x".repeat(25));
    }

    @Test
    void selectionsStayWithTheirTextAfterItScrolls() throws Exception {
        show("keep\r\n" + "l\r\n".repeat(6) + "end", 3, "end");

        assertThat(session.text(new Selection(0, 0, 0, 3, false))).isEqualTo("keep");
    }

    @Test
    void eitherCellOfAWideCharacterCopiesTheWholeCharacter() throws Exception {
        show("\u754c\uD83D\uDE80", 0, "\u754c\uE000\uD83D\uDE80");
        for (boolean block : new boolean[] {false, true}) {
            assertThat(session.text(Selection.at(0, 0, block))).isEqualTo("\u754c");
            assertThat(session.text(Selection.at(0, 1, block))).isEqualTo("\u754c");

        }
    }

    @Test
    void eitherCellOfASupplementaryCharacterCopiesTheWholeCharacter() throws Exception {
        show("\uD83D\uDE80", 0, "\uD83D\uDE80");
        for (boolean block : new boolean[] {false, true}) {
            assertThat(session.text(Selection.at(0, 0, block))).isEqualTo("\uD83D\uDE80");
            assertThat(session.text(Selection.at(0, 1, block))).isEqualTo("\uD83D\uDE80");
        }
    }

    private void show(String output, int row, String expected) throws Exception {
        connector.feed(output);
        Await.until(() -> expected.equals(session.snapshot().lineText(row)), "\"" + expected + "\" on row " + row);
    }
}
