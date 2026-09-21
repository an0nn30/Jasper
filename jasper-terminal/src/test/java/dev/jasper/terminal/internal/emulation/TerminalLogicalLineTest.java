package dev.jasper.terminal.internal.emulation;

import dev.jasper.terminal.internal.text.Selection;
import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.testsupport.Await;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TerminalLogicalLineTest {
    @Test
    void rowLimitBoundsSelectionAroundTheClickedRowAndRejectsPartialUrl() throws Exception {
        assertBoundedLine(5, 5000, 4096);
    }

    @Test
    void textBudgetBoundsSelectionBeforeTheRowLimit() throws Exception {
        assertBoundedLine(1024, 600, 512);
    }

    @Test
    void aCompleteUrlAtTheBudgetBoundaryRemainsClickable() throws Exception {
        FakeConnector connector = new FakeConnector();
        try (TerminalSession session = EmulationFixture.unstarted(connector, 1024, 2, 600)) {
            session.internalAccess().startReading();
            String url = "https://example.com/" + "x".repeat(512 * 1024 - 20);
            connector.feed(url + "\r\nend\033]0;ready\007");
            Await.until(() -> session.title().equals("ready"), "complete boundary URL");
            assertThat(session.internalAccess().linkAt(0, 0)).contains(url);
            assertThat(session.internalAccess().linkAt(511, 500)).contains(url);
        }
    }

    @Test
    void outOfGridColumnsCannotOverflowTheVirtualUrlColumn() throws Exception {
        FakeConnector connector = new FakeConnector();
        try (TerminalSession session = EmulationFixture.unstarted(connector, 20, 2, 100)) {
            session.internalAccess().startReading();
            connector.feed("https://example.com/a\033]0;ready\007");
            Await.until(() -> session.title().equals("ready"), "ordinary URL");
            for (int column : new int[]{-1, 20, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                assertThat(session.internalAccess().linkAt(0, column)).isEmpty();
            }
        }
    }

    private static void assertBoundedLine(int width, int lineRows, int expectedRows) throws Exception {
        FakeConnector connector = new FakeConnector();
        try (TerminalSession session = EmulationFixture.unstarted(connector, width, 2, lineRows)) {
            session.internalAccess().startReading();
            String url = "https://example.com/";
            connector.feed(url + "x".repeat(width * lineRows - url.length()) + "\r\nend\033]0;ready\007");
            Await.until(() -> session.title().equals("ready"), "long wrapped line");
            for (int clicked : new int[]{0, lineRows / 2, lineRows - 1}) {
                Selection selection = session.internalAccess().lineSelection(clicked);
                assertThat(selection.startRow()).isLessThanOrEqualTo(clicked);
                assertThat(selection.endRow()).isGreaterThanOrEqualTo(clicked);
                assertThat(selection.endRow() - selection.startRow() + 1).isEqualTo(expectedRows);
                assertThat(session.internalAccess().text(selection).length()).isEqualTo(expectedRows * width);
                assertThat(session.internalAccess().linkAt(clicked, 0)).as("truncated logical URL context").isEmpty();
            }
            assertThat(session.internalAccess().text(session.internalAccess().lineSelection(lineRows))).isEqualTo("end");
        }
    }
}
