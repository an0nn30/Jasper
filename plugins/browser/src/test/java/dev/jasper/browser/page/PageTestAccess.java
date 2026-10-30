package dev.jasper.browser.page;

/** Test-only access to what Chromium's handlers tell a page, for tests outside this package. */
public final class PageTestAccess {
    private PageTestAccess() { }

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
}
