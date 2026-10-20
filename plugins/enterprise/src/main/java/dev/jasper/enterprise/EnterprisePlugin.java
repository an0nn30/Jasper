package dev.jasper.enterprise;

import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;

/**
 * The Enterprise look. Its kit file, fonts and theme are data the host reads from the descriptor before the first window;
 * its start does nothing, so it cannot fail.
 */
public final class EnterprisePlugin implements Plugin {
    /** Created by the runtime. */
    public EnterprisePlugin() { }

    /** Does nothing: the look is the descriptor's data. */
    @Override public void start(PluginContext context) { }
}
