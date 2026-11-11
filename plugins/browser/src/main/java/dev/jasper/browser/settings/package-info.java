/**
 * Settings › Tools › Browser: {@link dev.jasper.browser.settings.BrowserSettings} declares the Browser's settings in the
 * plugin's settings file, among them the {@code item} package's search engines and the Downloads folder, which the
 * {@code downloads} package checks, each under its section, reads them as one snapshot, gives the HTTP proxy as
 * Chromium and the plugin's own fetches take it, and saves the link choices "Remember my choice" keeps.
 * {@link dev.jasper.browser.settings.BrowserSettingsPanel} is the Browser's own page there: the declared settings by
 * section, and in Privacy &amp; Data Clear Cookies, Clear Website Data and Manage Site Permissions, with the Credential
 * Vault Manage Saved Passwords too, whose actions and prompts the plugin gives it, and in Ad Blocking the
 * {@code adblock} package's filter list's statistics and Update Now.
 * Depends on the {@code adblock}, {@code downloads} and {@code item} packages, the SDK and the JDK, and names no Chromium
 * type and no type of the {@code logins} package.
 */
package dev.jasper.browser.settings;
