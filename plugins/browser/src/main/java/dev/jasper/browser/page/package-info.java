/**
 * A page on screen: {@link dev.jasper.browser.page.PageView}, the lightweight canvas Chromium's off-screen frames are
 * painted on and its input comes from, {@link dev.jasper.browser.page.PageRenderer}, which receives those frames, and
 * {@link dev.jasper.browser.page.PageBrowser}, the page's browser. Adapted from JetBrains' JCEF test classes, as
 * {@code notices.toml} records. {@link dev.jasper.browser.page.LastPress} names no Chromium type, and these classes load
 * only once a page is made.
 */
package dev.jasper.browser.page;
