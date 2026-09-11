package dev.moray.terminal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchSessionTest {
    private FakeConnector connector;
    private TerminalSession session;

    @BeforeEach
    void start() throws Exception {
        connector = new FakeConnector();
        session = new TerminalSession(connector, 10, 3, 100);
        session.startReading();
        connector.feed("foo\r\nbar\r\nfoo bar\r\nbaz\r\nqux");
        Await.until(() -> "qux".equals(session.snapshot().lineText(2)), "five lines on a three-row screen");
    }

    @AfterEach
    void stop() {
        session.close();
    }

    @Test
    void searchCoversScrollbackAndScreenOldestFirst() {
        assertThat(session.search("foo", false, false))
            .containsExactly(new TerminalSearch.Match(0, 0, 2), new TerminalSearch.Match(2, 0, 2));
    }

    @Test
    void anEmptyQueryFindsNothing() {
        assertThat(session.search("", false, false)).isEmpty();
    }

    @Test
    void theAlternateScreenSearchesOnlyItsOwnRows() throws Exception {
        connector.feed("\r\nneedle\r\nx\r\ny\r\nz\033[?1049h");
        Await.until(() -> session.snapshot().alternateBuffer(), "alternate screen");

        assertThat(session.search("needle", false, false)).isEmpty();

        connector.feed("\033[Hhay needle");
        Await.until(() -> session.snapshot().lineText(0).startsWith("hay needle"), "text on the alternate screen");
        assertThat(session.search("needle", false, false))
            .containsExactly(new TerminalSearch.Match(session.snapshot().firstRow(), 4, 9));
    }

    @Test
    void noMatchesIsAnEmptyList() {
        assertThat(session.search("zzz", false, false)).isEmpty();
    }
}
