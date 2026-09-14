package dev.jasper.terminal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class TerminalAppearanceTest {
    private final TerminalOptions options = TerminalOptions.defaults();
    private final FontSet defaultFonts = new FontSet(options.fontFamily(), options.fontSize(), options.fallbackFonts(),
        options.ligatures());
    private FakeConnector connector;
    private TerminalSession session;
    private TerminalView view;

    @BeforeEach
    void setUp() throws Exception {
        connector = new FakeConnector();
        session = new TerminalSession(connector, 20, 4, 100);
        session.startReading();
        view = new TerminalView(session, options);
        view.setSize(20 * defaultFonts.cellWidth(), 4 * defaultFonts.cellHeight());
    }

    @AfterEach
    void tearDown() {
        session.close();
    }

    @Test
    void changingFontSizeRebuildsMetricsAndPreservesTheSessionText() throws Exception {
        connector.feed("hello");
        Await.until(() -> session.snapshot().lineText(0).startsWith("hello"), "hello on screen");

        view.setFontSize(20f);

        assertThat(view.fontSize()).isEqualTo(20f);
        assertThat(session.columns()).isLessThan(20);
        assertThat(screenText()).contains("hello");

        view.resetFontSize();

        assertThat(view.fontSize()).isEqualTo(14f);
        assertThat(session.columns()).isEqualTo(20);
        assertThat(screenText()).contains("hello");
    }

    @Test
    void fontSizeIsBoundedForApplicationCommands() {
        view.setFontSize(100f);
        assertThat(view.fontSize()).isEqualTo(72f);

        view.setFontSize(1f);
        assertThat(view.fontSize()).isEqualTo(6f);
    }

    @Test
    void inactiveDimmingOverlaysThePaintedTerminalWithItsBackground() throws Exception {
        connector.feed("\033[32m█");
        Await.until(() -> session.snapshot().cursorColumn() == 1, "block character on screen");
        BufferedImage active = paint();

        view.setInactiveDim(1f);
        BufferedImage inactive = paint();

        int x = defaultFonts.cellWidth() / 2;
        int y = defaultFonts.cellHeight() / 2;
        assertThat(rgb(active, x, y)).isEqualTo(rgb(options.palette().ansi().get(2)));
        assertThat(rgb(inactive, x, y)).isEqualTo(rgb(options.palette().background()));
    }

    private BufferedImage paint() {
        BufferedImage image = new BufferedImage(view.getWidth(), view.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            view.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private String screenText() {
        ScreenSnapshot snapshot = session.snapshot();
        StringBuilder text = new StringBuilder();
        for (int row = 0; row < snapshot.height(); row++) {
            text.append(snapshot.lineText(row)).append('\n');
        }
        return text.toString();
    }

    private static int rgb(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) & 0xFFFFFF;
    }

    private static int rgb(java.awt.Color color) {
        return color.getRGB() & 0xFFFFFF;
    }
}
