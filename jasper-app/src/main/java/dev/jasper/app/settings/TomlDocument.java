package dev.jasper.app.settings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * A TOML file as text, edited in place: comments, blank lines, key order and line endings survive every
 * edit. Each operation returns a new document, and is refused with an IllegalArgumentException unless
 * the result parses to exactly the intended values with every other value unchanged.
 */
public final class TomlDocument {
    private enum Kind { HEADER, ASSIGNMENT }

    /**
     * One logical statement. {@code end} is where its text stops: a comment's {@code #}, the line break, or the
     * end of the file. {@code lineEnd} is just past its last line break. For an assignment, {@code path} is
     * the full key and {@code table} the header it sits under; {@code array} marks array-of-tables content.
     */
    private record Statement(Kind kind, int start, int end, int lineEnd, List<String> path, List<String> table,
                             int valueStart, int valueEnd, boolean array, boolean inline) {
        boolean comment(String text) { return end < text.length() && text.charAt(end) == '#'; }
    }

    private record Edit(int start, int end, String text) { }

    private final String text;
    private final String newline;
    private final List<Statement> statements;
    private final Map<List<String>, Object> values;

    private TomlDocument(String text, Map<List<String>, Object> values) {
        this.text = text;
        this.newline = text.contains("\r\n") ? "\r\n" : "\n";
        this.statements = index(text);
        this.values = Map.copyOf(values);
    }

    /** Indexes {@code text}, which must be valid TOML. */
    public static TomlDocument parse(String text) {
        TomlParseResult parsed = Toml.parse(text);
        if (parsed.hasErrors()) throw new IllegalArgumentException("The file has TOML errors; fix them before saving.");
        return new TomlDocument(text, leaves(parsed));
    }

    public String text() { return text; }

    /** Every value in {@code text} by full key path; arrays become lists and tables inside arrays maps. */
    public static Map<List<String>, Object> leaves(String text) {
        TomlParseResult parsed = Toml.parse(text);
        if (parsed.hasErrors()) throw new IllegalArgumentException("Not valid TOML.");
        return leaves(parsed);
    }

    /** Every assignment at or below {@code prefix}, by full key path, in file order. */
    public List<List<String>> keysUnder(List<String> prefix) {
        return statements.stream()
            .filter(statement -> statement.kind() == Kind.ASSIGNMENT && SettingsSchema.startsWith(statement.path(), prefix))
            .map(Statement::path).toList();
    }

    /** Sets {@code path} to the TOML value {@code literal}: in place when it exists, otherwise inserted. */
    public TomlDocument set(List<String> path, String literal) { return set(path, literal, ""); }

    TomlDocument set(List<String> path, String literal, String comment) {
        requirePath(path);
        Object value = TomlLiterals.parseValue(literal);
        if (value instanceof TomlTable) throw new IllegalArgumentException("Set " + TomlLiterals.path(path) + "'s entries one at a time.");
        var intended = new LinkedHashMap<>(values);
        intended.put(path, plain(value));
        Statement existing = assignment(path);
        if (existing != null) return apply(path, intended, List.of(new Edit(existing.valueStart(), existing.valueEnd(), literal)));
        Statement holder = inlineHolding(path);
        if (holder != null) return apply(path, intended, List.of(rewriteInline(holder, path, Optional.of(literal))));
        for (Edit insertion : insertions(path, literal + comment)) {
            try {
                return apply(path, intended, List.of(insertion));
            } catch (IllegalArgumentException refused) {
                // Another placement may be valid TOML.
            }
        }
        throw refused(path);
    }

    /** Removes {@code path}, or an inline table with everything in it; a same-line comment stays as a comment line. */
    public TomlDocument remove(List<String> path) {
        requirePath(path);
        var intended = new LinkedHashMap<>(values);
        intended.keySet().removeIf(key -> SettingsSchema.startsWith(key, path));
        Statement existing = assignment(path);
        if (existing != null) return apply(path, intended, List.of(delete(existing)));
        Statement holder = inlineHolding(path);
        if (holder != null) return apply(path, intended, List.of(rewriteInline(holder, path, Optional.empty())));
        if (intended.size() == values.size()) return this;
        throw refused(path);
    }

