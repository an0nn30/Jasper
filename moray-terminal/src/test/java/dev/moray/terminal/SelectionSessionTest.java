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

    private void show(String output, int row, String expected) throws Exception {
        connector.feed(output);
        Await.until(() -> expected.equals(session.snapshot().lineText(row)), "\"" + expected + "\" on row " + row);
    }
}
