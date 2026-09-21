package dev.jasper.app.snippets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/** The snippets.toml format: an array of [[snippet]] tables. Lenient per entry, strict per file, append-only. */
final class SnippetFile {
    static final String HEADER = "# Jasper snippets. Edit freely; Jasper only ever appends new [[snippet]] tables.\n";
    static final int MAX_BYTES = 1024 * 1024;

    record Parsed(List<Snippet> snippets, List<String> warnings) {}

    private SnippetFile() {}

    /** Throws when the text is not valid TOML; skips and reports individual bad entries. */
    static Parsed parse(String text) throws IOException {
        TomlParseResult toml = Toml.parse(text);
        if (toml.hasErrors()) throw new IOException("snippets.toml has TOML errors: " + toml.errors().getFirst());
        Object value = toml.get("snippet");
        if (value == null) return new Parsed(List.of(), List.of());
        if (!(value instanceof TomlArray array)) return new Parsed(List.of(), List.of("'snippet' must be an array of tables"));
        var snippets = new ArrayList<Snippet>();
        var warnings = new ArrayList<String>();
        var keys = new HashSet<String>();
        for (int i = 0; i < array.size(); i++) {
            Object item = array.get(i);
            String where = "snippet " + (i + 1) + ": ";
            if (!(item instanceof TomlTable table)) { warnings.add(where + "not a table"); continue; }
            try {
                var snippet = new Snippet(string(table, "name"), string(table, "command"), strings(table, "keywords"));
                if (!keys.add(snippet.key())) { warnings.add(where + "duplicate name " + snippet.name()); continue; }
                snippets.add(snippet);
            } catch (IllegalArgumentException invalid) {
                warnings.add(where + invalid.getMessage());
            }
        }
        return new Parsed(List.copyOf(snippets), List.copyOf(warnings));
    }

    private static String string(TomlTable table, String key) {
        if (!(table.get(key) instanceof String text)) throw new IllegalArgumentException("'" + key + "' must be a string");
        return text;
    }

    private static List<String> strings(TomlTable table, String key) {
        Object value = table.get(key);
        if (value == null) return List.of();
        if (!(value instanceof TomlArray array)) throw new IllegalArgumentException("'" + key + "' must be an array of strings");
        var values = new ArrayList<String>();
        for (int i = 0; i < array.size(); i++) {
            if (!(array.get(i) instanceof String text)) throw new IllegalArgumentException("'" + key + "' must be an array of strings");
            values.add(text);
        }
        return values;
    }

    /** The file text with one more table at the end; existing bytes are untouched. */
    static String append(String existing, Snippet snippet) {
        var out = new StringBuilder(existing.isEmpty() ? HEADER : existing);
        if (out.charAt(out.length() - 1) != '\n') out.append('\n');
        if (!existing.isEmpty()) out.append('\n');
        out.append("[[snippet]]\n");
        out.append("name = ").append(tomlString(snippet.name())).append('\n');
        out.append("command = ").append(tomlString(snippet.command())).append('\n');
        if (!snippet.keywords().isEmpty()) {
            out.append("keywords = [");
            for (int i = 0; i < snippet.keywords().size(); i++) {
                if (i > 0) out.append(", ");
                out.append(tomlString(snippet.keywords().get(i)));
            }
            out.append("]\n");
        }
        return out.toString();
    }

    /** A TOML basic string: backslash, quote, tab, newlines and other control characters escaped. */
    static String tomlString(String text) {
        var out = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || c == 0x7f) out.append(String.format(Locale.ROOT, "\\u%04X", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }
}
