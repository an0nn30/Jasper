package dev.jasper.terminal.internal.emulation;

import dev.jasper.terminal.search.SearchQuery;

import dev.jasper.terminal.internal.text.TerminalSearch;
import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.testsupport.Await;

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
        session = EmulationFixture.unstarted(connector, 10, 3, 100);
        session.internalAccess().startReading();
        connector.feed("foo\r\nbar\r\nfoo bar\r\nbaz\r\nqux");
        Await.until(() -> "qux".equals(session.internalAccess().snapshot().lineText(2)), "five lines on a three-row screen");
    }

    @AfterEach
    void stop() {
        session.close();
    }

    @Test
    void searchCoversScrollbackAndScreenOldestFirst() {
        assertThat(session.internalAccess().search(new SearchQuery("foo", false, false)))
            .containsExactly(new TerminalSearch.Match(0, 0, 2), new TerminalSearch.Match(2, 0, 2));
    }

    @Test
    void anEmptyQueryFindsNothing() {
        assertThat(session.internalAccess().search(new SearchQuery("", false, false))).isEmpty();
    }

    @Test
    void theAlternateScreenSearchesOnlyItsOwnRows() throws Exception {
        connector.feed("\r\nneedle\r\nx\r\ny\r\nz\033[?1049h");
        Await.until(() -> session.internalAccess().snapshot().alternateBuffer(), "alternate screen");

        assertThat(session.internalAccess().search(new SearchQuery("needle", false, false))).isEmpty();

        connector.feed("\033[Hhay needle");
        Await.until(() -> session.internalAccess().snapshot().lineText(0).startsWith("hay needle"), "text on the alternate screen");
        assertThat(session.internalAccess().search(new SearchQuery("needle", false, false)))
            .containsExactly(new TerminalSearch.Match(session.internalAccess().snapshot().firstRow(), 4, 9));
    }

    @Test
    void noMatchesIsAnEmptyList() {
        assertThat(session.internalAccess().search(new SearchQuery("zzz", false, false))).isEmpty();
    }
}
