/**
 * Browser items: {@link dev.jasper.browser.item.BrowserItem}, one page in one pane, with its toolbar built from SDK
 * nodes and its find bar, {@code BrowserFindBar}, below the page, and {@link dev.jasper.browser.item.BrowserItems}, the
 * open ones. An item drives its page only through {@link dev.jasper.browser.page.Page}'s plain types, so this package
 * names no Chromium type.
 * {@link dev.jasper.browser.item.AddressInput} and {@link dev.jasper.browser.item.TabTitle} decide what the address field
 * loads and what the tab is called, {@link dev.jasper.browser.item.SearchEngine} where a search goes, the field's
 * suggestions what it offers as the user types, with the engine's own fetched through the {@code http} package apart
 * from Chromium and the bookmarks and visited sites of the {@code store} package's indexes ({@code StoreSuggestions}),
 * and {@link dev.jasper.browser.item.FaviconDecoder} turns a page's favicon into the image its tab and
 * address field show. A failed load shows {@link dev.jasper.browser.item.ErrorPage} in place of the page, as the
 * item's {@code ErrorPages} decide, whose Continue past a certificate error its store's {@code CertificateAllowance}
 * remembers for every item in that store. Each item opens in a {@link dev.jasper.browser.item.DataStore}, which
 * {@link dev.jasper.browser.item.DataStores} makes (spec section 12): the persistent one, or a /dev/null group's, with
 * its own request context and history and bookmarks in memory, which closes with the last item of the group; the links
 * and popups an item's page opens open in its store, and Jasper's pages answer from the store of the browser that asks.
 * Each store keeps its site permissions through the {@code permissions} package, and the menu button's
 * {@code PrivacyMenu} resets those of the page's site. An item's {@code SitePermissions} answers its page's camera,
 * microphone and notification requests from its store, or with the {@code dialogs} package's prompt, and the
 * notifications its page shows are Jasper's, in the plugin's group for them
 * ({@link dev.jasper.browser.item.PageNotifications}); a /dev/null item denies notifications.
 * View Source shows
 * {@link dev.jasper.browser.item.SourcePage}, a page's source fetched again through the {@code http} package or as the
 * item's {@code SourcePages} captured it; both take the theme's colours and their tokens from the {@code pages}
 * package's {@link dev.jasper.browser.pages.PageHtml}. Inspect Element shows a page's DevTools in its item's
 * {@code DevToolsItem}, a pane below the item's that is never saved. Each item records its page's loads and titles in
 * the {@code store} package's history ({@code HistoryRecorder}), and the Web menu's History and the hold menus' Show
 * Full History load the {@code pages} package's history page. The menu button's Add Bookmark bookmarks the page in the
 * {@code store} package's bookmarks and opens {@link dev.jasper.browser.item.BookmarkPanel}, where its tags are
 * edited, and its Manage Bookmarks and History load the {@code pages} package's bookmarks and history pages. Text another
 * plugin inserts into an item, such as a snippet, goes into its page's focused field, or is typed where the page's focus
 * is, through DevTools and never the clipboard. While Copy on select is on, each item copies the selections the user
 * finishes in its page, but none in a frame of another origin. Each item
 * saves its restore state through the {@code restore} package, and {@code BrowserItems} opens saved ones again.
 */
package dev.jasper.browser.item;
