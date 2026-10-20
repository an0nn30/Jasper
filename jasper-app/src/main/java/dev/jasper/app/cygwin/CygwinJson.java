package dev.jasper.app.cygwin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small, bounded JSON codec for owned state and distribution manifests. */
public final class CygwinJson {
    private final String text;
    private int at;
    private CygwinJson(String text) { this.text = text; }
    public static Object parse(String text) throws IOException {
        if (text.length() > 8 * 1024 * 1024) throw new IOException("JSON exceeds size limit");
        var parser = new CygwinJson(text); Object value = parser.value(0); parser.space();
        if (parser.at != text.length()) throw new IOException("Trailing JSON content");
        return value;
    }
    private IOException invalid() { return new IOException("Invalid JSON at character " + at); }
    private void space() { while (at < text.length() && " \t\r\n".indexOf(text.charAt(at)) >= 0) at++; }
    private boolean take(char c) { space(); if (at < text.length() && text.charAt(at) == c) { at++; return true; } return false; }
    private Object value(int depth) throws IOException {
        space(); if (depth > 32 || at == text.length()) throw invalid();
        char c = text.charAt(at);
        if (c == '"') return string();
        if (take('{')) {
            var result = new LinkedHashMap<String, Object>();
            if (take('}')) return Map.copyOf(result);
            do {
                space(); if (at == text.length() || text.charAt(at) != '"') throw invalid();
                String key = string(); if (!take(':') || result.containsKey(key)) throw invalid();
                result.put(key, value(depth + 1));
            } while (take(','));
            if (!take('}')) throw invalid(); return Map.copyOf(result);
        }
        if (take('[')) {
            var result = new ArrayList<Object>();
            if (take(']')) return List.copyOf(result);
            do { result.add(value(depth + 1)); } while (take(','));
            if (!take(']')) throw invalid(); return List.copyOf(result);
        }
        if (text.startsWith("true", at)) { at += 4; return true; }
        if (text.startsWith("false", at)) { at += 5; return false; }
        int begin = at; if (c == '-') at++;
        if (at == text.length() || !Character.isDigit(text.charAt(at))) throw invalid();
        if (text.charAt(at) == '0') at++;
        else while (at < text.length() && text.charAt(at) >= '0' && text.charAt(at) <= '9') at++;
        try { return Long.parseLong(text.substring(begin, at)); }
        catch (NumberFormatException failure) { throw invalid(); }
    }
    private String string() throws IOException {
        at++; var out = new StringBuilder();
        while (at < text.length()) {
            char c = text.charAt(at++);
            if (c == '"') return out.toString();
            if (c < 32) throw invalid();
            if (c == '\\') {
                if (at == text.length()) throw invalid();
                c = text.charAt(at++);
                switch (c) {
                    case '"', '\\', '/' -> out.append(c);
                    case 'b' -> out.append('\b'); case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n'); case 'r' -> out.append('\r'); case 't' -> out.append('\t');
                    case 'u' -> {
                        if (at + 4 > text.length()) throw invalid();
                        try { out.append((char) Integer.parseInt(text.substring(at, at + 4), 16)); }
                        catch (NumberFormatException failure) { throw invalid(); } at += 4;
                    }
                    default -> throw invalid();
                }
            } else out.append(c);
        }
        throw invalid();
    }
    public static String write(Object value) {
        if (value instanceof String string) {
            var out = new StringBuilder("\"");
            for (char c : string.toCharArray()) {
                if (c == '"' || c == '\\') out.append('\\').append(c);
                else if (c < 32 || Character.isSurrogate(c)) out.append(String.format("\\u%04x", (int)c));
                else out.append(c);
            }
            return out.append('"').toString();
        }
        if (value instanceof Map<?, ?> map) return "{" + map.entrySet().stream()
            .map(e -> write(e.getKey().toString()) + ":" + write(e.getValue())).collect(java.util.stream.Collectors.joining(",")) + "}";
        if (value instanceof List<?> list) return "[" + list.stream().map(CygwinJson::write).collect(java.util.stream.Collectors.joining(",")) + "]";
        if (value instanceof Long || value instanceof Integer || value instanceof Boolean) return value.toString();
        throw new IllegalArgumentException("Unsupported JSON value");
    }
    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object value) throws IOException {
        if (!(value instanceof Map<?, ?>)) throw new IOException("Expected JSON object");
        return (Map<String, Object>)value;
    }
    public static String string(Map<String, Object> object, String key) throws IOException {
        if (!(object.get(key) instanceof String value) || value.isBlank()) throw new IOException("Missing string: " + key);
        return value;
    }
    public static long number(Map<String, Object> object, String key) throws IOException {
        if (!(object.get(key) instanceof Long value)) throw new IOException("Missing integer: " + key); return value;
    }
    public static List<?> list(Map<String, Object> object, String key) throws IOException {
        if (!(object.get(key) instanceof List<?> value)) throw new IOException("Missing array: " + key); return value;
    }
}
