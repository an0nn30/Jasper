package dev.jasper.app.plugins;

import dev.jasper.sdk.PluginInfo;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/** Pure parsing and validation of {@code plugin.toml}; every failure names the offending key. */
final class DescriptorParser {
    static final Set<String> CAPABILITIES = Set.of("terminal.observe", "terminal.selection",
        "terminal.inject", "terminal.open", "session.provide", "palette.contribute", "files.source", "network.listen",
        "clipboard.read", "terminal.environment", "editor.provide", "presentation.provide", "browser.provide");
    /** The capability that goes with the {@code presentation} key, and only with it. */
    static final String PRESENTATION_PROVIDE = "presentation.provide";
    private static final java.util.regex.Pattern SEGMENT = java.util.regex.Pattern.compile("[a-z0-9_.-]+");
    /** Packages a plugin may neither define nor export. */
    static final List<String> FORBIDDEN_PACKAGES = List.of("dev.jasper.sdk", "dev.jasper.app",
        "dev.jasper.terminal", "dev.jasper.buddy", "java", "javax", "jdk");
    private static final Set<String> KEYS = Set.of("id", "name", "version", "entry", "sdk", "description",
        "vendor", "capabilities", "exports", "requires", "icon", "homepage", "icon_hue", "icon_selected_ink",
        "subcommands", "presentation", "themes", "enabled_by_default");
    private static final System.Logger LOG = System.getLogger(DescriptorParser.class.getName());
    private static final String CLASS_NAME = "[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+";
    private static final String PACKAGE_NAME = "[a-z_][a-z0-9_]*(\\.[a-z_][a-z0-9_]*)*";
    private static final java.util.regex.Pattern SUBCOMMAND = java.util.regex.Pattern.compile("[a-z][a-z0-9-]*");
    /** Names the {@code jasper} command keeps for itself. */
    static final Set<String> RESERVED_SUBCOMMANDS = Set.of("help", "version", "open-url");

    static final class InvalidDescriptor extends Exception {
        private static final long serialVersionUID = 1L;
        InvalidDescriptor(String message) { super(message); }
    }

    private DescriptorParser() { }

    /**
     * Whether the text shows anything: some character that is not white space, a space separator (no-break spaces
     * included), a formatting mark (zero-width characters, bidi controls) or another control character. A plugin's name
     * titles its notification groups, which must show something.
     */
    private static boolean visible(String text) {
        return text.codePoints().anyMatch(point -> !Character.isWhitespace(point) && !Character.isSpaceChar(point)
            && Character.getType(point) != Character.FORMAT && !Character.isISOControl(point));
    }

    static PluginDescriptor parse(String text) throws InvalidDescriptor {
        TomlParseResult toml = Toml.parse(text);
        if (toml.hasErrors()) throw new InvalidDescriptor("plugin.toml is not valid TOML: " + toml.errors().get(0).getMessage());
        for (String key : toml.keySet()) if (!KEYS.contains(key)) throw new InvalidDescriptor("Unknown key: " + key);
        String id = text(toml, "id", true);
        if (!PluginInfo.validId(id)) throw new InvalidDescriptor("id is malformed or reserved: " + id);
        String name = text(toml, "name", true);
        if (!visible(name)) throw new InvalidDescriptor("name must show something: not only spaces, zero-width or bidi characters");
        Version version = version(text(toml, "version", true), "version");
        String entry = text(toml, "entry", true);
        if (!entry.matches(CLASS_NAME)) throw new InvalidDescriptor("entry is not a class name: " + entry);
        VersionRange sdk = range(text(toml, "sdk", true), "sdk");
        Set<String> capabilities = strings(toml, "capabilities");
        for (String capability : capabilities)
            if (!CAPABILITIES.contains(capability)) throw new InvalidDescriptor("capabilities names an unknown capability: " + capability);
        Set<String> exports = strings(toml, "exports");
        for (String exported : exports) {
            if (!exported.matches(PACKAGE_NAME)) throw new InvalidDescriptor("exports is not a package name: " + exported);
            if (forbidden(exported)) throw new InvalidDescriptor("exports names a reserved package: " + exported);
        }
        String presentation = text(toml, "presentation", false);
        if (!presentation.isEmpty()) path(presentation, "presentation");
        boolean provides = capabilities.contains(PRESENTATION_PROVIDE);
        if (!presentation.isEmpty() && !provides) throw new InvalidDescriptor("presentation needs the " + PRESENTATION_PROVIDE + " capability");
        if (provides && presentation.isEmpty()) throw new InvalidDescriptor("capabilities: " + PRESENTATION_PROVIDE + " needs the presentation key");
        return new PluginDescriptor(id, name, version, entry, sdk, text(toml, "description", false),
            text(toml, "vendor", false), capabilities, exports, requires(toml, id),
            icon(text(toml, "icon", false), id), homepage(text(toml, "homepage", false), id), iconHue(toml, id), iconSelectedInk(toml, id), subcommands(toml),
            presentation, themes(toml), flag(toml, "enabled_by_default", true));
    }

