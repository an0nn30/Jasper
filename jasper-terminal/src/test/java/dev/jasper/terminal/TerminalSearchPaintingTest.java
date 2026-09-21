package dev.jasper.terminal;

import org.junit.jupiter.api.Test;
import java.util.AbstractList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

class TerminalSearchPaintingTest {
    @Test void paintingSkipsOffscreenMatchesAndKeepsOverlapsAtBothViewportEdges() throws Exception {
        try (var session = new TerminalSession(new FakeConnector(), 20, 4, 100)) {
            var view = new TerminalView(session, TerminalOptions.defaults());
            var reads = new AtomicInteger();
            var matches = new AbstractList<TerminalSearch.Match>() {
                @Override public int size() { return 200_000; }
                @Override public TerminalSearch.Match get(int index) {
                    reads.incrementAndGet();
                    return new TerminalSearch.Match(index / 2, index % 2, 3);
                }
            };
            set(view, "matches", matches);
            set(view, "currentMatch", 100_006);
            var live = session.snapshot();
            var snapshot = new ScreenSnapshot(20, 4, live.lines(), 0, 0, false, null,
                50_000, 0, 0, false);
            var highlights = highlights(view, snapshot);
            assertThat(highlights).hasSize(8);
            assertThat(highlights).extracting(TerminalPainter.Highlight::row)
                .containsExactly(0, 0, 1, 1, 2, 2, 3, 3);
            assertThat(highlights).extracting(TerminalPainter.Highlight::startColumn)
                .containsExactly(0, 1, 0, 1, 0, 1, 0, 1);
            assertThat(highlights).extracting(TerminalPainter.Highlight::endColumn).containsOnly(3);
            assertThat(highlights.get(6).color()).isNotEqualTo(highlights.get(7).color());
            assertThat(reads.get()).as("bounded match visits for a four-row viewport").isLessThan(40);
        }
    }

    @Test void realSupplementarySearchPaintsBothUtf16CellsAtTheScreenEdge() throws Exception {
        var connector = new FakeConnector();
        try (var session = new TerminalSession(connector, 6, 2, 100)) {
            session.startReading();
            connector.feed("abcd\uD83D\uDE80\r\n\uD83D\uDE80");
            Await.until(() -> session.snapshot().lineText(1).equals("\uD83D\uDE80"), "emoji at both edges");
            var view = new TerminalView(session, TerminalOptions.defaults());
            assertThat(view.find("\uD83D\uDE80", false, true).count()).isEqualTo(2);
            var highlights = highlights(view, session.snapshot());
            assertThat(highlights).extracting(TerminalPainter.Highlight::row).containsExactly(0, 1);
            assertThat(highlights).extracting(TerminalPainter.Highlight::startColumn).containsExactly(4, 0);
            assertThat(highlights).extracting(TerminalPainter.Highlight::endColumn).containsExactly(5, 1);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<TerminalPainter.Highlight> highlights(TerminalView view, ScreenSnapshot snapshot) throws Exception {
        var method = TerminalView.class.getDeclaredMethod("highlights", ScreenSnapshot.class);
        method.setAccessible(true);
        return (List<TerminalPainter.Highlight>) method.invoke(view, snapshot);
    }

    private static void set(TerminalView view, String name, Object value) throws Exception {
        var controller = SessionInspection.field(view, "search");
        var field = SearchController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(controller, value);
    }
}
