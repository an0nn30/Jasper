/**
 * The Plugins manager: an application-owned window, built through the same auxiliary-window chrome as
 * plugin windows, that lists plugins and drives the runtime's manager operations and the restart
 * conversation. It sees only app-native values. The application owns the {@link dev.jasper.app.pluginmanager.PluginManager};
 * its window closes with the application's auxiliary windows.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.plugins, dev.jasper.app.restart, dev.jasper.app.windows.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.pluginmanager;
