package dev.jasper.app.startup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The start-up trace's JSON file: written atomically by the application, read back by the start-up benchmark. */
public final class StartupTraceFile {
    private StartupTraceFile() { }

    /** Writes {@code document} to {@code file} through a sibling temporary file and a move. */
    public static void write(Path file, Map<String, ?> document) throws IOException {
        Path target = file.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".startup-trace-", ".tmp");
        try {
            Files.writeString(temporary, json(document) + "\n", StandardCharsets.UTF_8);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Reads a JSON object: objects become maps, arrays lists, numbers doubles. */
    public static Map<String, Object> read(Path file) throws IOException {
        Object value = new Parser(Files.readString(file, StandardCharsets.UTF_8)).document();
        if (!(value instanceof Map<?, ?>)) throw new IOException("A start-up trace is a JSON object: " + file);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) value;
        return result;
    }

    static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof Number number) return Double.isFinite(number.doubleValue()) ? number.toString() : "null";
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            List<String> members = new ArrayList<>();
            map.forEach((key, member) -> members.add(json(String.valueOf(key)) + ":" + json(member)));
            return "{" + String.join(",", members) + "}";
        }
        if (value instanceof Collection<?> list) return "[" + String.join(",", list.stream().map(StartupTraceFile::json).toList()) + "]";
        StringBuilder out = new StringBuilder("\"");
        for (char ch : value.toString().toCharArray()) {
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20) out.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
                    else out.append(ch);
                }
            }
        }
        return out.append('"').toString();
    }

    private static final class Parser {
        private final String text;
        private int at;

        Parser(String text) { this.text = text; }

        Object document() throws IOException {
            Object value = value();
            skip();
            if (at != text.length()) throw error("trailing text");
            return value;
        }

        private Object value() throws IOException {
            skip();
            if (at >= text.length()) throw error("unexpected end");
            return switch (text.charAt(at)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() throws IOException {
            Map<String, Object> result = new LinkedHashMap<>();
            at++;
            skip();
            if (peek('}')) { at++; return result; }
            while (true) {
                skip();
                if (!peek('"')) throw error("expected a member name");
                String key = string();
                skip();
                expect(':');
                result.put(key, value());
                skip();
                if (peek(',')) { at++; continue; }
                expect('}');
                return result;
            }
        }

        private List<Object> array() throws IOException {
            List<Object> result = new ArrayList<>();
            at++;
            skip();
            if (peek(']')) { at++; return result; }
            while (true) {
                result.add(value());
                skip();
                if (peek(',')) { at++; continue; }
                expect(']');
                return result;
            }
        }

        private String string() throws IOException {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (at < text.length()) {
                char ch = text.charAt(at++);
                if (ch == '"') return out.toString();
                if (ch != '\\') { out.append(ch); continue; }
                if (at >= text.length()) break;
                char escaped = text.charAt(at++);
                switch (escaped) {
                    case '"', '\\', '/' -> out.append(escaped);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (at + 4 > text.length()) throw error("short unicode escape");
                        try { out.append((char) Integer.parseInt(text.substring(at, at + 4), 16)); }
                        catch (NumberFormatException bad) { throw error("bad unicode escape"); }
                        at += 4;
                    }
                    default -> throw error("bad escape");
                }
            }
            throw error("unterminated string");
        }

        private Double number() throws IOException {
            int start = at;
            while (at < text.length() && "+-0123456789.eE".indexOf(text.charAt(at)) >= 0) at++;
            if (start == at) throw error("unexpected character");
            try { return Double.valueOf(text.substring(start, at)); }
            catch (NumberFormatException bad) { throw error("bad number"); }
        }

        private Object literal(String word, Object value) throws IOException {
            if (!text.startsWith(word, at)) throw error("unexpected character");
            at += word.length();
            return value;
        }

        private boolean peek(char expected) { return at < text.length() && text.charAt(at) == expected; }

        private void expect(char expected) throws IOException {
            if (!peek(expected)) throw error("expected '" + expected + "'");
            at++;
        }

        private void skip() { while (at < text.length() && Character.isWhitespace(text.charAt(at))) at++; }

        private IOException error(String message) { return new IOException(message + " at offset " + at); }
    }
}
