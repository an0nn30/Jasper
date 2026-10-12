package dev.jasper.ui.theme;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A strict reader for Jasper's bundled theme file: JSON objects whose values are strings or objects, nothing else.
 * Numbers, arrays, booleans, null, duplicate keys and trailing content are errors naming the origin and line. The
 * theme package is JDK-only, so it cannot use FlatLaf's parser.
 */
final class ThemeJson {
    private final String text, origin;
    private int at;

    private ThemeJson(String text, String origin) { this.text = text; this.origin = origin; }

    /** The top-level object, as ordered maps of strings and maps. */
    static Map<String, Object> parse(String text, String origin) {
        var reader = new ThemeJson(text, origin);
        reader.space();
        var root = reader.object();
        reader.space();
        if (reader.at != text.length()) throw reader.error("Unexpected content after the theme object");
        return root;
    }

    private Map<String, Object> object() {
        expect('{');
        var map = new LinkedHashMap<String, Object>();
        space();
        if (peek() == '}') { at++; return map; }
        while (true) {
            space();
            int keyAt = at;
            String key = string();
            space(); expect(':'); space();
            char first = peek();
            Object value = first == '{' ? object() : first == '"' ? string() : null;
            if (value == null) throw error("Expected a string or an object for \"" + key + "\"");
            if (map.putIfAbsent(key, value) != null) { at = keyAt; throw error("Duplicate key \"" + key + "\""); }
            space();
            char next = peek();
            if (next == '}') { at++; return map; }
            if (next != ',') throw error("Expected ',' or '}'");
            at++;
        }
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
