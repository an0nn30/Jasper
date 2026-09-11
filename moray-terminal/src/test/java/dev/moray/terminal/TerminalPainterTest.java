package dev.moray.terminal;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TerminalPainterTest {
    private static final int COLUMNS = 10;
    private static final int ROWS = 3;

    private final Palette palette = Palette.morayDark();
    private final FontSet fonts = new FontSet("JetBrains Mono", 14f, List.of(), true);
    private final TerminalPainter painter = new TerminalPainter(fonts, palette);
    private final int cw = fonts.cellWidth();
    private final int ch = fonts.cellHeight();

    @Test
    void emptyScreenIsThemeBackground() throws Exception {
        BufferedImage image = paint(snapshotAfter("", 0), cursorOff());

        assertThat(rgb(image, COLUMNS * cw - 1, ROWS * ch - 1)).isEqualTo(rgb(palette.background()));
    }

    @Test
    void coloredBackgroundFillsOnlyItsCells() throws Exception {
        BufferedImage image = paint(snapshotAfter("\033[41m  \033[0m", 2), cursorOff());

        assertThat(rgb(image, cw / 2, ch / 2)).isEqualTo(rgb(palette.ansi().get(1)));
        assertThat(rgb(image, cw + cw / 2, ch / 2)).isEqualTo(rgb(palette.ansi().get(1)));
        assertThat(rgb(image, 3 * cw + cw / 2, ch / 2)).isEqualTo(rgb(palette.background()));
    }

    @Test
    void textIsDrawnInItsForegroundColor() throws Exception {
        BufferedImage image = paint(snapshotAfter("\033[32m█\033[0m", 1), cursorOff());

        assertThat(rgb(image, cw / 2, ch / 2)).isEqualTo(rgb(palette.ansi().get(2)));
    }

    @Test
    void focusedBlockCursorFillsItsCell() throws Exception {
        BufferedImage image = paint(snapshotAfter("ab", 2), new TerminalPainter.CursorLook(CursorStyle.BLOCK, true, true));

        assertThat(rgb(image, 2 * cw + 1, 1)).isEqualTo(rgb(palette.cursor()));
        assertThat(rgb(image, 2 * cw + cw / 2, ch / 2)).isEqualTo(rgb(palette.cursor()));
    }

    @Test
    void cursorInBlinkOffPhaseIsNotDrawn() throws Exception {
        BufferedImage image = paint(snapshotAfter("ab", 2), cursorOff());

        assertThat(rgb(image, 2 * cw + cw / 2, ch / 2)).isEqualTo(rgb(palette.background()));
    }

    @Test
    void unfocusedCursorIsHollow() throws Exception {
        BufferedImage image = paint(snapshotAfter("ab", 2), new TerminalPainter.CursorLook(CursorStyle.BLOCK, true, false));

        assertThat(rgb(image, 2 * cw, ch / 2)).isEqualTo(rgb(palette.cursor()));
        assertThat(rgb(image, 2 * cw + cw / 2, ch / 2)).isEqualTo(rgb(palette.background()));
    }

    @Test
    void beamCursorIsNarrow() throws Exception {
        BufferedImage image = paint(snapshotAfter("ab", 2), new TerminalPainter.CursorLook(CursorStyle.BEAM, true, true));

        assertThat(rgb(image, 2 * cw, ch / 2)).isEqualTo(rgb(palette.cursor()));
        assertThat(rgb(image, 2 * cw + cw - 2, ch / 2)).isEqualTo(rgb(palette.background()));
    }

    @Test
    void cursorInPendingWrapStateIsDrawnInTheLastColumn() throws Exception {
        BufferedImage image = paint(snapshotAfter("abcdefghij", 10), new TerminalPainter.CursorLook(CursorStyle.BEAM, true, true));

        assertThat(rgb(image, (COLUMNS - 1) * cw, ch / 2)).isEqualTo(rgb(palette.cursor()));
    }

    @Test
    void highlightsFillTheirCells() throws Exception {
        BufferedImage image = paint(snapshotAfter("", 0), cursorOff(),
            List.of(new TerminalPainter.Highlight(1, 2, 3, Color.RED)));

        assertThat(rgb(image, 2 * cw + cw / 2, ch + ch / 2)).isEqualTo(rgb(Color.RED));
        assertThat(rgb(image, 3 * cw + cw / 2, ch + ch / 2)).isEqualTo(rgb(Color.RED));
        assertThat(rgb(image, 4 * cw + cw / 2, ch + ch / 2)).isEqualTo(rgb(palette.background()));
        assertThat(rgb(image, 2 * cw + cw / 2, ch / 2)).isEqualTo(rgb(palette.background()));
    }

    @Test
    void underlineCursorSitsOnTheBottomOfItsCell() throws Exception {
        BufferedImage image = paint(snapshotAfter("ab", 2), new TerminalPainter.CursorLook(CursorStyle.UNDERLINE, true, true));

        assertThat(rgb(image, 2 * cw + cw / 2, ch - 1)).isEqualTo(rgb(palette.cursor()));
        assertThat(rgb(image, 2 * cw + cw / 2, ch / 2)).isEqualTo(rgb(palette.background()));
    }

    @Test
    void aScrollIndicatorShowsOnlyWhenScrolledBack() throws Exception {
        FakeConnector connector = new FakeConnector();
        TerminalSession session = new TerminalSession(connector, COLUMNS, ROWS, 10);
        session.startReading();
        try {
            connector.feed("a\r\nb\r\nc\r\nd\r\ne");
            Await.until(() -> "e".equals(session.snapshot().lineText(2)), "two lines of scrollback");

            BufferedImage scrolled = paint(session.snapshot(0), cursorOff());
            BufferedImage live = paint(session.snapshot(), cursorOff());

            assertThat(rgb(scrolled, COLUMNS * cw - 2, 1)).isNotEqualTo(rgb(palette.background()));
            assertThat(rgb(live, COLUMNS * cw - 2, 1)).isEqualTo(rgb(palette.background()));
        } finally {
            session.close();
        }
    }

    private ScreenSnapshot snapshotAfter(String output, int expectedCursorColumn) throws Exception {
        FakeConnector connector = new FakeConnector();
        TerminalSession session = new TerminalSession(connector, COLUMNS, ROWS, 10);
        session.startReading();
        try {
            connector.feed(output);
            Await.until(() -> session.snapshot().cursorColumn() == expectedCursorColumn, "cursor at column " + expectedCursorColumn);
            return session.snapshot();
        } finally {
            session.close();
        }
    }

    private BufferedImage paint(ScreenSnapshot snapshot, TerminalPainter.CursorLook cursor) {
        return paint(snapshot, cursor, List.of());
    }

    private BufferedImage paint(ScreenSnapshot snapshot, TerminalPainter.CursorLook cursor,
                                List<TerminalPainter.Highlight> highlights) {
        BufferedImage image = new BufferedImage(COLUMNS * cw, ROWS * ch, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            painter.paint(g, snapshot, cursor, highlights, image.getWidth(), image.getHeight());
        } finally {
            g.dispose();
        }
        return image;
    }

    private static TerminalPainter.CursorLook cursorOff() {
        return new TerminalPainter.CursorLook(CursorStyle.BLOCK, false, true);
    }

    private static int rgb(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) & 0xFFFFFF;
    }

    private static int rgb(Color color) {
        return color.getRGB() & 0xFFFFFF;
    }
}
