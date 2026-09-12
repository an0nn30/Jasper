package dev.moray.terminal;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.util.CharUtils;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Splits a terminal line into runs of identical style and font. Event Dispatch Thread only. */
final class RunBuilder {
    private static final int MAX_CACHED_STYLES = 4096;
    private static final char REPLACEMENT_CHARACTER = (char) 0xFFFD;

    private final FontSet fonts;
    private final Palette palette;
    private final Map<TextStyle, CellStyle> styleCache = new HashMap<>();

    RunBuilder(FontSet fonts, Palette palette) {
        this.fonts = fonts;
        this.palette = palette;
    }

    List<Run> build(TerminalLine line, int width) {
        char[] chars = new char[width];
        TextStyle[] styles = new TextStyle[width];
        readCells(line, width, chars, styles);
        return build(chars, styles, width);
    }

    /** Pass null for styles when extracting only text, avoiding unused per-cell style storage. */
    static void readCells(TerminalLine line, int width, char[] chars, TextStyle[] styles) {
        Arrays.fill(chars, 0, width, ' ');
        if (styles != null) Arrays.fill(styles, 0, width, TextStyle.EMPTY);
        int column = 0;
        for (TerminalLine.TextEntry entry : line.getEntries()) {
            CharBuffer text = entry.getText();
            TextStyle style = entry.getStyle();
            for (int i = 0; i < text.length() && column < width; i++, column++) {
                char c = text.charAt(i);
                chars[column] = c == CharUtils.NUL_CHAR ? ' ' : c;
                if (styles != null) styles[column] = style;
            }
        }
    }

    List<Run> build(char[] chars, TextStyle[] styles, int width) {
        List<Run> runs = new ArrayList<>();
        Accumulator current = null;
        int column = 0;
        while (column < width) {
            char first = chars[column];
            char second = 0;
            int codePoint;
            int span;
            if (Character.isHighSurrogate(first) && column + 1 < width && Character.isLowSurrogate(chars[column + 1])) {
                second = chars[column + 1];
                codePoint = Character.toCodePoint(first, second);
                span = 2;
            } else {
                if (first == CharUtils.DWC) {
                    first = ' ';
                } else if (Character.isSurrogate(first)) {
                    first = REPLACEMENT_CHARACTER; // half of a pair whose partner is missing
                }
                codePoint = first;
                span = column + 1 < width && chars[column + 1] == CharUtils.DWC ? 2 : 1;
            }
            CellStyle style = styleOf(styles[column]);
            Font font = fonts.fontFor(codePoint, style.bold(), style.italic());
            if (current == null || !current.accepts(style, font)) {
                if (current != null) {
                    runs.add(current.toRun());
                }
                current = new Accumulator(column, style, font);
            }
            current.add(first, column);
            if (second != 0) {
                current.add(second, column);
            }
            column += span;
            current.endColumn = column;
        }
        if (current != null) {
            runs.add(current.toRun());
        }
        return runs;
    }

    private CellStyle styleOf(TextStyle style) {
        if (styleCache.size() > MAX_CACHED_STYLES) {
            styleCache.clear();
        }
        return styleCache.computeIfAbsent(style, s -> CellStyle.resolve(s, palette));
    }

    private static final class Accumulator {
        private final int startColumn;
        private final CellStyle style;
        private final Font font;
        private final StringBuilder text = new StringBuilder();
        private int[] charColumns = new int[16];
        private int endColumn;

        Accumulator(int startColumn, CellStyle style, Font font) {
            this.startColumn = startColumn;
            this.endColumn = startColumn;
            this.style = style;
            this.font = font;
        }

        boolean accepts(CellStyle style, Font font) {
            return this.font == font && this.style.equals(style);
        }

        void add(char c, int column) {
            if (text.length() == charColumns.length) {
                charColumns = Arrays.copyOf(charColumns, charColumns.length * 2);
            }
            charColumns[text.length()] = column - startColumn;
            text.append(c);
        }

        Run toRun() {
            int length = text.length();
            char[] chars = new char[length];
            text.getChars(0, length, chars, 0);
            return new Run(startColumn, endColumn - startColumn, chars, Arrays.copyOf(charColumns, length), style, font);
        }
    }
}
