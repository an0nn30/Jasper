package dev.jasper.app.settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An ordered set of settings: lookup by key or old key, the tables their keys imply, tables that other
 * readers own ({@code keybindings}, say), and short notes the generated template prints under table headers.
 */
public final class SettingsSchema {
    private final List<Setting<?>> settings;
    private final Map<List<String>, Setting<?>> byKey = new HashMap<>();
    private final Map<List<String>, Setting<?>> byAlias = new HashMap<>();
    private final Set<List<String>> tables = new HashSet<>();
    private final Set<List<String>> reserved;
    private final Map<List<String>, String> notes;

    private SettingsSchema(List<Setting<?>> settings, Set<List<String>> reserved, Map<List<String>, String> notes) {
        this.settings = List.copyOf(settings);
        this.reserved = reserved.stream().map(List::copyOf).collect(Collectors.toUnmodifiableSet());
        this.notes = Map.copyOf(notes);
        var paths = new ArrayList<List<String>>();
        for (Setting<?> setting : this.settings) {
            claim(paths, setting.key());
            byKey.put(setting.key(), setting);
            for (List<String> alias : setting.aliases()) {
                claim(paths, alias);
                byAlias.put(alias, setting);
            }
        }
        for (int i = 0; i < paths.size(); i++)
            for (int j = 0; j < paths.size(); j++)
                if (i != j && paths.get(j).size() > paths.get(i).size() && startsWith(paths.get(j), paths.get(i)))
                    throw new IllegalArgumentException(TomlLiterals.path(paths.get(j)) + " lies inside the setting " + TomlLiterals.path(paths.get(i)));
        for (List<String> table : this.reserved)
            for (List<String> path : paths)
                if (startsWith(path, table) || startsWith(table, path))
                    throw new IllegalArgumentException(TomlLiterals.path(path) + " overlaps the reserved table " + TomlLiterals.path(table));
        for (List<String> path : paths)
            for (int length = 1; length < path.size(); length++) tables.add(List.copyOf(path.subList(0, length)));
    }

    private static void claim(List<List<String>> paths, List<String> path) {
        if (paths.contains(path)) throw new IllegalArgumentException("Duplicate setting key: " + TomlLiterals.path(path));
        paths.add(path);
    }

    /**
     * Builds a schema.
     *
     * @param settings every setting, in template order
     * @param reserved tables read by their own readers; no setting may overlap one
     * @param tableNotes plain text printed under a table's header in the generated template
     */
    public static SettingsSchema of(List<Setting<?>> settings, Set<List<String>> reserved, Map<List<String>, String> tableNotes) {
        return new SettingsSchema(settings, reserved, tableNotes);
    }

    public List<Setting<?>> settings() { return settings; }

    /** The setting whose current key is {@code key}. */
    public Optional<Setting<?>> find(List<String> key) { return Optional.ofNullable(byKey.get(key)); }

    /** The setting that reads {@code path} as an old key. */
    public Optional<Setting<?>> aliasOf(List<String> path) { return Optional.ofNullable(byAlias.get(path)); }

    /** Whether {@code path} is a table some key or old key lies inside. */
    public boolean knownTable(List<String> path) { return tables.contains(path); }

    /** Whether {@code path} is a table another reader owns. */
    public boolean reserved(List<String> path) { return reserved.contains(path); }

    public Optional<String> tableNote(List<String> table) { return Optional.ofNullable(notes.get(table)); }

    /** Whether {@code path} equals {@code prefix} or lies below it. */
    public static boolean startsWith(List<String> path, List<String> prefix) {
        return path.size() >= prefix.size() && path.subList(0, prefix.size()).equals(prefix);
    }
}
