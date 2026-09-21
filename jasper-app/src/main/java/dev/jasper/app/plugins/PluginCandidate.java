package dev.jasper.app.plugins;

import java.nio.file.Path;
import java.util.List;

/** A plugin found on disk with a valid descriptor; nothing of it has been loaded. */
record PluginCandidate(PluginDescriptor descriptor, Path directory, List<Path> jars, Origin origin) {
    /** Bundled and development plugins are pre-consented; user plugins need the user's review. */
    enum Origin { BUNDLED, USER, DEV }

    PluginCandidate { jars = List.copyOf(jars); }

    String id() { return descriptor.id(); }
}
