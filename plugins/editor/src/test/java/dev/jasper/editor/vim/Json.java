package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Just enough JSON for the corpus: quoting strings, and reading what Vim's json_encode writes. */
final class Json {
    private final String text;
    private int at;

    private Json(String text) { this.text = text; }

    /** @return a JSON string literal for the text */
    static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\t' -> out.append("\\t");
                case '\r' -> out.append("\\r");
                default -> {
                    if (c < 0x20 || c == 0x7f || Character.isSurrogate(c) || c >= 0xe000 && c <= 0xf8ff) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }

    /** @return strings, longs, booleans, null, lists and maps */
    static Object parse(String text) {
        var json = new Json(text);
        Object value = json.value();
        json.space();
        if (json.at != text.length()) throw new IllegalArgumentException("Trailing JSON at " + json.at + ": " + text);
        return value;
    }

    /** @return the string a JSON string literal holds */
    static String unquote(String literal) { return (String) parse(literal); }

    private Object value() {
        space();
        char c = text.charAt(at);
        switch (c) {
            case '{' -> {
                at++;
                Map<String, Object> map = new LinkedHashMap<>();
                space();
                if (text.charAt(at) == '}') {
                    at++;
                    return map;
                }
                while (true) {
                    space();
                    String key = string();
                    space();
                    expect(':');
                    map.put(key, value());
                    space();
                    if (text.charAt(at) == ',') at++;
                    else {
                        expect('}');
                        return map;
                    }
                }
            }
            case '[' -> {
                at++;
                List<Object> list = new ArrayList<>();
                space();
                if (text.charAt(at) == ']') {
                    at++;
                    return list;
                }
                while (true) {
                    list.add(value());
                    space();
                    if (text.charAt(at) == ',') at++;
                    else {
                        expect(']');
                        return list;
                    }
                }
            }
            case '"' -> { return string(); }
            default -> {
                if (text.startsWith("true", at)) { at += 4; return true; }
                if (text.startsWith("false", at)) { at += 5; return false; }
                if (text.startsWith("null", at) || text.startsWith("v:null", at)) { at += text.startsWith("null", at) ? 4 : 6; return null; }
                int start = at;
                while (at < text.length() && "-+.0123456789eE".indexOf(text.charAt(at)) >= 0) at++;
                return Long.parseLong(text.substring(start, at));
            }
        }
    }

    private String string() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (true) {
            char c = text.charAt(at++);
            if (c == '"') return out.toString();
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char e = text.charAt(at++);
            switch (e) {
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case 'r' -> out.append('\r');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'u' -> {
                    out.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                    at += 4;
                }
                default -> out.append(e);
            }
        }
    }

    private void expect(char c) {
        if (text.charAt(at) != c) throw new IllegalArgumentException("Expected " + c + " at " + at + " in " + text);
        at++;
    }

    private void space() { while (at < text.length() && Character.isWhitespace(text.charAt(at))) at++; }
}
