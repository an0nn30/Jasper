package dev.jasper.editor.syntax;

import java.util.regex.Pattern;
import javax.swing.text.Segment;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenMakerBase;

/**
 * A hand-written TOML lexer (spec 3.2). A line's own characters always keep their real token types; a line
 * that ends with open {@code [}/{@code {} brackets, inside a multi-line {@code """} string or inside a
 * multi-line {@code '''} string ends with one extra zero-length token, appended after its last real token,
 * whose type is a private negative int encoding both the open-bracket depth and which kind of multi-line
 * string (if any) is unterminated (see {@code encodeState}/{@code decodeState}). RSyntaxTextArea does not
 * paint non-positive types ({@code isPaintable()} is {@code type > Token.NULL}) and hands this type back as
 * the next line's {@code initialTokenType}, the same way its own HTML and JSP lexers carry embedded-language
 * state across lines. A single positive/negative bit is not enough on its own: a nested multi-line array
 * needs its full bracket depth restored, not just "some bracket is open", or a line like {@code ],} at a
 * deeper nesting would wrongly pop all the way out.
 */
public final class TomlTokenMaker extends TokenMakerBase {
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(0|[1-9](_?\\d)*)");
    private static final Pattern FLOAT = Pattern.compile("[+-]?(0|[1-9](_?\\d)*)(\\.\\d(_?\\d)*)?([eE][+-]?\\d(_?\\d)*)?|[+-]?(inf|nan)");
    private static final Pattern RADIX = Pattern.compile("0x[0-9A-Fa-f](_?[0-9A-Fa-f])*|0o[0-7](_?[0-7])*|0b[01](_?[01])*");
    private static final Pattern DATE_TIME = Pattern.compile(
        "\\d{4}-\\d{2}-\\d{2}([Tt ]\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?([Zz]|[+-]\\d{2}:\\d{2})?)?|\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?");
    private static final Pattern LOCAL_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    /** The open-bracket depth an encoded end-of-line state can carry; deeper nesting is capped here. */
    private static final int MAX_DEPTH = 64;

    @Override public String[] getLineCommentStartAndEnd(int languageIndex) { return new String[] {"#", null}; }

    @Override public boolean getMarkOccurrencesOfTokenType(int type) { return type == Token.VARIABLE; }

    @Override
    public int getClosestStandardTokenTypeForInternalType(int type) {
        if (type >= Token.NULL) return type;
        return switch (decodeState(type).kind()) {
            case 1 -> Token.LITERAL_STRING_DOUBLE_QUOTE;
            case 2 -> Token.LITERAL_CHAR;
            default -> Token.SEPARATOR;
        };
    }

    @Override
    public Token getTokenList(Segment text, int initialTokenType, int startOffset) {
        resetTokenList();
        char[] a = text.array;
        int from = text.offset, end = text.offset + text.count, shift = startOffset - text.offset;
        int i = from;
        StringBuilder brackets = new StringBuilder();
        boolean value = false, seen = false;
        if (initialTokenType < Token.NULL) {
            LineState state = decodeState(initialTokenType);
            for (int d = 0; d < state.depth(); d++) brackets.append('[');
            if (state.kind() != 0) {
                char quote = state.kind() == 1 ? '"' : '\'';
                int type = state.kind() == 1 ? Token.LITERAL_STRING_DOUBLE_QUOTE : Token.LITERAL_CHAR;
                int close = closeMultiline(a, from, end, quote);
                if (close < 0) {
                    add(a, from, end, type, shift);
                    appendState(a, end, shift, brackets.length(), state.kind());
                    return firstToken;
                }
                add(a, from, close, type, shift);
                i = close;
            }
            value = true;
            seen = true;
        }
        while (i < end) {
            char c = a[i];
            int start = i;
            if (c == ' ' || c == '\t') {
                while (i < end && (a[i] == ' ' || a[i] == '\t')) i++;
                add(a, start, i, Token.WHITESPACE, shift);
                continue;
            }
            if (c == '#') { add(a, i, end, Token.COMMENT_EOL, shift); i = end; break; }
            if (!value && !seen && brackets.isEmpty() && c == '[') {
                i = header(a, i, end);
                add(a, start, i, Token.RESERVED_WORD, shift);
                seen = true;
                continue;
            }
            seen = true;
            if (c == '"' || c == '\'') {
                int type = c == '"' ? Token.LITERAL_STRING_DOUBLE_QUOTE : Token.LITERAL_CHAR;
                if (value && i + 2 < end && a[i + 1] == c && a[i + 2] == c) {
                    int close = closeMultiline(a, i + 3, end, c);
                    if (close < 0) {
                        add(a, start, end, type, shift);
                        appendState(a, end, shift, brackets.length(), c == '"' ? 1 : 2);
                        return firstToken;
                    }
                    add(a, start, close, type, shift);
                    i = close;
                    continue;
                }
                i = closeString(a, i + 1, end, c);
                add(a, start, i, value ? type : Token.VARIABLE, shift);
                continue;
            }
            if (c == ']' || c == '}') {
                if (!brackets.isEmpty()) brackets.setLength(brackets.length() - 1);
                add(a, i, i + 1, Token.SEPARATOR, shift);
                i++;
                value = true;
                continue;
            }
            if (c == ',') {
                add(a, i, i + 1, Token.SEPARATOR, shift);
                i++;
                value = !inTable(brackets);
                continue;
            }
            if (!value) {
                if (bare(c)) {
                    while (i < end && bare(a[i])) i++;
                    add(a, start, i, Token.VARIABLE, shift);
                    continue;
                }
                add(a, i, i + 1, c == '.' || c == '=' ? Token.OPERATOR : Token.ERROR_IDENTIFIER, shift);
                i++;
                if (c == '=') value = true;
                continue;
            }
            if (c == '[' || c == '{') {
                brackets.append(c);
                add(a, i, i + 1, Token.SEPARATOR, shift);
                i++;
                value = c == '[';
                continue;
            }
            if (Character.isLetterOrDigit(c) || c == '+' || c == '-') {
                i = wordEnd(a, i, end);
                add(a, start, i, classify(new String(a, start, i - start)), shift);
                continue;
            }
            add(a, i, i + 1, Token.ERROR_IDENTIFIER, shift);
            i++;
        }
        if (!brackets.isEmpty()) { appendState(a, end, shift, brackets.length(), 0); return firstToken; }
        addNullToken();
        return firstToken;
    }

