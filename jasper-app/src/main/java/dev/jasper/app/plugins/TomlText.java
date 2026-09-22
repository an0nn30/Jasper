package dev.jasper.app.plugins;

import java.util.List;
import java.util.Map;

/** Writes the value kinds {@code PluginConfig} reads as TOML: scalars and string lists first, then one table per nested map. */
final class TomlText {
    private TomlText() { }

    static String write(Map<String, Object> table) {
        var out = new StringBuilder();
        write(out, "", table);
        return out.toString();
    }

    private static void write(StringBuilder out, String prefix, Map<String, Object> table) {
        for (var entry : table.entrySet())
            if (!(entry.getValue() instanceof Map<?, ?>)) out.append(key(entry.getKey())).append(" = ").append(value(entry.getValue())).append('\n');
        for (var entry : table.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> nested)) continue;
            String name = prefix.isEmpty() ? key(entry.getKey()) : prefix + "." + key(entry.getKey());
            out.append('\n').append('[').append(name).append("]\n");
            @SuppressWarnings("unchecked") Map<String, Object> map = (Map<String, Object>) nested;
            write(out, name, map);
        }
    }

    private static String key(String key) { return key.matches("[A-Za-z0-9_-]+") ? key : quote(key); }

    private static String value(Object value) {
        return switch (value) {
            case String text -> quote(text);
            case Boolean flag -> flag.toString();
            case Long number -> number.toString();
            case Integer number -> number.toString();
            case Double number -> number.toString();
            case List<?> list -> "[" + String.join(", ", list.stream().map(TomlText::value).toList()) + "]";
            default -> quote(String.valueOf(value));
        };
    }

    static String quote(String text) {
        var out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> { if (c < 0x20) out.append(String.format("\\u%04X", (int) c)); else out.append(c); }
            }
        }
        return out.append('"').toString();
    }
}
