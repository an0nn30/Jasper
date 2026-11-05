/**
 * The Browser plugin: Chromium pages as Jasper items, through the runtime's in-process {@code jcef} module. Depends on
 * the SDK, the JDK and that module. Only the {@code engine}, {@code page}, {@code links} and {@code dialogs} packages
 * name a Chromium type, so starting the plugin loads none. The {@code api} package, the one the plugin exports, is the
 * Markdown previews' service, which the {@code preview} package implements. {@link dev.jasper.browser.BrowserPlugin} owns
 * every registration's lifetime.
 */
package dev.jasper.browser;
