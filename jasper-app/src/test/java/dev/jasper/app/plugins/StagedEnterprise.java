package dev.jasper.app.plugins;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.Plugin;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/** The staged Enterprise jar started in a real plugin host, so staged consumers find Workstation (spec 14.2). */
final class StagedEnterprise {
    static final PluginInfo INFO = new PluginInfo("dev.jasper.enterprise", "Enterprise", "0.1.0", Set.of(Capabilities.PRESENTATION_PROVIDE));

    private StagedEnterprise() { }

    /** Starts it in {@code host}; the returned loader is the provider of {@code dev.jasper.enterprise.api}, closed by the caller. */
    static PluginClassLoader start(PluginHost host) throws Exception {
        Path staged = Path.of(System.getProperty("jasper.stagedPlugins")).resolve(INFO.id());
        var loader = new PluginClassLoader(INFO.id(), ManagedVaultLoadingTest.jars(staged), StagedEnterprise.class.getClassLoader(), Map.of());
        var plugin = (Plugin) loader.loadClass("dev.jasper.enterprise.EnterprisePlugin").getConstructor().newInstance();
        AppContractTest.onEdt(() -> host.start(new HostedPlugin(INFO, Set.of(), Set.of(), Set.of("dev.jasper.enterprise.api"), loader, () -> plugin)));
        return loader;
    }
}