    private static dev.jasper.ui.icons.IdentityArtwork.SelectedInk iconSelectedInk(TomlTable toml, String id) throws InvalidDescriptor {
        if (!toml.contains("icon_selected_ink")) return dev.jasper.ui.icons.IdentityArtwork.SelectedInk.AUTHORED;
        String value = text(toml, "icon_selected_ink", false);
        return switch (value) {
            case "authored" -> dev.jasper.ui.icons.IdentityArtwork.SelectedInk.AUTHORED;
            case "foreground" -> dev.jasper.ui.icons.IdentityArtwork.SelectedInk.FOREGROUND;
            default -> {
                LOG.log(System.Logger.Level.WARNING, "Plugin " + id + ": invalid icon_selected_ink; using authored: " + value);
                yield dev.jasper.ui.icons.IdentityArtwork.SelectedInk.AUTHORED;
            }
        };
    }

    private static dev.jasper.ui.model.Nodes.Hue iconHue(TomlTable toml, String id) throws InvalidDescriptor {
        if (!toml.contains("icon_hue")) return dev.jasper.ui.model.Nodes.Hue.NEUTRAL;
        String value = text(toml, "icon_hue", false);
        return switch (value) {
            case "neutral" -> dev.jasper.ui.model.Nodes.Hue.NEUTRAL;
            case "blue" -> dev.jasper.ui.model.Nodes.Hue.BLUE;
            case "green" -> dev.jasper.ui.model.Nodes.Hue.GREEN;
            case "amber" -> dev.jasper.ui.model.Nodes.Hue.AMBER;
            case "purple" -> dev.jasper.ui.model.Nodes.Hue.PURPLE;
            default -> {
                LOG.log(System.Logger.Level.WARNING, "Plugin " + id + ": invalid icon_hue; using neutral: " + value);
                yield dev.jasper.ui.model.Nodes.Hue.NEUTRAL;
            }
        };
    }

    static boolean forbidden(String packageName) {
        for (String prefix : FORBIDDEN_PACKAGES)
            if (packageName.equals(prefix) || packageName.startsWith(prefix + ".")) return true;
        return false;
    }

    /** A path ending in .svg or .png; anything else is dropped, because an icon never stops a plugin loading. */
    private static String icon(String path, String id) {
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        if (path.isEmpty() || lower.endsWith(".svg") || lower.endsWith(".png")) return path;
        LOG.log(System.Logger.Level.WARNING, "Plugin " + id + ": icon must be an .svg or .png file; ignored: " + path);
        return "";
    }

    /** An absolute http or https URI; anything else is dropped with a warning. */
    private static String homepage(String text, String id) {
        if (text.isEmpty()) return "";
        try {
            var uri = new java.net.URI(text);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
            if ((scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null) return text;
        } catch (java.net.URISyntaxException malformed) {
            // Falls through to the warning.
        }
        LOG.log(System.Logger.Level.WARNING, "Plugin " + id + ": homepage must be an http or https address; ignored: " + text);
        return "";
    }

    private static List<PluginDescriptor.Requirement> requires(TomlTable toml, String self) throws InvalidDescriptor {
        Object value = toml.get(List.of("requires"));
        if (value == null) return List.of();
        if (!(value instanceof TomlArray array)) throw new InvalidDescriptor("requires must be an array of tables");
        List<PluginDescriptor.Requirement> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < array.size(); i++) {
            if (!(array.get(i) instanceof TomlTable table)) throw new InvalidDescriptor("requires must be an array of tables");
            for (String key : table.keySet())
                if (!Set.of("id", "version", "optional").contains(key)) throw new InvalidDescriptor("requires has an unknown key: " + key);
            String id = text(table, "id", true);
            if (!PluginInfo.validId(id)) throw new InvalidDescriptor("requires names a malformed id: " + id);
            if (id.equals(self)) throw new InvalidDescriptor("requires names the plugin itself: " + id);
            if (!seen.add(id)) throw new InvalidDescriptor("requires names a plugin twice: " + id);
            Object optional = table.get(List.of("optional"));
            if (optional != null && !(optional instanceof Boolean)) throw new InvalidDescriptor("requires.optional must be a boolean");
            result.add(new PluginDescriptor.Requirement(id, range(text(table, "version", false), "requires.version"),
                Boolean.TRUE.equals(optional)));
        }
        return result;
    }

