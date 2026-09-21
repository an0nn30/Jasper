package dev.jasper.sdk.plugin;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.activity.Activities;
import dev.jasper.sdk.events.Events;
import dev.jasper.sdk.services.Services;
import java.nio.file.Path;
import java.util.concurrent.Executor;

/**
 * Everything the application offers one plugin. Each plugin has its own context, so every
 * registration is attributed to it. After a failed start, and after shutdown, the context is closed:
 * contributing calls throw {@link IllegalStateException}, {@link #background()} rejects tasks and
 * handles already issued do nothing. {@link #log()} keeps working.
 */
public interface PluginContext {
    /**
     * This plugin's verified identity.
     *
     * @return the identity
     */
    PluginInfo plugin();

    /**
     * A logger routed to the application log and named after the plugin.
     *
     * @return the logger
     */
    System.Logger log();

    /**
     * The plugin's private data directory, created on first use. The plugin owns its contents.
     *
     * @return an existing directory
     */
    Path dataDirectory();

    /**
     * The plugin's configuration table.
     *
     * @return the read-only view
     */
    PluginConfig config();

    /**
     * A plugin-scoped executor for slow work. It stops admitting tasks at shutdown; tasks already
     * accepted are interrupted when the shutdown wait elapses.
     *
     * @return the executor
     */
    Executor background();

    /**
     * The event bus.
     *
     * @return the bus as seen by this plugin
     */
    Events events();

    /**
     * Activity publication.
     *
     * @return the activities service
     */
    Activities activities();

    /**
     * The service registry.
     *
     * @return the registry as seen by this plugin
     */
    Services services();
}
