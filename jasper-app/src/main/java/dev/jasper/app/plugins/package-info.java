/**
 * The plugin runtime: descriptors, locked consent state, resolution, per-plugin classloaders, the
 * queued EDT event bus, activities, the service registry and plugin lifetimes. {@link dev.jasper.app.plugins.PluginRuntime}
 * is the only public type and is owned and closed by the application. This is the only application
 * package that may reference {@code dev.jasper.sdk}; everything it needs from other packages arrives
 * as app-native values or JDK functional types.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.appearance, dev.jasper.app.commandline, dev.jasper.app.commands, dev.jasper.app.contributions, dev.jasper.app.dock, dev.jasper.app.files, dev.jasper.app.launch, dev.jasper.app.notifications, dev.jasper.app.persistence, dev.jasper.app.platform, dev.jasper.app.presentation, dev.jasper.app.config, dev.jasper.app.settings, dev.jasper.app.lifecycle, dev.jasper.app.settingsui, dev.jasper.app.startup, dev.jasper.app.terminals, dev.jasper.app.windows, dev.jasper.sdk, dev.jasper.sdk.activity, dev.jasper.sdk.cli, dev.jasper.sdk.events, dev.jasper.sdk.files, dev.jasper.sdk.notifications, dev.jasper.sdk.palette, dev.jasper.sdk.plugin, dev.jasper.sdk.services, dev.jasper.sdk.settings, dev.jasper.sdk.terminal, dev.jasper.sdk.ui, dev.jasper.terminal.session.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.plugins;
