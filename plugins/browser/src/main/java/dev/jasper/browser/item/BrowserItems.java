package dev.jasper.browser.item;

import dev.jasper.browser.downloads.FileManager;
import dev.jasper.browser.engine.AboutScheme;
import dev.jasper.browser.page.Favicons;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.restore.BrowserRestore;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.browser.tools.SavePage;
import dev.jasper.sdk.RestoreState;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.ContentTab;
import dev.jasper.sdk.ui.IconAsset;
import dev.jasper.sdk.ui.TabPlacement;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.Timer;

/**
 * The plugin's open browser items, in every window: it opens them, finds one by the OSC 8 target it was opened for,
 * and knows each window's focused one. It registers the globe every item shows, gives the items the settings and the
 * plugin's {@link DataStores}, whose store each item opens in holds the history and bookmarks it records into and
 * suggests from, the site permissions its page is answered from and the certificates the user went on past, reloads a
 * store's items that show a site whose permissions changed, hands each error page's Try Again and
 * Continue to the item whose page it is, finds the item's capture a source page shows, holds the notifications their
 * pages show ({@link PageNotifications}), and holds the fetchers of their pages' favicons and their address fields'
 * search suggestions until {@link #close()}. Its items' Save Page As saves start here ({@link #savePage}), with the
 * Downloads folder and file manager the plugin gives ({@link #useSaving}) and the HTTP proxy as it is then, whose change
 * retires each save's client (plan 7's D6). A page is made, and Chromium so asked for, only when its item first shows.
 * UI thread.
 */
public final class BrowserItems implements AutoCloseable {
    /** The globe: a browser item's tab icon, and its actions' and split item's. */
    public static final String GLOBE = "dev.jasper.browser:globe";
    // The plugin icon's circle, equator and meridian, on the kit's 16-unit grid.
    private static final IconAsset GLOBE_ART = new IconAsset(GLOBE, "M 1.7 8 A 6.3 6.3 0 1 0 14.3 8 A 6.3 6.3 0 1 0 1.7 8 Z "
        + "M 1.7 8 L 14.3 8 M 8 1.7 A 2.7 6.3 0 1 0 8 14.3 A 2.7 6.3 0 1 0 8 1.7 Z");
    private final PluginContext context;
    private final Supplier<BrowserSettings> settings;
    private final DataStores stores;
    private final Consumer<Page> start;
    private final BiFunction<Duration, Runnable, Runnable> later;
    private final Function<URI, CompletableFuture<List<String>>> suggest;
    private final List<BrowserItem> open = new java.util.ArrayList<>();
    // Made with the first item; it makes its HTTP client only once a page names a favicon.
    private Favicons favicons;
    // Made with the first suggestion request.
    private SuggestionFetcher suggestions;
    // The HTTP proxy both fetchers go through, those made later too (plan 6's F3).
    private Optional<InetSocketAddress> proxy = Optional.empty();
    // How many times the proxy changed, which a save's client is current for until it changes (plan 7's D6); read on any thread.
    private volatile long proxyChanges;
    // Save Page As's folder, file manager and client maker, which the plugin gives; null before.
    private Saving saving;
    // Made with the first item.
    private PageNotifications notifications;
    // Read on any thread, as a save's client asks whether it may go on.
    private volatile boolean closed;

    /** What Save Page As needs of the plugin: where the chooser starts, what shows the saved folder, and the save's client. */
    private record Saving(Supplier<Path> folder, FileManager files, Function<Optional<InetSocketAddress>, HttpClient> clients) { }

    /**
     * The items of a plugin run, whose pages {@code start} makes: the plugin's has Chromium make them, and a test's
     * records them, as a page cannot be made without Chromium.
     *
     * @param context the plugin's context; while it starts, as the globe is registered now
     * @param settings the settings as they are now, which the items read each time they search or suggest
     * @param stores the data stores the items open in, whose history and bookmarks they record into and suggest from;
     *     the plugin closes them
     * @param start makes a page, the first time its item shows
     */
    public BrowserItems(PluginContext context, Supplier<BrowserSettings> settings, DataStores stores, Consumer<Page> start) {
        this(context, settings, stores, start, BrowserItems::onUiAfter, null);
    }

    /**
     * As {@link #BrowserItems(PluginContext, Supplier, DataStores, Consumer)}, with {@code later} running a task on the UI
     * thread after a delay, answering what cancels it, and {@code suggest} fetching an engine's suggestions; null fetches
     * them with the plugin's own {@link SuggestionFetcher}.
     */
    BrowserItems(PluginContext context, Supplier<BrowserSettings> settings, DataStores stores, Consumer<Page> start,
                 BiFunction<Duration, Runnable, Runnable> later, Function<URI, CompletableFuture<List<String>>> suggest) {
        this.context = Objects.requireNonNull(context, "context");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.stores = Objects.requireNonNull(stores, "stores");
        this.start = Objects.requireNonNull(start, "start");
        this.later = Objects.requireNonNull(later, "later");
        this.suggest = suggest == null ? this::fetchSuggestions : suggest;
        context.kit().registerIcon(GLOBE_ART);
    }