    /** Moves {@code from}'s value, exactly as written, and its same-line comment to {@code to}, which must be unset. */
    public TomlDocument move(List<String> from, List<String> to) {
        requirePath(from);
        requirePath(to);
        Statement source = assignment(from);
        if (source == null || source.inline() || values.keySet().stream().anyMatch(key -> SettingsSchema.startsWith(key, to)))
            throw refused(from);
        String value = text.substring(source.valueStart(), source.valueEnd());
        String comment = source.comment(text) ? " " + text.substring(source.end(), lineContentEnd(source)).strip() : "";
        var intended = new LinkedHashMap<>(values);
        intended.remove(from);
        return apply(from, intended, List.of(new Edit(source.start(), source.lineEnd(), ""))).set(to, value, comment);
    }

    /** Renames the {@code [from]} header, and any header below it, to {@code to}. */
    public TomlDocument renameTable(List<String> from, List<String> to) {
        requirePath(from);
        requirePath(to);
        var edits = new ArrayList<Edit>();
        for (Statement statement : statements)
            if (statement.kind() == Kind.HEADER && SettingsSchema.startsWith(statement.path(), from)) {
                if (statement.array()) throw refused(from);
                var renamed = new ArrayList<>(to);
                renamed.addAll(statement.path().subList(from.size(), statement.path().size()));
                int open = text.indexOf('[', statement.start());
                int close = text.lastIndexOf(']', statement.end() - 1) + 1;
                edits.add(new Edit(open, close, "[" + TomlLiterals.path(renamed) + "]"));
            }
        if (edits.isEmpty()) throw refused(from);
        var intended = new LinkedHashMap<List<String>, Object>();
        values.forEach((key, value) -> {
            if (!SettingsSchema.startsWith(key, from)) { intended.put(key, value); return; }
            var renamed = new ArrayList<>(to);
            renamed.addAll(key.subList(from.size(), key.size()));
            intended.put(List.copyOf(renamed), value);
        });
        return apply(from, intended, edits);
    }

    /** Removes every value at or below {@code table}, and every header naming it or a table below it. */
    public TomlDocument removeTable(List<String> table) {
        requirePath(table);
        var intended = new LinkedHashMap<>(values);
        intended.keySet().removeIf(key -> SettingsSchema.startsWith(key, table));
        var edits = new ArrayList<Edit>();
        for (Statement statement : statements) {
            if (!SettingsSchema.startsWith(statement.path(), table)) continue;
            if (statement.array()) throw refused(table);
            edits.add(statement.kind() == Kind.HEADER ? deleteHeader(statement) : delete(statement));
        }
        if (edits.isEmpty() && intended.size() == values.size()) return this;
        return apply(table, intended, edits);
    }

    /** Removes the {@code [table]} header when no assignment follows it before the next header. */
    public TomlDocument removeTableIfEmpty(List<String> table) {
        requirePath(table);
        for (int index = 0; index < statements.size(); index++) {
            Statement statement = statements.get(index);
            if (statement.kind() != Kind.HEADER || statement.array() || !statement.path().equals(table)) continue;
            boolean empty = index + 1 == statements.size() || statements.get(index + 1).kind() == Kind.HEADER;
            return empty ? apply(table, values, List.of(deleteHeader(statement))) : this;
        }
        return this;
    }

    /** Deletes a header line and one blank line just above it; a same-line comment stays behind. */
    private Edit deleteHeader(Statement header) {
        if (header.comment(text)) return new Edit(header.start(), header.end(), "");
        int start = header.start();
        if (start > 0) {
            int previous = text.lastIndexOf('\n', start - 2) + 1;
            if (text.substring(previous, start).isBlank()) start = previous;
        }
        return new Edit(start, header.lineEnd(), "");
    }

    private int lineContentEnd(Statement statement) {
        int at = statement.lineEnd();
        while (at > statement.end() && (text.charAt(at - 1) == '\n' || text.charAt(at - 1) == '\r')) at--;
        return at;
    }

    private Statement assignment(List<String> path) {
        for (Statement statement : statements)
            if (statement.kind() == Kind.ASSIGNMENT && statement.path().equals(path)) {
                if (statement.array()) throw refused(path);
                return statement;
            }
        return null;
    }

