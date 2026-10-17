package dev.jasper.app.plugins;

import dev.jasper.app.presentation.KitParser;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Reads files from a candidate's jars, bounded; runs no plugin code. Any thread. */
final class PluginResources {
    private static final System.Logger LOG = System.getLogger(PluginResources.class.getName());

    private PluginResources() { }

    /** The candidate's jars as a kit file reader: the first jar holding a path wins. */
    static KitParser.Files of(PluginCandidate candidate) {
        return (path, maxBytes) -> {
            for (Path jar : candidate.jars()) {
                try (var file = new JarFile(jar.toFile())) {
                    JarEntry entry = file.getJarEntry(path);
                    if (entry == null || entry.isDirectory()) continue;
                    try (var input = file.getInputStream(entry)) { return Optional.of(input.readNBytes(maxBytes + 1)); }
                } catch (IOException failure) {
                    LOG.log(System.Logger.Level.DEBUG, "Could not read " + path + " from " + jar, failure);
                }
            }
            return Optional.empty();
        };
    }
}
