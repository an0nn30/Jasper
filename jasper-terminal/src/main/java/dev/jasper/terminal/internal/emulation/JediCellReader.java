package dev.jasper.terminal.internal.emulation;

import dev.jasper.terminal.config.CursorStyle;
import dev.jasper.terminal.internal.text.CellAttributes;
import dev.jasper.terminal.internal.text.CursorRequest;
import dev.jasper.terminal.internal.text.TerminalRow;
import com.jediterm.terminal.*;
import com.jediterm.terminal.model.TerminalLine;
import java.util.HashMap;
import java.util.Map;

/** Converts live vendor cells while the caller holds the buffer lock. */
final class JediCellReader {
    private final Map<TextStyle, CellAttributes> styles = new HashMap<>();
    /** Snapshot entry membership and styles without expanding full-width cell arrays. */
    TerminalRow capture(TerminalLine line, int width) {
        var entries = line.getEntries().toArray(TerminalLine.TextEntry[]::new);
        int textLength = 0;
        boolean ended = false;
        for (int i = 0; i < entries.length; i++) {
            ended |= entries[i].isNul();
            if (!ended) textLength += entries[i].getLength();
        }
        return new DetachedRow(entries, line.isWrapped(), line.length(), textLength);
    }

    /** Borrowed view: every use, including style conversion, requires the buffer lock. */
    TerminalRow live(TerminalLine line) { return new LiveRow(line); }

    private final class LiveRow implements TerminalRow {
        private final TerminalLine line;
        LiveRow(TerminalLine line) { this.line = line; }
        public String getText() { return line.getText(); }
        public boolean isWrapped() { return line.isWrapped(); }
        public int length() { return line.length(); }
        public CellAttributes attributesAt(int column) { return attributes(line.getStyleAt(column)); }
        public void readCells(int width, char[] target, CellAttributes[] styles) {
            prepare(width, target, styles);
            int column = 0;
            for (var entry : line.getEntries()) {
                column = read(entry, styles == null ? null : attributes(entry.getStyle()), column, width, target, styles);
                if (column >= width) break;
            }
        }
    }

    /**
     * JediTerm 3.76 replaces entries when writing. The sole in-place entry mutation
     * converts trailing NULs to spaces when appending: equivalent for readCells.
     * Capture the pre-mutation text length so getText also remains detached.
     */
    private static final class DetachedRow implements TerminalRow {
        private final TerminalLine.TextEntry[] entries;
        private final boolean wrapped;
        private final int length, textLength;
        DetachedRow(TerminalLine.TextEntry[] entries, boolean wrapped, int length, int textLength) {
            this.entries = entries;
            this.wrapped = wrapped;
            this.length = length;
            this.textLength = textLength;
        }
        public boolean isWrapped() { return wrapped; }
        public int length() { return length; }
        public String getText() {
            var text = new StringBuilder(textLength);
            int remaining = textLength;
            for (var entry : entries) {
                if (remaining == 0) break;
                int count = Math.min(remaining, entry.getLength());
                text.append(entry.getText(), 0, count);
                remaining -= count;
            }
            return text.toString();
        }
        public CellAttributes attributesAt(int column) {
            for (int i = 0; i < entries.length; i++) {
                if (column < entries[i].getLength()) return convert(entries[i].getStyle());
                column -= entries[i].getLength();
            }
            return CellAttributes.DEFAULT;
        }
        public void readCells(int width, char[] target, CellAttributes[] styles) {
            prepare(width, target, styles);
            int column = 0;
            for (int i = 0; i < entries.length && column < width; i++) {
                column = read(entries[i], styles == null ? null : convert(entries[i].getStyle()), column, width, target, styles);
            }
        }
    }
    private static void prepare(int width, char[] target, CellAttributes[] styles) {
        if (width < 0 || target.length < width || (styles != null && styles.length < width))
            throw new IllegalArgumentException("cell target too small");
        java.util.Arrays.fill(target, 0, width, ' ');
        if (styles != null) java.util.Arrays.fill(styles, 0, width, CellAttributes.DEFAULT);
    }
    private static int read(TerminalLine.TextEntry entry, CellAttributes attributes, int column,
                            int width, char[] target, CellAttributes[] styles) {
        var text = entry.getText();
        for (int i = 0; i < text.length() && column < width; i++, column++) {
            char c = text.charAt(i);
            target[column] = c == 0 ? ' ' : c;
            if (styles != null) styles[column] = attributes;
        }
        return column;
    }
    CellAttributes attributes(TextStyle style) {
        if (styles.size() > 4096) styles.clear();
        return styles.computeIfAbsent(style, JediCellReader::convert);
    }
    private static CellAttributes convert(TextStyle style) {
        if (style == TextStyle.EMPTY) return CellAttributes.DEFAULT;
        int flags = 0;
        if (style.hasOption(TextStyle.Option.BOLD)) flags |= CellAttributes.BOLD;
        if (style.hasOption(TextStyle.Option.ITALIC)) flags |= CellAttributes.ITALIC;
        if (style.hasOption(TextStyle.Option.UNDERLINED) || style instanceof HyperlinkStyle) flags |= CellAttributes.UNDERLINE;
        if (style.hasOption(TextStyle.Option.INVERSE)) flags |= CellAttributes.INVERSE;
        if (style.hasOption(TextStyle.Option.DIM)) flags |= CellAttributes.DIM;
        if (style.hasOption(TextStyle.Option.HIDDEN)) flags |= CellAttributes.HIDDEN;
        String link = style instanceof HyperlinkStyle h && h.getLinkInfo() instanceof UriLink u ? u.uri() : null;
        return new CellAttributes(color(style.getForeground()), color(style.getBackground()), flags, link);
    }
    static int color(TerminalColor value) {
        if (value == null) return -1;
        if (value.isIndexed()) return value.getColorIndex();
        var rgb = value.toColor();
        return 0x01000000 | (rgb.getRed() << 16) | (rgb.getGreen() << 8) | rgb.getBlue();
    }
    static CursorRequest cursor(CursorShape shape) {
        if (shape == null) return null;
        return switch (shape) {
            case BLINK_BLOCK -> new CursorRequest(CursorStyle.BLOCK, true);
            case STEADY_BLOCK -> new CursorRequest(CursorStyle.BLOCK, false);
            case BLINK_UNDERLINE -> new CursorRequest(CursorStyle.UNDERLINE, true);
            case STEADY_UNDERLINE -> new CursorRequest(CursorStyle.UNDERLINE, false);
            case BLINK_VERTICAL_BAR -> new CursorRequest(CursorStyle.BEAM, true);
            case STEADY_VERTICAL_BAR -> new CursorRequest(CursorStyle.BEAM, false);
        };
    }
}
