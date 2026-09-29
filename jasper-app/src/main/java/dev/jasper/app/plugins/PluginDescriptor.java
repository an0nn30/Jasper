package dev.jasper.app.plugins;

import dev.jasper.sdk.PluginInfo;
import java.util.List;
import java.util.Set;

/** A validated {@code plugin.toml}. */
record PluginDescriptor(String id, String name, Version version, String entry, VersionRange sdk,
                        String description, String vendor, Set<String> capabilities, Set<String> exports,
                        List<Requirement> requires, String icon, String homepage) {
    record Requirement(String id, VersionRange version, boolean optional) { }

    PluginDescriptor {
        capabilities = Set.copyOf(capabilities);
        exports = Set.copyOf(exports);
        requires = List.copyOf(requires);
    }

    PluginInfo info() { return new PluginInfo(id, name, version.toString(), capabilities); }
}
