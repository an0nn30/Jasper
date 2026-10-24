package dev.jasper.app.testsupport;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Plugin folders on disk for presentation tests: one jar with a descriptor, text resources and, from {@link #loadable}, a start class. */
public final class LookFixtures {
    /** A kit file that names only its format and base. */
    public static final String KIT = "kit = 1\nbase = \"default\"\n";

    private LookFixtures() { }

    /** The required descriptor keys of a plugin on SDK 0.21. */
    public static String descriptor(String id, String name) {
        return "id = \"" + id + "\"\nname = \"" + name + "\"\nversion = \"0.1.0\"\nentry = \"" + id + ".Main\"\n"
            + "sdk = \">=0.21.0, <0.22\"\n";
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

    /**
     * As {@link #plugin}, with a {@code Main} class in package {@code id} (the descriptor's entry) whose start writes
     * {@code started} into the plugin's data folder: a provider that loads, so only its look decides its attempt.
     */
    public static Path loadable(Path root, String id, String descriptor, Map<String, String> resources) throws IOException {
        String main = "package " + id + ";\npublic final class Main implements dev.jasper.sdk.plugin.Plugin {\n"
            + "    @Override public void start(dev.jasper.sdk.plugin.PluginContext context) {\n"
            + "        try { java.nio.file.Files.writeString(context.dataDirectory().resolve(\"started\"), \"yes\"); }\n"
            + "        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }\n    }\n}\n";
        Path jar = PluginJars.build(root.resolve(id).resolve("jars"), id + ".jar", descriptor, Map.of(id + ".Main", main), List.of());
        for (var resource : new java.util.TreeMap<>(resources).entrySet()) PluginJars.addResource(jar, resource.getKey(), resource.getValue());
        return root.resolve(id);
    }
}
