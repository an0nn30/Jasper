package dev.jasper.browser.item;

import dev.jasper.browser.page.Page;
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
     * The item's page canvas, which shows the item when added to a showing parent.
     *
     * @param item a browser item
     * @return its canvas
     */
    public static JComponent pageView(BrowserItem item) {
        return item.pageView();
    }
}
