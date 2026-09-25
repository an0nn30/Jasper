package dev.jasper.app.config;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.PaletteSettings;
import dev.jasper.app.config.ToolbarMode;

import dev.jasper.terminal.config.BellMode;
import dev.jasper.terminal.config.CursorStyle;
import dev.jasper.terminal.config.OptionAsMeta;
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
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/** Pure TOML parsing and validation, with no file or Swing operations. */
public final class ConfigLoader {
    public record Result(ConfigSnapshot snapshot, List<ConfigDiagnostic> diagnostics, boolean rejected) {
        public Result {
            Objects.requireNonNull(snapshot, "snapshot");
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private static final Map<List<String>, Set<String>> FIELDS = Map.ofEntries(
        Map.entry(List.of(), Set.of("window", "font", "ui", "keybindings", "terminal", "buddy", "palette",
            "notifications", "background", "plugins")),
        Map.entry(List.of("background"), Set.of("enabled")),
        Map.entry(List.of("buddy"), Set.of("enabled")),
        Map.entry(List.of("notifications"), Set.of("long_command_seconds")),
        Map.entry(List.of("palette"), Set.of("max_results", "scopes")),
        // palette.scopes once held the History scope's settings; the table is kept known so the
        // moved-to-a-plugin report below replaces the generic unknown-key warning.
        Map.entry(List.of("palette", "scopes"), Set.of()),
        Map.entry(List.of("window"), Set.of("tab_height", "toolbar", "status_bar", "columns", "lines")),
        Map.entry(List.of("font"), Set.of("family", "size", "fallback", "ligatures", "line_height")),
        Map.entry(List.of("ui"), Set.of("theme", "font")),
        Map.entry(List.of("ui", "font"), Set.of("family", "size")),
        Map.entry(List.of("ui", "theme"), Set.of("variant", "style", "terminal")),
        Map.entry(List.of("terminal"), Set.of("shell", "env", "scrollback", "option_as_meta", "cursor",
            "dim_inactive_panes", "copy_on_select", "bell", "on_exit", "shell_integration")),
        Map.entry(List.of("terminal", "shell"), Set.of("program", "args")),
        Map.entry(List.of("terminal", "cursor"), Set.of("shape", "blink")));

    private final Path file;
    private final TomlParseResult toml;
    private final boolean macOs;
    private final List<ConfigDiagnostic> diagnostics = new ArrayList<>();
    private boolean rejected;
    private int tabHeight = 38;
    private ToolbarMode toolbar = ToolbarMode.ICONS_AND_LABELS;
    private boolean statusBar = true;
    private boolean buddyEnabled = true;
    private boolean backgroundEnabled;
    private int longCommandSeconds = 10;
    private int maxResults = PaletteSettings.DEFAULT_MAX_RESULTS;
    private int columns = 150;
    private int lines = 45;
    private String fontFamily = FontConfig.defaults().family();
    private float fontSize = 16f;
    private String uiFontFamily = UiFontConfig.defaults().family();
    private float uiFontSize;
    private List<String> fallback = FontConfig.defaults().fallback();
    private boolean ligatures = true;
    private float lineHeight = 1f;
    private String program = "";
    private List<String> args = List.of();
    private Map<String, String> env = Map.of();
    private int scrollback = 10_000;
    private OptionAsMeta optionAsMeta = OptionAsMeta.LEFT;
    private CursorStyle cursorShape = CursorStyle.BLOCK;
    private boolean cursorBlink = true;
    private float dimInactivePanes = .3f;
    private boolean copyOnSelect;
    private BellMode bell = BellMode.VISUAL;
    private ShellExitBehavior onExit = ShellExitBehavior.KEEP_OPEN;
    private ShellIntegrationMode shellIntegration = ShellIntegrationMode.AUTO;
    private Appearance variant = Appearance.DARK;
    private ThemeStyle style = ThemeStyle.MODERN;
    private TerminalColors terminalColors = TerminalColors.MATCH;
    private Map<String, String> keybindings = Map.of();
    private Map<String, Map<String, Object>> plugins = Map.of();
    private static final java.util.regex.Pattern PLUGIN_ID = java.util.regex.Pattern.compile("[a-z][a-z0-9_.-]{0,127}");

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
            return new Result(ConfigSnapshot.defaults(), diagnostics, true);
        }
        readTable(List.of(), toml);
        diagnostics.sort(Comparator.comparingInt(ConfigDiagnostic::line).thenComparingInt(ConfigDiagnostic::column));
        var snapshot = new ConfigSnapshot(tabHeight, toolbar, statusBar,
            new FontConfig(fontFamily, fontSize, fallback, ligatures, lineHeight), variant, keybindings, columns, lines,
            new TerminalConfig(new TerminalConfig.Shell(program, args), env, scrollback, optionAsMeta,
                cursorShape, cursorBlink, dimInactivePanes, copyOnSelect, bell, onExit, shellIntegration),
                buddyEnabled, maxResults, longCommandSeconds, backgroundEnabled, plugins, style, new UiFontConfig(uiFontFamily, uiFontSize), terminalColors);
        return new Result(snapshot, diagnostics, rejected);
    }