    /** The inline table assignment whose value holds {@code path}, if any. */
    private Statement inlineHolding(List<String> path) {
        for (Statement statement : statements)
            if (statement.kind() == Kind.ASSIGNMENT && statement.inline() && path.size() > statement.path().size()
                    && SettingsSchema.startsWith(path, statement.path())) {
                if (statement.array()) throw refused(path);
                return statement;
            }
        return null;
    }

    private Edit rewriteInline(Statement holder, List<String> path, Optional<String> literal) {
        if (path.size() != holder.path().size() + 1) throw refused(path);
        if (!(TomlLiterals.parseValue(text.substring(holder.valueStart(), holder.valueEnd())) instanceof TomlTable table)) throw refused(path);
        var entries = new LinkedHashMap<String, String>();
        for (String key : table.keySet()) {
            Object value = table.get(List.of(key));
            if (value instanceof TomlTable) throw refused(path);
            entries.put(key, TomlLiterals.format(value));
        }
        if (literal.isPresent()) entries.put(path.getLast(), literal.get());
        else entries.remove(path.getLast());
        String inline = entries.isEmpty() ? "{}" : entries.entrySet().stream()
            .map(entry -> TomlLiterals.key(entry.getKey()) + " = " + entry.getValue())
            .collect(Collectors.joining(", ", "{ ", " }"));
        return new Edit(holder.valueStart(), holder.valueEnd(), inline);
    }

    /**
     * Candidate placements for a new key, best first: after the last assignment of its own table (the root
     * counts as always present), beside a dotted sibling in the same table, then a new section at the end.
     */
    private List<Edit> insertions(List<String> path, String valueText) {
        List<String> parent = path.subList(0, path.size() - 1);
        String leaf = TomlLiterals.key(path.getLast()) + " = " + valueText;
        var edits = new ArrayList<Edit>();
        sectionEnd(parent).ifPresent(at -> edits.add(line(at, leaf)));
        for (Statement statement : statements)
            if (statement.kind() == Kind.ASSIGNMENT && !statement.array() && !statement.inline()
                    && statement.path().size() == path.size() && statement.path().subList(0, parent.size()).equals(parent)
                    && statement.table().size() < parent.size() && SettingsSchema.startsWith(parent, statement.table()))
                edits.add(line(statement.lineEnd(),
                    TomlLiterals.path(path.subList(statement.table().size(), path.size())) + " = " + valueText));
        if (!parent.isEmpty()) edits.add(section(parent, leaf));
        return edits;
    }

    /** Where a new line for {@code table} goes: after its last assignment, or just after its header. */
    private Optional<Integer> sectionEnd(List<String> table) {
        int at;
        int from;
        if (table.isEmpty()) {
            at = leadingComments();
            from = 0;
        } else {
            int header = -1;
            for (int index = 0; index < statements.size(); index++) {
                Statement statement = statements.get(index);
                if (statement.kind() == Kind.HEADER && !statement.array() && statement.path().equals(table)) { header = index; break; }
            }
            if (header < 0) return Optional.empty();
            at = statements.get(header).lineEnd();
            from = header + 1;
        }
        for (int index = from; index < statements.size() && statements.get(index).kind() == Kind.ASSIGNMENT; index++)
            at = statements.get(index).lineEnd();
        return Optional.of(at);
    }

    /** Just past the file's opening run of comment lines. */
    private int leadingComments() {
        int at = 0;
        while (at < text.length()) {
            int lineBreak = text.indexOf('\n', at);
            int next = lineBreak < 0 ? text.length() : lineBreak + 1;
            if (!text.substring(at, next).strip().startsWith("#")) break;
            at = next;
        }
        return at;
    }

    private Edit line(int at, String content) {
        String before = at == text.length() && !text.isEmpty() && !text.endsWith("\n") ? newline : "";
        return new Edit(at, at, before + content + newline);
    }

    private Edit section(List<String> table, String content) {
        String before = "";
        if (!text.isEmpty()) {
            before = text.endsWith("\n") ? "" : newline;
            if (!(text.endsWith("\n\n") || text.endsWith("\n\r\n"))) before += newline;
        }
        return new Edit(text.length(), text.length(), before + "[" + TomlLiterals.path(table) + "]" + newline + content + newline);
    }

