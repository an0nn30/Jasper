/**
 * Browser items: {@link dev.jasper.browser.item.BrowserItem}, one page in one pane, with its toolbar built from SDK
 * nodes and its find bar, {@code BrowserFindBar}, below the page, and {@link dev.jasper.browser.item.BrowserItems}, the
 * open ones. An item drives its page only through {@link dev.jasper.browser.page.Page}'s plain types, so this package
 * names no Chromium type.
 * {@link dev.jasper.browser.item.AddressInput} and {@link dev.jasper.browser.item.TabTitle} decide what the address field
 * loads and what the tab is called, {@link dev.jasper.browser.item.SearchEngine} where a search goes, the field's
 * suggestions what it offers as the user types, with the engine's own fetched through the {@code http} package apart
 * from Chromium, and {@link dev.jasper.browser.item.FaviconDecoder} turns a page's favicon into the image its tab and
 * address field show. A failed load shows {@link dev.jasper.browser.item.ErrorPage} in place of the page, as the
 * item's {@code ErrorPages} decide, whose Continue past a certificate error the plugin run's
 * {@code CertificateAllowance} remembers for every item. View Source shows
 * {@link dev.jasper.browser.item.SourcePage}, a page's source fetched again through the {@code http} package or as the
 * item's {@code SourcePages} captured it, and {@link dev.jasper.browser.item.PageHtml} gives Jasper's own pages the
 * theme's colours and their tokens. Each item saves its restore
 * state through the {@code restore} package, and {@code BrowserItems} opens saved ones again.
 */
package dev.jasper.browser.item;
