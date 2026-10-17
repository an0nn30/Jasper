package dev.jasper.app.config;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.settings.LegacyKey;
import dev.jasper.app.settings.Reading;
import dev.jasper.app.settings.SchemaReader;
import dev.jasper.app.settings.SettingValues;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
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
    private static final java.util.Set<List<String>> RETIRED_ANIMATION_TIMINGS = java.util.Set.of(
        List.of("ui", "animations", "tab_open_ms"), List.of("ui", "animations", "tab_close_ms"),
        List.of("ui", "animations", "tab_switch_ms"));
    private static final java.util.regex.Pattern PLUGIN_ID = java.util.regex.Pattern.compile("[a-z][a-z0-9_.-]{0,127}");

    private final Path file;
    private final SchemaReader reader;
    private final boolean macOs;
    private final List<List<String>> deadTables = new ArrayList<>();
    private Map<String, String> keybindings = Map.of();
    private Map<String, Map<String, Object>> plugins = Map.of();

    private final DefaultSource defaults;

    private ConfigLoader(Path file, String text, boolean macOs, DefaultSource defaults) {
        this.file = Objects.requireNonNull(file, "file");
        this.reader = new SchemaReader(CoreSettings.SCHEMA, text, (path, value) -> value);
        this.macOs = macOs;
        this.defaults = Objects.requireNonNull(defaults, "defaults");
    }

    /** The core look's reading of {@code text}. */
    public static Result parse(Path file, String text, boolean macOs) { return parse(file, text, macOs, DefaultSource.CORE); }

    /** {@code text} read with {@code defaults} for the allowlisted keys it does not set. */
    public static Result parse(Path file, String text, boolean macOs, DefaultSource defaults) {
        return new ConfigLoader(file, text, macOs, defaults).parse();
    }

    private Result parse() {
        SchemaReader.Result read = reader.read(this::special);
        List<ConfigDiagnostic> diagnostics = read.problems().stream().map(this::diagnostic).toList();
        if (read.syntaxError())
            return new Result(ConfigSnapshot.defaults(defaults), diagnostics, true, read.values(), List.of(), List.of());
        return new Result(ConfigSnapshot.from(read.values(), defaults, keybindings, plugins), diagnostics, read.rejected(), read.values(),
            read.legacyKeys(), deadTables);
    }

    /** Retired tab tuning keys, the version key and the tables other readers own. */
    private boolean special(List<String> path, Object value) {
        if (RETIRED_ANIMATION_TIMINGS.contains(path)) return true;
        if (path.equals(List.of("themes"))) { readRetiredThemes(path, value); return true; }
        if (path.equals(VERSION_KEY)) { readVersion(path, value); return true; }
        if (!path.equals(KEYBINDINGS) && !path.equals(PLUGINS) && !path.equals(PALETTE_SCOPES)) return false;
        if (!(value instanceof TomlTable table)) { typeError(path, "a table"); return true; }
        if (path.equals(KEYBINDINGS)) readBindings(table);
        else if (path.equals(PLUGINS)) readPlugins(table);
        else readOldScopes(table);
        return true;
    }

    /** Only the removed Marketplace enabled key is ignored; unknown siblings still get ordinary warnings. */
    private void readRetiredThemes(List<String> path, Object value) {
        if (path.equals(List.of("themes", "marketplace", "enabled"))) return;
        if ((path.equals(List.of("themes")) || path.equals(List.of("themes", "marketplace"))) && value instanceof TomlTable table) {
            for (String key : table.keySet()) {
                var child = new java.util.ArrayList<>(path); child.add(key);
                readRetiredThemes(List.copyOf(child), table.get(List.of(key)));
            }
        } else reader.warning(path, "Unknown setting or table; ignored.");
    }

    private ConfigDiagnostic diagnostic(SchemaReader.Problem problem) {
        return new ConfigDiagnostic(problem.severity() == Reading.Severity.WARNING ? ConfigDiagnostic.Severity.WARNING
            : ConfigDiagnostic.Severity.ERROR, file, problem.line(), problem.column(), problem.key(), problem.message());
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

    private void typeError(List<String> path, String expected) { reader.typeError(path, expected); }

    private void warning(List<String> path, String message) { reader.warning(path, message); }

    private void valueError(List<String> path, String message) { reader.error(path, message); }
}