    /** Adds a token over {@code a[start, end)}; RSyntaxTextArea's end index is inclusive. */
    private void add(char[] a, int start, int end, int type, int shift) { addToken(a, start, end - 1, type, start + shift); }

    /** Appends the zero-length token that carries {@code depth} open brackets and {@code kind} to the next line. */
    private void appendState(char[] a, int pos, int shift, int depth, int kind) { add(a, pos, pos, encodeState(depth, kind), shift); }

    /**
     * A line's end-of-line state beyond a fresh start (a plain {@code Token.NULL}): how many {@code [}/{@code {}
     * brackets are still open, and whether the line ends inside a multi-line string (0 = no, 1 = inside
     * {@code """}, 2 = inside {@code '''}).
     */
    private record LineState(int depth, int kind) { }

    /** Encodes a {@link LineState} as the private negative token type carried between lines; depth is capped at {@link #MAX_DEPTH}. */
    private static int encodeState(int depth, int kind) { return -(1 + Math.min(depth, MAX_DEPTH) * 3 + kind); }

    private static LineState decodeState(int state) { int n = -state - 1; return new LineState(n / 3, n % 3); }

    static int classify(String word) {
        if (word.equals("true") || word.equals("false")) return Token.LITERAL_BOOLEAN;
        if (RADIX.matcher(word).matches()) return Token.LITERAL_NUMBER_HEXADECIMAL;
        if (DECIMAL.matcher(word).matches()) return Token.LITERAL_NUMBER_DECIMAL_INT;
        if (FLOAT.matcher(word).matches()) return Token.LITERAL_NUMBER_FLOAT;
        if (DATE_TIME.matcher(word).matches()) return Token.DATA_TYPE;
        return Token.ERROR_IDENTIFIER;
    }

    /** The end of a {@code [table]} or {@code [[array]]} header, or of the text before a comment when it is unclosed. */
    private static int header(char[] a, int i, int end) {
        boolean array = i + 1 < end && a[i + 1] == '[';
        for (int k = i + (array ? 2 : 1); k < end; k++) {
            if (a[k] == '#') return k;
            if (a[k] == ']' && (!array || k + 1 < end && a[k + 1] == ']')) return k + (array ? 2 : 1);
        }
        return end;
    }

    /** The index after a single-line string's closing quote, or the line's end when it has none. */
    private static int closeString(char[] a, int i, int end, char quote) {
        while (i < end) {
            char c = a[i++];
            if (c == '\\' && quote == '"' && i < end) i++;
            else if (c == quote) return i;
        }
        return end;
    }

    /** The index after a multi-line string's closing delimiter, with up to two extra quotes, or -1 when the line has none. */
    private static int closeMultiline(char[] a, int i, int end, char quote) {
        for (int k = i; k + 2 < end; k++) {
            if (a[k] != quote || a[k + 1] != quote || a[k + 2] != quote) continue;
            if (quote == '"' && escaped(a, i, k)) continue;
            int close = k + 3;
            for (int extra = 0; extra < 2 && close < end && a[close] == quote; extra++) close++;
            return close;
        }
        return -1;
    }

    private static boolean escaped(char[] a, int from, int k) {
        int slashes = 0;
        for (int j = k - 1; j >= from && a[j] == '\\'; j--) slashes++;
        return slashes % 2 == 1;
    }

    /** A value word; a local date followed by a space and a time is one date-time. */
    private static int wordEnd(char[] a, int i, int end) {
        int start = i;
        while (i < end && word(a[i])) i++;
        if (LOCAL_DATE.matcher(new String(a, start, i - start)).matches() && i + 3 < end && a[i] == ' '
                && Character.isDigit(a[i + 1]) && Character.isDigit(a[i + 2]) && a[i + 3] == ':') {
            i++;
            while (i < end && word(a[i])) i++;
        }
        return i;
    }

    private static boolean word(char c) { return Character.isLetterOrDigit(c) || c == '_' || c == '+' || c == '-' || c == '.' || c == ':'; }

    private static boolean bare(char c) { return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '-'; }

    private static boolean inTable(StringBuilder brackets) { return !brackets.isEmpty() && brackets.charAt(brackets.length() - 1) == '{'; }
}
