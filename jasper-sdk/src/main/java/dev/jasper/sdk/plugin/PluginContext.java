package dev.jasper.sdk.plugin;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.activity.Activities;
import dev.jasper.sdk.cli.CommandLine;
import dev.jasper.sdk.events.Events;
import dev.jasper.sdk.services.Services;
import dev.jasper.sdk.settings.PluginSettings;
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
import dev.jasper.sdk.palette.Palette;
import dev.jasper.sdk.files.FileSources;
import dev.jasper.sdk.notifications.Notifications;
import dev.jasper.sdk.ui.Platform;

/**
 * Everything the application offers one plugin. Each plugin has its own context, so every
 * registration is attributed to it. After a failed start, and after shutdown, the context is closed:
 * contributing calls throw {@link IllegalStateException}, {@link #background()} rejects tasks and
 * handles already issued do nothing. {@link #log()} keeps working.
 */
public interface PluginContext {
    /** Plugin-owned declarative view factory. @return the kit */
    dev.jasper.sdk.ui.UiKit kit();
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
     * The plugin's declared settings.
     *
     * @return this plugin's settings
     */
    PluginSettings settings();

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
     * The application's colours, variant, presentation and their change notifications.
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

        /** @return the command palette; registering needs {@code palette.contribute} */
        Palette palette();

    /**
     * Places this plugin adds to Jasper's file chooser.
     *
     * @return the file sources; registering needs {@code files.source}
     * @since 0.10.0
     */
    FileSources fileSources();

    /**
     * The plugin's notifications: its default group and the groups it declares. No capability is needed.
     *
     * @return the notifications service
     * @since 0.14.0
     */
    Notifications notifications();

    /**
     * This plugin's {@code jasper} subcommands, declared under {@code [[subcommands]]} in {@code plugin.toml}. No
     * capability is needed.
     *
     * @return the command line
     * @since 0.17.0
     */
    CommandLine commandLine();

        /** @return desktop integration such as opening a file in the editor */
        Platform platform();
}
