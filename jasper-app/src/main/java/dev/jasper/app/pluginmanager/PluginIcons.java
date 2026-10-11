package dev.jasper.app.pluginmanager;

import dev.jasper.app.plugins.PluginRuntime;
import dev.jasper.ui.icons.ReceivingArtwork;

/** Declared manifest bytes enter the shared receiver; malformed or missing artwork has a truthful generic fallback. */
final class PluginIcons {
    private PluginIcons() { }
    static String name(ReceivingArtwork receiver, PluginRuntime.Row row) {
        try { return row.icon().map(icon -> receiver.name(icon.artwork())).orElse("plugin"); }
        catch(IllegalArgumentException rejected) { return "plugin"; }
    }
}
