package dev.jasper.app.presentation;

import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.config.DefaultSource;
import dev.jasper.app.settings.Reading;
import dev.jasper.app.settings.Setting;
import dev.jasper.ui.kit.ColorSlots;
import dev.jasper.ui.kit.ControlVariants;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.MetricTokens;
import dev.jasper.ui.metrics.TypeScale;
import dev.jasper.ui.theme.JasperTheme;
import dev.jasper.ui.theme.SemanticColor;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Reads a kit file (spec 5): TOML 1.0, strict and all or nothing, in the style of the plugin descriptor parser. A kit
 * names its format ({@code kit = 1}) and a host catalog ({@code base}), then optionally the chrome points, the control
 * points, the eleven public tokens, its type base and face files, two colour slots and four setting defaults. Every
 * failure is one {@link Invalid} naming the key. Pure: files arrive through {@link Files}.
 */
public final class KitParser {
    /** The largest kit file: 64 KiB. */
    public static final int MAX_KIT_BYTES = 64 * 1024;
    /** The largest font file: 2 MiB. */
    public static final int MAX_FONT_BYTES = 2 * 1024 * 1024;
    private static final Set<String> TOP = Set.of("kit", "base", "chrome", "controls", "metrics", "type", "colors", "defaults");
    private static final Pattern SEGMENT = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern COLOUR = Pattern.compile(
        "#(?:[0-9A-Fa-f]{8}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{3})(?![0-9A-Za-z])|\\b0[xX][0-9A-Fa-f]{6}\\b|\\brgba?\\(");
    private static final Pattern TOKEN = Pattern.compile("\\{([^{}]*)}");
    private static final Set<String> TITLE_TOKENS = Set.of("context", "session", "directory");
    private static final List<String> CHROME = List.of("title_bar", "menu_bar", "window_title", "tool_windows", "editor_tabs",
        "status_bar", "palette", "dialogs", "motion");
    private static final List<String> CONTROLS = List.of("toggle", "exclusive", "tabs", "group", "form_label", "empty", "table",
        "tree", "stepper", "ordered_list", "buttons", "tag", "route", "prompt");

    private static final Map<String, Setting<?>> DEFAULTS = defaultsByKey();

    /** A kit file is invalid; the message names the key, as in {@code [metrics].row_height: 40 is outside 18-28}. */
    public static final class Invalid extends Exception {
        private static final long serialVersionUID = 1L;
        Invalid(String message) { super(message); }
    }

    /** Reads a jar path: empty when absent, else at most {@code maxBytes + 1} bytes, so a caller sees a file that is too large. */
    @FunctionalInterface
    public interface Files {
        /**
         * @param path a jar path
         * @param maxBytes the most bytes the caller accepts
         * @return the file's first {@code maxBytes + 1} bytes, or empty
         */
        Optional<byte[]> read(String path, int maxBytes);
    }

    /**
     * What a kit file is read against.
     *
     * @param id the provider's id, which names the kit in diagnostics
     * @param directory the kit file's folder in the jar, {@code ""} or ending in {@code /}; kit paths resolve against it
     * @param files reads the provider's jars
     * @param themes each theme id the provider declares, with its theme, or empty when its file did not load
     */
    public record Context(String id, String directory, Files files, Map<String, Optional<JasperTheme>> themes) {
        /** Copies the themes. */
        public Context {
            Objects.requireNonNull(id, "id"); Objects.requireNonNull(directory, "directory"); Objects.requireNonNull(files, "files");
            themes = Map.copyOf(themes);
        }
    }

    /**
     * The UI face: one file per weight.
     *
     * @param family the declared family
     * @param regular weight 400
     * @param medium weight 500
     * @param semibold weight 600
     */
    public record UiFaces(String family, FontFile regular, FontFile medium, FontFile semibold) { }

    /**
     * The data face: one file per weight.
     *
     * @param family the declared family
     * @param regular weight 400
     * @param medium weight 500
     */
    public record DataFaces(String family, FontFile regular, FontFile medium) { }

