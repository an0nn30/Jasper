package dev.jasper.browser.item;

import dev.jasper.browser.page.Page;
import dev.jasper.browser.restore.BrowserRestore;
import dev.jasper.sdk.RestoreState;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.IconAsset;
import dev.jasper.sdk.ui.TabPlacement;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The plugin's open browser items, in every window: it opens them, finds one by the OSC 8 target it was opened for,
 * and knows each window's focused one. It registers the globe every item shows. A page is made, and Chromium so asked
 * for, only when its item first shows. UI thread.
 */
public final class BrowserItems {
    /** The globe: a browser item's tab icon, and its actions' and split item's. */
    public static final String GLOBE = "dev.jasper.browser:globe";
    // The plugin icon's circle, equator and meridian, on the kit's 16-unit grid.
    private static final IconAsset GLOBE_ART = new IconAsset(GLOBE, "M 1.7 8 A 6.3 6.3 0 1 0 14.3 8 A 6.3 6.3 0 1 0 1.7 8 Z "
        + "M 1.7 8 L 14.3 8 M 8 1.7 A 2.7 6.3 0 1 0 8 14.3 A 2.7 6.3 0 1 0 8 1.7 Z");
    private final PluginContext context;
    private final Consumer<Page> start;
    private final List<BrowserItem> open = new java.util.ArrayList<>();

    /**
     * The items of a plugin run, whose pages {@code start} makes: the plugin's has Chromium make them, and a test's
     * records them, as a page cannot be made without Chromium.
     *
     * @param context the plugin's context; while it starts, as the globe is registered now
     * @param start makes a page, the first time its item shows
     */
    public BrowserItems(PluginContext context, Consumer<Page> start) {
        this.context = Objects.requireNonNull(context, "context");
        this.start = Objects.requireNonNull(start, "start");
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
     * page loads, and Chromium starts, the first time it shows. It asks the user nothing.
     *
     * @param saved the item's saved restore state
     * @param start the start page
     * @param window the window being restored
     * @return the item
     */
    public BrowserItem restore(RestoreState saved, URI start, WindowOwner window) {
        Objects.requireNonNull(saved, "saved");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(window, "window");
        return open(BrowserRestore.url(saved).orElse(start), TabPlacement.NEW_TAB, BrowserRestore.target(saved), Optional.of(window));
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

    PluginContext context() {
        return context;
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
