package dev.jasper.app.plugins;

import dev.jasper.sdk.plugin.Plugin;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarFile;

/** Builds a plugin's classloader after checking what its jars define; instantiates the entry class. */
final class PluginLoader {
    static final class LoadFailure extends Exception {
        private static final long serialVersionUID = 1L;
        LoadFailure(String message, Throwable cause) { super(message, cause); }
    }

    record Loaded(PluginCandidate candidate, PluginClassLoader loader) {
        Plugin instantiate() throws LoadFailure {
            String entry = candidate.descriptor().entry();
            try {
                Class<?> type = Class.forName(entry, true, loader);
                if (!Plugin.class.isAssignableFrom(type)) throw new LoadFailure(entry + " does not implement Plugin", null);
                return (Plugin) type.getConstructor().newInstance();
            } catch (ReflectiveOperationException | LinkageError failure) {
                throw new LoadFailure("Cannot create entry class " + entry + ": " + failure, failure);
            }
        }
    }

    private PluginLoader() { }

    /** {@code loadedById} must already hold every present dependency, hard or optional. */
    static Loaded load(PluginCandidate candidate, Map<String, Loaded> loadedById, ClassLoader sdk) throws LoadFailure {
        Map<String, ClassLoader> imports = new LinkedHashMap<>();
        for (PluginDescriptor.Requirement requirement : candidate.descriptor().requires()) {
            Loaded dependency = loadedById.get(requirement.id());
            if (dependency == null) continue;
            for (String exported : dependency.candidate().descriptor().exports()) imports.put(exported, dependency.loader());
        }
        for (var jar : candidate.jars()) {
            try (var file = new JarFile(jar.toFile())) {
                var entries = file.entries();
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement().getName();
                    int slash = name.lastIndexOf('/');
                    if (!name.endsWith(".class") || slash < 0) continue;
                    String packageName = name.substring(0, slash).replace('/', '.');
                    if (DescriptorParser.forbidden(packageName))
                        throw new LoadFailure(jar.getFileName() + " defines a class in the reserved package " + packageName, null);
                    if (imports.containsKey(packageName))
                        throw new LoadFailure(jar.getFileName() + " defines a class in " + packageName + ", which a dependency exports", null);
                }
            } catch (IOException failure) { throw new LoadFailure("Cannot read " + jar + ": " + failure.getMessage(), failure); }
        }
        return new Loaded(candidate, new PluginClassLoader(candidate.id(), candidate.jars(), sdk, imports));
    }
}
