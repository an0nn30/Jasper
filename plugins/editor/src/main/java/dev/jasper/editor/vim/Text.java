package dev.jasper.editor.vim;

import java.text.BreakIterator;

/** Pure helpers over one line's text: character boundaries, display columns and indents. */
final class Text {
    private Text() { }

    /**
     * @param line a line
     * @param col a boundary in it
     * @return the next character boundary (a grapheme: a surrogate pair or a letter with its combining marks is one
     *     character), or the line's length at its end
     */
    static int next(String line, int col) {
        if (col >= line.length()) return line.length();
        // Nothing below U+0300 combines or is a surrogate, so the common case needs no BreakIterator.
        if (line.charAt(col) < 0x300 && (col + 1 == line.length() || line.charAt(col + 1) < 0x300)) return col + 1;
        BreakIterator characters = BreakIterator.getCharacterInstance();
        characters.setText(line);
        int next = characters.following(col);
        return next == BreakIterator.DONE ? line.length() : next;
    }

    /**
     * @param line a line
     * @param col a boundary in it, or its length
     * @return the previous character boundary, or 0
     */
    static int previous(String line, int col) {
        if (col <= 0) return 0;
        col = Math.min(col, line.length());
        if (line.charAt(col - 1) < 0x300) return col - 1;
        BreakIterator characters = BreakIterator.getCharacterInstance();
        characters.setText(line);
        int previous = characters.preceding(col);
        return previous == BreakIterator.DONE ? 0 : previous;
    }

    /** @return the start of the last character, or 0 for an empty line */
    static int last(String line) { return previous(line, line.length()); }

    /** @return the number of characters (graphemes) before {@code col} */
    static int characters(String line, int col) {
        int count = 0;
        for (int i = 0; i < col && i < line.length(); i = next(line, i)) count++;
        return count;
    }

    /** @return the column of the {@code index}-th character (0-based), or the line's length */
    static int column(String line, int index) {
        int col = 0;
        for (int i = 0; i < index && col < line.length(); i++) col = next(line, col);
        return col;
    }

    /** @return the display width of a code point that is not a tab: 0 for combining marks, 2 for wide characters */
    static int width(int cp) {
        int type = Character.getType(cp);
        if (type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK || type == Character.FORMAT) return 0;
        if (cp >= 0x1100 && (cp <= 0x115f || cp == 0x2329 || cp == 0x232a || cp >= 0x2e80 && cp <= 0xa4cf && cp != 0x303f
            || cp >= 0xac00 && cp <= 0xd7a3 || cp >= 0xf900 && cp <= 0xfaff || cp >= 0xfe30 && cp <= 0xfe6f
            || cp >= 0xff00 && cp <= 0xff60 || cp >= 0xffe0 && cp <= 0xffe6 || cp >= 0x1f300 && cp <= 0x1f64f
            || cp >= 0x1f900 && cp <= 0x1f9ff || cp >= 0x20000 && cp <= 0x3fffd)) return 2;
        return 1;
    }

    /** @return the display width of the character starting at {@code col}, which starts at display column {@code vcol} */
    static int width(String line, int col, int vcol, int tabstop) {
        if (line.charAt(col) == '\t') return tabstop - vcol % tabstop;
        int end = next(line, col), total = 0;
        for (int i = col; i < end; i += Character.charCount(line.codePointAt(i))) total += width(line.codePointAt(i));
        // A lone combining mark still takes a cell.
        return Math.max(total, 1);
    }

    /** @return the display column (0-based) at which the character at {@code col} starts */
    static int virtcol(String line, int col, int tabstop) {
        int vcol = 0;
        for (int i = 0; i < col && i < line.length(); i = next(line, i)) vcol += width(line, i, vcol, tabstop);
        return vcol;
    }

    /**
     * Vim's coladvance: the character covering display column {@code want}.
     *
     * @return its column, or the line's length when {@code want} is past the last character
     */
    static int columnAt(String line, int want, int tabstop) {
        int vcol = 0;
        for (int i = 0; i < line.length(); i = next(line, i)) {
            int width = width(line, i, vcol, tabstop);
            if (want < vcol + width) return i;
            vcol += width;
        }
        return line.length();
    }

    /** @return the column of the first character that is not a space or tab, or the line's length */
    static int firstNonBlank(String line) {
        int col = 0;
        while (col < line.length() && (line.charAt(col) == ' ' || line.charAt(col) == '\t')) col++;
        return col;
    }

    /** @return whether the line holds only spaces and tabs */
    static boolean blank(String line) { return firstNonBlank(line) == line.length(); }

    /** @return the display width of the line's leading spaces and tabs (Vim's get_indent) */
    static int indent(String line, int tabstop) { return virtcol(line, firstNonBlank(line), tabstop); }

    /** @return leading whitespace of display width {@code width}: tabs then spaces, or only spaces with 'expandtab' */
    static String makeIndent(int width, int tabstop, boolean expandtab) {
        if (width <= 0) return "";
        if (expandtab) return " ".repeat(width);
        return "\t".repeat(width / tabstop) + " ".repeat(width % tabstop);
    }
}
