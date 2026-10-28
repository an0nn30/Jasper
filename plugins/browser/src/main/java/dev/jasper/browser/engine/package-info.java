/**
 * Chromium's one life in a Jasper process: its start on first use, its settings, its {@code jasper-about:} pages and its
 * dispose. {@link dev.jasper.browser.engine.Chromium}, {@link dev.jasper.browser.engine.AboutScheme} and
 * {@code StartOnce} load with the plugin's start and run no JCEF code until Chromium starts; {@code ChromiumApp},
 * {@code ChromiumOptions} and {@code AboutSchemeFactory} hold the JCEF calls and load only then.
 */
package dev.jasper.browser.engine;
