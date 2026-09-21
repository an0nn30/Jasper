package dev.jasper.app.testsupport;

import dev.jasper.sdk.plugin.Plugin;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import javax.tools.ToolProvider;

/** Compiles small plugin sources against the SDK and packs them, with an optional descriptor, into a jar. */
public final class PluginJars {
    private PluginJars() { }

    /** A descriptor with the required keys; append further TOML as needed. */
    public static String descriptor(String id, String version, String entry) {
        return "id = \"" + id + "\"\nname = \"" + id + "\"\nversion = \"" + version + "\"\nentry = \"" + entry
            + "\"\nsdk = \">=0.1\"\n";
    }

    /** A source for a plugin whose start and stop do nothing. */
    public static String emptyPlugin(String packageName, String className) {
        return "package " + packageName + ";\npublic final class " + className
            + " implements dev.jasper.sdk.plugin.Plugin {\n    @Override public void start(dev.jasper.sdk.plugin.PluginContext context) { }\n}\n";
    }

    public static Path build(Path pluginDirectory, String jarName, String descriptorOrNull,
                             Map<String, String> sources, List<Path> classpath) throws IOException {
        Path work = Files.createTempDirectory("plugin-fixture");
        Path sourceRoot = Files.createDirectories(work.resolve("src"));
        Path classes = Files.createDirectories(work.resolve("classes"));
        List<Path> files = new ArrayList<>();
        for (var source : sources.entrySet()) {
            Path file = sourceRoot.resolve(source.getKey().replace('.', '/') + ".java");
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue());
            files.add(file);
        }
        if (!files.isEmpty()) {
            var compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) throw new IOException("Tests need a JDK, not a JRE");
            List<String> entries = new ArrayList<>();
            try { entries.add(Path.of(Plugin.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString()); }
            catch (java.net.URISyntaxException impossible) { throw new IOException(impossible); }
            classpath.forEach(path -> entries.add(path.toString()));
            var output = new StringWriter();
            try (var manager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
                boolean compiled = compiler.getTask(output, manager, null,
                    List.of("-d", classes.toString(), "-classpath", String.join(java.io.File.pathSeparator, entries), "-proc:none"),
                    null, manager.getJavaFileObjectsFromPaths(files)).call();
                if (!compiled) throw new IOException("Fixture did not compile:\n" + output);
            }
        }
        Files.createDirectories(pluginDirectory);
        Path jar = pluginDirectory.resolve(jarName);
        // A manifest guarantees at least one entry: a library jar with no classes must still open as a jar.
        var manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        try (var out = new JarOutputStream(Files.newOutputStream(jar), manifest); var walk = Files.walk(classes)) {
            if (descriptorOrNull != null) {
                out.putNextEntry(new JarEntry("plugin.toml"));
                out.write(descriptorOrNull.getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                out.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
                out.write(Files.readAllBytes(file));
                out.closeEntry();
            }
        }
        return jar;
    }
}