    /**
     * Opens a browser item at {@code url}, in {@link DataStores#forNewItem()}'s store; its page loads the first time it shows.
     *
     * @param url what it loads
     * @param placement where it opens
     * @param target the OSC 8 target it is for, or empty
     * @param window the window it opens from, or empty for the most recently active one
     * @return the item
     */
    public BrowserItem open(URI url, TabPlacement placement, Optional<String> target, Optional<WindowOwner> window) {
        return open(url, placement, target, window, stores.forNewItem());
    }

    /**
     * Opens a browser item at {@code url}, in {@code store}, which it holds until it closes; its page loads the first time
     * it shows.
     *
     * @param url what it loads
     * @param placement where it opens
     * @param target the OSC 8 target it is for, or empty
     * @param window the window it opens from, or empty for the most recently active one
     * @param store the data store it opens in: the persistent one, or a /dev/null group's
     * @return the item
     */
    public BrowserItem open(URI url, TabPlacement placement, Optional<String> target, Optional<WindowOwner> window, DataStore store) {
        return BrowserItem.open(this, url, placement, target, window, store);
    }

    /**
     * Opens a saved item again, as Jasper restores its windows: at once, in a new tab of {@code window} (where Jasper
     * then puts it as it was saved), at its saved address and zoom, for its saved OSC 8 target. A saved /dev/null item comes back at {@code start}, whatever
     * it saved. An item that saved no address, or one the address field would not load, comes back at {@code start}, and one that saved no zoom, or one
     * outside 25% to 500%, at Page zoom; a target WebLink would refuse is dropped. Its page loads, and Chromium starts,
     * the first time it shows; with {@code wait}, only once the user loads it. It asks the user nothing.
     *
     * @param saved the item's saved restore state
     * @param start the start page
     * @param window the window being restored
     * @param wait whether the item waits to be loaded, as after a run that ended while Chromium ran
     * @param store the data store it comes back in: the caller's choice by the saved mode, a new /dev/null store for a
     *     saved /dev/null item and the persistent one for any other
     * @return the item
     */
    public BrowserItem restore(RestoreState saved, URI start, WindowOwner window, boolean wait, DataStore store) {
        Objects.requireNonNull(saved, "saved");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(window, "window");
        URI at = BrowserRestore.devNull(saved) ? start : BrowserRestore.url(saved).orElse(start);
        return BrowserItem.open(this, at, TabPlacement.NEW_TAB, BrowserRestore.target(saved),
            Optional.of(window), BrowserRestore.zoom(saved), wait, store);
    }

    /**
     * The open items, in the order they opened.
     *
     * @return the items
     */
    public List<BrowserItem> items() {
        return List.copyOf(open);
    }

    /**
     * The item opened for {@code target}, in any window.
     *
     * @param target an OSC 8 target
     * @return the item, or empty
     */
    public Optional<BrowserItem> find(String target) {
        Objects.requireNonNull(target, "target");
        return open.stream().filter(item -> item.target().filter(target::equals).isPresent()).findFirst();
    }

    /**
     * The item {@code window} has focused: the item of its focused pane in its shown tab, when that is a browser item or
     * a browser item's DevTools item, which stands for its page's.
     *
     * @param window a window
     * @return the item, or empty
     */
    public Optional<BrowserItem> focused(WindowHandle window) {
        Objects.requireNonNull(window, "window");
        return open.stream().filter(item -> item.focusedIn(window)).findFirst();
    }

