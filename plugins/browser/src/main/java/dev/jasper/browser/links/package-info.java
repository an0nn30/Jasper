/**
 * Links and popups inside a page. {@link dev.jasper.browser.links.PageNavigation} holds the client's one request handler
 * and a life span handler, which decide whether a modified click's link or a popup opens and where, through
 * {@link dev.jasper.browser.links.LinkGesture} and {@code LinkRoute}, and hand the result to the page's
 * {@link dev.jasper.browser.page.Page}: the page loads it, or its item opens an item of its own beside itself.
 * {@code OpenerBridge}, with its page script {@code opener-bridge.js} and {@code BridgeJson}, opens a script's
 * {@code window.open} the same way and keeps the popup's {@code window.opener} working through the client's message
 * router, within the limits {@code Isolation} reads from each main frame's response: its origin, a sandbox, and its
 * opener policy. The router serves the {@code page} package's scripts too, each request by its op: a document's
 * favicon goes to its {@link dev.jasper.browser.page.Page}. Depends on {@code engine} and {@code page}; it loads only
 * once a page first asks for Chromium.
 */
package dev.jasper.browser.links;
