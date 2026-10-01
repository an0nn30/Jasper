package dev.jasper.fileserver;

import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import java.nio.file.Files;

/** The File Server plugin: shares local folders with other computers from the File Servers panel. */
public final class FileServerPlugin implements Plugin {
    private FileServerSettings settings;

    public FileServerPlugin() { }

    @Override public void start(PluginContext context) throws Exception {
        Files.createDirectories(context.dataDirectory());
        settings = new FileServerSettings(context.settings());
    }
}
