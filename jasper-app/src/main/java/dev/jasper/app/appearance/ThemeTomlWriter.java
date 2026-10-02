package dev.jasper.app.appearance;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/** Deterministic, data-only native serialization. Callers resolve inheritance when exporting a standalone theme. */
public final class ThemeTomlWriter {
    private ThemeTomlWriter() {}

    /** Writes every authored field, retaining ordered UI overrides; rejects data the native reader cannot accept. */
    public static String write(ThemeDocument document) {
        var out = new StringBuilder();
        entry(out, "name", document.name());
        document.dark().ifPresent(value -> entry(out, "dark", value));
        document.parent().ifPresent(value -> entry(out, "extends", value));
        document.uiComplete().ifPresent(value -> entry(out, "ui_complete", value));
        for (String key : List.of("author", "license", "source", "commit"))
            if (document.metadata().containsKey(key)) entry(out, key, document.metadata().get(key));
        if (!document.seeds().isEmpty()) {
            out.append("\n[colors]\n");
            for (Seed seed : Seed.values()) if (document.seeds().containsKey(seed)) entry(out, seed.key(), document.seeds().get(seed));
        }
        for (int offset : new int[] {0, 8}) {
            if (document.ansi().keySet().stream().noneMatch(index -> index >= offset && index < offset + 8)) continue;
            out.append(offset == 0 ? "\n[colors.normal]\n" : "\n[colors.bright]\n");
            for (int i = 0; i < 8; i++) if (document.ansi().containsKey(offset + i))
                entry(out, ThemeDocument.ANSI_NAMES.get(i), document.ansi().get(offset + i));
        }
        if (!document.tokens().isEmpty()) {
            out.append("\n[tokens]\n");
            for (Token token : Token.values()) if (document.tokens().containsKey(token)) entry(out, token.key(), document.tokens().get(token));
        }
        if (!document.syntax().isEmpty()) {
            out.append("\n[syntax]\n");
            for (SyntaxRole role : SyntaxRole.values()) {
                SyntaxStyle style = document.syntax().get(role);
                if (style == null) continue;
                out.append(quoted(role.key())).append(" = { color = ").append(value(style.color()))
                    .append(", bold = ").append(style.bold()).append(", italic = ").append(style.italic()).append(" }\n");
            }
        }
        section(out, "ui", document.ui());
        section(out, "icons", document.icons());
        String result = out.toString();
        ThemeToml.parse(result, document.origin());
        return result;
    }

    private static void section(StringBuilder out, String name, Map<String, Object> values) {
        if (values.isEmpty()) return;
        out.append("\n[").append(name).append("]\n");
        values.forEach((key, value) -> entry(out, key, value));
    }

    private static void entry(StringBuilder out, String key, Object value) {
        out.append(quoted(key)).append(" = ").append(value(value)).append('\n');
    }

    private static String value(Object value) {
        if (value instanceof Color color) return quoted(UiTemplate.hex(color));
        if (value instanceof String text) return quoted(text);
        if (value instanceof Boolean || value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)
            return value.toString();
        if (value instanceof Double number && Double.isFinite(number)) return number.toString();
        if (value instanceof Float number && Float.isFinite(number)) return number.toString();
        if (value instanceof Map<?, ?> map) {
            var entries = new StringJoiner(", ", "{", "}");
            map.forEach((key, child) -> {
                if (!(key instanceof String text)) throw new IllegalArgumentException("Theme keys must be strings");
                entries.add(quoted(text) + " = " + value(child));
            });
            return entries.toString();
        }
        if (value instanceof List<?> list) {
            var entries = new StringJoiner(", ", "[", "]");
            list.forEach(child -> entries.add(value(child)));
            return entries.toString();
        }
        throw new IllegalArgumentException("Unsupported theme value: " + value);
    }

    private static String quoted(String text) {
        var out = new StringBuilder("\"");
        for (int i = 0; i < text.length();) {
            int c = text.codePointAt(i);
            i += Character.charCount(c);
            if (c >= 0xd800 && c <= 0xdfff) throw new IllegalArgumentException("Unpaired surrogate in theme string");
            if (c == '"' || c == '\\') out.append('\\').append((char) c);
            else if (c < 0x20 || c == 0x7f) out.append(String.format("\\u%04x", (int) c));
            else out.appendCodePoint(c);
        }
        return out.append('"').toString();
    }
}