    /**
     * A jar path as the descriptor and kit files name them: {@code /}-separated segments of lowercase letters, digits,
     * {@code _}, {@code .} and {@code -}, none of them {@code .} or {@code ..}, with no leading or trailing {@code /}.
     */
    static boolean jarPath(String path) {
        if (path.isEmpty() || path.startsWith("/") || path.endsWith("/")) return false;
        for (String segment : path.split("/", -1))
            if (!SEGMENT.matcher(segment).matches() || segment.equals(".") || segment.equals("..")) return false;
        return true;
    }

    private static void path(String path, String key) throws InvalidDescriptor {
        if (!jarPath(path)) throw new InvalidDescriptor(key + ": " + path
            + " must be a jar path of lowercase letters, digits, /, _, . and -, without . or .. segments or a leading /");
    }

    /** Jasper theme files in the plugin's jars, each named after its theme's id. */
    private static List<String> themes(TomlTable toml) throws InvalidDescriptor {
        Object value = toml.get(List.of("themes"));
        if (value == null) return List.of();
        if (!(value instanceof TomlArray array)) throw new InvalidDescriptor("themes must be an array of strings");
        List<String> result = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (int i = 0; i < array.size(); i++) {
            if (!(array.get(i) instanceof String path)) throw new InvalidDescriptor("themes must be an array of strings");
            path(path, "themes");
            String file = path.substring(path.lastIndexOf('/') + 1);
            String id = file.endsWith(".json") ? file.substring(0, file.length() - ".json".length()) : "";
            if (!dev.jasper.ui.theme.JasperTheme.validId(id))
                throw new InvalidDescriptor("themes: " + path + " must name <id>.json, the id lowercase letters, digits and dashes");
            if (!ids.add(id)) throw new InvalidDescriptor("themes names the theme " + id + " twice");
            result.add(path);
        }
        return result;
    }

    private static boolean flag(TomlTable toml, String key, boolean fallback) throws InvalidDescriptor {
        Object value = toml.get(List.of(key));
        if (value == null) return fallback;
        if (!(value instanceof Boolean flag)) throw new InvalidDescriptor(key + " must be a boolean");
        return flag;
    }

    private static List<PluginDescriptor.Subcommand> subcommands(TomlTable toml) throws InvalidDescriptor {
        Object value = toml.get(List.of("subcommands"));
        if (value == null) return List.of();
        if (!(value instanceof TomlArray array)) throw new InvalidDescriptor("subcommands must be an array of tables");
        List<PluginDescriptor.Subcommand> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < array.size(); i++) {
            if (!(array.get(i) instanceof TomlTable table)) throw new InvalidDescriptor("subcommands must be an array of tables");
            for (String key : table.keySet())
                if (!Set.of("name", "summary").contains(key)) throw new InvalidDescriptor("subcommands has an unknown key: " + key);
            String name = text(table, "name", true);
            if (!SUBCOMMAND.matcher(name).matches()) throw new InvalidDescriptor("subcommands.name is not a command name: " + name);
            if (RESERVED_SUBCOMMANDS.contains(name)) throw new InvalidDescriptor("subcommands.name is reserved: " + name);
            if (!seen.add(name)) throw new InvalidDescriptor("subcommands names a command twice: " + name);
            String summary = text(table, "summary", true);
            // jasper --help prints it on one line of daemon/subcommands, whose fields are tab-separated.
            if (!visible(summary) || summary.chars().anyMatch(c -> c == '\t' || c == '\n' || c == '\r'))
                throw new InvalidDescriptor("subcommands.summary must be one visible line: " + name);
            result.add(new PluginDescriptor.Subcommand(name, summary));
        }
        return result;
    }

    private static String text(TomlTable table, String key, boolean required) throws InvalidDescriptor {
        Object value = table.get(List.of(key));
        if (value == null) {
            if (required) throw new InvalidDescriptor("Missing required key: " + key);
            return "";
        }
        if (!(value instanceof String text)) throw new InvalidDescriptor(key + " must be a string");
        return text;
    }

    private static Set<String> strings(TomlTable table, String key) throws InvalidDescriptor {
        Object value = table.get(List.of(key));
        if (value == null) return Set.of();
        if (!(value instanceof TomlArray array)) throw new InvalidDescriptor(key + " must be an array of strings");
        Set<String> result = new LinkedHashSet<>();
        for (int i = 0; i < array.size(); i++) {
            if (!(array.get(i) instanceof String text)) throw new InvalidDescriptor(key + " must be an array of strings");
            result.add(text);
        }
        return result;
    }

    private static Version version(String text, String key) throws InvalidDescriptor {
        try { return Version.parse(text); }
        catch (IllegalArgumentException invalid) { throw new InvalidDescriptor(key + ": " + invalid.getMessage()); }
    }

    private static VersionRange range(String text, String key) throws InvalidDescriptor {
        try { return VersionRange.parse(text); }
        catch (IllegalArgumentException invalid) { throw new InvalidDescriptor(key + ": " + invalid.getMessage()); }
    }
}
