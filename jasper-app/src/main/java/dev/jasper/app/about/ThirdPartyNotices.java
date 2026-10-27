package dev.jasper.app.about;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

/**
 * Jasper's license and the third-party software it ships, from {@code licenses/notices.toml} beside this class
 * ({@code verifyThirdPartyNotices} keeps it complete). Texts are read when asked for. Pure; any thread.
 */
public final class ThirdPartyNotices {
    private static final String FOLDER = "/dev/jasper/app/about/licenses/";
    /** The SPDX ids the index may use, with the names the Licenses window shows. */
    private static final Map<String, String> LICENSES = Map.of(
        "PolyForm-Noncommercial-1.0.0", "PolyForm Noncommercial 1.0.0",
        "Apache-2.0", "Apache License 2.0",
        "MIT", "MIT License",
        "BSD-2-Clause", "BSD 2-Clause License",
        "BSD-3-Clause", "BSD 3-Clause License",
        "EPL-1.0", "Eclipse Public License 1.0",
        "OFL-1.1", "SIL Open Font License 1.1",
        "GPL-2.0-only WITH Classpath-exception-2.0", "GNU GPL v2 with the Classpath Exception",
        "LicenseRef-Nerd-Fonts", "Nerd Fonts licenses",
        "LicenseRef-JogAmp", "JogAmp licenses (BSD 2-Clause and others)");

    private ThirdPartyNotices() {}

    /**
     * One licensed component.
     *
     * @param name what it is called
     * @param version its version, empty when it has none (bundled artwork)
     * @param license its SPDX id, one this class knows
     * @param homepage where it comes from
     * @param modules the exact {@code group:artifact:version} coordinates it covers; empty for one that is not a jar
     * @param text its license text: a path relative to the licenses folder, or an absolute classpath resource
     * @param notice its NOTICE text, the same way, when it has one
     */
    public record Component(String name, String version, String license, String homepage, List<String> modules, String text,
                            Optional<String> notice) {
        public Component {
            requireText(name, "name");
            Objects.requireNonNull(version, "version");
            if (!LICENSES.containsKey(license)) throw new IllegalArgumentException("Unknown license " + license + " for " + name);
            requireText(homepage, "homepage");
            modules = List.copyOf(modules);
            requireText(text, "text");
            Objects.requireNonNull(notice, "notice");
        }

        /** The license's name as the Licenses window shows it. */
        public String licenseName() { return LICENSES.get(license); }

        /** The name and version, as a list row and the detail heading show them. */
        public String title() { return version.isEmpty() ? name : name + " " + version; }
    }

    /** Jasper itself, at {@code version} (empty when the build knows none). */
    public static Component jasper(String version) {
        return new Component("Jasper", version, "PolyForm-Noncommercial-1.0.0", "https://github.com/an0nn30/Jasper", List.of(),
            "jasper.md", Optional.empty());
    }

    /** Jasper first, then the third-party components by name, ignoring case. */
    public static List<Component> listed(String jasperVersion) {
        var out = new ArrayList<Component>();
        out.add(jasper(jasperVersion));
        out.addAll(thirdParty().stream().sorted(Comparator.comparing(Component::name, String.CASE_INSENSITIVE_ORDER)).toList());
        return List.copyOf(out);
    }

    /**
     * The index's components, in file order.
     *
     * @throws IllegalStateException when the index is missing or malformed
     */
    public static List<Component> thirdParty() { return parse(read("notices.toml")); }

    static List<Component> parse(String toml) {
        var result = Toml.parse(toml);
        if (result.hasErrors()) throw new IllegalStateException("notices.toml: " + result.errors().getFirst());
        TomlArray components = result.getArrayOrEmpty("component");
        var out = new ArrayList<Component>();
        for (int i = 0; i < components.size(); i++) {
            TomlTable table = components.getTable(i);
            TomlArray modules = table.getArrayOrEmpty("modules");
            var coordinates = new ArrayList<String>();
            for (int j = 0; j < modules.size(); j++) coordinates.add(modules.getString(j));
            out.add(new Component(required(table, "name"), table.getString("version", () -> ""), required(table, "license"),
                required(table, "homepage"), coordinates, required(table, "text"), Optional.ofNullable(table.getString("notice"))));
        }
        return List.copyOf(out);
    }

    /**
     * A text the index names.
     *
     * @param path relative to the licenses folder, or an absolute classpath resource
     * @throws IllegalStateException when it is not on the classpath
     */
    public static String read(String path) {
        String resource = path.startsWith("/") ? path : FOLDER + path;
        try (InputStream in = ThirdPartyNotices.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Missing license text " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static String required(TomlTable table, String key) {
        String value = table.getString(key);
        if (value == null) throw new IllegalStateException("notices.toml: a component has no " + key);
        return value;
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
