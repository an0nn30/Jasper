/**
 * The plugin runtime: descriptors, locked consent state, resolution, per-plugin classloaders, the
 * queued EDT event bus, activities, the service registry and plugin lifetimes. {@link dev.jasper.app.plugins.PluginRuntime}
 * is the only public type and is owned and closed by the application. This is the only application
 * package that may reference {@code dev.jasper.sdk}; everything it needs from other packages arrives
 * as app-native values or JDK functional types.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.contributions, dev.jasper.app.notifications, dev.jasper.app.persistence, dev.jasper.app.platform, dev.jasper.sdk, dev.jasper.sdk.activity, dev.jasper.sdk.events, dev.jasper.sdk.plugin, dev.jasper.sdk.services, dev.jasper.sdk.terminal, dev.jasper.sdk.ui.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.plugins;
