/**
 * Jasper's own pages, at {@code jasper-about:} (spec section 14), as bundled templates, style sheets and scripts under
 * {@code dev/jasper/browser/pages/}. {@link dev.jasper.browser.pages.AboutPages} fills in a page's template for each
 * request, in the workspace theme, under a content security policy that loads nothing and runs only the script it
 * wrote; that script, {@code about.js}, gives the page {@code jasper.ask}, which asks Java through the {@code engine}
 * package's channel with the page's secret, and {@code jasper.el}, which puts a page's text in only as text.
 * {@link dev.jasper.browser.pages.HistoryPage} is {@code jasper-about:history}, which answers its script from the
 * {@code store} package's history, and {@link dev.jasper.browser.pages.BookmarksPage} is {@code jasper-about:bookmarks}, which
 * answers its script from the bookmarks. {@link dev.jasper.browser.pages.PageHtml} is what Jasper's pages share, the
 * {@code item} package's error and source pages too: the theme's colours as CSS, escaping and the pages' random tokens.
 * Depends on {@code engine} for the scheme's registry, {@code store}, {@code json}, the SDK and the JDK; it names no
 * Chromium type, and the {@code item} package may use it, never the reverse.
 */
package dev.jasper.browser.pages;
