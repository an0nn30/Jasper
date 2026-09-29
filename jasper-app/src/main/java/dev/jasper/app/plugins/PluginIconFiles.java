package dev.jasper.app.plugins;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Reads the icon a plugin's manifest names from its jars, once per plugin version. Runs no plugin code. Any thread. */
final class PluginIconFiles {
    /** The largest icon read, in bytes. */
    static final int MAX_BYTES = 256 * 1024;
    private static final System.Logger LOG = System.getLogger(PluginIconFiles.class.getName());
    private static final Map<String, Optional<PluginRuntime.PluginIcon>> CACHE = new ConcurrentHashMap<>();
    private static final AtomicInteger READS = new AtomicInteger();

    private PluginIconFiles() { }

    /** The candidate's icon; empty when it names none, or the file is missing or larger than {@link #MAX_BYTES}. */
    static Optional<PluginRuntime.PluginIcon> read(PluginCandidate candidate) {
        String name = candidate.descriptor().icon();
        if (name.isEmpty()) return Optional.empty();
        String key = candidate.id() + "@" + candidate.descriptor().version() + "@" + candidate.directory() + "@" + name;
        return CACHE.computeIfAbsent(key, ignored -> load(candidate, name));
    }

    /** Test seam: how many icons have been read from jars. */
    static int reads() { return READS.get(); }

    private static Optional<PluginRuntime.PluginIcon> load(PluginCandidate candidate, String name) {
        READS.incrementAndGet();
        for (Path jar : candidate.jars()) {
            try (var file = new JarFile(jar.toFile())) {
                JarEntry entry = file.getJarEntry(name);
                if (entry == null) continue;
                try (var input = file.getInputStream(entry)) {
                    byte[] bytes = input.readNBytes(MAX_BYTES + 1);
                    if (bytes.length > MAX_BYTES) {
                        LOG.log(System.Logger.Level.DEBUG, "The icon of " + candidate.id() + " is larger than 256 KiB; the default is used");
                        return Optional.empty();
                    }
                    return Optional.of(new PluginRuntime.PluginIcon(name, bytes));
                }
            } catch (IOException failure) {
                LOG.log(System.Logger.Level.DEBUG, "Could not read the icon of " + candidate.id() + " from " + jar, failure);
            }
        }
        LOG.log(System.Logger.Level.DEBUG, "No " + name + " in the jars of " + candidate.id() + "; the default icon is used");
        return Optional.empty();
    }
}
