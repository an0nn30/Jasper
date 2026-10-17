package dev.jasper.app.pluginmanager;

import dev.jasper.app.plugins.PluginRuntime;

/** Test access to the Plugins page's banner text from other packages' tests. */
public final class PluginManagerAccess {
    private PluginManagerAccess() { }

    /** The banner an idle, restart-needing snapshot shows. */
    public static String restartText(PluginRuntime.Snapshot snapshot) { return PluginManager.restartText(snapshot); }
}
