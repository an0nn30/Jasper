package dev.jasper.sdk.plugin;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.activity.Activities;
import dev.jasper.sdk.events.Events;
import dev.jasper.sdk.services.Services;
import dev.jasper.sdk.ui.Actions;
import dev.jasper.sdk.ui.Appearance;
import dev.jasper.sdk.ui.Menus;
import dev.jasper.sdk.ui.Panels;
import dev.jasper.sdk.ui.Rail;
import dev.jasper.sdk.ui.StatusBar;
import dev.jasper.sdk.ui.Toolbar;
import dev.jasper.sdk.ui.Windows;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import dev.jasper.sdk.terminal.Terminals;

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

    /**
     * Action registration.
     *
     * @return the actions service as seen by this plugin
     */
    Actions actions();

    /**
     * The main toolbar's plugin section.
     *
     * @return the toolbar service
     */
    Toolbar toolbar();

    /**
     * Menu bar and terminal context menu contributions.
     *
     * @return the menus service
     */
    Menus menus();

    /**
     * Status bar items.
     *
     * @return the status bar service
     */
    StatusBar statusBar();

    /**
     * The application's look, change notifications and theme-aware icons.
     *
     * @return the appearance service
     */
    Appearance appearance();

    /**
     * Side and bottom panels.
     *
     * @return the panels service
     */
    Panels panels();

    /**
     * Rail action buttons.
     *
     * @return the rail service
     */
    Rail rail();

    /**
     * Application-built windows and dialogs.
     *
     * @return the windows service
     */
    Windows windows();

    /**
     * The terminal windows, tabs and panes. Finding them needs no capability; what a handle may do does.
     *
     * @return the terminals service
     */
    Terminals terminals();
}
