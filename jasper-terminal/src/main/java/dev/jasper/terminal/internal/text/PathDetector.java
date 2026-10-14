package dev.jasper.terminal.internal.text;

import java.util.Optional;

/**
 * Finds a file path written as plain text under a click, as compilers and tools print one: {@code PATH},
 * {@code PATH:LINE}, {@code PATH:LINE:COLUMN}, {@code PATH(LINE)} or {@code PATH(LINE,COLUMN)}. A path is absolute,
 * starts with {@code ./}, {@code ../} or {@code ~/}, or is relative and has a separator or an extension; {@code C:\}
 * and backslash paths count only on Windows. It ends at whitespace, at {@code <>"'} and the backtick, and before
 * trailing punctuation, and it never contains {@code ://}, so a URL is no path. Pure text work: no buffer, lock or file
 * system.
 */
public final class PathDetector {
    /**
     * A path the click column falls in: its text, line and column (0 when none), and its index range in row.text().
     *
     * @param path the path as written
     * @param line the 1-based line written after it, or 0
     * @param column the 1-based column written after the line, or 0
     * @param start the index of the path's first character in the row's text
     * @param end the index after the path and its position, exclusive
     */
    public record Match(String path, int line, int column, int start, int end) { }

    private static final String ENDS = "<>\"'`";
    private static final String OPENERS = "([{";
    private static final String TRAILING = ".,;:!?)]}'\"";
    private static final int MAX_DIGITS = 9;

    private PathDetector() {
    }

    /**
     * The path whose text, or the position written after it, covers {@code column}.
     *
     * @param row the row, or soft-wrapped rows joined into one
     * @param column the clicked cell on {@code row}
     * @param windows whether {@code C:\} and backslash paths count
     * @return the path, or empty when the click is on anything else
     */
    public static Optional<Match> pathAt(RowText row, int column, boolean windows) {
        String text = row.text();
        int clicked = indexAt(row, column);
        if (clicked < 0 || ends(text.charAt(clicked))) return Optional.empty();
        int tokenStart = clicked;
        int tokenEnd = clicked + 1;
        while (tokenStart > 0 && !ends(text.charAt(tokenStart - 1))) tokenStart--;
        while (tokenEnd < text.length() && !ends(text.charAt(tokenEnd))) tokenEnd++;
        if (text.substring(tokenStart, tokenEnd).contains("://")) return Optional.empty();
        int start = tokenStart;
        while (start < tokenEnd && OPENERS.indexOf(text.charAt(start)) >= 0) start++;
        int pathEnd = windows && drive(text, start, tokenEnd) ? start + 2 : start;
        int[] position = null;
        while (pathEnd < tokenEnd && text.charAt(pathEnd) != ':'
            && (text.charAt(pathEnd) != '(' || (position = lineInParentheses(text, pathEnd, tokenEnd)) == null)) {
            pathEnd++;
        }
        if (position == null && pathEnd < tokenEnd) position = lineAfterColon(text, pathEnd, tokenEnd);
        int end;
        if (position == null) {
            while (pathEnd > start && TRAILING.indexOf(text.charAt(pathEnd - 1)) >= 0) pathEnd--;
            end = pathEnd;
        } else {
            end = position[2];
        }
        String path = text.substring(start, pathEnd);
        if (clicked < start || clicked >= end || !qualifies(path, windows)) return Optional.empty();
        return Optional.of(new Match(path, position == null ? 0 : position[0], position == null ? 0 : position[1], start, end));
    }

    /** The text index whose cells cover {@code column}, or -1. */
    private static int indexAt(RowText row, int column) {
        for (int i = 0; i < row.text().length(); i++) {
            if (column >= row.columns()[i] && column <= row.lastColumns()[i]) return i;
        }
        return -1;
    }

    private static boolean ends(char c) {
        return Character.isWhitespace(c) || ENDS.indexOf(c) >= 0;
    }

