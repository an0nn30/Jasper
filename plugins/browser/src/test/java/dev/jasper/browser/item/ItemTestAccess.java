package dev.jasper.browser.item;

import dev.jasper.browser.page.Page;
import dev.jasper.sdk.ui.PixelImage;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.swing.JComponent;

/** Test-only access to a browser item's parts, for tests outside this package. */
public final class ItemTestAccess {
    private ItemTestAccess() { }

    /**
     * The item's page.
     *
     * @param item a browser item
     * @return its page
     */
    public static Page page(BrowserItem item) {
        return item.page();
    }

    /**
     * Whether the item has asked for its page, which it does the first time it shows.
     *
     * @param item a browser item
     * @return whether it asked
     */
    public static boolean pageAsked(BrowserItem item) {
        return item.pageAsked();
    }

    /**
     * Whether the item waits to be loaded, as a restored item does after a run that ended while Chromium ran.
     *
     * @param item a browser item
     * @return whether it waits
     */
    public static boolean waiting(BrowserItem item) {
        return item.waiting();
    }

    /**
     * The item's page canvas, which shows the item when added to a showing parent.
     *
     * @param item a browser item
     * @return its canvas
     */
    public static JComponent pageView(BrowserItem item) {
        return item.pageView();
    }

    /**
     * Asks the items' search suggestion fetcher for {@code request}.
     *
     * @param items the plugin's items
     * @param request the suggestion address
     * @return the suggestions, to come
     */
    public static CompletableFuture<List<String>> suggest(BrowserItems items, URI request) {
        return items.suggest(request);
    }

    /**
     * Asks the items' favicon fetcher for {@code icon}, which {@code page} names.
     *
     * @param items the plugin's items
     * @param icon the icon's address
     * @param page the page's address
     * @return the icon, to come
     */
    public static CompletableFuture<Optional<PixelImage>> favicon(BrowserItems items, URI icon, URI page) {
        return items.favicons().fetch(icon, page);
    }

    /**
     * Has View Source's page fetch {@code page}'s source again.
     *
     * @param sources View Source's page
     * @param page the page's address
     * @return the source, to come
     */
    public static CompletableFuture<Optional<String>> fetchSource(SourcePage sources, URI page) {
        return sources.fetch(page);
    }
}
