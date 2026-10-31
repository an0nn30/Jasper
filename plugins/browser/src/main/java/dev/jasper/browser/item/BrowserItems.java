package dev.jasper.browser.item;

import dev.jasper.browser.page.Favicons;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.restore.BrowserRestore;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.sdk.RestoreState;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.IconAsset;
import dev.jasper.sdk.ui.TabPlacement;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.Timer;

/**
 * The plugin's open browser items, in every window: it opens them, finds one by the OSC 8 target it was opened for,
 * and knows each window's focused one. It registers the globe every item shows, gives the items the settings, and holds
 * the fetchers of their pages' favicons and their address fields' search suggestions until {@link #close()}. A page is
 * made, and Chromium so asked for, only when its item first shows. UI thread.
 */
public final class BrowserItems implements AutoCloseable {
    /** The globe: a browser item's tab icon, and its actions' and split item's. */
    public static final String GLOBE = "dev.jasper.browser:globe";
    // The plugin icon's circle, equator and meridian, on the kit's 16-unit grid.
    private static final IconAsset GLOBE_ART = new IconAsset(GLOBE, "M 1.7 8 A 6.3 6.3 0 1 0 14.3 8 A 6.3 6.3 0 1 0 1.7 8 Z "
        + "M 1.7 8 L 14.3 8 M 8 1.7 A 2.7 6.3 0 1 0 8 14.3 A 2.7 6.3 0 1 0 8 1.7 Z");
    private final PluginContext context;
    private final Supplier<BrowserSettings> settings;
    private final Consumer<Page> start;
    private final BiFunction<Duration, Runnable, Runnable> later;
    private final Function<URI, CompletableFuture<List<String>>> suggest;
    private final List<BrowserItem> open = new java.util.ArrayList<>();
    // Made with the first item; it makes its HTTP client only once a page names a favicon.
    private Favicons favicons;
    // Made with the first suggestion request.
    private SuggestionFetcher suggestions;
    private boolean closed;

    /**
     * The items of a plugin run, whose pages {@code start} makes: the plugin's has Chromium make them, and a test's
     * records them, as a page cannot be made without Chromium.
     *
     * @param context the plugin's context; while it starts, as the globe is registered now
     * @param settings the settings as they are now, which the items read each time they search or suggest
     * @param start makes a page, the first time its item shows
     */
    public BrowserItems(PluginContext context, Supplier<BrowserSettings> settings, Consumer<Page> start) {
        this(context, settings, start, BrowserItems::onUiAfter, null);
    }

    /**
     * As {@link #BrowserItems(PluginContext, Supplier, Consumer)}, with {@code later} running a task on the UI thread
     * after a delay, answering what cancels it, and {@code suggest} fetching an engine's suggestions; null fetches them
     * with the plugin's own {@link SuggestionFetcher}.
     */
    BrowserItems(PluginContext context, Supplier<BrowserSettings> settings, Consumer<Page> start,
                 BiFunction<Duration, Runnable, Runnable> later, Function<URI, CompletableFuture<List<String>>> suggest) {
        this.context = Objects.requireNonNull(context, "context");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.start = Objects.requireNonNull(start, "start");
        this.later = Objects.requireNonNull(later, "later");
        this.suggest = suggest == null ? this::fetchSuggestions : suggest;
        context.kit().registerIcon(GLOBE_ART);
    }

    /**
     * Opens a browser item at {@code url}; its page loads the first time it shows.
     *
     * @param url what it loads
     * @param placement where it opens
     * @param target the OSC 8 target it is for, or empty
     * @param window the window it opens from, or empty for the most recently active one
     * @return the item
     */
    public BrowserItem open(URI url, TabPlacement placement, Optional<String> target, Optional<WindowOwner> window) {
        return BrowserItem.open(this, url, placement, target, window);
    }

    /**
     * Opens a saved item again, as Jasper restores its windows: at once, in a new tab of {@code window} (where Jasper
     * then puts it as it was saved), at its saved address, for its saved OSC 8 target. An item that saved no address, or
     * one the address field would not load, comes back at {@code start}; a target WebLink would refuse is dropped. Its
     * page loads, and Chromium starts, the first time it shows; with {@code wait}, only once the user loads it. It asks
     * the user nothing.
     *
     * @param saved the item's saved restore state
     * @param start the start page
     * @param window the window being restored
     * @param wait whether the item waits to be loaded, as after a run that ended while Chromium ran
     * @return the item
     */
    public BrowserItem restore(RestoreState saved, URI start, WindowOwner window, boolean wait) {
        Objects.requireNonNull(saved, "saved");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(window, "window");
        return BrowserItem.open(this, BrowserRestore.url(saved).orElse(start), TabPlacement.NEW_TAB, BrowserRestore.target(saved),
            Optional.of(window), wait);
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
     * The item {@code window} has focused: the item of its focused pane in its shown tab, when that is a browser item.
     *
     * @param window a window
     * @return the item, or empty
     */
    public Optional<BrowserItem> focused(WindowHandle window) {
        Objects.requireNonNull(window, "window");
        return open.stream().filter(item -> item.tab().isActive() && item.tab().window().id().equals(window.id())).findFirst();
    }

    /** Opens Settings at the Browser's page. */
    public void showSettings() {
        context.settings().show();
    }

    /**
     * The plugin stops: the favicon and suggestion fetches under way end, and no page or field fetches one from now on.
     */
    @Override public void close() {
        closed = true;
        if (favicons != null) favicons.close();
        if (suggestions != null) suggestions.close();
    }

    /** The fetcher of the items' pages' favicons; one that fetches nothing once the plugin has stopped. */
    Favicons favicons() {
        if (favicons == null) {
            favicons = new Favicons(FaviconDecoder::toPng, context.background());
            if (closed) favicons.close();
        }
        return favicons;
    }

    PluginContext context() {
        return context;
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
