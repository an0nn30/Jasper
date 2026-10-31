package dev.jasper.browser.page;

/** Test-only access to what Chromium's handlers tell a page, for tests outside this package. */
public final class PageTestAccess {
    private PageTestAccess() { }

    /**
     * A favicon fetcher that fetches nothing, as it is closed: for pages whose favicons a test does not fetch.
     *
     * @return the fetcher
     */
    public static Favicons noFavicons() {
        var favicons = new Favicons(bytes -> java.util.Optional.empty(), Runnable::run);
        favicons.close();
        return favicons;
    }

    /**
     * The icon the page's current document named, which the page fetches or fetched.
     *
     * @param page a page
     * @return the icon's address, or empty before the document names one
     */
    public static java.util.Optional<java.net.URI> declaredFavicon(Page page) {
        return page.declaredFavicon();
    }

    /**
     * A favicon fetched for {@code document} arrives, as a fetch the page made completes. UI thread.
     *
     * @param page a page
     * @param document the address it was fetched for
     * @param image what the fetch gave
     */
    public static void faviconArrived(Page page, String document, java.util.Optional<dev.jasper.sdk.ui.PixelImage> image) {
        page.faviconArrived(document, image);
    }

    /**
     * The page's main frame starts loading {@code url}, as the client's load handler tells it. UI thread.
     *
     * @param page a page
     * @param url what it loads
     */
    public static void loadStarted(Page page, String url) {
        page.loadStarted(url);
    }

    /**
     * The page's main frame is at {@code url}, as the client's display handler tells it when a navigation commits. UI
     * thread.
     *
     * @param page a page
     * @param url where it is
     */
    public static void addressChanged(Page page, String url) {
        page.addressChanged(url);
    }

    /**
     * The page's title is {@code title}, as the client's display handler tells it. UI thread.
     *
     * @param page a page
     * @param title its title, or null
     */
    public static void titleChanged(Page page, String title) {
        page.titleChanged(title);
    }

    /**
     * The page loads or has stopped, and where it can go, as the client's load handler tells it. UI thread.
     *
     * @param page a page
     * @param loading whether it loads
     * @param canGoBack whether it can go back
     * @param canGoForward whether it can go forward
     */
    public static void loadingChanged(Page page, boolean loading, boolean canGoBack, boolean canGoForward) {
        page.loadingChanged(loading, canGoBack, canGoForward);
    }

    /**
     * A read of the page's history gives {@code history}, as DevTools answers it, or as a read that failed gives
     * {@link NavigationHistory.History#EMPTY}. UI thread.
     *
     * @param page a page
     * @param history what the read gave
     */
    public static void historyRead(Page page, NavigationHistory.History history) {
        page.historyRead(history);
    }
}
