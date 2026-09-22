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
        "terminal.inject", "terminal.open", "session.provide", "palette.contribute");
    /** Packages a plugin may neither define nor export. */
    static final List<String> FORBIDDEN_PACKAGES = List.of("dev.jasper.sdk", "dev.jasper.app",
        "dev.jasper.terminal", "dev.jasper.buddy", "java", "javax", "jdk");
    private static final Set<String> KEYS = Set.of("id", "name", "version", "entry", "sdk", "description",
        "vendor", "capabilities", "exports", "requires");
    private static final String CLASS_NAME = "[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+";
    private static final String PACKAGE_NAME = "[a-z_][a-z0-9_]*(\\.[a-z_][a-z0-9_]*)*";

    static final class InvalidDescriptor extends Exception {
        private static final long serialVersionUID = 1L;
        InvalidDescriptor(String message) { super(message); }
    }

    private DescriptorParser() { }

    static PluginDescriptor parse(String text) throws InvalidDescriptor {
        TomlParseResult toml = Toml.parse(text);
        if (toml.hasErrors()) throw new InvalidDescriptor("plugin.toml is not valid TOML: " + toml.errors().get(0).getMessage());
        for (String key : toml.keySet()) if (!KEYS.contains(key)) throw new InvalidDescriptor("Unknown key: " + key);
        String id = text(toml, "id", true);
        if (!PluginInfo.validId(id)) throw new InvalidDescriptor("id is malformed or reserved: " + id);
        String name = text(toml, "name", true);
        if (name.isBlank()) throw new InvalidDescriptor("name must not be blank");
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
        return new PluginDescriptor(id, name, version, entry, sdk, text(toml, "description", false),
            text(toml, "vendor", false), capabilities, exports, requires(toml, id));
    }

    static boolean forbidden(String packageName) {
        for (String prefix : FORBIDDEN_PACKAGES)
            if (packageName.equals(prefix) || packageName.startsWith(prefix + ".")) return true;
        return false;
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
