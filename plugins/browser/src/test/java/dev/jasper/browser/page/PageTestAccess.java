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
     * The zoom the page shows at, which its browser takes once made.
     *
     * @param page a page
     * @return Chromium's zoom level
     */
    public static double zoomLevel(Page page) {
        return page.zoomLevel();
    }

    /**
     * Has the page's canvas follow the Ctrl+wheel rule of the platform {@code osName} names, as {@code os.name} does: a
     * test of Windows' or macOS's on any host. UI thread.
     *
     * @param page a page
     * @param osName an {@code os.name} value
     */
    public static void useOsName(Page page, String osName) {
        ((PageView) page.view()).useOsName(osName);
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
     * A load of the page's main frame failed, as the client's load handler tells it. UI thread.
     *
     * @param page a page
     * @param url the address that failed
     * @param code Chromium's name for the error
     */
    public static void loadFailed(Page page, String url, String code) {
        page.loadFailed(url, code);
    }

    /**
     * Has {@code page} go to its history's entries with {@code jumper} from now on, which is told the page and each
     * entry's id: a test's recording one, as a page without Chromium goes nowhere.
     *
     * @param page a page
     * @param jumper told each entry the page goes to
     */
    public static void useHistoryJumper(Page page, java.util.function.ObjIntConsumer<Page> jumper) {
        page.useHistoryJumper(jumper);
    }

    /**
     * Has {@code page} read the HTML its main frame shows with {@code reader} from now on, which is told the page: a
     * test's, as a page without Chromium has none to read.
     *
     * @param page a page
     * @param reader gives the HTML, or empty for none, as Chromium's {@code getSource} would
     */
    public static void useSourceReader(Page page, java.util.function.Function<Page, java.util.concurrent.CompletableFuture<
            java.util.Optional<String>>> reader) {
        page.useSourceReader(reader);
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

    /**
     * A find for a page whose frames are {@code frames}, in that order, and whose focus is in {@code focused}: each script
     * it would run in a frame is added to {@code ran} as the frame, a space and the command {@code find.js} is called
     * with ({@link #findCommand}), and each wait for the frames' answers to {@code waits}, where it waits until the test
     * runs it.
     *
     * @param frames the page's frames
     * @param focused the frame with the focus, or empty
     * @param ran told each script
     * @param waits told each wait
     * @return the find
     */
    public static PageFind recordingFind(java.util.List<String> frames, java.util.Optional<String> focused, java.util.List<String> ran,
                                         java.util.List<Runnable> waits) {
        return new PageFind(() -> frames, () -> focused, (frame, script) -> ran.add(frame + " " + findCommand(script)), (delay, task) -> {
            waits.add(task);
            return () -> waits.remove(task);
        });
    }

    /**
     * Has {@code page} find with {@code find} from now on.
     *
     * @param page a page
     * @param find its find, such as a {@link #recordingFind}
     */
    public static void useFind(Page page, PageFind find) {
        page.useFind(find);
    }

    /**
     * A frame's {@code find.js} answers a search, as the message router hands it on. UI thread.
     *
     * @param find the page's find
     * @param frame the frame's identifier
     * @param search the search's id
     * @param count the frame's matches
     * @param invalid whether the pattern is invalid
     */
    public static void findAnswered(PageFind find, String frame, long search, int count, boolean invalid) {
        find.answered(frame, search, count, invalid);
    }

    /**
     * A frame's {@code find.js} answers the selection asked for, as the message router hands it on. UI thread.
     *
     * @param find the page's find
     * @param frame the frame's identifier
     * @param search the request's id
     * @param selection the frame's selected text
     */
    public static void findSelected(PageFind find, String frame, long search, String selection) {
        find.selected(frame, search, selection);
    }

    /**
     * The command a script Jasper runs in a frame calls {@code find.js} with, as its JSON.
     *
     * @param script the script: {@code find.js}, then the call with one command
     * @return the command
     */
    public static String findCommand(String script) {
        String start = PageFind.SOURCE + "\n(";
        if (!script.startsWith(start) || !script.endsWith(");")) throw new AssertionError("Not find.js called with one command: " + script);
        return script.substring(start.length(), script.length() - 2);
    }
}
