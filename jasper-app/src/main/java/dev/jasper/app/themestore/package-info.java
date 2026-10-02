/**
 * Settings › Themes › IntelliJ Themes: the theme plugins in the themes folder beside config.toml, installing them from
 * disk, and browsing, installing and updating them from the JetBrains Marketplace, which makes this Jasper's only network
 * client. Nothing contacts the Marketplace unless that page asks, and themes.marketplace.enabled turns it off. The
 * application owns the {@link dev.jasper.app.themestore.ThemeStore} (its worker and HTTP client) and closes it at
 * shutdown; SettingsDialog owns each page. Swing work is EDT only; file and network work runs on the store's worker.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.themeimport, dev.jasper.app.appearance, dev.jasper.app.config, dev.jasper.app.lifecycle,
 * dev.jasper.app.settings, dev.jasper.app.settingsui.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.themestore;
