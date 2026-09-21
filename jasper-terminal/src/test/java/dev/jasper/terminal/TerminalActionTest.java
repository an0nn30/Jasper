package dev.jasper.terminal;
import org.junit.jupiter.api.*;
import javax.swing.SwingUtilities;
import static org.assertj.core.api.Assertions.*;
class TerminalActionTest {
    private FakeConnector connector;
    private TerminalSession session;
    private TerminalAccess access;
    private TerminalView view;
    @BeforeEach void start() throws Exception {
        connector = new FakeConnector();
        session = new TerminalSession(connector, 20, 4, 100);
        access = session.internalAccess();
        view = new TerminalView(session, TerminalOptions.defaults());
        session.startReading();
    }
    @AfterEach void close() { session.close(); }
@Test void pasteActionUsesTheSameBracketedPastePathAsDirectPaste() throws Exception {
    connector.feed("\033[?2004hREADY");
    Await.until(() -> access.snapshot().lineText(0).equals("READY"), "paste mode enabled");
    SwingUtilities.invokeAndWait(() -> {
        view.setClipboard(() -> "a\nb", text -> {});
        view.execute(TerminalAction.PASTE_CLIPBOARD);
    });
    assertThat(connector.written()).isEqualTo("\033[200~a\rb\033[201~");
}

@Test void actionDispatchRejectsOffEdtCalls() {
    assertThatThrownBy(() -> view.execute(TerminalAction.COPY_SELECTION))
        .isInstanceOf(IllegalStateException.class);
}
}