    /** Deletes a statement; a same-line comment stays behind as a comment line, otherwise the whole line goes. */
    private Edit delete(Statement statement) {
        if (statement.comment(text)) return new Edit(statement.start(), statement.end(), "");
        return new Edit(statement.start(), statement.lineEnd(), "");
    }

    private TomlDocument apply(List<String> path, Map<List<String>, Object> intended, List<Edit> edits) {
        var result = new StringBuilder(text);
        edits.stream().sorted(Comparator.comparingInt(Edit::start).reversed())
            .forEach(edit -> result.replace(edit.start(), edit.end(), edit.text()));
        String next = result.toString();
        TomlParseResult parsed = Toml.parse(next);
        if (parsed.hasErrors() || !leaves(parsed).equals(intended)) throw refused(path);
        return new TomlDocument(next, intended);
    }

    private static IllegalArgumentException refused(List<String> path) {
        return new IllegalArgumentException("Can't safely edit " + TomlLiterals.path(path) + " here; edit the file by hand.");
    }

    private static void requirePath(List<String> path) {
        if (path.isEmpty()) throw new IllegalArgumentException("A key path needs at least one key.");
    }

    private static Map<List<String>, Object> leaves(TomlTable table) {
        var result = new LinkedHashMap<List<String>, Object>();
        collect(List.of(), table, result);
        return result;
    }

    private static void collect(List<String> parent, TomlTable table, Map<List<String>, Object> into) {
        for (String key : table.keySet()) {
            var path = new ArrayList<>(parent);
            path.add(key);
            Object value = table.get(List.of(key));
            if (value instanceof TomlTable nested) collect(List.copyOf(path), nested, into);
            else into.put(List.copyOf(path), plain(value));
        }
    }

    private static Object plain(Object value) {
        if (value instanceof TomlArray array) {
            var items = new ArrayList<Object>();
            for (int index = 0; index < array.size(); index++) items.add(plain(array.get(index)));
            return List.copyOf(items);
        }
        if (value instanceof TomlTable table) {
            var map = new LinkedHashMap<String, Object>();
            for (String key : table.keySet()) map.put(key, plain(table.get(List.of(key))));
            return Map.copyOf(map);
        }
        return value;
    }

    private static List<Statement> index(String text) {
        var result = new ArrayList<Statement>();
        var arrays = new ArrayList<List<String>>();
        List<String> table = List.of();
        boolean array = false;
        for (int start = 0; start < text.length();) {
            int end = statementEnd(text, start);
            int lineEnd = end;
            while (lineEnd < text.length() && text.charAt(lineEnd) != '\n') lineEnd++;
            if (lineEnd < text.length()) lineEnd++;
            String statement = text.substring(start, end).strip();
            if (statement.startsWith("[[")) {
                table = List.copyOf(Toml.parseDottedKey(statement.substring(2, statement.lastIndexOf("]]")).strip()));
                arrays.add(table);
                array = true;
                result.add(new Statement(Kind.HEADER, start, end, lineEnd, table, List.of(), -1, -1, true, false));
            } else if (statement.startsWith("[")) {
                List<String> header = List.copyOf(Toml.parseDottedKey(statement.substring(1, statement.lastIndexOf(']')).strip()));
                table = header;
                array = arrays.stream().anyMatch(prefix -> SettingsSchema.startsWith(header, prefix));
                result.add(new Statement(Kind.HEADER, start, end, lineEnd, table, List.of(), -1, -1, array, false));
            } else if (!statement.isEmpty()) {
                int equals = assignmentEquals(text, start, end);
                if (equals < 0) throw new IllegalArgumentException("Cannot read the assignment at offset " + start + ".");
                var path = new ArrayList<>(table);
                path.addAll(Toml.parseDottedKey(text.substring(start, equals).strip()));
                int valueStart = equals + 1;
                while (valueStart < end && Character.isWhitespace(text.charAt(valueStart))) valueStart++;
                int valueEnd = end;
                while (valueEnd > valueStart && Character.isWhitespace(text.charAt(valueEnd - 1))) valueEnd--;
                result.add(new Statement(Kind.ASSIGNMENT, start, end, lineEnd, List.copyOf(path), table, valueStart, valueEnd,
                    array, valueStart < end && text.charAt(valueStart) == '{'));
            }
            start = lineEnd;
        }
        return List.copyOf(result);
    }

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
