/**
 * The plugin's own fetches from the web, apart from Chromium: {@link dev.jasper.browser.http.WebFetch} gets what the
 * {@code page} package's favicons and the {@code item} package's search suggestions and View Source need, bounded in
 * bytes and time, without cookies or credentials, through the HTTP proxy the plugin gives it, routed as Chromium routes
 * its pages' requests ({@link dev.jasper.browser.http.ProxyRoute}), which the {@code adblock} package's filter list
 * shares. Depends on the JDK only and names no Chromium type.
 */
package dev.jasper.browser.http;
