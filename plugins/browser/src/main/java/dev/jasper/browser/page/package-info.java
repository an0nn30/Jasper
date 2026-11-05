/**
 * A page on screen. {@link dev.jasper.browser.page.Page} is what a browser item drives, in plain types: it makes the
 * page's canvas at once and its browser only once asked to start. {@link dev.jasper.browser.page.PageView} is the
 * lightweight canvas Chromium's off-screen frames are painted on and its input comes from,
 * {@link dev.jasper.browser.page.PageRenderer} receives those frames, and {@link dev.jasper.browser.page.PageBrowser}
 * is the page's browser. {@link dev.jasper.browser.page.PageEvents} holds the client's one display, load and keyboard
 * handler, which hand each page's callbacks to its {@code Page} and refuse fullscreen, and tells the {@code links}
 * package's hook each main frame's load start on Chromium's thread; the main frame then runs {@code detect-favicon.js},
 * which names the document's icon through the {@code links} package's message router, and
 * {@link dev.jasper.browser.page.Favicons} fetches it for the page. {@link dev.jasper.browser.page.NavigationHistory}
 * reads a page's history through DevTools after each main-frame load end and committed navigation, and a moment after a
 * title change, for its item's Back and Forward hold menus, and goes to the entry chosen; the {@code json} package
 * reads DevTools' answer. {@link dev.jasper.browser.page.PageFind} is find in page, behind the item's find bar:
 * {@code find.js} runs in each of the page's frames, highlights the matches and answers its count through the
 * {@code links} package's message router, and the {@code json} package writes the query into the script.
 * {@link dev.jasper.browser.page.DialogPlace} is where the {@code dialogs} package shows a page's dialogs, as its item
 * gives it. {@code PageMenu} is the client's one context-menu handler: Jasper shows the page's right-click menu,
 * {@code PageMenuItems} for what is under the pointer, at the {@link dev.jasper.browser.page.MenuPlace} the item gives,
 * over its tab, and Chromium shows none; its View Source reads the page's HTML through {@code Page.source}, and its
 * Inspect Element opens the page's {@link dev.jasper.browser.page.DevTools}: a canvas of their own, which the page
 * makes, and Chromium's DevTools browser, which the page's browser makes, on the page's client.
 * {@link dev.jasper.browser.page.LinkSchemes} decides which addresses a page's links open, and which a page may open
 * with Jasper's rights, for the {@code links} package and the menu. {@link dev.jasper.browser.page.LoadFailure} is a
 * main frame's failed load, as the load handler hands it to the page for its item's error page, with the
 * {@link dev.jasper.browser.page.ServerCertificate} Chromium could not verify for it, which the {@code links} package's
 * request handler goes on past only when the page's item allows it. The canvas, renderer and browser are adapted from
 * JetBrains' JCEF test classes, as {@code notices.toml} records. {@link dev.jasper.browser.page.PreviewPage} is a
 * Markdown preview's page, in plain types, for the {@code preview} package: it makes its canvas at once, and its browser,
 * on Chromium's preview client, only once the canvas first shows; {@code PreviewHandlers} are the preview client's own
 * handlers and message router, which refuse every navigation but the shell's first load, every request off the
 * preview's origin, popups, dialogs and the right-click menu, and its Find runs {@code find.js} too.
 * {@link dev.jasper.browser.page.LastPress}, {@code Favicons}, {@code Page}'s methods and {@code PreviewPage}'s name no
 * Chromium type, and none of these classes loads until a browser item opens or a Markdown preview is made.
 */
package dev.jasper.browser.page;
