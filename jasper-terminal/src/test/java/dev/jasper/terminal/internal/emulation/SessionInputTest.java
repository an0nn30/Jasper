package dev.jasper.terminal.internal.emulation;

import dev.jasper.terminal.internal.text.MouseInput;
import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.testsupport.Await;

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
        session = EmulationFixture.unstarted(connector, 20, 4, 100);
        session.internalAccess().startReading();
    }

    @AfterEach
    void stop() {
        session.close();
    }

    @Test
    void rightClickIsReportedInSgrMode() throws Exception {
        enableSgrMouse();

        boolean reported = session.internalAccess().reportMouse(0, 0, new MouseInput(MouseInput.Type.PRESSED, MouseInput.Button.RIGHT, false, false, false, 0));

        assertThat(reported).isTrue();
        assertThat(connector.written()).isEqualTo("\033[<2;1;1M");
    }

    @Test
    void reportsAreClampedOntoTheScreen() throws Exception {
        enableSgrMouse();

        session.internalAccess().reportMouse(-3, 99, new MouseInput(MouseInput.Type.PRESSED, MouseInput.Button.LEFT, false, false, false, 0));

        assertThat(connector.written()).isEqualTo("\033[<0;1;4M");
    }

    @Test
    void nothingIsReportedWithoutMouseMode() {
        assertThat(session.internalAccess().mouseReporting()).isFalse();

        boolean reported = session.internalAccess().reportMouse(1, 1, new MouseInput(MouseInput.Type.PRESSED, MouseInput.Button.LEFT, false, false, false, 0));

        assertThat(reported).isFalse();
        assertThat(connector.written()).isEmpty();
    }

    @Test
    void pasteTurnsNewlinesIntoCarriageReturns() {
        session.internalAccess().paste("a\nb\r\nc");

        assertThat(connector.written()).isEqualTo("a\rb\rc");
    }

    @Test
    void bracketedPasteWrapsTheTextAndRemovesEmbeddedEndMarkers() throws Exception {
        connector.feed("\033[?2004h\033]0;paste-ready\007");
        Await.until(() -> session.title().equals("paste-ready"), "bracketed paste on");

        session.internalAccess().paste("x\033[201~y");

        assertThat(connector.written()).isEqualTo("\033[200~xy\033[201~");
    }

    @Test void aPasteFollowedByARawReturnKeepsTheReturnOutsideTheBracket() throws Exception {
        connector.feed("\033[?2004h\033]0;paste-ready\007");
        Await.until(() -> session.title().equals("paste-ready"), "bracketed paste on");
        session.internalAccess().paste("ls -la\n");
        session.write("\r");
        assertThat(connector.written()).isEqualTo("\033[200~ls -la\r\033[201~\r");
    }

    @Test
    void osc8LinksAreFoundUnderTheirText() throws Exception {
        connector.feed("\033]8;;https://example.com\007link\033]8;;\007 plain");
        Await.until(() -> "link plain".equals(session.internalAccess().snapshot().lineText(0)), "linked text");

        assertThat(session.internalAccess().linkAt(0, 1)).contains("https://example.com");
        assertThat(session.internalAccess().linkAt(0, 6)).isEmpty();
    }

    @Test
    void osc8LinksWithOtherSchemesAreRefused() throws Exception {
        connector.feed("\033]8;;file:///Applications/Calculator.app\007calc\033]8;;\007 "
            + "\033]8;;x-custom://thing\007odd\033]8;;\007");
        Await.until(() -> "calc odd".equals(session.internalAccess().snapshot().lineText(0)), "linked text");

        assertThat(session.internalAccess().linkAt(0, 1)).isEmpty();
        assertThat(session.internalAccess().linkAt(0, 6)).isEmpty();
    }

    @Test
    void aRefusedOsc8LinkIsNotReplacedByTheUrlInItsText() throws Exception {
        connector.feed("\033]8;;x-custom://thing\007https://shown.dev\033]8;;\007");
        Await.until(() -> "https://shown.dev".equals(session.internalAccess().snapshot().lineText(0)), "linked text");

        assertThat(session.internalAccess().linkAt(0, 3)).isEmpty();
    }

    @Test
    void osc8MailtoLinksAreFound() throws Exception {
        connector.feed("\033]8;;mailto:someone@example.com\007mail\033]8;;\007");
        Await.until(() -> "mail".equals(session.internalAccess().snapshot().lineText(0)), "linked text");

        assertThat(session.internalAccess().linkAt(0, 1)).contains("mailto:someone@example.com");
    }

    @Test
    void motionWithoutAButtonIsNotReportedInClickOnlyMode() throws Exception {
        enableSgrMouse();

        session.internalAccess().reportMouse(1, 1, new MouseInput(MouseInput.Type.MOVED, MouseInput.Button.NONE, false, false, false, 0));

        assertThat(connector.written()).isEmpty();
    }

    @Test
    void draggingIsNotReportedInClickOnlyMode() throws Exception {
        enableSgrMouse();

        session.internalAccess().reportMouse(1, 1, new MouseInput(MouseInput.Type.DRAGGED, MouseInput.Button.LEFT, false, false, false, 0));

        assertThat(connector.written()).isEmpty();
    }

    @Test
    void draggingIsReportedInButtonMotionMode() throws Exception {
        connector.feed("\033[?1002h\033[?1006h");
        Await.until(session.internalAccess()::mouseReporting, "mouse reporting on");

        session.internalAccess().reportMouse(1, 1, new MouseInput(MouseInput.Type.DRAGGED, MouseInput.Button.LEFT, false, false, false, 0));

        assertThat(connector.written()).isNotEmpty();
    }

    @Test
    void motionIsReportedInAllMotionMode() throws Exception {
        connector.feed("\033[?1003h\033[?1006h");
        Await.until(session.internalAccess()::mouseReporting, "mouse reporting on");

        session.internalAccess().reportMouse(1, 1, new MouseInput(MouseInput.Type.MOVED, MouseInput.Button.NONE, false, false, false, 0));

        assertThat(connector.written()).isNotEmpty();
    }

    @Test
    void urlsInPlainTextAreFound() throws Exception {
        connector.feed("see https://jasper.dev/docs.");
        Await.until(() -> {
            var snapshot = session.internalAccess().snapshot();
            return (snapshot.lineText(0) + snapshot.lineText(1)).equals("see https://jasper.dev/docs.");
        }, "complete wrapped url text");

        assertThat(session.internalAccess().linkAt(0, 10)).contains("https://jasper.dev/docs");
    }

    @Test
    void theAlternateScreenIsReported() throws Exception {
        assertThat(session.internalAccess().usingAlternateBuffer()).isFalse();

        connector.feed("\033[?1049h");

        Await.until(session.internalAccess()::usingAlternateBuffer, "alternate screen");
    }

    private void enableSgrMouse() throws Exception {
        connector.feed("\033[?1000h\033[?1006h");
        Await.until(session.internalAccess()::mouseReporting, "mouse reporting on");
    }
}
