/**
 * The Browser plugin: Chromium pages as Jasper items, through the runtime's in-process {@code jcef} module. Depends on
 * the SDK, the JDK, that module, in the {@code store} package only the bundled sqlite-jdbc and, in the {@code logins}
 * package's {@code VaultLogins} only, the Credential Vault's API, an optional plugin's. Only the
 * {@code engine}, {@code page}, {@code links} and {@code dialogs} packages name a Chromium type, so starting the plugin
 * loads none. The {@code api} package, the one the plugin exports, is the Markdown previews' service, which the
 * {@code preview} package implements. History, bookmarks and the {@code permissions} package's site permissions live
 * in the {@code store} package's one SQLite database, which opens at its first use, never at the start; each group of
 * /dev/null items keeps its own in memory, in the {@code item} package's data store for that group.
 * {@link dev.jasper.browser.BrowserPlugin} owns every registration's lifetime.
 */
package dev.jasper.browser;
