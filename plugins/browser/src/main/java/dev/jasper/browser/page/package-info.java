/**
 * A page on screen. {@link dev.jasper.browser.page.Page} is what a browser item drives, in plain types: it makes the
 * page's canvas at once and its browser only once asked to start. {@link dev.jasper.browser.page.PageView} is the
 * lightweight canvas Chromium's off-screen frames are painted on and its input comes from,
 * {@link dev.jasper.browser.page.PageRenderer} receives those frames, and {@link dev.jasper.browser.page.PageBrowser} is
 * the page's browser. {@link dev.jasper.browser.page.PageEvents} holds the client's one display, load and keyboard
 * handler, which hand each page's callbacks to its {@code Page} and refuse fullscreen, and tells the {@code links}
 * package's hook each main frame's load start on Chromium's thread; the main frame then runs
 * {@code detect-favicon.js}, which names the document's icon through the {@code links} package's message router, and
 * {@link dev.jasper.browser.page.Favicons} fetches it for the page. {@link dev.jasper.browser.page.NavigationHistory}
 * reads a page's history through DevTools after each main-frame load end, committed navigation and title change, for
 * its item's Back and Forward hold menus, and goes to the entry chosen; the {@code json} package reads DevTools'
 * answer. {@link dev.jasper.browser.page.PageFind} is find in page, behind the item's find bar: {@code find.js} runs in
 * each of the page's frames, highlights the matches and answers its count through the {@code links} package's message
 * router, and the {@code json} package writes the query into the script.
 * {@link dev.jasper.browser.page.DialogPlace} is where the {@code dialogs} package shows a page's dialogs, as its item
 * gives it. {@code PageMenu} is the client's one context-menu handler: Jasper shows the page's right-click menu,
 * {@code PageMenuItems} for what is under the pointer, at the {@link dev.jasper.browser.page.MenuPlace} the item gives,
 * over its tab, and Chromium shows none. {@link dev.jasper.browser.page.LinkSchemes} decides which addresses a page's
 * links open, and which a page may open with Jasper's rights, for the {@code links} package and the menu. The canvas,
 * renderer and browser are adapted from JetBrains' JCEF test classes, as {@code notices.toml} records. {@link dev.jasper.browser.page.LastPress},
 * {@code Favicons} and {@code Page}'s methods name no Chromium type, and none of these classes loads until a browser
 * item opens.
 */
package dev.jasper.browser.page;
