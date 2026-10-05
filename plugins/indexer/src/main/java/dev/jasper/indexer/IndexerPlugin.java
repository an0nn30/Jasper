package dev.jasper.indexer;

import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;

/** Project Index: offers to index projects terminals enter and finds their files from the palette. */
public final class IndexerPlugin implements Plugin {
    /** The plugin id. */
    public static final String ID = "dev.jasper.indexer";

    /** Created by the runtime. */
    public IndexerPlugin() { }

    @Override public void start(PluginContext context) { }
}
