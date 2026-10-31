package dev.jasper.browser.page;

import dev.jasper.sdk.ui.ContentTab;
import java.net.URI;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Where a page's right-click menu shows, and what its items reach outside the page, in plain types: over its item's tab,
 * with the plugin's clipboard, with the search engine the settings choose each time the menu opens, with its item's
 * Back and Reload, which know the item's error pages, and with its item's View Source. The page's item gives it.
 *
 * @param tab the item's tab, over whose content the menu shows
 * @param clipboard puts text on the system clipboard, as the SDK's {@code Platform.copy} does
 * @param engineName the name of the search engine chosen now, as Search with says it
 * @param search the address of a search for some terms with the search engine chosen now
 * @param canGoBack whether the item's Back goes anywhere now
 * @param back the item's Back
 * @param reload the item's Reload
 * @param viewSource the item's View Source, which shows the page's source in the item
 */
public record MenuPlace(ContentTab tab, Consumer<String> clipboard, Supplier<String> engineName, Function<String, URI> search,
                        BooleanSupplier canGoBack, Runnable back, Runnable reload, Runnable viewSource) {
    /** Rejects nulls. */
    public MenuPlace {
        Objects.requireNonNull(tab, "tab");
        Objects.requireNonNull(clipboard, "clipboard");
        Objects.requireNonNull(engineName, "engineName");
        Objects.requireNonNull(search, "search");
        Objects.requireNonNull(canGoBack, "canGoBack");
        Objects.requireNonNull(back, "back");
        Objects.requireNonNull(reload, "reload");
        Objects.requireNonNull(viewSource, "viewSource");
    }
}
