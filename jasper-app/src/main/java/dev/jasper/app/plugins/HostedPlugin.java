package dev.jasper.app.plugins;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.Plugin;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * What the host needs to run one plugin, independent of how it was loaded: production passes a
 * {@link PluginClassLoader} and reflective instantiation, tests pass an in-memory plugin.
 * {@code requires} are hard dependencies; services flow from {@code requires} and {@code optional}.
 * {@code iconHue} is the manifest's {@code icon_hue}, never NONE: a palette scope that names no hue takes it.
 */
record HostedPlugin(PluginInfo info, Set<String> requires, Set<String> optional, Set<String> exports,
                    ClassLoader loader, Callable<Plugin> instantiate, dev.jasper.ui.model.Nodes.Hue iconHue) {
    HostedPlugin {
        requires = Set.copyOf(requires);
        optional = Set.copyOf(optional);
        exports = Set.copyOf(exports);
        iconHue = iconHue == null || iconHue == dev.jasper.ui.model.Nodes.Hue.NONE ? dev.jasper.ui.model.Nodes.Hue.NEUTRAL : iconHue;
    }

    /** A plugin whose manifest names no icon hue: NEUTRAL. */
    HostedPlugin(PluginInfo info, Set<String> requires, Set<String> optional, Set<String> exports,
                 ClassLoader loader, Callable<Plugin> instantiate) {
        this(info, requires, optional, exports, loader, instantiate, dev.jasper.ui.model.Nodes.Hue.NEUTRAL);
    }
}
