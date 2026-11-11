/**
 * Links and popups inside a page. {@link dev.jasper.browser.links.PageNavigation} holds the client's one request handler
 * and a life span handler, which decide whether a modified click's link or a popup opens and where, through
 * {@link dev.jasper.browser.links.LinkGesture} and {@code LinkRoute}, and hand the result to the page's
 * {@link dev.jasper.browser.page.Page}: the page loads it, or its item opens an item of its own beside itself. The
 * request handler also lets Chromium go on past a certificate it could not verify only when the page allows it, and
 * otherwise hands the page the certificate for its error page, and it serves {@code jasper-about:} to the pages of an
 * in-memory context, which Chromium's scheme handler factory does not reach. It hands a page each HTTP sign-in
 * Chromium asks for one of its requests, as a {@link dev.jasper.browser.page.SignIn} over Chromium's callback, posted
 * to the page, whose item answers it later, once ({@link dev.jasper.browser.page.Page#signIn}); it never blocks
 * Chromium's thread, and a browser that is no page's gets none. It notes the address of each main-frame navigation
 * request of a page, and the opener bridge's reader each redirect of one, as the page's top address, which a sign-in
 * is judged against, and that reader posts the page each final main-frame response's status (plan 8).
 * {@code OpenerBridge}, with its page script {@code opener-bridge.js} and {@code BridgeJson}, opens a script's
 * {@code window.open} the same way and keeps the popup's {@code window.opener} working through the client's message
 * router, within the limits {@code Isolation} reads from each main frame's response: its origin, a sandbox, and its
 * opener policy. The router serves the {@code page} package's scripts too, each request by its op: a document's
 * favicon goes to its {@link dev.jasper.browser.page.Page}, when {@code http} would fetch it, and what a frame's
 * {@code find.js} found to the page's {@link dev.jasper.browser.page.PageFind}, from any frame of the page, and what the
 * {@code tools} package's scripts report, from the page's top frame, to the page's tools
 * ({@link dev.jasper.browser.page.Page#toolReported}). A {@code login.pick} from the page's top frame becomes a
 * {@link dev.jasper.browser.page.PageQuery} the page's password manager answers later, once
 * ({@link dev.jasper.browser.page.Page#loginQuery}), which holds none of the page's waiting places and which Chromium's
 * cancel of the query cancels.
 * {@code PrivacyQueries} is the client's privacy router, apart from the bridge's as its permission requests can wait
 * minutes, which the same installer adds: the {@code page} package's {@code notifications.js} asks through it, from a
 * page's top frame, and its {@code audio.js} tells of a document's first sound, from any frame but a challenge frame,
 * once per document; each request, untrusted, is answered from the page's
 * {@link dev.jasper.browser.page.PagePrivacy}, judged for the site of the document that asked, as the
 * {@code permissions} package names it; a /dev/null page, a document outside a secure context and one whose origin a
 * sandbox made opaque, as the bridge recorded it, have none that may have notifications. While ad blocking's filter
 * list has a rule set active, {@code AdBlockRequests}, the request handler's part, cancels each request of a page but
 * its main frame's that the {@code adblock} package's rules block. Depends on {@code adblock}, {@code engine},
 * {@code http}, {@code json}, {@code page} and {@code permissions}; it loads only once a page first asks for Chromium.
 */
package dev.jasper.browser.links;
