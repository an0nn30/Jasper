package dev.moray.terminal;

import com.jediterm.core.util.TermSize;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TerminalPaletteTest {
    private static final int COLUMNS = 20;
    private static final int ROWS = 4;
    private static final int[] DARK_ANSI = {
        0x282c34, 0xe06c75, 0x98c379, 0xe5c07b, 0x61afef, 0xc678dd, 0x56b6c2, 0xabb2bf,
        0x5c6370, 0xef7b85, 0xa9d48a, 0xf0cc8c, 0x74bff8, 0xd68bee, 0x67c7d3, 0xe6e9ef
    };
    private static final int[] LIGHT_ANSI = {
        0x383a42, 0xe45649, 0x50a14f, 0xc18401, 0x4078f2, 0xa626a4, 0x0184bc, 0xa0a1a7,
        0x696c77, 0xca4035, 0x3d7d3b, 0x986801, 0x315fc4, 0x87218b, 0x006b96, 0xffffff
    };

    private final TerminalOptions options = TerminalOptions.defaults();
    private FakeConnector connector;
    private TerminalSession session;
    private TerminalView view;

    @BeforeEach
    void setUp() throws Exception {
        connector = new FakeConnector();
        session = new TerminalSession(connector, COLUMNS, ROWS, 100);
        session.startReading();
        onEdt(() -> {
            view = new TerminalView(session, options);
            FontSet fonts = fontsAt(options.fontSize());
            view.setSize(COLUMNS * fonts.cellWidth(), ROWS * fonts.cellHeight());
        });
        drainEdt();
    }

    @AfterEach
    void tearDown() {
        session.close();
    }

    @Test
    void switchingPaletteRecolorsExistingDefaultAndIndexedCellsButKeepsTruecolor() throws Exception {
        connector.feed(paletteBlocks());
        Await.until(() -> session.snapshot().cursorColumn() == 18, "palette blocks on screen");

        BufferedImage dark = onEdtResult(this::paint);
        assertPalettePixels(dark, fontsAt(14f), 0xabb2bf, 0x282c34, DARK_ANSI);
        assertThat(cellCenter(dark, fontsAt(14f), 17)).isEqualTo(0x123456);
        assertThat(cursorEdge(dark, fontsAt(14f), 18)).isEqualTo(0xabb2bf);

        onEdt(() -> view.setPalette(Palette.morayLight()));

        BufferedImage light = onEdtResult(this::paint);
        assertPalettePixels(light, fontsAt(14f), 0x383a42, 0xfafafa, LIGHT_ANSI);
        assertThat(cellCenter(light, fontsAt(14f), 17)).isEqualTo(0x123456);
        assertThat(cursorEdge(light, fontsAt(14f), 18)).isEqualTo(0x526fff);

        onEdt(() -> view.setFontSize(12f));

        BufferedImage afterFontChange = onEdtResult(this::paint);
        assertPalettePixels(afterFontChange, fontsAt(12f), 0x383a42, 0xfafafa, LIGHT_ANSI);
        assertThat(cellCenter(afterFontChange, fontsAt(12f), 17)).isEqualTo(0x123456);
        assertThat(cursorEdge(afterFontChange, fontsAt(12f), 18)).isEqualTo(0x526fff);
    }

    @Test
    void switchingPalettePreservesGridContentSelectionFindAndFontWithoutResizing() throws Exception {
        onEdt(() -> view.setFontSize(16f));
        connector.feed("match me\033[?25l");
        Await.until(() -> session.snapshot().lineText(0).startsWith("match me"), "text on screen");
        onEdt(() -> {
            selectByDragging(0, 0, 4, 0);
            assertThat(view.find("match", false, false)).isEqualTo(new FindResult(1, 1, null));
        });

        String contentBefore = session.snapshot().lineText(0);
        int columnsBefore = session.columns();
        int rowsBefore = session.rows();
        TermSize resizeBefore = connector.lastResize();
        Dimension minimumBefore = onEdtResult(view::getMinimumSize);
        String selectionBefore = onEdtResult(() -> view.selectedText().orElseThrow());
        float fontBefore = onEdtResult(view::fontSize);

        onEdt(() -> view.setPalette(Palette.morayLight()));

        assertThat(session.snapshot().lineText(0)).isEqualTo(contentBefore);
        assertThat(session.columns()).isEqualTo(columnsBefore);
        assertThat(session.rows()).isEqualTo(rowsBefore);
        assertThat(connector.lastResize()).isEqualTo(resizeBefore);
        assertThat(onEdtResult(view::getMinimumSize)).isEqualTo(minimumBefore);
        assertThat(onEdtResult(() -> view.selectedText().orElseThrow())).isEqualTo(selectionBefore);
        assertThat(onEdtResult(view::findNext)).isEqualTo(new FindResult(1, 1, null));
        assertThat(onEdtResult(view::fontSize)).isEqualTo(fontBefore);
        assertThat(onEdtResult(view::palette)).isEqualTo(Palette.morayLight());
        assertThat(onEdtResult(view::getBackground)).isEqualTo(new Color(0xfafafa));
    }

    @Test
    void selectionSearchAndInactiveOverlayUseTheCurrentPalette() throws Exception {
        connector.feed("xx    \033[?25l");
        Await.until(() -> session.snapshot().cursorColumn() == 6, "search text on screen");
        onEdt(() -> {
            view.setPalette(Palette.morayLight());
            assertThat(view.find("x", false, false)).isEqualTo(new FindResult(2, 2, null));
            selectByDragging(3, 0, 4, 0);
        });

        FontSet fonts = fontsAt(14f);
        BufferedImage highlighted = onEdtResult(this::paint);
        assertThat(cellCorner(highlighted, fonts, 0)).isEqualTo(0xe9d7af);
        assertThat(cellCorner(highlighted, fonts, 1)).isEqualTo(0xd5ad58);
        assertThat(cellCorner(highlighted, fonts, 3)).isEqualTo(0xd5def5);

        onEdt(() -> view.setInactiveDim(1f));

        BufferedImage inactive = onEdtResult(this::paint);
        assertThat(cellCorner(inactive, fonts, 0)).isEqualTo(0xfafafa);
        assertThat(cellCenter(inactive, fonts, 1)).isEqualTo(0xfafafa);
    }

    @Test
    void nullPaletteFailsWithoutChangingTheCurrentPaletteOrRenderedBackground() throws Exception {
        onEdt(() -> view.setPalette(Palette.morayLight()));
        BufferedImage before = onEdtResult(this::paint);

        onEdt(() -> assertThatThrownBy(() -> view.setPalette(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("palette"));

        assertThat(onEdtResult(view::palette)).isEqualTo(Palette.morayLight());
        assertThat(onEdtResult(view::getBackground)).isEqualTo(new Color(0xfafafa));
        assertThat(onEdtResult(this::paint).getRGB(view.getWidth() - 1, view.getHeight() - 1))
            .isEqualTo(before.getRGB(view.getWidth() - 1, view.getHeight() - 1));
    }

    @Test
    void paletteRejectsMissingRequiredColorsAndNullAnsiEntries() {
        List<Color> colors = new ArrayList<>(Collections.nCopies(16, Color.BLACK));
        colors.set(7, null);

        assertThatThrownBy(() -> new Palette(null, Color.BLACK, Color.WHITE, Color.GRAY,
            Collections.nCopies(16, Color.BLACK))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Palette(Color.WHITE, null, Color.WHITE, Color.GRAY,
            Collections.nCopies(16, Color.BLACK))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Palette(Color.WHITE, Color.BLACK, null, Color.GRAY,
            Collections.nCopies(16, Color.BLACK))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Palette(Color.WHITE, Color.BLACK, Color.WHITE, null,
            Collections.nCopies(16, Color.BLACK))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Palette(Color.WHITE, Color.BLACK, Color.WHITE, Color.GRAY, null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Palette(Color.WHITE, Color.BLACK, Color.WHITE, Color.GRAY, colors))
            .isInstanceOf(NullPointerException.class);
    }

    private String paletteBlocks() {
        StringBuilder output = new StringBuilder("\033[39m█");
        for (int index = 0; index < 16; index++) {
            output.append("\033[38;5;").append(index).append("m█");
        }
        return output.append("\033[38;2;18;52;86m█").toString();
    }

    private void assertPalettePixels(BufferedImage image, FontSet fonts, int foreground, int background,
                                     int[] ansi) {
        assertThat(cellCenter(image, fonts, 0)).isEqualTo(foreground);
        for (int index = 0; index < ansi.length; index++) {
            assertThat(cellCenter(image, fonts, index + 1)).as("ANSI color %s", index).isEqualTo(ansi[index]);
        }
        assertThat(rgb(image, view.getWidth() - 1, view.getHeight() - 1)).isEqualTo(background);
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

    private void selectByDragging(int fromColumn, int fromRow, int toColumn, int toRow) {
        FontSet fonts = fontsAt(view.fontSize());
        view.handleMouse(new MouseEvent(view, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON1_DOWN_MASK,
            x(fonts, fromColumn), y(fonts, fromRow), 1, false, MouseEvent.BUTTON1));
        view.handleMouse(new MouseEvent(view, MouseEvent.MOUSE_DRAGGED, 0, InputEvent.BUTTON1_DOWN_MASK,
            x(fonts, toColumn), y(fonts, toRow), 1, false, MouseEvent.NOBUTTON));
        view.handleMouse(new MouseEvent(view, MouseEvent.MOUSE_RELEASED, 0, 0,
            x(fonts, toColumn), y(fonts, toRow), 1, false, MouseEvent.BUTTON1));
    }

    private FontSet fontsAt(float size) {
        return new FontSet(options.fontFamily(), size, options.fallbackFonts(), options.ligatures());
    }

    private static int cellCenter(BufferedImage image, FontSet fonts, int column) {
        return rgb(image, column * fonts.cellWidth() + fonts.cellWidth() / 2, fonts.cellHeight() / 2);
    }

    private static int cellCorner(BufferedImage image, FontSet fonts, int column) {
        return rgb(image, column * fonts.cellWidth() + 1, 1);
    }

    private static int cursorEdge(BufferedImage image, FontSet fonts, int column) {
        return rgb(image, column * fonts.cellWidth(), fonts.cellHeight() / 2);
    }

    private static int x(FontSet fonts, int column) {
        return column * fonts.cellWidth() + fonts.cellWidth() / 2;
    }

    private static int y(FontSet fonts, int row) {
        return row * fonts.cellHeight() + fonts.cellHeight() / 2;
    }

    private static int rgb(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) & 0xFFFFFF;
    }

    private static void onEdt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }

    private static <T> T onEdtResult(Supplier<T> action) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        onEdt(() -> result.set(action.get()));
        return result.get();
    }

    private static void drainEdt() throws Exception {
        onEdt(() -> { });
    }
}
