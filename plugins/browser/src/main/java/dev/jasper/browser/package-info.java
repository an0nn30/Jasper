/**
 * The Browser plugin: Chromium pages as Jasper items, through the runtime's in-process {@code jcef} module. Depends on
 * the SDK, the JDK and that module. Only the {@code engine}, {@code page}, {@code links} and {@code dialogs} packages
 * name a Chromium type, so starting the plugin loads none. {@link dev.jasper.browser.BrowserPlugin} owns every
 * registration's lifetime.
 */
package dev.jasper.browser;
