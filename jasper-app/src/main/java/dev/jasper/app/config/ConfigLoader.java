package dev.jasper.app.config;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.settings.LegacyKey;
import dev.jasper.app.settings.Reading;
import dev.jasper.app.settings.Setting;
import dev.jasper.app.settings.SettingValues;
import dev.jasper.app.settings.SettingsSchema;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlPosition;
import org.tomlj.TomlTable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure TOML parsing and validation, with no file or Swing operations; the schema is {@link CoreSettings#SCHEMA}. */
public final class ConfigLoader {
    /**
     * A parsed file.
     *
     * @param snapshot the settings to use (meaningless when {@code rejected})
     * @param diagnostics problems, by position
     * @param rejected whether a syntax or type error means the file must not be applied
     * @param values each setting's loaded value and where it came from
     * @param legacyKeys old keys found, in file order, with the keys they moved to
     * @param deadTables old {@code palette.scopes} and {@code plugins."<id>"} tables Migrate Config may remove
     */
    public record Result(ConfigSnapshot snapshot, List<ConfigDiagnostic> diagnostics, boolean rejected,
                         SettingValues values, List<LegacyKey> legacyKeys, List<List<String>> deadTables) {
        public Result {
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(values, "values");
            diagnostics = List.copyOf(diagnostics);
            legacyKeys = List.copyOf(legacyKeys);
            deadTables = deadTables.stream().map(List::copyOf).toList();
        }

        /** A result with default values and nothing to migrate. */
        public Result(ConfigSnapshot snapshot, List<ConfigDiagnostic> diagnostics, boolean rejected) {
            this(snapshot, diagnostics, rejected, SettingValues.defaults(CoreSettings.SCHEMA), List.of(), List.of());
        }
    }

    /** The layout this build writes; a file without {@code version} is version 1. */
    public static final int VERSION = 2;

    private static final List<String> KEYBINDINGS = List.of("keybindings");
    private static final List<String> PLUGINS = List.of("plugins");
    private static final List<String> PALETTE_SCOPES = List.of("palette", "scopes");
    private static final List<String> VERSION_KEY = List.of("version");
    private static final java.util.regex.Pattern PLUGIN_ID = java.util.regex.Pattern.compile("[a-z][a-z0-9_.-]{0,127}");

    private record AliasHit(Setting<?> setting, List<String> path, Object value) { }

    private final SettingsSchema schema = CoreSettings.SCHEMA;
    private final Path file;
    private final TomlParseResult toml;
    private final boolean macOs;
    private final List<ConfigDiagnostic> diagnostics = new ArrayList<>();
    private final SettingValues.Builder values = SettingValues.builder(CoreSettings.SCHEMA);
    private final List<AliasHit> aliasHits = new ArrayList<>();
    private final List<List<String>> deadTables = new ArrayList<>();
    private boolean rejected;
    private Map<String, String> keybindings = Map.of();
    private Map<String, Map<String, Object>> plugins = Map.of();

    private ConfigLoader(Path file, String text, boolean macOs) {
        this.file = Objects.requireNonNull(file, "file");
        this.toml = Toml.parse(text);
        this.macOs = macOs;
    }

    public static Result parse(Path file, String text, boolean macOs) {
        return new ConfigLoader(file, text, macOs).parse();
    }

    private Result parse() {
        if (toml.hasErrors()) {
            toml.errors().forEach(error -> diagnostics.add(new ConfigDiagnostic(
                ConfigDiagnostic.Severity.ERROR, file, error.position().line(), error.position().column(), "",
                "Invalid TOML syntax or duplicate definition; check this location.")));
            return new Result(ConfigSnapshot.defaults(), diagnostics, true, SettingValues.defaults(schema), List.of(), List.of());
        }
        readTable(List.of(), toml);
        List<LegacyKey> legacyKeys = resolveAliases();
        diagnostics.sort(Comparator.comparingInt(ConfigDiagnostic::line).thenComparingInt(ConfigDiagnostic::column));
        SettingValues loaded = values.build();
        return new Result(ConfigSnapshot.from(loaded, keybindings, plugins), diagnostics, rejected, loaded, legacyKeys, deadTables);
    }

    private void readTable(List<String> parent, TomlTable table) {
        for (String key : table.keySet()) {
            List<String> path = append(parent, key);
            Object value = table.get(List.of(key));
            if (path.equals(VERSION_KEY)) { readVersion(path, value); continue; }
            var current = schema.find(path);
            if (current.isPresent()) { read(current.get(), path, value); continue; }
            var aliased = schema.aliasOf(path);
            if (aliased.isPresent()) { aliasHits.add(new AliasHit(aliased.get(), path, value)); continue; }
            if (!schema.reserved(path) && !schema.knownTable(path)) { warning(path, "Unknown setting or table; ignored."); continue; }
            if (!(value instanceof TomlTable nested)) typeError(path, "a table");
            else if (path.equals(KEYBINDINGS)) readBindings(nested);
            else if (path.equals(PLUGINS)) readPlugins(nested);
            else if (path.equals(PALETTE_SCOPES)) readOldScopes(nested);
            else readTable(path, nested);
        }
    }

    private <T> void read(Setting<T> setting, List<String> path, Object value) {
        Reading<T> reading = setting.kind().read(value, setting.defaultValue());
        for (Reading.Issue issue : reading.issues()) {
            if (issue.rejectsFile()) rejected = true;
            var at = new ArrayList<>(path);
            at.addAll(issue.subPath());
            diagnostic(issue.severity() == Reading.Severity.WARNING ? ConfigDiagnostic.Severity.WARNING : ConfigDiagnostic.Severity.ERROR,
                at, issue.message());
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
                read(setting, hit.path(), hit.value());
                warning(hit.path(), "Moved to " + setting.name() + ". Use Migrate Config to update the file.");
            }
        }
        return legacy;
    }

    private void readVersion(List<String> path, Object value) {
        if (!(value instanceof Long number)) typeError(path, "an integer");
        else if (number < 1) valueError(path, "Use a version from 1–" + VERSION + "; treating this file as version 1.");
        else if (number > VERSION) warning(path, "Written by a newer Jasper; unknown settings are ignored.");
    }

    /** palette.scopes once held the History scope's settings; it is kept known so the move is explained. */
    private void readOldScopes(TomlTable table) {
        deadTables.add(PALETTE_SCOPES);
        for (String key : table.keySet()) {
            List<String> path = append(PALETTE_SCOPES, key);
            if (key.equals("history"))
                warning(path, "History moved to the Shell History plugin: its settings (trivial_commands, deprioritize_trivial) "
                    + "now live in plugins/dev.jasper.history/dev.jasper.history.toml; disable the plugin in Manage Plugins to turn it off.");
            else warning(path, "Unknown setting or table; ignored.");
        }
    }

    /** Plugin tables are opaque to the application: no schema, so no unknown-key warnings inside them. */
    private void readPlugins(TomlTable table) {
        var staged = new LinkedHashMap<String, Map<String, Object>>();
        for (String id : table.keySet()) {
            List<String> path = List.of("plugins", id);
            Object value = table.get(List.of(id));
            if (!PLUGIN_ID.matcher(id).matches()) { warning(path, "Not a plugin id; ignored."); continue; }
            if (!(value instanceof TomlTable nested)) { typeError(path, "a table"); continue; }
            staged.put(id, freeze(path, nested));
            deadTables.add(path);
            warning(path, "Plugin settings moved to plugins/" + id + "/" + id + ".toml; this table only seeds that file when it is missing.");
        }
        plugins = Map.copyOf(staged);
    }

    private Map<String, Object> freeze(List<String> parent, TomlTable table) { return freeze(parent, table, this::warning); }

    /**
     * The value kinds plugin settings may hold, as an immutable map: strings, integers, floats, booleans, arrays of
     * strings and nested tables; anything else is dropped silently.
     *
     * @param table a parsed TOML table
     * @return the frozen values
     */
    public static Map<String, Object> freeze(TomlTable table) { return freeze(List.of(), table, (path, message) -> { }); }

    private static Map<String, Object> freeze(List<String> parent, TomlTable table, java.util.function.BiConsumer<List<String>, String> problem) {
        var result = new LinkedHashMap<String, Object>();
        for (String key : table.keySet()) {
            var path = new ArrayList<>(parent);
            path.add(key);
            Object value = table.get(List.of(key));
            switch (value) {
                case String text -> result.put(key, text);
                case Long number -> result.put(key, number);
                case Double number -> result.put(key, number);
                case Boolean flag -> result.put(key, flag);
                case TomlTable nested -> result.put(key, freeze(path, nested, problem));
                case TomlArray array -> {
                    List<String> items = new ArrayList<>();
                    for (int i = 0; i < array.size(); i++) if (array.get(i) instanceof String text) items.add(text);
                    if (items.size() == array.size()) result.put(key, List.copyOf(items));
                    else problem.accept(path, "Only arrays of strings are supported in plugin settings; ignored.");
                }
                default -> problem.accept(path, "Unsupported value type in plugin settings; ignored.");
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private void readBindings(TomlTable table) {
        Map<String, String> overrides = new LinkedHashMap<>();
        boolean invalid = false;
        for (String action : table.keySet()) {
            List<String> path = List.of("keybindings", action);
            if (!knownAction(action) && !KeyBindings.extensionId(action)) {
                warning(path, "Unknown action; ignored.");
                continue;
            }
            Object value = table.get(List.of(action));
            if (!(value instanceof String binding)) {
                typeError(path, "a shortcut string");
                continue;
            }
            try {
                KeyBindings.parse(binding, macOs);
                overrides.put(action, binding);
            } catch (IllegalArgumentException ignored) {
                // The action may belong to a plugin that is not even installed: a bad shortcut for it
                // must not cost the user every other binding, so only this entry is ignored.
                if (!knownAction(action)) { warning(path, "Use a valid shortcut or none; ignored."); continue; }
                valueError(path, "Use a valid shortcut or none; using all default keybindings.");
                invalid = true;
            }
        }
        // Remove every overridden default first, so valid swaps do not collide with
        // the old binding. The existing engine alone decides shortcut equivalence.
        Map<String, String> staged = new LinkedHashMap<>();
        overrides.keySet().forEach(action -> staged.put(action, "none"));
        for (var entry : overrides.entrySet()) {
            staged.put(entry.getKey(), entry.getValue());
            try {
                KeyBindings.withOverrides(macOs, staged);
            } catch (IllegalArgumentException ignored) {
                valueError(List.of("keybindings", entry.getKey()),
                    "Shortcut conflicts with another action; use unique shortcuts or none. Using all default keybindings.");
                staged.put(entry.getKey(), "none");
                invalid = true;
            }
        }
        if (!invalid) keybindings = Map.copyOf(overrides);
    }

    private static boolean knownAction(String id) {
        for (ActionId action : ActionId.values()) if (action.id().equals(id)) return true;
        return false;
    }

    private static List<String> append(List<String> parent, String key) {
        var path = new ArrayList<>(parent);
        path.add(key);
        return List.copyOf(path);
    }

    private int line(List<String> path) {
        TomlPosition position = toml.inputPositionOf(path);
        return position == null ? 0 : position.line();
    }

    private int column(List<String> path) {
        TomlPosition position = toml.inputPositionOf(path);
        return position == null ? 0 : position.column();
    }

    private void typeError(List<String> path, String expected) {
        rejected = true;
        valueError(path, "Expected " + expected + "; configuration was not applied.");
    }

    private void warning(List<String> path, String message) {
        diagnostic(ConfigDiagnostic.Severity.WARNING, path, message);
    }

    private void valueError(List<String> path, String message) {
        diagnostic(ConfigDiagnostic.Severity.ERROR, path, message);
    }

    private void diagnostic(ConfigDiagnostic.Severity severity, List<String> path, String message) {
        TomlPosition position = toml.inputPositionOf(path);
        diagnostics.add(new ConfigDiagnostic(severity, file,
            position == null ? 0 : position.line(), position == null ? 0 : position.column(),
            Toml.joinKeyPath(path), message));
    }
}
