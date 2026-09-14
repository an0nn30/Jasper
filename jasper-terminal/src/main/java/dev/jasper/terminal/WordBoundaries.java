package dev.jasper.terminal;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.util.CharUtils;

/** What a double-click selects: a run of word characters, where paths and URLs count as one word. */
final class WordBoundaries {
    private static final String WORD_PUNCTUATION = "_-./~:@%+=?&#";

    private WordBoundaries() {
    }

    /** The word covering a column, as inclusive {from, to}; a non-word cell selects only itself. */
    static int[] wordAt(TerminalLine line, int width, int column) {
        char[] chars = new char[width];
        TextStyle[] styles = new TextStyle[width];
        RunBuilder.readCells(line, width, chars, styles);
        int at = Math.max(0, Math.min(width - 1, column));
        if (chars[at] == CharUtils.DWC && at > 0) {
            at--;
        }
        if (!isWordChar(chars[at])) {
            return new int[] {at, at};
        }
        int from = at;
        while (from > 0 && (isWordChar(chars[from - 1]) || chars[from - 1] == CharUtils.DWC)) {
            from--;
        }
        int to = at;
        while (to < width - 1 && (isWordChar(chars[to + 1]) || chars[to + 1] == CharUtils.DWC)) {
            to++;
        }
        return new int[] {from, to};
    }

    static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || Character.isSurrogate(c) || WORD_PUNCTUATION.indexOf(c) >= 0;
    }
}
