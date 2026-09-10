package dev.moray.terminal;

import com.jediterm.core.util.TermSize;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class TerminalViewTest {
    private final TerminalOptions options = TerminalOptions.defaults();
    private final FontSet fonts = new FontSet(options.fontFamily(), options.fontSize(), options.fallbackFonts(), options.ligatures());
    private FakeConnector connector;
    private TerminalSession session;
    private TerminalView view;

    @BeforeEach
    void setUp() throws Exception {
        connector = new FakeConnector();
        session = new TerminalSession(connector, 20, 4, 100);
        session.startReading();
        view = new TerminalView(session, options);
    }

    @AfterEach
    void tearDown() {
        session.close();
    }

    @Test
    void preferredSizeIsTheSessionGrid() {
        assertThat(view.getPreferredSize())
            .isEqualTo(new Dimension(20 * fonts.cellWidth(), 4 * fonts.cellHeight()));
    }

    @Test
    void resizingTheViewResizesTheSession() {
        view.setSize(10 * fonts.cellWidth() + 3, 2 * fonts.cellHeight() + 1);

        view.resizeSessionToFit();

        assertThat(session.columns()).isEqualTo(10);
        assertThat(session.rows()).isEqualTo(2);
        assertThat(connector.lastResize()).isEqualTo(new TermSize(10, 2));
    }

    @Test
    void enterSendsCarriageReturnAndItsTypedEventIsSkipped() {
        view.handleKey(pressed(KeyEvent.VK_ENTER));
        view.handleKey(typed('\n'));

        assertThat(connector.written()).isEqualTo("\r");
    }

    @Test
    void typedCharactersAreSent() {
        view.handleKey(pressed(KeyEvent.VK_A));
        view.handleKey(typed('a'));

        assertThat(connector.written()).isEqualTo("a");
    }

    @Test
    void anArrowKeyDoesNotSwallowTheNextCharacter() {
        view.handleKey(pressed(KeyEvent.VK_LEFT));
        view.handleKey(pressed(KeyEvent.VK_B));
        view.handleKey(typed('b'));

        assertThat(connector.written()).isEqualTo("\033[Db");
    }

    @Test
    void paintsTheThemeBackground() {
        view.setSize(20 * fonts.cellWidth(), 4 * fonts.cellHeight());
        BufferedImage image = new BufferedImage(view.getWidth(), view.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            view.paint(g);
        } finally {
            g.dispose();
        }

        assertThat(image.getRGB(view.getWidth() - 1, view.getHeight() - 1) & 0xFFFFFF)
            .isEqualTo(options.palette().background().getRGB() & 0xFFFFFF);
    }

    private KeyEvent pressed(int keyCode) {
        return new KeyEvent(view, KeyEvent.KEY_PRESSED, 0, 0, keyCode, KeyEvent.CHAR_UNDEFINED);
    }

    private KeyEvent typed(char c) {
        return new KeyEvent(view, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, c);
    }
}
