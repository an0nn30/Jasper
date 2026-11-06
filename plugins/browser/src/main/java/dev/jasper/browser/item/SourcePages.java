package dev.jasper.browser.item;

import dev.jasper.browser.page.NavigationHistory;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.pages.PageHtml;
import java.awt.EventQueue;
import java.net.URI;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * One browser item's View Source (spec section 10; P42). Before the item goes to its page's {@link SourcePage}, the HTML
 * the page's main frame shows is captured, as Chromium serializes it, and kept under a new random token, which the source
 * page's address carries. The source page shows that capture for a page that is no web page, and for a web page whose
 * fresh fetch fails, while the item holds the token for that page; a token is made for one capture and is never
 * another's. An item keeps its last {@value #MAX} captures within {@value #BUDGET} characters in all, the oldest dropped
 * first, each kept to one character past {@link SourcePage#LIMIT}, so that its page says when it was cut; they end with
 * the item. UI thread.
 */
final class SourcePages {
    /** The most captures an item keeps. */
    static final int MAX = 20;
    /** The most characters an item's captures hold in all: two of the longest. */
    static final int BUDGET = 2 * (SourcePage.LIMIT + 1);
    private final Page page;
    private final Consumer<URI> load;
    // The captures, by their tokens, oldest first, and how many characters they hold.
    private final Map<String, Captured> captures = new LinkedHashMap<>();
    private long held;
    private boolean closed;

    /**
     * The View Source of the item whose page is {@code page}.
     *
     * @param page the item's page
     * @param load loads an address in the item, as its address field would
     */
    SourcePages(Page page, Consumer<URI> load) {
        this.page = Objects.requireNonNull(page, "page");
        this.load = Objects.requireNonNull(load, "load");
    }

    /**
     * View Source: the page's HTML is read, and the item then loads the page's source page, with a new token for the
     * capture when Chromium gave the HTML. When the page went elsewhere meanwhile, the item stays where it is.
     */
    void show() {
        if (closed) return;
        String at = page.address();
        page.source().whenComplete((source, failure) -> EventQueue.invokeLater(() ->
            read(at, failure == null && source != null ? source : Optional.empty())));
    }

    /**
     * The HTML the item captured under {@code token} for the page at {@code address}.
     *
     * @param token the token a source page's address carries
     * @param address the page's address, without user information
     * @return the HTML; empty when the item holds no such capture, or holds it for another page
     */
    Optional<String> source(String token, String address) {
        return Optional.ofNullable(captures.get(token)).filter(captured -> captured.page().equals(address)).map(Captured::source);
    }

    /** The item closed: its captures end, and a View Source under way goes nowhere. */
    void close() {
        closed = true;
        captures.clear();
        held = 0;
    }

    /** The page's HTML was read for a View Source that began at {@code at}. */
    private void read(String at, Optional<String> source) {
        if (closed || !page.address().equals(at)) return;
        String shown = NavigationHistory.withoutUserInfo(at);
        load.accept(SourcePage.address(shown, source.filter(html -> !html.isEmpty()).map(html -> keep(shown, html))));
    }

    /** Keeps {@code source} for the page at {@code address} under a new token, within the bound, and answers the token. */
    private String keep(String address, String source) {
        String kept = source.length() <= SourcePage.LIMIT + 1 ? source : source.substring(0, SourcePage.LIMIT + 1);
        String token = PageHtml.newToken();
        captures.put(token, new Captured(address, kept));
        held += kept.length();
        Iterator<Captured> oldest = captures.values().iterator();
        while (captures.size() > MAX || held > BUDGET) {
            held -= oldest.next().source().length();
            oldest.remove();
        }
        return token;
    }

    /** One capture: the page's address, without user information, and its HTML. */
    private record Captured(String page, String source) { }
}
