package dev.jasper.app.testsupport;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Plugin folders on disk for presentation tests: one jar with a descriptor and text resources, and no classes. */
public final class LookFixtures {
    /** A kit file that names only its format and base. */
    public static final String KIT = "kit = 1\nbase = \"default\"\n";

    private LookFixtures() { }

    /** The required descriptor keys of a plugin on SDK 0.18. */
    public static String descriptor(String id, String name) {
        return "id = \"" + id + "\"\nname = \"" + name + "\"\nversion = \"0.1.0\"\nentry = \"" + id + ".Main\"\n"
            + "sdk = \">=0.18.0, <0.19\"\n";
    }

    /** The lines that make a descriptor a presentation provider with its kit at {@code kitPath}. */
    public static String provider(String kitPath) {
        return "capabilities = [\"presentation.provide\"]\npresentation = \"" + kitPath + "\"\n";
    }

    /** {@code <root>/<id>/jars/<id>.jar} holding {@code descriptor} as plugin.toml and each resource by its jar path. */
    public static Path plugin(Path root, String id, String descriptor, Map<String, String> resources) throws IOException {
        Path jars = root.resolve(id).resolve("jars");
        Path jar = PluginJars.build(jars, id + ".jar", descriptor, Map.of(), List.of());
        for (var resource : new java.util.TreeMap<>(resources).entrySet()) PluginJars.addResource(jar, resource.getKey(), resource.getValue());
        return root.resolve(id);
    }
}