    /**
     * The items open in {@code store} whose page shows one of {@code origins} load again, as a permission of those sites
     * changed there: an item that shows an error page for one tries again. Items of another store, and an item that waits
     * or has not shown its page yet, load nothing.
     *
     * @param store the data store whose permissions changed
     * @param origins the sites, as {@code Origins.of} names them
     */
    public void reloadShowing(DataStore store, Set<String> origins) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(origins, "origins");
        if (origins.isEmpty()) return;
        for (BrowserItem item : List.copyOf(open)) if (item.store() == store) item.privacy().reloadShowing(origins);
    }

    /** Opens Settings at the Browser's page. */
    public void showSettings() {
        context.settings().show();
    }

    /**
     * An error page's Try Again, the {@code jasper-about:error-retry} action: the item whose error page has the token the
     * address names, while it shows that page, loads what failed there again. Any page may have written the address, so
     * the token is all it is taken for.
     *
     * @param address the action's address, with the token as its {@code token} parameter
     */
    public void tryAgain(URI address) {
        Objects.requireNonNull(address, "address");
        AboutScheme.parameter(address.toString(), "token").ifPresent(token -> List.copyOf(open).forEach(item -> item.tryAgain(token)));
    }

    /**
     * An error page's Continue to this site (unsafe), the {@code jasper-about:error-continue} action: the item whose error
     * page has Continue's one-time token, while it shows that page, allows the host and certificate it kept for it and
     * loads the page again. Any page may have written the address, so the token is all it is taken for.
     *
     * @param address the action's address, with Continue's token as its {@code token} parameter
     */
    public void proceed(URI address) {
        Objects.requireNonNull(address, "address");
        AboutScheme.parameter(address.toString(), "token").ifPresent(token -> List.copyOf(open).forEach(item -> item.proceed(token)));
    }

    /**
     * The HTML an open item captured for View Source under {@code token} for the page at {@code address}, which
     * {@code jasper-about:source} shows (P42). The token is all a source page's address is taken for: 128 random bits
     * that only the item that captured the HTML made, and only for that page.
     *
     * @param token the token the source page's address carries
     * @param address the page's address, without user information
     * @return the HTML; empty when no open item holds it
     */
    public Optional<String> capturedSource(String token, String address) {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(address, "address");
        for (BrowserItem item : open) {
            Optional<String> source = item.capturedSource(token, address);
            if (source.isPresent()) return source;
        }
        return Optional.empty();
    }

    /**
     * The plugin stops: each item's open Edit Bookmark panel closes, keeping its tags, and its page gives history its last
     * title, before the plugin closes the data stores; the favicon and suggestion fetches under way end, and no page or
     * field fetches one from now on.
     */
    @Override public void close() {
        closed = true;
        for (BrowserItem item : List.copyOf(open)) item.flush();
        if (favicons != null) favicons.close();
        if (suggestions != null) suggestions.close();
    }

    /**
     * Has the items' favicon and suggestion fetches from now on go through {@code proxy} (plan 6's F3), as
     * {@code http}'s {@code WebFetch} says, the fetchers made later too; a save under way asks nothing more (plan 7's D6).
     *
     * @param proxy the HTTP proxy's address; empty for the JDK's default proxy selector
     */
    public void useProxy(Optional<InetSocketAddress> proxy) {
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        proxyChanges++;
        if (favicons != null) favicons.useProxy(proxy);
        if (suggestions != null) suggestions.useProxy(proxy);
    }

    /**
     * Gives the items' Save Page As what it needs of the plugin (plan 7's Task 7); before this, Save Page As does nothing.
     *
     * @param folder the Downloads folder as it is now, where the save chooser starts
     * @param files shows a saved folder in the file manager
     * @param clients makes a save's HTTP client through the proxy given, on the background executor
     */
    public void useSaving(Supplier<Path> folder, FileManager files, Function<Optional<InetSocketAddress>, HttpClient> clients) {
        saving = new Saving(Objects.requireNonNull(folder, "folder"), Objects.requireNonNull(files, "files"),
            Objects.requireNonNull(clients, "clients"));
    }

    /**
     * Starts a Save Page As of {@code page} over {@code tab}, its item's: with the HTTP proxy as it is now, its client
     * retired once the proxy changes or the plugin stops.
     *
     * @return the save; empty once the plugin has stopped and before {@link #useSaving}
     */
    Optional<SavePage> savePage(Page page, ContentTab tab) {
        Saving given = saving;
        if (closed || given == null) return Optional.empty();
        long changes = proxyChanges;
        var network = new SavePage.Network(proxy, () -> !closed && proxyChanges == changes, () -> !closed, given.clients());
        return Optional.of(SavePage.start(page, tab, context, given.folder().get(), given.files(), network, this::later));
    }

    /** The fetcher of the items' pages' favicons; one that fetches nothing once the plugin has stopped. */
    Favicons favicons() {
        if (favicons == null) {
            favicons = new Favicons(FaviconDecoder::toPng, context.background());
            favicons.useProxy(proxy);
            if (closed) favicons.close();
        }
        return favicons;
    }

    /** The notifications the items' pages show, in the plugin's Page notifications group. */
    PageNotifications pageNotifications() {
        if (notifications == null)
            notifications = new PageNotifications(context.notifications().group(PageNotifications.GROUP),
                () -> System.nanoTime() / 1_000_000);
        return notifications;
    }

    PluginContext context() {
        return context;
    }

    /** The data stores the items open in. */
    DataStores dataStores() {
        return stores;
    }

    /** The settings as they are now. */
    BrowserSettings settings() {
        return settings.get();
    }

    /** Whether the plugin has stopped. */
    boolean closed() {
        return closed;
    }

    /** Runs {@code task} on the UI thread after {@code delay}; the answer cancels it. */
    Runnable later(Duration delay, Runnable task) {
        return later.apply(delay, task);
    }

    /** An engine's suggestions at {@code request}; none once the plugin has stopped. */
    CompletableFuture<List<String>> suggest(URI request) {
        return suggest.apply(request);
    }

    private CompletableFuture<List<String>> fetchSuggestions(URI request) {
        if (suggestions == null) {
            suggestions = new SuggestionFetcher(context.background());
            suggestions.useProxy(proxy);
            if (closed) suggestions.close();
        }
        return suggestions.fetch(request);
    }

    /** A UI-thread timer that runs {@code task} once, after {@code delay}. */
    private static Runnable onUiAfter(Duration delay, Runnable task) {
        var timer = new Timer((int) delay.toMillis(), event -> task.run());
        timer.setRepeats(false);
        timer.start();
        return timer::stop;
    }

    /** An item first shows: its page is made. */
    void start(Page page) {
        start.accept(page);
    }

    void opened(BrowserItem item) {
        open.add(item);
    }

    void closed(BrowserItem item) {
        open.remove(item);
    }
}