    private void readTable(List<String> parent, TomlTable table) {
        for (String key : table.keySet()) {
            var path = new ArrayList<>(parent);
            path.add(key);
            if (!FIELDS.get(parent).contains(key)) {
                if (parent.equals(List.of("palette", "scopes")) && key.equals("history"))
                    warning(path, "History moved to the Shell History plugin: use [plugins.\"dev.jasper.history\"] with "
                        + "trivial_commands and deprioritize_trivial, or disable the plugin in Manage Plugins.");
                else warning(path, "Unknown setting or table; ignored.");
                continue;
            }
            Object value = table.get(List.of(key));
            boolean bindings = path.equals(List.of("keybindings"));
            boolean environment = path.equals(List.of("terminal", "env"));
            boolean pluginTables = path.equals(List.of("plugins"));
            if (FIELDS.containsKey(path) || bindings || environment || pluginTables) {
                if (!(value instanceof TomlTable nested)) typeError(path, "a table");
                else if (bindings) readBindings(nested);
                else if (environment) readEnvironment(nested);
                else if (pluginTables) readPlugins(nested);
                else readTable(path, nested);
            } else {
                // Only schema-validated components reach this dispatch. Quoted dotted
                // keys stay single components throughout traversal and positioning.
                readField(String.join(".", path), path, value);
            }
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

    private void readField(String name, List<String> path, Object value) {
        switch (name) {
            case "window.tab_height" -> tabHeight = integer(path, value, 28, 72, tabHeight);
            case "window.columns" -> columns = integer(path, value, 5, 500, columns);
            case "window.lines" -> lines = integer(path, value, 2, 200, lines);
            case "window.toolbar" -> toolbar = choice(path, value, Map.of(
                "icons_and_labels", ToolbarMode.ICONS_AND_LABELS,
                "icons", ToolbarMode.ICONS, "hidden", ToolbarMode.HIDDEN), toolbar);
            case "window.status_bar" -> statusBar = bool(path, value, statusBar);
            case "buddy.enabled" -> buddyEnabled = bool(path, value, buddyEnabled);
            case "background.enabled" -> backgroundEnabled = bool(path, value, backgroundEnabled);
            case "notifications.long_command_seconds" ->
                longCommandSeconds = integer(path, value, 0, 3600, longCommandSeconds);
            case "palette.max_results" -> maxResults = integer(path, value, PaletteSettings.MIN_MAX_RESULTS, PaletteSettings.MAX_MAX_RESULTS, maxResults);
            case "ui.font.family" -> uiFontFamily = string(path, value, ConfigLoader::fontName,
                "Use a nonblank font name without NUL; using the default.", uiFontFamily);
            case "ui.font.size" -> uiFontSize = number(path, value, 8, 32, uiFontSize);
            case "font.family" -> fontFamily = string(path, value, ConfigLoader::fontName,
                "Use a nonblank font name without NUL; using the default.", fontFamily);
            case "font.size" -> fontSize = number(path, value, 6, 72, fontSize);
            case "font.fallback" -> fallback = strings(path, value, ConfigLoader::fontName, fallback);
            case "font.ligatures" -> ligatures = bool(path, value, ligatures);
            case "font.line_height" -> lineHeight = number(path, value, .5, 3, lineHeight);
            case "ui.theme.style" -> style = choice(path, value, Map.of(
    "modern", ThemeStyle.MODERN, "retro", ThemeStyle.RETRO), style);
            case "ui.theme.variant" -> variant = choice(path, value, Map.of(
                "light", Appearance.LIGHT, "dark", Appearance.DARK), variant);
            case "ui.theme.terminal" -> terminalColors = choice(path, value, Map.of(
                "match", TerminalColors.MATCH, "light", TerminalColors.LIGHT, "dark", TerminalColors.DARK), terminalColors);
            case "terminal.shell.program" -> program = string(path, value,
                text -> (text.isEmpty() || !text.isBlank()) && noNul(text),
                "Use an empty or nonblank executable name without NUL; using the default.", program);
            case "terminal.shell.args" -> args = strings(path, value, ConfigLoader::noNul, args);
            case "terminal.scrollback" -> scrollback = integer(path, value, 0, 1_000_000, scrollback);
            case "terminal.option_as_meta" -> optionAsMeta = choice(path, value, Map.of(
                "left", OptionAsMeta.LEFT, "right", OptionAsMeta.RIGHT, "both", OptionAsMeta.BOTH, "none", OptionAsMeta.NONE), optionAsMeta);
            case "terminal.cursor.shape" -> cursorShape = choice(path, value, Map.of(
                "block", CursorStyle.BLOCK, "beam", CursorStyle.BEAM, "underline", CursorStyle.UNDERLINE), cursorShape);
            case "terminal.cursor.blink" -> cursorBlink = bool(path, value, cursorBlink);
            case "terminal.dim_inactive_panes" -> dimInactivePanes = number(path, value, 0, 1, dimInactivePanes);
            case "terminal.copy_on_select" -> copyOnSelect = bool(path, value, copyOnSelect);
            case "terminal.bell" -> bell = choice(path, value,
                Map.of("visual", BellMode.VISUAL, "sound", BellMode.SOUND, "none", BellMode.NONE), bell);
            case "terminal.on_exit" -> onExit = choice(path, value, Map.of(
                "keep_open", ShellExitBehavior.KEEP_OPEN, "close_on_success", ShellExitBehavior.CLOSE_ON_SUCCESS,
                "close", ShellExitBehavior.CLOSE), onExit);
            case "terminal.shell_integration" -> shellIntegration = choice(path, value, Map.of(
                "auto", ShellIntegrationMode.AUTO, "manual", ShellIntegrationMode.MANUAL, "off", ShellIntegrationMode.OFF),
                shellIntegration);
            default -> throw new IllegalStateException("Unrecognized validated field.");
        }
    }

    private int integer(List<String> path, Object value, int min, int max, int defaultValue) {
        if (!(value instanceof Long number)) typeError(path, "an integer");
        else if (number < min || number > max) valueError(path, "Use an integer from " + min + "–" + max + "; using the default.");
        else return number.intValue();
        return defaultValue;
    }

    private float number(List<String> path, Object value, double min, double max, float defaultValue) {
        if (!(value instanceof Long) && !(value instanceof Double)) typeError(path, "a number");
        else {
            double number = ((Number) value).doubleValue();
            if (!Double.isFinite(number) || number < min || number > max) {
                valueError(path, "Use a finite number from " + bound(min) + "–" + bound(max) + "; using the default.");
            } else return (float) number;
        }
        return defaultValue;
    }

    /** Whole bounds print without a fraction, so existing messages read "6–72". */
    private static String bound(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    private boolean bool(List<String> path, Object value, boolean defaultValue) {
        if (value instanceof Boolean result) return result;
        typeError(path, "a boolean");
        return defaultValue;
    }

    private String string(List<String> path, Object value, Predicate<String> valid, String message, String defaultValue) {
        if (!(value instanceof String text)) typeError(path, "a string");
        else if (!valid.test(text)) valueError(path, message);
        else return text;
        return defaultValue;
    }

    private <T> T choice(List<String> path, Object value, Map<String, T> choices, T defaultValue) {
        if (!(value instanceof String text)) typeError(path, "a string");
        else if (!choices.containsKey(text)) {
            valueError(path, "Use " + String.join(", ", choices.keySet().stream().sorted().toList()) + "; using the default.");
        } else return choices.get(text);
        return defaultValue;
    }

    private List<String> strings(List<String> path, Object value, Predicate<String> valid, List<String> defaultValue) {
        if (!(value instanceof TomlArray array)) {
            typeError(path, "an array of strings");
            return defaultValue;
        }
        var values = new ArrayList<String>();
        boolean invalid = false;
        for (int index = 0; index < array.size(); index++) {
            Object element = array.get(index);
            if (!(element instanceof String text)) {
                typeError(path, "an array of strings");
                invalid = true;
            } else if (!valid.test(text)) {
                valueError(path, "Invalid string array element; using the default list.");
                invalid = true;
            } else values.add(text);
        }
        return invalid ? defaultValue : List.copyOf(values);
    }

    private void readEnvironment(TomlTable table) {
        var values = new LinkedHashMap<String, String>();
        for (String name : table.keySet()) {
            List<String> path = List.of("terminal", "env", name);
            Object value = table.get(List.of(name));
            if (!(value instanceof String text)) typeError(path, "a string environment value");
            else if (!TerminalConfig.validEnvName(name)) valueError(path, "Use a portable environment name; entry omitted.");
            else if (TerminalConfig.reservedEnvName(name)) warning(path, "Jasper sets this environment variable; entry omitted.");
            else if (!noNul(text)) valueError(path, "Environment values must contain no NUL; entry omitted.");
            else values.put(name, text);
        }
        env = Map.copyOf(values);
    }

    /** Only a command's first word is ever compared, so an entry with whitespace could never match. */
    private static boolean noNul(String text) {
        return text.indexOf('\0') < 0;
    }

    private static boolean fontName(String text) {
        return !text.isBlank() && noNul(text);
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
