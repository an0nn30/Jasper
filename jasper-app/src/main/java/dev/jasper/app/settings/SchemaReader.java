package dev.jasper.app.settings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.BiFunction;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlPosition;
import org.tomlj.TomlTable;

/**
 * Reads TOML text against a {@link SettingsSchema}: each setting's value through its kind, old keys, unknown keys and
 * type errors, with the position of every problem. A caller reads the paths the schema does not describe (a version
 * key, tables other readers own) through the {@code special} hook, reporting through this reader. One read per
 * instance; the caller chooses the thread.
 */
public final class SchemaReader {
    /**
     * One problem.
     *
     * @param severity how serious it is
     * @param key the dotted key where it was found; empty for the whole file
     * @param line 1-based line, 0 when unknown
     * @param column 1-based column, 0 when unknown
     * @param message plain text for the user
     */
    public record Problem(Reading.Severity severity, String key, int line, int column, String message) { }

    /**
     * What the text held.
     *
     * @param values each setting's value; all defaults after a syntax error
     * @param problems every problem, in file order
     * @param rejected whether a syntax or type error means the values must not be applied
     * @param syntaxError whether the text is not valid TOML
     * @param legacyKeys old keys found, in file order, with the keys they moved to
     */
    public record Result(SettingValues values, List<Problem> problems, boolean rejected, boolean syntaxError, List<LegacyKey> legacyKeys) {
        public Result {
            Objects.requireNonNull(values, "values");
            problems = List.copyOf(problems);
            legacyKeys = List.copyOf(legacyKeys);
        }
    }

    private record AliasHit(Setting<?> setting, List<String> path, Object value) { }

    private final SettingsSchema schema;
    private final BiFunction<List<String>, Object, Object> aliasValue;
    private final TomlParseResult toml;
    private final SettingValues.Builder values;
    private final List<Problem> problems = new ArrayList<>();
    private final List<AliasHit> aliasHits = new ArrayList<>();
    private boolean rejected;

    public SchemaReader(SettingsSchema schema, String text) {
        this(schema, text, (path, value) -> value);
    }

    public SchemaReader(SettingsSchema schema, String text, BiFunction<List<String>, Object, Object> aliasValue) {
        this.schema = Objects.requireNonNull(schema, "schema");
        this.aliasValue = Objects.requireNonNull(aliasValue, "aliasValue");
        this.toml = Toml.parse(Objects.requireNonNull(text, "text"));
        this.values = SettingValues.builder(schema);
    }

    /** Reads text that holds nothing beyond the schema's settings. */
    public static Result read(SettingsSchema schema, String text) {
        return new SchemaReader(schema, text).read((path, value) -> false);
    }

    /** Reads the text. {@code special} sees every path first and returns true for a path it read itself. */
    public Result read(BiPredicate<List<String>, Object> special) {
        if (toml.hasErrors()) {
            toml.errors().forEach(error -> problems.add(new Problem(Reading.Severity.ERROR, "", error.position().line(),
                error.position().column(), "Invalid TOML syntax or duplicate definition; check this location.")));
            return new Result(SettingValues.defaults(schema), problems, true, true, List.of());
        }
        readTable(List.of(), toml, special);
        List<LegacyKey> legacyKeys = resolveAliases();
        problems.sort(Comparator.comparingInt(Problem::line).thenComparingInt(Problem::column));
        return new Result(values.build(), problems, rejected, false, legacyKeys);
    }

    private void readTable(List<String> parent, TomlTable table, BiPredicate<List<String>, Object> special) {
        for (String key : table.keySet()) {
            List<String> path = append(parent, key);
            Object value = table.get(List.of(key));
            if (special.test(path, value)) continue;
            var current = schema.find(path);
            if (current.isPresent()) { read(current.get(), path, value); continue; }
            var aliased = schema.aliasOf(path);
            if (aliased.isPresent()) { aliasHits.add(new AliasHit(aliased.get(), path, value)); continue; }
            if (!schema.reserved(path) && !schema.knownTable(path)) { warning(path, "Unknown setting or table; ignored."); continue; }
            if (!(value instanceof TomlTable nested)) typeError(path, "a table");
            else readTable(path, nested, special);
        }
    }

    private <T> void read(Setting<T> setting, List<String> path, Object value) {
        Reading<T> reading = setting.kind().read(value, setting.defaultValue());
        for (Reading.Issue issue : reading.issues()) {
            if (issue.rejectsFile()) rejected = true;
            var at = new ArrayList<>(path);
            at.addAll(issue.subPath());
            problem(issue.severity(), at, issue.message());
        }
        values.put(setting, reading.value(), path);
    }

    /** Old keys are read after the walk, so a current key wins wherever it appears in the file. */
    private List<LegacyKey> resolveAliases() {
        aliasHits.sort(Comparator.comparingInt((AliasHit hit) -> line(hit.path())).thenComparingInt(hit -> column(hit.path())));
        var legacy = new ArrayList<LegacyKey>();
        for (AliasHit hit : aliasHits) {
            Setting<?> setting = hit.setting();
            legacy.add(new LegacyKey(hit.path(), setting.key()));
            if (values.present(setting)) warning(hit.path(), "Superseded by " + setting.name() + "; ignored.");
            else {
                read(setting, hit.path(), aliasValue.apply(hit.path(), hit.value()));
                warning(hit.path(), "Moved to " + setting.name() + ". Use Migrate Config to update the file.");
            }
        }
        return legacy;
    }

    /** A warning at {@code path}. */
    public void warning(List<String> path, String message) { problem(Reading.Severity.WARNING, path, message); }

    /** An error at {@code path} that does not reject the file. */
    public void error(List<String> path, String message) { problem(Reading.Severity.ERROR, path, message); }

    /** A value of the wrong TOML type: an error that rejects the file. */
    public void typeError(List<String> path, String expected) {
        rejected = true;
        error(path, "Expected " + expected + "; configuration was not applied.");
    }

    private void problem(Reading.Severity severity, List<String> path, String message) {
        problems.add(new Problem(severity, Toml.joinKeyPath(path), line(path), column(path), message));
    }

    private int line(List<String> path) {
        TomlPosition position = toml.inputPositionOf(path);
        return position == null ? 0 : position.line();
    }

    private int column(List<String> path) {
        TomlPosition position = toml.inputPositionOf(path);
        return position == null ? 0 : position.column();
    }

    private static List<String> append(List<String> parent, String key) {
        var path = new ArrayList<>(parent);
        path.add(key);
        return List.copyOf(path);
    }
}
