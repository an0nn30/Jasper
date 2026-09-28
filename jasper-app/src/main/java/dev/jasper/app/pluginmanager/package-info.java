/**
 * The Settings dialog's Plugins page: the plugin list, consent and confirm dialogs over Settings, and an EDT controller
 * that drives the runtime's manager operations and the restart conversation. It sees only app-native values. The
 * application owns the {@link dev.jasper.app.pluginmanager.PluginManager}; SettingsDialog owns each PluginsPage.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.plugins, dev.jasper.app.restart, dev.jasper.app.settingsui,
 * dev.jasper.app.windows.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.pluginmanager;
