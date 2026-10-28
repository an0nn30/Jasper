/**
 * A page on screen. {@link dev.jasper.browser.page.Page} is what a browser item drives, in plain types: it makes the
 * page's canvas at once and its browser only once asked to start. {@link dev.jasper.browser.page.PageView} is the
 * lightweight canvas Chromium's off-screen frames are painted on and its input comes from,
 * {@link dev.jasper.browser.page.PageRenderer} receives those frames, and {@link dev.jasper.browser.page.PageBrowser} is
 * the page's browser. {@code PageEvents} holds the client's one display handler and one load handler, which hand each
 * page's callbacks to its {@code Page}. The canvas, renderer and browser are adapted from JetBrains' JCEF test classes,
 * as {@code notices.toml} records. {@link dev.jasper.browser.page.LastPress} and {@code Page}'s methods name no Chromium
 * type, and none of these classes loads until a browser item opens.
 */
package dev.jasper.browser.page;
