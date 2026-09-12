package dev.moray.terminal;

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
        try (TerminalSession session = new TerminalSession(connector, 1024, 2, 600)) {
            session.startReading();
            String url = "https://example.com/" + "x".repeat(512 * 1024 - 20);
            connector.feed(url + "\r\nend\033]0;ready\007");
            Await.until(() -> session.title().equals("ready"), "complete boundary URL");
            assertThat(session.linkAt(0, 0)).contains(url);
            assertThat(session.linkAt(511, 500)).contains(url);
        }
    }

    @Test
    void outOfGridColumnsCannotOverflowTheVirtualUrlColumn() throws Exception {
        FakeConnector connector = new FakeConnector();
        try (TerminalSession session = new TerminalSession(connector, 20, 2, 100)) {
            session.startReading();
            connector.feed("https://example.com/a\033]0;ready\007");
            Await.until(() -> session.title().equals("ready"), "ordinary URL");
            for (int column : new int[]{-1, 20, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                assertThat(session.linkAt(0, column)).isEmpty();
            }
        }
    }

    private static void assertBoundedLine(int width, int lineRows, int expectedRows) throws Exception {
        FakeConnector connector = new FakeConnector();
        try (TerminalSession session = new TerminalSession(connector, width, 2, lineRows)) {
            session.startReading();
            String url = "https://example.com/";
            connector.feed(url + "x".repeat(width * lineRows - url.length()) + "\r\nend\033]0;ready\007");
            Await.until(() -> session.title().equals("ready"), "long wrapped line");
            for (int clicked : new int[]{0, lineRows / 2, lineRows - 1}) {
                Selection selection = session.lineSelection(clicked);
                assertThat(selection.startRow()).isLessThanOrEqualTo(clicked);
                assertThat(selection.endRow()).isGreaterThanOrEqualTo(clicked);
                assertThat(selection.endRow() - selection.startRow() + 1).isEqualTo(expectedRows);
                assertThat(session.text(selection).length()).isEqualTo(expectedRows * width);
                assertThat(session.linkAt(clicked, 0)).as("truncated logical URL context").isEmpty();
            }
            assertThat(session.text(session.lineSelection(lineRows))).isEqualTo("end");
        }
    }
}
