/**
 * Page tools (spec section 10): what the Browser does to the page it shows, through scripts in the page's top frame.
 * {@link dev.jasper.browser.tools.PageTools} is one page's tools: it checks each of the page's documents with
 * Readability.js, bundled unmodified under {@code third_party/}, offers the menu's items once a document has more than
 * 10 characters of reader text, lays the article over the page, runs distraction removal, and hides the overlay under
 * the right-click menu's point for Remove Element. {@link dev.jasper.browser.tools.ToolScripts} writes the scripts,
 * {@code reader.js} after Readability.js, and {@code distractions.js}, which answer through the {@code links} package's
 * opener bridge, whose reports the {@code page} package's {@link dev.jasper.browser.page.Page} hands on. May depend on
 * the {@code downloads}, {@code page}, {@code json} and {@code http} packages, the SDK and the JDK, and names no Chromium
 * type; the {@code item} package's browser item owns each page's tools.
 */
package dev.jasper.browser.tools;
