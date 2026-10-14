package dev.jasper.ui.theme;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A strict reader for JSON objects. Strings, objects and whole numbers of at most nine digits (the format marker) are
 * read as {@code String}, {@code Map} and {@code Long}; every other JSON value (booleans, null, arrays, any other
 * number) is checked and read as {@link Other}, so that the loader can tell a foreign file from a malformed one. Invalid
 * JSON, duplicate keys, nesting past {@link #MAX_DEPTH} and trailing content are errors naming the origin and line. The
 * theme package is JDK-only, so it cannot use FlatLaf's parser.
 */
final class ThemeJson {
    /** A theme file nests three deep; objects and arrays past eight are not one, and the limit keeps a hostile file from exhausting the stack. */
    static final int MAX_DEPTH = 8;
    private final String text, origin;
    private int at;

    /** Any JSON value other than a string, an object or a whole number of at most nine digits; the reader checks and discards it. */
    record Other() { }
    private static final Other OTHER = new Other();
    private static final List<String> LITERALS = List.of("true", "false", "null");

    private ThemeJson(String text, String origin) { this.text = text; this.origin = origin; }

    /** The top-level object, as ordered maps of strings, maps, {@code Long}s and {@link Other}s. */
    static Map<String, Object> parse(String text, String origin) {
        var reader = new ThemeJson(text, origin);
        reader.space();
        var root = reader.object(1);
        reader.space();
        if (reader.at != text.length()) throw reader.error("Unexpected content after the theme object");
        return root;
    }

    private Map<String, Object> object(int depth) {
        if (depth > MAX_DEPTH) throw error("Objects and arrays nested more than " + MAX_DEPTH + " deep");
        expect('{');
        var map = new LinkedHashMap<String, Object>();
        space();
        if (peek() == '}') { at++; return map; }
        while (true) {
            space();
            int keyAt = at;
            String key = string();
            space(); expect(':'); space();
            Object value = value(depth, key);
            if (map.putIfAbsent(key, value) != null) { at = keyAt; throw error("Duplicate key \"" + key + "\""); }
            space();
            char next = peek();
            if (next == '}') { at++; return map; }
            if (next != ',') throw error("Expected ',' or '}'");
            at++;
        }
    }

    /** An array's elements are checked and dropped: no theme file holds one. */
    private Other array(int depth) {
        if (depth > MAX_DEPTH) throw error("Objects and arrays nested more than " + MAX_DEPTH + " deep");
        expect('[');
        space();
        if (peek() == ']') { at++; return OTHER; }
        while (true) {
            space();
            value(depth, null);
            space();
            char next = peek();
            if (next == ']') { at++; return OTHER; }
            if (next != ',') throw error("Expected ',' or ']'");
            at++;
        }
    }

    /** The value at the cursor, inside a container at {@code depth}; {@code key} names it in errors, or is null in an array. */
    private Object value(int depth, String key) {
        char first = peek();
        if (first == '{') return object(depth + 1);
        if (first == '[') return array(depth + 1);
        if (first == '"') return string();
        if (first == '-' || first >= '0' && first <= '9') return number();
        for (String literal : LITERALS) {
            if (text.startsWith(literal, at)) { at += literal.length(); return OTHER; }
        }
        throw error("Expected a JSON value" + (key == null ? "" : " for \"" + key + "\""));
    }

    /**
     * A JSON number. Digits without sign, fraction or exponent, at most nine of them, are a {@code Long}, such as the
     * format marker {@code 1}; every other number is {@link Other}.
     */
    private Object number() {
        int start = at;
        if (peek() == '-') at++;
        int digits = at;
        digits();
        if (at == digits) throw error("Expected a digit");
        if (at - digits > 1 && text.charAt(digits) == '0') { at = digits; throw error("A number has no leading zeros"); }
        boolean plain = start == digits;
        if (at < text.length() && text.charAt(at) == '.') {
            plain = false;
            at++;
            int fraction = at;
            digits();
            if (at == fraction) throw error("Expected a digit after the decimal point");
        }
        if (at < text.length() && (text.charAt(at) == 'e' || text.charAt(at) == 'E')) {
            plain = false;
            at++;
            if (at < text.length() && (text.charAt(at) == '+' || text.charAt(at) == '-')) at++;
            int exponent = at;
            digits();
            if (at == exponent) throw error("Expected a digit in the exponent");
        }
        return plain && at - start <= 9 ? Long.parseLong(text.substring(start, at)) : OTHER;
    }

    private void digits() {
        while (at < text.length() && text.charAt(at) >= '0' && text.charAt(at) <= '9') at++;
    }

    private String string() {
        expect('"');
        var out = new StringBuilder();
        while (true) {
            if (at >= text.length()) throw error("Unterminated string");
            char c = text.charAt(at++);
            if (c == '"') return out.toString();
            if (c < 0x20) { at--; throw error("Control character in a string"); }
            if (c != '\\') { out.append(c); continue; }
            if (at >= text.length()) throw error("Unterminated escape");
            char escape = text.charAt(at++);
            switch (escape) {
                case '"', '\\', '/' -> out.append(escape);
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case 'r' -> out.append('\r');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'u' -> {
                    if (at + 4 > text.length()) throw error("Short unicode escape");
                    try { out.append((char) Integer.parseInt(text.substring(at, at + 4), 16)); }
                    catch (NumberFormatException failure) { throw error("Bad unicode escape"); }
                    at += 4;
                }
                default -> throw error("Unknown escape \\" + escape);
            }
        }
    }

    private void space() {
        while (at < text.length() && " \t\n\r".indexOf(text.charAt(at)) >= 0) at++;
    }

    private char peek() {
        if (at >= text.length()) throw error("Unexpected end");
        return text.charAt(at);
    }

    private void expect(char c) {
        if (peek() != c) throw error("Expected '" + c + "'");
        at++;
    }

    private ThemeFileException error(String message) {
        int line = 1;
        for (int i = 0; i < Math.min(at, text.length()); i++) if (text.charAt(i) == '\n') line++;
        return new ThemeFileException(origin, line, message);
    }
}
