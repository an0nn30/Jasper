package dev.moray.terminal;

import com.jediterm.core.input.MouseEvent;
import com.jediterm.terminal.emulator.mouse.MouseButtonCodes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionInputTest {
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
    void rightClickIsReportedInSgrMode() throws Exception {
        enableSgrMouse();

        boolean reported = session.reportMouse(0, 0, new MouseEvent(MouseEvent.Type.PRESSED, MouseButtonCodes.RIGHT, 0));

        assertThat(reported).isTrue();
        assertThat(connector.written()).isEqualTo("\033[<2;1;1M");
    }

    @Test
    void reportsAreClampedOntoTheScreen() throws Exception {
        enableSgrMouse();

        session.reportMouse(-3, 99, new MouseEvent(MouseEvent.Type.PRESSED, MouseButtonCodes.LEFT, 0));

        assertThat(connector.written()).isEqualTo("\033[<0;1;4M");
    }

    @Test
    void nothingIsReportedWithoutMouseMode() {
        assertThat(session.mouseReporting()).isFalse();

        boolean reported = session.reportMouse(1, 1, new MouseEvent(MouseEvent.Type.PRESSED, MouseButtonCodes.LEFT, 0));

        assertThat(reported).isFalse();
        assertThat(connector.written()).isEmpty();
    }

    @Test
    void pasteTurnsNewlinesIntoCarriageReturns() {
        session.paste("a\nb\r\nc");

        assertThat(connector.written()).isEqualTo("a\rb\rc");
    }

    @Test
    void bracketedPasteWrapsTheTextAndRemovesEmbeddedEndMarkers() throws Exception {
        connector.feed("\033[?2004h");
        Await.until(() -> session.display().bracketedPaste(), "bracketed paste on");

        session.paste("x\033[201~y");

        assertThat(connector.written()).isEqualTo("\033[200~xy\033[201~");
    }

    @Test
    void osc8LinksAreFoundUnderTheirText() throws Exception {
        connector.feed("\033]8;;https://example.com\007link\033]8;;\007 plain");
        Await.until(() -> "link plain".equals(session.snapshot().lineText(0)), "linked text");

        assertThat(session.linkAt(0, 1)).contains("https://example.com");
        assertThat(session.linkAt(0, 6)).isEmpty();
    }

    @Test
    void urlsInPlainTextAreFound() throws Exception {
        connector.feed("see https://moray.dev/docs.");
        Await.until(() -> session.snapshot().lineText(0).startsWith("see https"), "url text");

        assertThat(session.linkAt(0, 10)).contains("https://moray.dev/docs");
    }

    @Test
    void theAlternateScreenIsReported() throws Exception {
        assertThat(session.usingAlternateBuffer()).isFalse();

        connector.feed("\033[?1049h");

        Await.until(session::usingAlternateBuffer, "alternate screen");
    }

    private void enableSgrMouse() throws Exception {
        connector.feed("\033[?1000h\033[?1006h");
        Await.until(session::mouseReporting, "mouse reporting on");
    }
}
