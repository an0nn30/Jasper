package dev.jasper.browser.item;

import dev.jasper.browser.page.Page;

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
}
