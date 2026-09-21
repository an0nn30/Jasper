package dev.jasper.app.plugins;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.Plugin;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * What the host needs to run one plugin, independent of how it was loaded: production passes a
 * {@link PluginClassLoader} and reflective instantiation, tests pass an in-memory plugin.
 * {@code requires} are hard dependencies; services flow from {@code requires} and {@code optional}.
 */
record HostedPlugin(PluginInfo info, Set<String> requires, Set<String> optional, Set<String> exports,
                    ClassLoader loader, Callable<Plugin> instantiate) {
    HostedPlugin {
        requires = Set.copyOf(requires);
        optional = Set.copyOf(optional);
        exports = Set.copyOf(exports);
    }
}
