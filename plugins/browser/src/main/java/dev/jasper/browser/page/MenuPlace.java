package dev.jasper.browser.page;

import dev.jasper.sdk.ui.ContentTab;
import java.net.URI;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Where a page's right-click menu shows, and what its items reach outside the page, in plain types: over its item's tab,
 * with the plugin's clipboard, and with the search engine the settings choose each time the menu opens. The page's item
 * gives it.
 *
 * @param tab the item's tab, over whose content the menu shows
 * @param clipboard puts text on the system clipboard, as the SDK's {@code Platform.copy} does
 * @param engineName the name of the search engine chosen now, as Search with says it
 * @param search the address of a search for some terms with the search engine chosen now
 */
public record MenuPlace(ContentTab tab, Consumer<String> clipboard, Supplier<String> engineName, Function<String, URI> search) {
    /** Rejects nulls. */
    public MenuPlace {
        Objects.requireNonNull(tab, "tab");
        Objects.requireNonNull(clipboard, "clipboard");
        Objects.requireNonNull(engineName, "engineName");
        Objects.requireNonNull(search, "search");
    }
}