    /** Whether a drive such as {@code C:\} or {@code C:/} starts at {@code start}. */
    private static boolean drive(String text, int start, int tokenEnd) {
        if (start + 2 >= tokenEnd) return false;
        char letter = text.charAt(start);
        char separator = text.charAt(start + 2);
        return (letter >= 'A' && letter <= 'Z' || letter >= 'a' && letter <= 'z') && text.charAt(start + 1) == ':'
            && (separator == '\\' || separator == '/');
    }

    /**
     * {@code (LINE)} or {@code (LINE,COLUMN)} at {@code open}, followed only by trailing punctuation, as
     * {@code {line, column, end}}; null when it is not one, and the parenthesis is then part of the path.
     */
    private static int[] lineInParentheses(String text, int open, int tokenEnd) {
        int lineEnd = digits(text, open + 1, tokenEnd);
        if (lineEnd < 0) return null;
        int columnEnd = -1;
        if (lineEnd < tokenEnd && text.charAt(lineEnd) == ',') {
            columnEnd = digits(text, lineEnd + 1, tokenEnd);
            if (columnEnd < 0) return null;
        }
        int close = columnEnd < 0 ? lineEnd : columnEnd;
        if (close >= tokenEnd || text.charAt(close) != ')') return null;
        for (int i = close + 1; i < tokenEnd; i++) {
            if (TRAILING.indexOf(text.charAt(i)) < 0) return null;
        }
        return new int[] {number(text, open + 1, lineEnd), columnEnd < 0 ? 0 : number(text, lineEnd + 1, columnEnd), close + 1};
    }

    /**
     * {@code :LINE} or {@code :LINE:COLUMN} at {@code colon}, as {@code {line, column, end}}; null when no line follows.
     * A line or column ends at the token's end or at punctuation; what follows that, such as grep's matched text, is
     * not part of the match.
     */
    private static int[] lineAfterColon(String text, int colon, int tokenEnd) {
        int lineEnd = digits(text, colon + 1, tokenEnd);
        if (lineEnd < 0 || !endsNumber(text, lineEnd, tokenEnd)) return null;
        int line = number(text, colon + 1, lineEnd);
        if (lineEnd < tokenEnd && text.charAt(lineEnd) == ':') {
            int columnEnd = digits(text, lineEnd + 1, tokenEnd);
            if (columnEnd >= 0 && endsNumber(text, columnEnd, tokenEnd)) {
                return new int[] {line, number(text, lineEnd + 1, columnEnd), columnEnd};
            }
        }
        return new int[] {line, 0, lineEnd};
    }

    /** The end of a run of 1 to 9 digits at {@code from}, or -1: a longer run is no line, so a line always fits an int. */
    private static int digits(String text, int from, int tokenEnd) {
        int end = from;
        while (end < tokenEnd && text.charAt(end) >= '0' && text.charAt(end) <= '9') end++;
        return end == from || end - from > MAX_DIGITS ? -1 : end;
    }

    private static boolean endsNumber(String text, int at, int tokenEnd) {
        return at == tokenEnd || TRAILING.indexOf(text.charAt(at)) >= 0;
    }

    private static int number(String text, int from, int to) {
        return Integer.parseInt(text, from, to, 10);
    }

    /**
     * Whether {@code path} names a file: one with a separator (absolute, {@code ./}, {@code ../}, {@code ~/}, or a
     * relative path with a directory) or a name with an extension, and in both cases a name that is not {@code .} or
     * {@code ..}.
     */
    private static boolean qualifies(String path, boolean windows) {
        if (!windows && path.indexOf('\\') >= 0) return false;
        int separator = windows ? Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) : path.lastIndexOf('/');
        String name = path.substring(separator + 1);
        if (name.isEmpty() || name.equals(".") || name.equals("..")) return false;
        return separator >= 0 || hasExtension(name);
    }

    /** {@code Foo.java} has one; {@code .bashrc}, {@code 1.2.3} and {@code Makefile} do not. */
    private static boolean hasExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) return false;
        boolean letter = false;
        for (int i = dot + 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c)) return false;
            letter |= Character.isLetter(c);
        }
        return letter;
    }
}
