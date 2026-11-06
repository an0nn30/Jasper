/**
 * Links and popups inside a page. {@link dev.jasper.browser.links.PageNavigation} holds the client's one request handler
 * and a life span handler, which decide whether a modified click's link or a popup opens and where, through
 * {@link dev.jasper.browser.links.LinkGesture} and {@code LinkRoute}, and hand the result to the page's
 * {@link dev.jasper.browser.page.Page}: the page loads it, or its item opens an item of its own beside itself. The
 * request handler also lets Chromium go on past a certificate it could not verify only when the page allows it, and
 * otherwise hands the page the certificate for its error page, and it serves {@code jasper-about:} to the pages of an
 * in-memory context, which Chromium's scheme handler factory does not reach.
 * {@code OpenerBridge}, with its page script {@code opener-bridge.js} and {@code BridgeJson}, opens a script's
 * {@code window.open} the same way and keeps the popup's {@code window.opener} working through the client's message
 * router, within the limits {@code Isolation} reads from each main frame's response: its origin, a sandbox, and its
 * opener policy. The router serves the {@code page} package's scripts too, each request by its op: a document's
 * favicon goes to its {@link dev.jasper.browser.page.Page}, when {@code http} would fetch it, and what a frame's
 * {@code find.js} found to the page's {@link dev.jasper.browser.page.PageFind}, from any frame of the page.
 * {@link dev.jasper.browser.links.PrivacyQueries} is the client's privacy router, apart from the bridge's as its
 * permission requests can wait minutes: the {@code page} package's {@code notifications.js} asks through it, from a
 * page's top frame, and each request, untrusted, is answered from the page's
 * {@link dev.jasper.browser.page.PagePrivacy}, judged for the site of the document that asked, as the
 * {@code permissions} package names it. Depends on {@code engine}, {@code http}, {@code json}, {@code page} and
 * {@code permissions}; it loads only once a page first asks for Chromium.
 */
package dev.jasper.browser.links;
