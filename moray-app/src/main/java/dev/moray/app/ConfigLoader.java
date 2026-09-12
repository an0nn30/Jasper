package dev.moray.app;

import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlPosition;
import org.tomlj.TomlTable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Pure TOML parsing and validation, with no file or Swing operations. */
final class ConfigLoader {
    record Result(ConfigSnapshot snapshot, List<ConfigDiagnostic> diagnostics, boolean rejected) {
        Result {
            Objects.requireNonNull(snapshot, "snapshot");
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private static final Map<String, Set<String>> FIELDS = Map.of(
        "window", Set.of("tab_height", "toolbar", "status_bar"),
        "font", Set.of("size"), "colors", Set.of("theme"));

    private final Path file;
    private final TomlParseResult toml;
    private final boolean macOs;
    private final List<ConfigDiagnostic> diagnostics = new ArrayList<>();
    private boolean rejected;
    private int tabHeight = 38;
    private WindowContent.ToolbarMode toolbar = WindowContent.ToolbarMode.ICONS_AND_LABELS;
    private boolean statusBar = true;
    private float fontSize = 16f;
    private BuiltinTheme theme = BuiltinTheme.DARK;
    private Map<String, String> keybindings = Map.of();

    private ConfigLoader(Path file, String text, boolean macOs) {
        this.file = Objects.requireNonNull(file, "file");
        this.toml = Toml.parse(text);
        this.macOs = macOs;
    }

    static Result parse(Path file, String text, boolean macOs) {
        return new ConfigLoader(file, text, macOs).parse();
    }

    private Result parse() {
        if (toml.hasErrors()) {
            toml.errors().forEach(error -> diagnostics.add(new ConfigDiagnostic(
                ConfigDiagnostic.Severity.ERROR, file, error.position().line(), error.position().column(), "",
                "Invalid TOML syntax or duplicate definition; check this location.")));
            return new Result(ConfigSnapshot.defaults(), diagnostics, true);
        }
        for (String section : toml.keySet()) {
            List<String> path = List.of(section);
            boolean bindings = section.equals("keybindings");
            if (!bindings && !FIELDS.containsKey(section)) {
                warning(path, "Unknown setting or table; ignored.");
                continue;
            }
            Object value = toml.get(path);
            if (!(value instanceof TomlTable table)) {
                typeError(path, "a table");
                continue;
            }
            if (bindings) {
                readBindings(table);
            } else {
                for (String key : table.keySet()) {
                    List<String> field = List.of(section, key);
                    if (!FIELDS.get(section).contains(key)) warning(field, "Unknown setting; ignored.");
                    else readField(section + "." + key, field, table.get(List.of(key)));
                }
            }
        }
        diagnostics.sort(Comparator.comparingInt(ConfigDiagnostic::line).thenComparingInt(ConfigDiagnostic::column));
        var snapshot = new ConfigSnapshot(tabHeight, toolbar, statusBar, fontSize, theme, keybindings);
        return new Result(snapshot, diagnostics, rejected);
    }

    private void readField(String name, List<String> path, Object value) {
        switch (name) {
            case "window.tab_height" -> {
                if (!(value instanceof Long height)) typeError(path, "an integer");
                else if (height < 28 || height > 72) valueError(path, "Use an integer from 28–72; using the default.");
                else tabHeight = height.intValue();
            }
            case "window.toolbar" -> {
                if (!(value instanceof String mode)) typeError(path, "a string");
                else switch (mode) {
                    case "icons_and_labels" -> toolbar = WindowContent.ToolbarMode.ICONS_AND_LABELS;
                    case "icons" -> toolbar = WindowContent.ToolbarMode.ICONS;
                    case "hidden" -> toolbar = WindowContent.ToolbarMode.HIDDEN;
                    default -> valueError(path, "Use icons_and_labels, icons, or hidden; using the default.");
                }
            }
            case "window.status_bar" -> {
                if (!(value instanceof Boolean visible)) typeError(path, "a boolean");
                else statusBar = visible;
            }
            case "font.size" -> {
                if (!(value instanceof Long) && !(value instanceof Double)) typeError(path, "a number");
                else {
                    double size = ((Number) value).doubleValue();
                    if (!Double.isFinite(size) || size < 6 || size > 72) {
                        valueError(path, "Use a finite number from 6–72; using the default.");
                    } else fontSize = (float) size;
                }
            }
            case "colors.theme" -> {
                if (!(value instanceof String id)) typeError(path, "a string");
                else switch (id) {
                    case "moray-dark" -> theme = BuiltinTheme.DARK;
                    case "moray-light" -> theme = BuiltinTheme.LIGHT;
                    default -> valueError(path, "Use moray-dark or moray-light; using the default.");
                }
            }
            default -> throw new IllegalStateException("Unrecognized validated field.");
        }
    }

    private void readBindings(TomlTable table) {
        Map<String, String> overrides = new LinkedHashMap<>();
        boolean invalid = false;
        for (String action : table.keySet()) {
            List<String> path = List.of("keybindings", action);
            if (!knownAction(action)) {
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