    /**
     * The faces a kit names; a part left out keeps the base's faces.
     *
     * @param ui the UI face files
     * @param data the data face files
     */
    public record Faces(Optional<UiFaces> ui, Optional<DataFaces> data) {
        /** No face files. */
        public static final Faces NONE = new Faces(Optional.empty(), Optional.empty());

        /** Every named file in declared order: the UI face's regular, medium and semibold, then the data face's regular and medium. */
        public List<FontFile> files() {
            var files = new ArrayList<FontFile>();
            ui.ifPresent(face -> files.addAll(List.of(face.regular(), face.medium(), face.semibold())));
            data.ifPresent(face -> files.addAll(List.of(face.regular(), face.medium())));
            return List.copyOf(files);
        }
    }

    /**
     * A valid kit file.
     *
     * @param kit the jasper-ui kit
     * @param chrome the chrome points
     * @param classicBase whether the kit starts from the classic catalog
     * @param defaults the kit's setting defaults
     * @param dropped the {@code [defaults]} keys left out because the plugin theme they name did not load
     * @param faces the declared face files
     */
    public record Parsed(Kit kit, ChromeSpec chrome, boolean classicBase, DefaultSource defaults, List<String> dropped, Faces faces) {
        /** Copies the list. */
        public Parsed { dropped = List.copyOf(dropped); }
    }

    private record TypeAndFaces(TypeScale scale, Faces faces) { }
    private record Defaults(Map<Setting<?>, Object> values, List<String> dropped) { }

    private KitParser() { }

    /**
     * The kit in {@code bytes}.
     *
     * @param bytes the kit file
     * @param context what its paths and theme names refer to
     * @return the parsed kit
     * @throws Invalid with one message naming the key
     */
    public static Parsed parse(byte[] bytes, Context context) throws Invalid {
        Objects.requireNonNull(context, "context");
        try { return read(bytes, context); }
        catch (StackOverflowError deep) {
            // Rulings 13: a file too deep for a reader (tomlj's parser included) is not a kit file; nothing escapes the pre-pass.
            throw new Invalid("kit file: not valid TOML");
        }
    }

    private static Parsed read(byte[] bytes, Context context) throws Invalid {
        if (bytes.length > MAX_KIT_BYTES) throw new Invalid("kit file: larger than 64 KiB");
        String text = utf8(bytes);
        TomlParseResult toml;
        try { toml = Toml.parse(text); }
        catch (RuntimeException unreadable) { throw new Invalid("kit file: not valid TOML"); }
        if (toml.hasErrors()) throw new Invalid("kit file: not valid TOML: " + toml.errors().get(0).getMessage());
        // The top-level keys first, so an unknown table is refused before anything walks its contents (Rulings 13).
        for (String key : toml.keySet())
            if (!TOP.contains(key)) throw new Invalid(toml.get(List.of(key)) instanceof TomlTable ? "[" + key + "]: unknown table" : key + ": unknown key");
        noColours(toml);
        noColours(text);
        Object format = toml.get(List.of("kit"));
        if (format == null) throw new Invalid("kit: missing; a kit file begins with kit = 1");
        if (!(format instanceof Long number)) throw new Invalid("kit: expected an integer");
        if (number != 1) throw new Invalid("kit: " + number + " is not a format this Jasper reads; expected 1");
        Object baseValue = toml.get(List.of("base"));
        if (baseValue == null) throw new Invalid("base: missing; use \"default\" or \"classic\"");
        if (!(baseValue instanceof String base)) throw new Invalid("base: expected a string");
        boolean classic = switch (base) {
            case "default" -> false;
            case "classic" -> true;
            default -> throw new Invalid("base: \"" + base + "\" is not one of default, classic");
        };
        Kit start = classic ? Kit.CLASSIC : Kit.DEFAULT;
        ChromeSpec chrome = chrome(table(toml, "chrome"), classic ? ChromeSpec.CLASSIC : ChromeSpec.DEFAULT);
        ControlVariants controls = controls(table(toml, "controls"), start.controls());
        DesignMetrics metrics = metrics(table(toml, "metrics"), start.metrics());
        TypeAndFaces type = type(table(toml, "type"), start.type(), context);
        ColorSlots colors = colors(table(toml, "colors"), start.colors());
        Defaults defaults = defaults(table(toml, "defaults"), context);
        Kit kit = new Kit(context.id(), metrics, type.scale(), faces(type.faces(), start.faces()), controls, colors, start.uiKeys());
        return new Parsed(kit, chrome, classic, new DefaultSource(defaults.values()), defaults.dropped(), type.faces());
    }

