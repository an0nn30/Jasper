/**
 * Browser items: {@link dev.jasper.browser.item.BrowserItem}, one page in one pane, with its toolbar built from SDK
 * nodes and its find bar, {@code BrowserFindBar}, below the page, and {@link dev.jasper.browser.item.BrowserItems}, the
 * open ones. An item drives its page only through {@link dev.jasper.browser.page.Page}'s plain types, so this package
 * names no Chromium type.
 * {@link dev.jasper.browser.item.AddressInput} and {@link dev.jasper.browser.item.TabTitle} decide what the address field
 * loads and what the tab is called, {@link dev.jasper.browser.item.SearchEngine} where a search goes, the field's
 * suggestions what it offers as the user types, with the engine's own fetched through the {@code http} package apart
 * from Chromium, and {@link dev.jasper.browser.item.FaviconDecoder} turns a page's favicon into the image its tab and
 * address field show. Each item saves its restore state through the {@code restore} package, and {@code BrowserItems}
 * opens saved ones again.
 */
package dev.jasper.browser.item;
