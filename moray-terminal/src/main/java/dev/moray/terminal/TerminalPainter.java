package dev.moray.terminal;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.util.CharUtils;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.GlyphVector;
import java.awt.geom.Point2D;
import java.util.List;

/** Draws a {@link ScreenSnapshot}. Event Dispatch Thread only. */
final class TerminalPainter {

    /** How the cursor should look this frame; {@code on} is false during the blink-off phase. */
    record CursorLook(CursorStyle style, boolean on, boolean focused) {
    }

    /** A background fill over one viewport row, columns inclusive: a selection or a search match. */
    record Highlight(int row, int startColumn, int endColumn, Color color) {
    }

    private static final int BAR_THICKNESS = 2;
    private static final int INDICATOR_WIDTH = 4;
    private static final int INDICATOR_MIN_HEIGHT = 12;

    private final FontSet fonts;
    private final Palette palette;
    private final RunBuilder runs;

    TerminalPainter(FontSet fonts, Palette palette) {
        this.fonts = fonts;
        this.palette = palette;
        this.runs = new RunBuilder(fonts, palette);
    }

    void paint(Graphics2D g, ScreenSnapshot snapshot, CursorLook cursor, List<Highlight> highlights, int widthPx, int heightPx) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setColor(palette.background());
        g.fillRect(0, 0, widthPx, heightPx);

        int cellWidth = fonts.cellWidth();
        int cellHeight = fonts.cellHeight();
        for (int row = 0; row < snapshot.height(); row++) {
            List<Run> rowRuns = runs.build(snapshot.lines().get(row), snapshot.width());
            int top = row * cellHeight;
            for (Run run : rowRuns) {
                Color background = run.style().background();
                if (!background.equals(palette.background())) {
                    g.setColor(background);
                    g.fillRect(run.startColumn() * cellWidth, top, run.columns() * cellWidth, cellHeight);
                }
            }
            for (Highlight highlight : highlights) {
                if (highlight.row() == row) {
                    g.setColor(highlight.color());
                    g.fillRect(highlight.startColumn() * cellWidth, top,
                        (highlight.endColumn() - highlight.startColumn() + 1) * cellWidth, cellHeight);
                }
            }
            for (Run run : rowRuns) {
                drawRun(g, run, top);
            }
        }
        paintCursor(g, snapshot, cursor);
        if (snapshot.scrollOffset() > 0) {
            paintScrollIndicator(g, snapshot, widthPx, heightPx);
        }
    }

    private void drawRun(Graphics2D g, Run run, int top) {
        int cellWidth = fonts.cellWidth();
        int left = run.startColumn() * cellWidth;
        int baseline = top + fonts.ascent();
        g.setColor(run.style().foreground());
        if (!isBlank(run.text())) {
            GlyphVector glyphs = fonts.layout(run.font(), run.text());
            int[] charColumns = run.charColumns();
            for (int i = 0; i < glyphs.getNumGlyphs(); i++) {
                int charIndex = Math.min(glyphs.getGlyphCharIndex(i), charColumns.length - 1);
                glyphs.setGlyphPosition(i, new Point2D.Float(charColumns[charIndex] * cellWidth, 0));
            }
            g.drawGlyphVector(glyphs, left, baseline);
        }
        if (run.style().underline()) {
            g.fillRect(left, baseline + 1, run.columns() * cellWidth, 1);
        }
    }

    private void paintCursor(Graphics2D g, ScreenSnapshot snapshot, CursorLook look) {
        if (!look.on() || !snapshot.cursorVisible()) {
            return;
        }
        int column = Math.min(snapshot.cursorColumn(), snapshot.width() - 1);
        int row = snapshot.cursorRow();
        if (column < 0 || row < 0 || column >= snapshot.width() || row >= snapshot.height()) {
            return;
        }
        int cellWidth = fonts.cellWidth();
        int cellHeight = fonts.cellHeight();
        int x = column * cellWidth;
        int y = row * cellHeight;
        g.setColor(palette.cursor());
        if (!look.focused()) {
            g.drawRect(x, y, cellWidth - 1, cellHeight - 1);
            return;
        }
        switch (look.style()) {
            case BLOCK -> {
                g.fillRect(x, y, cellWidth, cellHeight);
                drawCharacterUnderBlockCursor(g, snapshot, column, row, x, y);
            }
            case BEAM -> g.fillRect(x, y, BAR_THICKNESS, cellHeight);
            case UNDERLINE -> g.fillRect(x, y + cellHeight - BAR_THICKNESS, cellWidth, BAR_THICKNESS);
        }
    }

    /** Where the view sits in the scrollback: a translucent thumb on the right edge. */
    private void paintScrollIndicator(Graphics2D g, ScreenSnapshot snapshot, int widthPx, int heightPx) {
        int total = snapshot.historyLines() + snapshot.height();
        int thumbHeight = Math.max(INDICATOR_MIN_HEIGHT, heightPx * snapshot.height() / total);
        int linesAboveView = snapshot.historyLines() - snapshot.scrollOffset();
        int thumbTop = (heightPx - thumbHeight) * linesAboveView / Math.max(1, snapshot.historyLines());
        Color foreground = palette.foreground();
        g.setColor(new Color(foreground.getRed(), foreground.getGreen(), foreground.getBlue(), 0x70));
        g.fillRect(widthPx - INDICATOR_WIDTH, thumbTop, INDICATOR_WIDTH, thumbHeight);
    }

    private void drawCharacterUnderBlockCursor(Graphics2D g, ScreenSnapshot snapshot, int column, int row, int x, int y) {
        char[] chars = new char[snapshot.width()];
        TextStyle[] styles = new TextStyle[snapshot.width()];
        RunBuilder.readCells(snapshot.lines().get(row), snapshot.width(), chars, styles);
        char c = chars[column];
        if (c == ' ' || c == CharUtils.DWC || Character.isSurrogate(c)) {
            return;
        }
        CellStyle style = CellStyle.resolve(styles[column], palette);
        Font font = fonts.fontFor(c, style.bold(), style.italic());
        g.setColor(palette.background());
        g.drawGlyphVector(fonts.layout(font, new char[] {c}), x, y + fonts.ascent());
    }

    private static boolean isBlank(char[] text) {
        for (char c : text) {
            if (c != ' ') {
                return false;
            }
        }
        return true;
    }
}
