/**
 * Chromium's one life in a Jasper process: its start on first use, its settings, its {@code jasper-about:} pages, those
 * whose HTML comes later answered without holding Chromium's IO thread, their redirects and their queries, which
 * {@link dev.jasper.browser.engine.AboutQueries}' message router takes from a page's script and answers on the plugin's
 * background executor for that page's own main frame alone, the Markdown previews' {@code jasper-preview:} origins, the
 * marker that it runs and its dispose. {@link dev.jasper.browser.engine.Chromium},
 * {@link dev.jasper.browser.engine.AboutScheme}, {@link dev.jasper.browser.engine.PreviewScheme},
 * {@link dev.jasper.browser.engine.RunMarker}, {@code StartOnce}, {@code ChromiumLife}, which makes the life's
 * decisions, and {@code ChromiumThread}, the work waiting for Chromium's own UI thread, load with the plugin's start and
 * run no JCEF code until Chromium starts; {@code ChromiumApp},
 * {@code ChromiumOptions}, {@code AboutSchemeFactory}, {@code AboutQueries} and {@code PreviewSchemeFactory} hold the
 * JCEF calls and load only then. Pages are made on Chromium's client and previews on its preview client, a second client
 * of the same app; each client's handlers are installed through {@link dev.jasper.browser.engine.Chromium#onClient} or
 * {@link dev.jasper.browser.engine.Chromium#onPreviewClient}, once each, before any page or preview is made on it.
 * Chromium makes a /dev/null group's in-memory request context on its own UI thread and releases it once, never after
 * it has stopped; each browser of the group is handed a {@code BrowserContext} of its own over it, as JCEF disposes a
 * browser's context object as the browser closes. It serves {@code jasper-about:} to such a context's pages through
 * their request handler, as its scheme handler factory serves the global context alone.
 */
package dev.jasper.browser.engine;
