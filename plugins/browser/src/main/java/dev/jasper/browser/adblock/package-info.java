/**
 * Ad blocking's compiler (spec section 17): a WebKit content-blocker list, which comes from the web and is read as
 * hostile input. {@link dev.jasper.browser.adblock.BlockerJson} reads its text through the {@code json} package's
 * strict reader, within bounds, and {@link dev.jasper.browser.adblock.ContentRules} compiles the triggers and actions
 * Jasper applies into {@link dev.jasper.browser.adblock.Rule}s, skipping and counting every other rule; each rule's
 * {@code url-filter} is a {@link dev.jasper.browser.adblock.UrlFilter} in WebKit's grammar alone, whose match is
 * bounded, and the {@link dev.jasper.browser.adblock.ResourceKind}s it covers take WebKit's names.
 * {@link dev.jasper.browser.adblock.RuleSet} applies the rules through an index: whether a request is blocked, and
 * which of a page's elements are hidden, with first and third party told apart by
 * {@link dev.jasper.browser.adblock.BaseDomain}'s sites, as Jasper has no public suffix list.
 * {@link dev.jasper.browser.adblock.FilterList} is the list itself: compiled on the background executor and checked
 * every hour, on a thread of its own, once a page has started or the user turned ad blocking on; the {@code links} and
 * {@code page} packages apply its rule set to Chromium's requests and documents.
 * {@link dev.jasper.browser.adblock.ListDownload} downloads it from the Filter List URL with the JDK's HTTP client,
 * within bounds, following redirects itself, and {@code ListCache} keeps it in the data folder's {@code adblock/}.
 * Depends on the {@code json} package and the JDK only, and names no Chromium type.
 */
package dev.jasper.browser.adblock;
