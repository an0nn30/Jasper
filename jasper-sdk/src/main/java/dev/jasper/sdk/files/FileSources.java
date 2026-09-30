package dev.jasper.sdk.files;

import dev.jasper.sdk.Subscription;

/**
 * The file chooser's plugin sources. Registering needs {@code files.source}, the UI thread and an open context;
 * implemented by the application and by the testkit.
 *
 * @since 0.10.0
 */
public interface FileSources {
    /**
     * Registers a source; its roots appear in every chooser whose {@link FileChoice#sources()} names it.
     *
     * @param source the source; its spec id starts with the plugin's id and a dot and is unique
     * @return closing removes the source
     */
    Subscription register(FileSource source);
}