    /** A jar path inside the kit's folder: {@code /}-separated segments of {@code [a-z0-9_.-]}, none {@code .} or {@code ..}. */
    public static boolean path(String path) {
        if (path.isEmpty() || path.startsWith("/") || path.endsWith("/")) return false;
        for (String segment : path.split("/", -1))
            if (!SEGMENT.matcher(segment).matches() || segment.equals(".") || segment.equals("..")) return false;
        return true;
    }

    private static String utf8(byte[] bytes) throws Invalid {
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            return text.startsWith("\uFEFF") ? text.substring(1) : text;
        } catch (CharacterCodingException malformed) { throw new Invalid("kit file: not UTF-8 text"); }
    }

    /** {@code [a.b].c}, {@code [defaults]."ui.theme.dark"} or a top-level {@code kit}. */
    private static String name(List<String> path) {
        String last = path.getLast().contains(".") ? "\"" + path.getLast() + "\"" : path.getLast();
        return path.size() == 1 ? last : "[" + String.join(".", path.subList(0, path.size() - 1)) + "]." + last;
    }

    /** One value on the walk: its parent and its key, so the path is built only for a message. */
    private record Walked(Object value, Walked parent, String key) {
        List<String> path() {
            var path = new java.util.ArrayDeque<String>();
            for (Walked at = this; at != null && at.key() != null; at = at.parent()) path.push(at.key());
            return List.copyOf(path);
        }
    }

    /** Every string value, however deeply nested, with an explicit stack (Rulings 13): a colour literal is named by its key. */
    private static void noColours(TomlTable root) throws Invalid {
        var pending = new java.util.ArrayDeque<Walked>();
        pending.push(new Walked(root, null, null));
        while (!pending.isEmpty()) {
            Walked item = pending.pop();
            if (item.value() instanceof TomlTable table) {
                for (String key : table.keySet()) pending.push(new Walked(table.get(List.of(key)), item, key));
            } else if (item.value() instanceof TomlArray array) {
                for (int i = 0; i < array.size(); i++) pending.push(new Walked(array.get(i), item.parent(), item.key()));
            } else if (item.value() instanceof String text) {
                Matcher match = COLOUR.matcher(text);
                if (match.find())
                    throw new Invalid(name(item.path()) + ": \"" + match.group() + "\" looks like a colour; colours come from the theme");
            }
        }
    }

    /** Comments are not values: a literal there is named by its line. */
    private static void noColours(String text) throws Invalid {
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher match = COLOUR.matcher(lines[i]);
            if (match.find()) throw new Invalid("kit file: line " + (i + 1) + ": \"" + match.group() + "\" looks like a colour; colours come from the theme");
        }
    }

    private static TomlTable table(TomlTable toml, String name) throws Invalid {
        Object value = toml.get(List.of(name));
        if (value == null) return null;
        if (!(value instanceof TomlTable table)) throw new Invalid(name + ": expected a table");
        return table;
    }

    private static void known(TomlTable table, String name, List<String> keys) throws Invalid {
        for (String key : table.keySet()) if (!keys.contains(key)) throw new Invalid("[" + name + "]." + key + ": unknown key");
    }

    private static String string(TomlTable table, String name, String key) throws Invalid {
        Object value = table.get(List.of(key));
        if (value == null) throw new Invalid("[" + name + "]." + key + ": missing");
        if (!(value instanceof String text)) throw new Invalid("[" + name + "]." + key + ": expected a string");
        return text;
    }

    private static <E extends Enum<E>> E point(TomlTable table, String name, String key, Class<E> type, E base) throws Invalid {
        if (table == null || table.get(List.of(key)) == null) return base;
        String text = string(table, name, key);
        for (E constant : type.getEnumConstants()) if (constant.name().toLowerCase(Locale.ROOT).equals(text)) return constant;
        throw new Invalid("[" + name + "]." + key + ": \"" + text + "\" is not one of " + Arrays.stream(type.getEnumConstants())
            .map(constant -> constant.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(", ")));
    }

    private static ChromeSpec chrome(TomlTable table, ChromeSpec base) throws Invalid {
        if (table == null) return base;
        known(table, "chrome", CHROME);
        var titleBar = point(table, "chrome", "title_bar", ChromeSpec.TitleBar.class, base.titleBar());
        var menuBar = point(table, "chrome", "menu_bar", ChromeSpec.MenuBar.class, base.menuBar());
        String title = table.get(List.of("window_title")) == null ? base.windowTitle() : windowTitle(string(table, "chrome", "window_title"));
        var toolWindows = point(table, "chrome", "tool_windows", ChromeSpec.ToolWindows.class, base.toolWindows());
        var editorTabs = point(table, "chrome", "editor_tabs", ChromeSpec.EditorTabs.class, base.editorTabs());
        var statusBar = point(table, "chrome", "status_bar", ChromeSpec.StatusBar.class, base.statusBar());
        var palette = point(table, "chrome", "palette", ChromeSpec.PaletteKind.class, base.palette());
        var dialogs = point(table, "chrome", "dialogs", ChromeSpec.DialogKind.class, base.dialogs());
        var motion = point(table, "chrome", "motion", ChromeSpec.MotionKind.class, base.motion());
        if (menuBar == ChromeSpec.MenuBar.WINDOW && titleBar != ChromeSpec.TitleBar.NATIVE)
            throw new Invalid("[chrome].menu_bar: \"window\" needs [chrome].title_bar = \"native\"");
        return new ChromeSpec(titleBar, menuBar, title, toolWindows, editorTabs, statusBar, palette, dialogs, motion);
    }

    private static String windowTitle(String text) throws Invalid {
        if (text.isBlank() || text.length() > 120) throw new Invalid("[chrome].window_title: expected 1 to 120 characters");
        Matcher token = TOKEN.matcher(text);
        while (token.find())
            if (!TITLE_TOKENS.contains(token.group(1)))
                throw new Invalid("[chrome].window_title: unknown token {" + token.group(1) + "}; use {context}, {session} and {directory}");
        String rest = TOKEN.matcher(text).replaceAll("");
        if (rest.indexOf('{') >= 0 || rest.indexOf('}') >= 0)
            throw new Invalid("[chrome].window_title: a brace that is not part of {context}, {session} or {directory}");
        return text;
    }

    private static ControlVariants controls(TomlTable table, ControlVariants base) throws Invalid {
        if (table == null) return base;
        known(table, "controls", CONTROLS);
        return new ControlVariants(
            point(table, "controls", "toggle", ControlVariants.Toggle.class, base.toggle()),
            point(table, "controls", "exclusive", ControlVariants.Exclusive.class, base.exclusive()),
            point(table, "controls", "tabs", ControlVariants.Tabs.class, base.tabs()),
            point(table, "controls", "group", ControlVariants.Group.class, base.group()),
            point(table, "controls", "form_label", ControlVariants.FormLabel.class, base.formLabel()),
            point(table, "controls", "empty", ControlVariants.Empty.class, base.empty()),
            point(table, "controls", "table", ControlVariants.Table.class, base.table()),
            point(table, "controls", "tree", ControlVariants.Tree.class, base.tree()),
            point(table, "controls", "stepper", ControlVariants.Stepper.class, base.stepper()),
            point(table, "controls", "ordered_list", ControlVariants.OrderedList.class, base.orderedList()),
            point(table, "controls", "buttons", ControlVariants.Buttons.class, base.buttons()),
            point(table, "controls", "tag", ControlVariants.Tag.class, base.tag()),
            point(table, "controls", "route", ControlVariants.Route.class, base.route()),
            point(table, "controls", "prompt", ControlVariants.Prompt.class, base.prompt()));
    }

    private static DesignMetrics metrics(TomlTable table, DesignMetrics base) throws Invalid {
        if (table == null) return base;
        known(table, "metrics", Arrays.stream(MetricTokens.Token.values()).map(MetricTokens.Token::key).toList());
        // The base catalog's own tokens: a key left out keeps the base's value (plan 4a's of reads each token's first role).
        var tokens = MetricTokens.of(base);
        for (var token : MetricTokens.Token.values()) {
            Object value = table.get(List.of(token.key()));
            if (value == null) continue;
            if (!(value instanceof Long number)) throw new Invalid("[metrics]." + token.key() + ": expected an integer");
            if (number != number.intValue())
                throw new Invalid("[metrics]." + token.key() + ": " + number + " is outside " + token.min() + "-" + token.max());
            // MetricTokens owns the ranges and the message, for example "[metrics].row_height: 40 is outside 18-28".
            try { tokens = tokens.with(token, number.intValue()); }
            catch (IllegalArgumentException outside) { throw new Invalid(outside.getMessage()); }
        }
        return base.with(tokens);
    }

    private static TypeAndFaces type(TomlTable table, TypeScale base, Context context) throws Invalid {
        if (table == null) return new TypeAndFaces(base, Faces.NONE);
        known(table, "type", List.of("base", "ui", "data"));
        TypeScale scale = base;
        Object body = table.get(List.of("base"));
        if (body != null) {
            if (!(body instanceof Long number)) throw new Invalid("[type].base: expected an integer");
            if (number < 10 || number > 16) throw new Invalid("[type].base: " + number + " is not a body size from 10 to 16");
            scale = base.withBody(number);
        }
        Optional<UiFaces> ui = Optional.empty();
        Object uiValue = table.get(List.of("ui"));
        if (uiValue != null) {
            if (!(uiValue instanceof TomlTable face)) throw new Invalid("[type].ui: expected a table");
            known(face, "type.ui", List.of("family", "regular", "medium", "semibold"));
            ui = Optional.of(new UiFaces(family(face, "type.ui"), font(face, "type.ui", "regular", context),
                font(face, "type.ui", "medium", context), font(face, "type.ui", "semibold", context)));
        }
        Optional<DataFaces> data = Optional.empty();
        Object dataValue = table.get(List.of("data"));
        if (dataValue != null) {
            if (!(dataValue instanceof TomlTable face)) throw new Invalid("[type].data: expected a table");
            known(face, "type.data", List.of("family", "regular", "medium"));
            data = Optional.of(new DataFaces(family(face, "type.data"), font(face, "type.data", "regular", context),
                font(face, "type.data", "medium", context)));
        }
        return new TypeAndFaces(scale, new Faces(ui, data));
    }

    /** The declared faces by their files' full names over {@code base}'s; a file with unreadable names keeps the platform face. */
    private static dev.jasper.ui.metrics.FaceSet faces(Faces declared, dev.jasper.ui.metrics.FaceSet base) {
        if (declared.ui().isEmpty() && declared.data().isEmpty()) return base;
        var ui = declared.ui();
        var data = declared.data();
        return dev.jasper.ui.metrics.FaceSet.of(ui.map(face -> face.regular().fullName()).orElse(base.uiFace(400)),
            ui.map(face -> face.medium().fullName()).orElse(base.uiFace(500)), ui.map(face -> face.semibold().fullName()).orElse(base.uiFace(600)),
            data.map(face -> face.regular().fullName()).orElse(base.dataRegular()), data.map(face -> face.medium().fullName()).orElse(base.dataMedium()));
    }

    private static String family(TomlTable table, String name) throws Invalid {
        String family = string(table, name, "family");
        if (family.isBlank() || family.length() > 64) throw new Invalid("[" + name + "].family: expected a family name of 1 to 64 characters");
        return family;
    }

    private static FontFile font(TomlTable table, String name, String weight, Context context) throws Invalid {
        String where = "[" + name + "]." + weight;
        String file = string(table, name, weight);
        if (!path(file)) throw new Invalid(where + ": " + file + " must be a lowercase path inside the kit's folder, without . or .. segments");
        if (!file.endsWith(".ttf") && !file.endsWith(".otf")) throw new Invalid(where + ": " + file + " is not a .ttf or .otf file");
        byte[] bytes = context.files().read(context.directory() + file, MAX_FONT_BYTES)
            .orElseThrow(() -> new Invalid(where + ": " + file + " is not in the plugin's jars"));
        if (bytes.length > MAX_FONT_BYTES) throw new Invalid(where + ": " + file + " is larger than 2 MiB");
        return FontFile.read(file, bytes);
    }

    private static ColorSlots colors(TomlTable table, ColorSlots base) throws Invalid {
        if (table == null) return base;
        known(table, "colors", List.of("dialog_surface", "navigation_surface"));
        return new ColorSlots(slot(table, "dialog_surface", List.of("surface.dialog", "surface.app"), base.dialogSurface()),
            slot(table, "navigation_surface", List.of("surface.sidebar", "surface.card"), base.navigationSurface()));
    }

    private static SemanticColor slot(TomlTable table, String key, List<String> allowed, SemanticColor base) throws Invalid {
        if (table.get(List.of(key)) == null) return base;
        String role = string(table, "colors", key);
        if (!allowed.contains(role)) throw new Invalid("[colors]." + key + ": \"" + role + "\" is not one of " + String.join(", ", allowed));
        return SemanticColor.of(role).orElseThrow();
    }

    private static Defaults defaults(TomlTable table, Context context) throws Invalid {
        if (table == null) return new Defaults(Map.of(), List.of());
        var values = new LinkedHashMap<Setting<?>, Object>();
        var dropped = new ArrayList<String>();
        for (String key : table.keySet()) {
            String where = "[defaults].\"" + key + "\"";
            Setting<?> setting = DEFAULTS.get(key);
            if (setting == null) throw new Invalid(where + ": not a setting a look may default; use "
                + DEFAULTS.keySet().stream().map(name -> "\"" + name + "\"").collect(Collectors.joining(", ")).replaceFirst(", ([^,]*)$", " or $1"));
            Reading<?> reading = read(setting, table.get(List.of(key)));
            // The setting reader's "; using ..." promises a fallback; an invalid kit is refused whole instead.
            if (!reading.issues().isEmpty())
                throw new Invalid(where + ": " + reading.issues().getFirst().message().replaceFirst("; using [^;]*\\.$", "."));
            if (setting == CoreSettings.UI_THEME_LIGHT || setting == CoreSettings.UI_THEME_DARK) {
                boolean dark = setting == CoreSettings.UI_THEME_DARK;
                String id = (String) reading.value();
                Optional<JasperTheme> theme;
                var builtIn = JasperTheme.builtIn(id);
                if (builtIn.isPresent()) theme = builtIn;
                else if (context.themes().containsKey(id)) theme = context.themes().get(id);
                else throw new Invalid(where + ": \"" + id + "\" is neither a built-in theme nor one of this plugin's themes");
                if (theme.isEmpty()) { dropped.add(key); continue; }
                if (!theme.get().has(dark)) throw new Invalid(where + ": \"" + id + "\" has no " + (dark ? "Dark" : "Light") + " variant");
            }
            values.put(setting, reading.value());
        }
        return new Defaults(values, dropped);
    }

    private static <T> Reading<T> read(Setting<T> setting, Object raw) { return setting.kind().read(raw, setting.defaultValue()); }

    private static Map<String, Setting<?>> defaultsByKey() {
        var byKey = new LinkedHashMap<String, Setting<?>>();
        for (Setting<?> setting : List.of(CoreSettings.UI_THEME_LIGHT, CoreSettings.UI_THEME_DARK, CoreSettings.PALETTE_VISIBLE_ROWS,
                CoreSettings.WINDOW_TOOLBAR_HEIGHT)) byKey.put(String.join(".", setting.key()), setting);
        return java.util.Collections.unmodifiableMap(byKey);
    }
}
