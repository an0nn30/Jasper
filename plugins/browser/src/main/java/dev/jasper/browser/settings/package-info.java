/**
 * Settings › Tools › Browser: {@link dev.jasper.browser.settings.BrowserSettings} declares the Browser's settings in the
 * plugin's settings file, among them the {@code item} package's search engines, each under its section, reads them as
 * one snapshot, and saves the link choices "Remember my choice" keeps.
 * {@link dev.jasper.browser.settings.BrowserSettingsPanel} is the Browser's own page there: the declared settings by
 * section, and in Privacy &amp; Data Clear Cookies, Clear Website Data and Manage Site Permissions, whose actions and
 * prompts the plugin gives it, and in Ad Blocking the {@code adblock} package's filter list's statistics and Update Now.
 * Depends on the {@code adblock} and {@code item} packages, the SDK and the JDK, and names no Chromium type.
 */
package dev.jasper.browser.settings;
