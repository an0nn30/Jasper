package dev.moray.terminal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ScrollbackTest {
    private FakeConnector connector;
    private TerminalSession session;

    @BeforeEach
    void start() throws Exception {
        connector = new FakeConnector();
        session = new TerminalSession(connector, 10, 3, 100);
        session.startReading();
    }

    @AfterEach
    void stop() {
        session.close();
    }

    @Test
    void followingShowsTheLiveScreen() throws Exception {
        fiveLines();

        ScreenSnapshot snapshot = session.snapshot();

        assertThat(texts(snapshot)).containsExactly("c", "d", "e");
        assertThat(snapshot.firstRow()).isEqualTo(2);
        assertThat(snapshot.scrollOffset()).isZero();
        assertThat(snapshot.historyLines()).isEqualTo(2);
    }

    @Test
    void anchoredTopRowShowsScrollback() throws Exception {
        fiveLines();

        ScreenSnapshot snapshot = session.snapshot(0);

        assertThat(texts(snapshot)).containsExactly("a", "b", "c");
        assertThat(snapshot.firstRow()).isZero();
        assertThat(snapshot.scrollOffset()).isEqualTo(2);
        assertThat(snapshot.cursorRow()).isEqualTo(4); // the live cursor row 2, two rows below the viewport
    }

    @Test
    void topRowIsClampedToTheAvailableScrollback() throws Exception {
        fiveLines();

        assertThat(session.snapshot(-10).firstRow()).isZero();
        assertThat(session.snapshot(99).scrollOffset()).isZero();
    }

    @Test
    void anAnchoredViewKeepsItsLinesWhileOutputArrives() throws Exception {
        fiveLines();
        assertThat(texts(session.snapshot(1))).containsExactly("b", "c", "d");

        connector.feed("\r\nf\r\ng");
        Await.until(() -> "g".equals(session.snapshot().lineText(2)), "more output");

        ScreenSnapshot anchored = session.snapshot(1);
        assertThat(texts(anchored)).containsExactly("b", "c", "d");
        assertThat(anchored.scrollOffset()).isEqualTo(3);
    }

    @Test
    void theAlternateScreenHasNoScrollback() throws Exception {
        fiveLines();
        connector.feed("\033[?1049h");
        Await.until(() -> session.snapshot().alternateBuffer(), "alternate screen");

        ScreenSnapshot snapshot = session.snapshot(0);

        assertThat(snapshot.scrollOffset()).isZero();
        assertThat(snapshot.historyLines()).isZero();
    }

    @Test
    void theExitMessageIsWrittenToTheScreen() throws Exception {
        connector.feed("bye");
        connector.finish();

        assertThat(session.exitFuture().get(10, TimeUnit.SECONDS)).isZero();
        assertThat(String.join("", texts(session.snapshot()))).contains("[process exited with code 0]");
    }

    private void fiveLines() throws Exception {
        connector.feed("a\r\nb\r\nc\r\nd\r\ne");
        Await.until(() -> "e".equals(session.snapshot().lineText(2)), "five lines on a three-row screen");
    }

    private static java.util.List<String> texts(ScreenSnapshot snapshot) {
        java.util.List<String> texts = new java.util.ArrayList<>();
        for (int row = 0; row < snapshot.height(); row++) {
            texts.add(snapshot.lineText(row));
        }
        return texts;
    }
}
