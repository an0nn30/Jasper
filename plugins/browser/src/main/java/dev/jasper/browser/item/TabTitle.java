package dev.jasper.browser.item;

import java.util.Optional;

/** A browser item's tab title (spec section 5). */
public final class TabTitle {
    /** The title of a page with neither a title nor a host. */
    public static final String UNTITLED = "Web Browser";

    private TabTitle() { }

    /**
     * The page's title, trimmed; while the page loads with a blank title, the name the tab had; otherwise its host, or
     * else "Web Browser".
     *
     * @param pageTitle the title the page gives, or null
     * @param host the host of the page's address
     * @param previous the name the tab had
     * @param loading whether the page loads
     * @return the title
     */
    public static String of(String pageTitle, Optional<String> host, String previous, boolean loading) {
        String title = pageTitle == null ? "" : pageTitle.strip();
        if (!title.isEmpty()) return title;
        if (loading && previous != null && !previous.isBlank()) return previous;
        return host.map(String::strip).filter(name -> !name.isEmpty()).orElse(UNTITLED);
    }
}
