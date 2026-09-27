package dev.jasper.app.config;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.tomlj.Toml;
import org.tomlj.TomlTable;

/** Patches binding assignments, leaving comments and unrelated TOML text byte-for-byte intact. */
final class KeybindingText {
    private KeybindingText() { }
    private record Edit(int start, int end, String text) { }

    static String update(String text, Map<String, Optional<String>> changes) {
        var parsed = Toml.parse(text);
        if (parsed.hasErrors()) throw new IllegalArgumentException("Fix the settings file's TOML errors before saving shortcuts.");
        String newline = text.contains("\r\n") ? "\r\n" : "\n";
        var remaining = new LinkedHashMap<>(changes);
        var edits = new ArrayList<Edit>();
        boolean inBindings = false, root = true;
        int insertion = -1;
        for (int start = 0; start < text.length();) {
            int end = statementEnd(text, start);
            String statement = text.substring(start, end).strip();
            int next = end;
            // End may be the beginning of a trailing comment; retain it in place.
            while (next < text.length() && text.charAt(next) != '\n') next++;
            if (next < text.length()) next++;
            if (statement.startsWith("[")) {
                root = false;
                var header = Toml.parse(statement + "\n__shortcut_probe__ = 1");
                inBindings = !header.hasErrors() && header.contains(List.of("keybindings", "__shortcut_probe__"));
                if (inBindings) insertion = next;
            } else if (!statement.isEmpty() && (root || inBindings)) {
                var single = Toml.parse((inBindings ? "[keybindings]\n" : "") + statement);
                Object value = single.hasErrors() ? null : single.get("keybindings");
                if (value instanceof TomlTable table) {
                    int equals = assignmentEquals(text, start, end);
                    if (equals < 0) throw new IllegalArgumentException("Cannot edit this keybindings declaration.");
                    int valueStart = equals + 1;
                    while (valueStart < end && Character.isWhitespace(text.charAt(valueStart))) valueStart++;
                    int valueEnd = end;
                    while (valueEnd > valueStart && Character.isWhitespace(text.charAt(valueEnd - 1))) valueEnd--;
                    if (text.charAt(valueStart) == '{') {
                        var values = new LinkedHashMap<>(table.toMap());
                        changes.forEach((id, binding) -> { if (binding.isPresent()) values.put(id, binding.get()); else values.remove(id); });
                        String inline = values.entrySet().stream().map(e -> quote(e.getKey()) + " = " + quote((String) e.getValue()))
                            .collect(java.util.stream.Collectors.joining(", ", "{ ", " }"));
                        edits.add(new Edit(valueStart, valueEnd, inline));
                        remaining.clear();
                    } else if (table.keySet().size() == 1) {
                        String id = table.keySet().iterator().next();
                        if (changes.containsKey(id)) {
                            Optional<String> binding = changes.get(id);
                            edits.add(binding.isPresent() ? new Edit(valueStart, valueEnd, quote(binding.get())) : new Edit(start, valueEnd, ""));
                            remaining.remove(id);
                        }
                    }
                }
            }
            start = next;
        }
        var addition = new StringBuilder();
        for (var entry : remaining.entrySet()) if (entry.getValue().isPresent()) addition
            .append(insertionPrefix(entry.getKey(), insertion >= 0)).append(" = ").append(quote(entry.getValue().get())).append(newline);
        if (!addition.isEmpty()) {
            int at = Math.max(0, insertion);
            String prefix = at > 0 && text.charAt(at - 1) != '\n' ? newline : "";
            edits.add(new Edit(at, at, prefix + addition));
        }
        edits.sort(Comparator.comparingInt(Edit::start).reversed());
        var result = new StringBuilder(text);
        for (Edit edit : edits) result.replace(edit.start(), edit.end(), edit.text());
        var checked = Toml.parse(result.toString());
        if (checked.hasErrors()) throw new IllegalArgumentException("Cannot safely edit this keybindings declaration; use Settings to edit it.");
        return result.toString();
    }

    private static String insertionPrefix(String id, boolean table) { return (table ? "" : "keybindings.") + quote(id); }
    private static String quote(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }

    private static int assignmentEquals(String text, int start, int end) {
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            if (c == '\'' || c == '"') i = stringEnd(text, i) - 1;
            else if (c == '=') return i;
        }
        return -1;
    }

    /** TOML logical statements, including multiline strings and arrays, without trailing comments. */
    private static int statementEnd(String text, int start) {
        int nesting = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'' || c == '"') i = stringEnd(text, i) - 1;
            else if (c == '#') {
                if (nesting == 0) return i;
                while (i < text.length() && text.charAt(i) != '\n') i++;
            } else if (c == '[' || c == '{') nesting++;
            else if (c == ']' || c == '}') nesting--;
            else if (c == '\n' && nesting == 0) return i;
        }
        return text.length();
    }

    private static int stringEnd(String text, int start) {
        char quote = text.charAt(start);
        boolean triple = start + 2 < text.length() && text.charAt(start + 1) == quote && text.charAt(start + 2) == quote;
        for (int i = start + (triple ? 3 : 1); i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && quote == '"') { i++; continue; }
            if (c != quote) continue;
            if (!triple) return i + 1;
            int end = i;
            while (end < text.length() && text.charAt(end) == quote) end++;
            if (end - i >= 3) return end;
            i = end - 1;
        }
        throw new IllegalArgumentException("Unterminated TOML string.");
    }
}
