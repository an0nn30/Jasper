/**
 * Chromium's one life in a Jasper process: its start on first use, its settings, its {@code jasper-about:} pages, the
 * marker that it runs and its dispose. {@link dev.jasper.browser.engine.Chromium},
 * {@link dev.jasper.browser.engine.AboutScheme}, {@link dev.jasper.browser.engine.RunMarker}, {@code StartOnce} and
 * {@code ChromiumLife}, which makes the life's decisions, load with the plugin's start and run no JCEF code until
 * Chromium starts; {@code ChromiumApp},
 * {@code ChromiumOptions} and {@code AboutSchemeFactory} hold the JCEF calls and load only then. The client's handlers are
 * installed through {@link dev.jasper.browser.engine.Chromium#onClient}, once each, before any page is made on it.
 */
package dev.jasper.browser.engine;
