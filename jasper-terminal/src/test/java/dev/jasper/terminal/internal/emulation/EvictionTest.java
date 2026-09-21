package dev.jasper.terminal.internal.emulation;

import dev.jasper.terminal.internal.rendering.ScreenSnapshot;
import dev.jasper.terminal.internal.text.AbsoluteRowState;
import dev.jasper.terminal.internal.text.Selection;
import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.testsupport.Await;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Lines pushed out of a full scrollback: absolute rows before them must read as gone, not as other lines. */
class EvictionTest {
    private FakeConnector connector;
    private TerminalSession session;

    @BeforeEach
    void start() throws Exception {
        connector = new FakeConnector();
        session = EmulationFixture.unstarted(connector, 10, 3, 2);
        session.internalAccess().startReading();
        // Eight lines, of which a 3-row screen and a 2-line scrollback keep five: p0, l1 and l2 are evicted.
        connector.feed("\033]133;A\007p0\r\nl1\r\nl2\r\nl3\r\nl4\r\nl5\r\nl6\r\nl7");
        Await.until(() -> "l7".equals(session.internalAccess().snapshot().lineText(2)), "eight lines through a five-line buffer");
    }

    @AfterEach
    void stop() {
        session.close();
    }

    @Test
    void anEvictedPromptIsRemovedFromTheStoredPromptRows() throws Exception {
        assertThat(session.internalAccess().promptRows()).doesNotContain(0L).isEmpty();
        Field promptRows = AbsoluteRowState.class.getDeclaredField("prompts");
        promptRows.setAccessible(true);
        assertThat((List<?>) promptRows.get(SessionInspection.rows(session))).isEmpty();
    }

    @Test
    void anEvictedRowHasNoText() {
        assertThat(session.internalAccess().lineText(0)).isNull();
        assertThat(session.internalAccess().lineText(3)).isEqualTo("l3");
    }

    @Test
    void aSnapshotAboveTheScrollbackStartsAtTheOldestLineLeft() {
        ScreenSnapshot snapshot = session.internalAccess().snapshot(0);

        assertThat(snapshot.firstRow()).isEqualTo(3);
        assertThat(snapshot.lineText(0)).isEqualTo("l3");
    }

    @Test
    void aSelectionReachingEvictedRowsKeepsOnlyTheTextStillPresent() {
        assertThat(session.internalAccess().text(new Selection(0, 0, 7, 3, false))).isEqualTo("l3\nl4\nl5\nl6\nl7");
    }
}
