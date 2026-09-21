package dev.jasper.app.plugins;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarFile;

/** Finds plugin directories and reads their descriptors. Opens jars briefly; loads no classes. */
final class PluginDiscovery {
    private static final int MAX_DESCRIPTOR_BYTES = 64 * 1024;

    private PluginDiscovery() { }

    /** Each subdirectory of {@code root} named after its plugin id. A missing root is simply empty. */
    static List<PluginCandidate> scan(Path root, PluginCandidate.Origin origin, List<String> problems) {
        List<PluginCandidate> found = new ArrayList<>();
        if (root == null || !Files.isDirectory(root)) return found;
        List<Path> directories;
        try (var children = Files.list(root)) { directories = children.filter(Files::isDirectory).sorted().toList(); }
        catch (IOException failure) { problems.add(root + ": cannot be listed: " + failure.getMessage()); return found; }
        for (Path directory : directories) {
            Optional<PluginCandidate> candidate = single(directory, origin, problems);
            if (candidate.isEmpty()) continue;
            String name = directory.getFileName().toString();
            if (!candidate.get().id().equals(name)) {
                problems.add(directory + ": directory " + name + " holds plugin " + candidate.get().id() + "; rename it to the plugin id");
                continue;
            }
            found.add(candidate.get());
        }
        return found;
    }

    /** One plugin directory; used directly for {@code --plugin-dir}, where the directory name is free. */
    static Optional<PluginCandidate> single(Path directory, PluginCandidate.Origin origin, List<String> problems) {
        List<Path> jars;
        try (var children = Files.list(directory)) {
            jars = children.filter(path -> path.getFileName().toString().endsWith(".jar") && Files.isRegularFile(path)).sorted().toList();
        } catch (IOException failure) { problems.add(directory + ": cannot be listed: " + failure.getMessage()); return Optional.empty(); }
        String descriptor = null;
        for (Path jar : jars) {
            try (var file = new JarFile(jar.toFile())) {
                var entry = file.getEntry("plugin.toml");
                if (entry == null) continue;
                if (descriptor != null) { problems.add(directory + ": more than one jar has a plugin.toml"); return Optional.empty(); }
                try (var input = file.getInputStream(entry)) {
                    byte[] bytes = input.readNBytes(MAX_DESCRIPTOR_BYTES + 1);
                    if (bytes.length > MAX_DESCRIPTOR_BYTES) { problems.add(directory + ": plugin.toml is too large"); return Optional.empty(); }
                    descriptor = new String(bytes, StandardCharsets.UTF_8);
                }
            } catch (IOException failure) { problems.add(jar + ": cannot be read: " + failure.getMessage()); return Optional.empty(); }
        }
        if (descriptor == null) { problems.add(directory + ": no plugin.toml in any jar"); return Optional.empty(); }
        try { return Optional.of(new PluginCandidate(DescriptorParser.parse(descriptor), directory, jars, origin)); }
        catch (DescriptorParser.InvalidDescriptor invalid) { problems.add(directory + ": " + invalid.getMessage()); return Optional.empty(); }
    }
}
