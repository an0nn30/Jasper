package dev.jasper.editor.vim;

/**
 * Bracket matching, ported from Vim's findmatchlimit (search.c) for 'matchpairs' brackets: nesting, backslash
 * escapes (a bracket only matches one escaped the same way) and, when {@code quotes} is on, Vim's skipping of
 * brackets inside double quotes and {@code 'x'} character literals. Vim's C comment and preprocessor matching is not
 * ported (docs/vim-divergences.md).
 */
final class Matching {
    private Matching() { }

    /**
     * {@code %} without a count: from the first 'matchpairs' bracket at or after the cursor on its line, its partner.
     *
     * @return the partner's position, or null
     */
    static Pos percent(Buffer buffer, Pos cursor, String matchpairs) {
        String line = buffer.line(cursor.line);
        for (int col = cursor.col; col < line.length(); col++) {
            char c = line.charAt(col);
            for (String pair : matchpairs.split(",")) {
                if (pair.length() != 3) continue;
                char open = pair.charAt(0), close = pair.charAt(2);
                if (c == open) return scan(buffer, new Pos(cursor.line, col), open, close, false, true, escaped(line, col));
                if (c == close) return scan(buffer, new Pos(cursor.line, col), close, open, true, true, escaped(line, col));
            }
        }
        return null;
    }

    /**
     * {@code [(}, {@code ])} and the block text objects: the unmatched bracket around the cursor.
     *
     * @param open the opening bracket
     * @param close the closing bracket
     * @param findOpen true to search backward for the unmatched {@code open}, false to search forward for {@code close}
     * @param quotes whether brackets in double quotes are skipped
     * @return its position, or null
     */
    static Pos unmatched(Buffer buffer, Pos cursor, char open, char close, boolean findOpen, boolean quotes) {
        return findOpen ? scan(buffer, cursor.copy(), close, open, true, quotes, false) : scan(buffer, cursor.copy(), open, close, false, quotes, false);
    }

    /**
     * The next {@code find} at nesting level 0 counting {@code nest} as opening a level, starting after {@code from}.
     * This is findmatchlimit's main loop.
     */
    static Pos scan(Buffer buffer, Pos from, char nest, char find, boolean backward, boolean quotes, boolean matchEscaped) {
        Pos pos = from.copy();
        String line = buffer.line(pos.line);
        int doQuotes = -1;
        boolean inQuote = false, startInQuotes = false;
        int count = 0;
        while (true) {
            if (backward) {
                if (pos.col == 0) {
                    if (pos.line == 0) return null;
                    pos.line--;
                    line = buffer.line(pos.line);
                    pos.col = line.length();
                    doQuotes = -1;
                } else pos.col--;
            } else {
                if (pos.col >= line.length()) {
                    if (pos.line == buffer.count() - 1) return null;
                    pos.line++;
                    line = buffer.line(pos.line);
                    pos.col = 0;
                    doQuotes = -1;
                } else pos.col++;
            }
            if (!quotes) doQuotes = 0;
            else if (doQuotes == -1) doQuotes = evenQuotes(line) ? 1 : 0;
            int c = pos.col < line.length() ? line.charAt(pos.col) : 0;
            switch (c) {
                case 0 -> {
                    if (pos.col == 0 || line.charAt(pos.col - 1) != '\\') {
                        inQuote = false;
                        startInQuotes = false;
                    }
                }
                case '"' -> {
                    if (doQuotes == 1 && !escaped(line, pos.col)) {
                        inQuote = !inQuote;
                        startInQuotes = false;
                    }
                }
                case '\'' -> {
                    if (quotes && nest != '\'' && find != '\'') {
                        if (backward) {
                            if (pos.col >= 3 && line.charAt(pos.col - 1) != '\'' && line.charAt(pos.col - 2) == '\\' && line.charAt(pos.col - 3) == '\'') pos.col -= 3;
                            else if (pos.col >= 2 && line.charAt(pos.col - 2) == '\'') pos.col -= 2;
                        } else if (pos.col + 1 < line.length()) {
                            if (line.charAt(pos.col + 1) == '\\' && pos.col + 3 < line.length() && line.charAt(pos.col + 3) == '\'') pos.col += 3;
                            else if (pos.col + 2 < line.length() && line.charAt(pos.col + 2) == '\'') pos.col += 2;
                        }
                    }
                }
                default -> {
                    if ((!inQuote || startInQuotes) && (c == nest || c == find) && escaped(line, pos.col) == matchEscaped) {
                        if (c == nest) count++;
                        else if (count == 0) return pos;
                        else count--;
                    }
                }
            }
        }
    }

    /** @return whether an odd number of backslashes precede the column */
    static boolean escaped(String line, int col) {
        int backslashes = 0;
        for (int i = col - 1; i >= 0 && line.charAt(i) == '\\'; i--) backslashes++;
        return (backslashes & 1) == 1;
    }

    /** @return whether the line has an even number of double quotes, skipping {@code \"} and {@code '"'} */
    private static boolean evenQuotes(String line) {
        int quotes = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"' && (i == 0 || line.charAt(i - 1) != '\'' || i + 1 >= line.length() || line.charAt(i + 1) != '\'')) quotes++;
            if (c == '\\' && i + 1 < line.length()) i++;
        }
        return quotes % 2 == 0;
    }
}
