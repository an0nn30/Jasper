package dev.jasper.enterprise;

import dev.jasper.enterprise.api.Workstation;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;

/**
 * The Enterprise look. Its kit file, fonts and theme are data the host reads from the descriptor before the first window;
 * this code only publishes {@link Workstation}, so its start cannot fail.
 */
public final class EnterprisePlugin implements Plugin {
    /** Created by the runtime. */
    public EnterprisePlugin() { }

    /** Publishes {@link Workstation} and nothing else. */
    @Override public void start(PluginContext context) {
        context.services().publish(Workstation.class, new Workstation() { });
    }
}
