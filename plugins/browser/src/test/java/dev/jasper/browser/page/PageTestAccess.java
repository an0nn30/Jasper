package dev.jasper.browser.page;

import dev.jasper.browser.engine.RecordingContext;
import dev.jasper.browser.store.Reached;
import org.cef.browser.CefRequestContext;

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
     * A favicon fetched for the document the page shows now arrives, as a fetch the page made completes. UI thread.
     *
     * @param page a page
     * @param image what the fetch gave
     */
    public static void faviconArrived(Page page, java.util.Optional<dev.jasper.sdk.ui.PixelImage> image) {
        page.faviconArrived(page.document(), image);
    }

    /**
     * A favicon fetched for the {@code document}th document the page's main frame showed arrives. UI thread.
     *
     * @param page a page
     * @param document the document it was fetched for, as {@link #document} counted it
     * @param image what the fetch gave
     */
    public static void faviconArrived(Page page, long document, java.util.Optional<dev.jasper.sdk.ui.PixelImage> image) {
        page.faviconArrived(document, image);
    }

    /**
     * Which document the page's main frame shows, counted by its load starts. UI thread.
     *
     * @param page a page
     * @return the count
     */
    public static long document(Page page) {
        return page.document();
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
     * The page's main frame starts loading {@code url}, reached in a way Chromium does not name ({@link Reached#OTHER}), as
     * the client's load handler tells it. UI thread.
     *
     * @param page a page
     * @param url what it loads
     */
    public static void loadStarted(Page page, String url) {
        page.loadStarted(url, Reached.OTHER);
    }

    /**
     * The page's main frame starts loading {@code url}, reached {@code reached}, as the client's load handler tells it.
     * UI thread.
     *
     * @param page a page
     * @param url what it loads
     * @param reached how the load reached it
     */
    public static void loadStarted(Page page, String url, Reached reached) {
        page.loadStarted(url, reached);
    }

    /**
     * The page's main frame finished loading its document, as the client's load handler tells it. UI thread.
     *
     * @param page a page
     */
    public static void loadEnded(Page page) {
        page.loadEnded();
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
     * Has {@code page} put text into its focused field with {@code inserter} from now on, which is told the page and each
     * text and answers what became of it, as the page's script would: a test's, as a page without Chromium inserts
     * nothing.
     *
     * @param page a page
     * @param inserter answers each insertion
     */
    public static void useFieldInserter(Page page, java.util.function.BiFunction<Page, String,
            java.util.concurrent.CompletableFuture<Page.Insertion>> inserter) {
        page.useFieldInserter(inserter);
    }

    /**
     * Has {@code page} type text where its focus is with {@code inserter} from now on, which is told the page and each
     * text and answers once it is typed, as DevTools' {@code Input.insertText} would: a test's, as a page without
     * Chromium types nothing.
     *
     * @param page a page
     * @param inserter answers each typing
     */
    public static void useTextInserter(Page page, java.util.function.BiFunction<Page, String,
            java.util.concurrent.CompletableFuture<Void>> inserter) {
        page.useTextInserter(inserter);
    }

    /**
     * Has {@code page} press Return with {@code presser} from now on, which is told the page each time: a test's
     * recording one, as a page without Chromium presses nothing.
     *
     * @param page a page
     * @param presser told each Return
     */
    public static void useReturnPresser(Page page, java.util.function.Consumer<Page> presser) {
        page.useReturnPresser(presser);
    }

    /**
     * Has {@code page} load again with {@code reloader} from now on, which is told the page each time: a test's recording
     * one, as a page without Chromium loads nothing.
     *
     * @param page a page
     * @param reloader told each reload
     */
    public static void useReloader(Page page, java.util.function.Consumer<Page> reloader) {
        page.useReloader(reloader);
    }

    /**
     * Has {@code page} read its selection with {@code reader} from now on, which is told the page each time and answers
     * what is selected, as the page's script would: a test's, as a page without Chromium has nothing selected.
     *
     * @param page a page
     * @param reader answers each read
     */
    public static void useSelectionReader(Page page, java.util.function.Function<Page,
            java.util.concurrent.CompletableFuture<Page.Selection>> reader) {
        page.useSelectionReader(reader);
    }

    /**
     * Has {@code page} run Chromium's copy in its focused frame with {@code copier} from now on, which is told the page
     * each time: a test's recording one, as a page without Chromium copies nothing.
     *
     * @param page a page
     * @param copier told each copy
     */
    public static void useCopier(Page page, java.util.function.Consumer<Page> copier) {
        page.useCopier(copier);
    }

    /**
     * The user finished a selection in the page, as its canvas tells 100 ms after the last selection gesture: those who
     * asked {@link Page#onSelectionGesture} are told. UI thread.
     *
     * @param page a page
     */
    public static void selectionGesture(Page page) {
        page.selectionGestured();
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
     * The page's DevTools, inspecting the element at {@code at} on its canvas, as {@link Page#inspect} makes them for a
     * page whose browser is ready: a page without Chromium has none, so Chromium never makes their browser. UI thread.
     *
     * @param page a page
     * @param at a point on its canvas
     * @return the DevTools
     */
    public static DevTools devTools(Page page, java.awt.Point at) {
        return page.devTools(at);
    }

    /**
     * A page's browser made without its constructor, which would start Chromium: it has only its own fields, its page and
     * the page's canvas, and every Chromium call on it answers nothing, as JCEF's calls do before a native browser
     * exists. For the client's handlers, which ask a browser whose it is.
     *
     * @param owner the page
     * @return the browser
     */
    public static PageBrowser pageBrowser(Page owner) {
        PageBrowser made = withoutConstructor();
        set(made, "view", owner.view());
        made.owner(owner);
        return made;
    }

    /**
     * Chromium has made the page's browser, as its life span handler tells on Chromium's thread: the browser's identifier
     * is the page's from now on. Any thread, as Chromium's.
     *
     * @param page a page
     * @param browser the browser's identifier
     */
    public static void browserMade(Page page, int browser) {
        page.browserMade(browser);
    }

    /**
     * Chromium has closed the page's browser, as its life span handler tells on Chromium's thread. Any thread.
     *
     * @param page a page
     */
    public static void browserClosed(Page page) {
        page.browserClosed();
    }

    /**
     * An in-memory context whose kept Chromium context is a stand-in ({@link RecordingContext}), made at once, and whose
     * each release counts in {@code released} instead of reaching Chromium: for the tests of the stores that hold
     * contexts, as no context can be made without Chromium.
     *
     * @param released counts the releases
     * @return the context
     */
    public static PageContext recordingContext(java.util.concurrent.atomic.AtomicInteger released) {
        CefRequestContext kept = new RecordingContext(1);
        return new PageContext(true, chromium -> java.util.concurrent.CompletableFuture.completedFuture(kept),
            (chromium, context) -> released.incrementAndGet());
    }

    /**
     * The page's client and its context object {@code context} are ready, as its start waits for them, with a client
     * that stands for none: for the cases that make no browser, which a test can reach without Chromium.
     *
     * @param page a page
     * @param chromium the plugin's Chromium
     * @param context the context object handed out for the page's browser
     */
    public static void make(Page page, dev.jasper.browser.engine.Chromium chromium, CefRequestContext context) {
        page.make(chromium, null, context);
    }

    /**
     * Whether the page has made its browser.
     *
     * @param page a page
     * @return whether it has one
     */
    public static boolean hasBrowser(Page page) {
        return page.hasBrowser();
    }

    /**
     * Has {@code context} asked for as a page made with it asks, as it starts: a context object of the page's own, over
     * the group's kept context, which is asked of {@code chromium} the first time.
     *
     * @param context a page context
     * @param chromium the plugin's Chromium
     * @return the page's context object's future
     */
    public static java.util.concurrent.CompletableFuture<?> askContext(PageContext context, dev.jasper.browser.engine.Chromium chromium) {
        return context.context(chromium);
    }

    /**
     * Chromium's DevTools browser for {@code tools}, made as {@link #pageBrowser} makes a page's: no page's, drawn on the
     * DevTools' canvas.
     *
     * @param tools the DevTools
     * @return the browser
     */
    public static PageBrowser devToolsBrowser(DevTools tools) {
        PageBrowser made = withoutConstructor();
        set(made, "view", tools.canvas());
        set(made, "devTools", tools);
        return made;
    }

    /**
     * A {@code PageBrowser} whose constructors never ran, only {@code Object}'s: the JDK's serialization constructor,
     * from {@code jdk.unsupported}'s {@code sun.reflect.ReflectionFactory}, found by name so the compiler has no internal
     * API to warn of.
     */
    private static PageBrowser withoutConstructor() {
        try {
            Class<?> factory = Class.forName("sun.reflect.ReflectionFactory");
            Object reflection = factory.getMethod("getReflectionFactory").invoke(null);
            var constructor = (java.lang.reflect.Constructor<?>) factory.getMethod("newConstructorForSerialization", Class.class,
                java.lang.reflect.Constructor.class).invoke(reflection, PageBrowser.class, Object.class.getDeclaredConstructor());
            return PageBrowser.class.cast(constructor.newInstance());
        } catch (ReflectiveOperationException unavailable) {
            throw new AssertionError("A page's browser could not be made without Chromium", unavailable);
        }
    }

    /** Sets {@code made}'s field {@code name}, final ones included, as its constructor would have. */
    private static void set(PageBrowser made, String name, Object value) {
        try {
            var field = PageBrowser.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(made, value);
        } catch (ReflectiveOperationException unavailable) {
            throw new AssertionError("A page's browser has no field " + name, unavailable);
        }
    }

    /**
     * Chromium has closed the DevTools' browser, as it does once they or their page close. UI thread.
     *
     * @param tools the DevTools
     */
    public static void devToolsEnded(DevTools tools) {
        tools.ended();
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
