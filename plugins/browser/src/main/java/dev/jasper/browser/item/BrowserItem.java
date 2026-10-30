package dev.jasper.browser.item;

import dev.jasper.browser.engine.AboutScheme;
import dev.jasper.browser.page.DialogPlace;
import dev.jasper.browser.page.NavigationButton;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.restore.BrowserRestore;
import dev.jasper.sdk.RestoreState;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.ui.ContentCommand;
import dev.jasper.sdk.ui.ContentPresentation;
import dev.jasper.sdk.ui.ContentSpec;
import dev.jasper.sdk.ui.ContentStatus;
import dev.jasper.sdk.ui.ContentTab;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.CustomContent;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.PixelImage;
import dev.jasper.sdk.ui.TabPlacement;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.awt.event.HierarchyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.swing.JComponent;

/**
 * One page in one pane: a content tab whose one view holds the toolbar above the page's canvas, a custom content of
 * kind PREVIEW. The item asks for its page, and so for Chromium, the first time its view shows, not when it opens. Its
 * tab shows the page's title (as {@link TabTitle} gives it), its icon and, as its location and status, the page's
 * address, which the address field shows too, each without a user name or password. The icon, in the tab and at the
 * field's left, is the page's favicon once the page has loaded, Jasper's own icon on Jasper's {@code jasper-about:}
 * pages, and the globe while the page loads and whenever it has no favicon (spec sections 5 and 6). Its content
 * commands {@code copy} and {@code paste} are what Jasper's Copy and Paste run in it, and the mouse's back and forward
 * buttons over the page go back and forward. It saves its URL and target with Jasper's workspace
 * ({@link BrowserRestore}): what it loads first until it shows, then where each committed navigation takes its page.
 * Restored after a run that ended while Chromium ran, an item waits: in place of the canvas it says that Jasper closed
 * while the page was open, and it asks for its page only once the user loads it, with its Load Page button, Enter in the
 * address field, Reload or {@link #load}. UI thread.
 */
public final class BrowserItem implements AutoCloseable {
    static final String ROOT = "browser", PAGE = "browser.page", CONTENT = "browser.page.content";
    /** A waiting item's placeholder, and its Load Page button and the action it reports. */
    static final String WAITING = "browser.waiting", LOAD_PAGE = "browser.waiting.load";
    private static final ControlState HIDDEN = new ControlState(false, true, false, "");
    // Each item's own: reopening a content key selects the tab that has it.
    private static final String KEY = "browser:";
    // The longest address the tab's location and status show; a data: address can run to megabytes.
    private static final int SHOWN_ADDRESS = 2_000;
    private final BrowserItems items;
    private final Optional<String> target;
    private final Page page;
    private final BrowserToolbar toolbar = new BrowserToolbar();
    private final UiView view;
    private ContentTab tab;
    private URI url;
    private String title;
    private boolean waiting, asked, closed;

    private BrowserItem(BrowserItems items, URI url, Optional<String> target, boolean waiting) {
        this.items = items;
        this.target = target;
        this.url = url;
        this.waiting = waiting;
        page = new Page(url, this::changed, items.favicons());
        page.onNavigationButton(this::navigationButton);
        page.onOpenItem(this::openItem);
        page.onCloseItem(this::close);
        page.onShowItem(this::show);
        page.onAddressChange(this::committed);
        title = TabTitle.of("", host(url.toString()), "", false);
        view = items.context().kit().create(spec(), this::event);
        view.registerContent(new CustomContent(CONTENT, Nodes.CustomKind.PREVIEW, page.view(), page));
        page.view().addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && page.view().isShowing()) shown();
        });
    }

    /**
     * Opens a browser item at {@code url}. Its page loads the first time the item shows.
     *
     * @param items the plugin's items, which keep it while it is open
     * @param url what it loads
     * @param placement where it opens
     * @param target the OSC 8 target it is for, or empty
     * @param window the window it opens from, or empty for the most recently active one
     * @return the item
     */
    public static BrowserItem open(BrowserItems items, URI url, TabPlacement placement, Optional<String> target,
                                   Optional<WindowOwner> window) {
        return open(items, url, placement, target, window, false);
    }

    /** As {@link #open(BrowserItems, URI, TabPlacement, Optional, Optional)}; with {@code waiting}, the item waits. */
    static BrowserItem open(BrowserItems items, URI url, TabPlacement placement, Optional<String> target,
                            Optional<WindowOwner> window, boolean waiting) {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(window, "window");
        var item = new BrowserItem(items, url, target, waiting);
        item.attach(placement, window);
        return item;
    }

    private void attach(TabPlacement placement, Optional<WindowOwner> window) {
        var spec = new ContentSpec(KEY + UUID.randomUUID(), title);
        try {
            tab = items.context().windows().openTab(restore(url).map(spec::withRestore).orElse(spec), window.orElse(null), view, placement);
        } catch (RuntimeException | Error failure) {
            view.close();
            throw failure;
        }
        tab.setFocusTarget(this::focusContent);
        // No key of their own: Jasper's Copy and Paste run them, on the keys the keymap gives those.
        tab.setCommands(List.of(new ContentCommand("copy", "Copy", null, true, this::copy),
            new ContentCommand("paste", "Paste", null, true, this::paste)));
        tab.onClosed(this::closed);
        // The page's alerts, confirmations and file choosers show over this item's tab, and close with it.
        page.setDialogPlace(new DialogPlace(items.context().kit(), items.context().windows(), tab));
        items.opened(this);
        present();
    }

    /**
     * Where the page is.
     *
     * @return its address
     */
    public URI url() {
        return url;
    }

    /**
     * The OSC 8 target the item was opened for.
     *
     * @return the target, or empty
     */
    public Optional<String> target() {
        return target;
    }

    /**
     * The item's tab.
     *
     * @return the tab
     */
    public ContentTab tab() {
        return tab;
    }

    /**
     * Loads {@code url} in the item; the address field gives up what was typed in it. Before the item first shows, it is
     * also what the item saves, as it is what the item will load. A waiting item stops waiting.
     *
     * @param url the address
     */
    public void load(URI url) {
        Objects.requireNonNull(url, "url");
        if (closed) return;
        toolbar.restore();
        page.load(url);
        if (!asked && tab != null) tab.setRestore(restore(url));
        endWait();
    }

    /** Goes back in the page's history. */
    public void goBack() {
        if (!closed) page.back();
    }

    /** Goes forward in the page's history. */
    public void goForward() {
        if (!closed) page.forward();
    }

    /** Loads the page again; a waiting item stops waiting and loads its address. */
    public void reload() {
        if (closed) return;
        if (waiting) endWait();
        else page.reload();
    }

    /** Stops the page loading. */
    public void stop() {
        if (!closed) page.stop();
    }

    /** Copies the page's selection. */
    public void copy() {
        if (!closed) page.copy();
    }

    /** Pastes the clipboard into the page. */
    public void paste() {
        if (!closed) page.paste();
    }

    /** Focuses the address field and selects its text, as Open Location does. */
    public void focusAddress() {
        if (closed) return;
        view.focus(BrowserToolbar.ADDRESS);
        view.selectAll(BrowserToolbar.ADDRESS);
    }

    /** Closes the item without asking. */
    @Override public void close() {
        if (tab != null) tab.close();
        else view.close();
    }

    /** Whether the item has asked for its page, which it does the first time it shows. */
    boolean pageAsked() {
        return asked;
    }

    /** Whether the item waits to be loaded. */
    boolean waiting() {
        return waiting;
    }

    /** The page's canvas. */
    JComponent pageView() {
        return page.view();
    }

    /** The item's page. */
    Page page() {
        return page;
    }

    private void shown() {
        if (asked || closed || waiting) return;
        asked = true;
        items.start(page);
    }

    /**
     * The wait ends: the canvas takes the placeholder's place, and the page is asked for once the canvas shows, at once
     * when it shows now.
     */
    private void endWait() {
        if (!waiting || closed) return;
        waiting = false;
        render();
        if (page.view().isShowing()) shown();
    }

    /** What the tab's focus goes to: Load Page while the item waits, else the page. */
    private void focusContent() {
        if (waiting && !closed) view.focus(LOAD_PAGE);
        else page.focus();
    }

    private void closed() {
        closed = true;
        items.closed(this);
        view.close();
        page.close();
    }

    /** The mouse's back or forward button was released over the page. */
    private void navigationButton(NavigationButton button) {
        if (button == NavigationButton.BACK) goBack();
        else goForward();
    }

    /**
     * A link or popup of the page opens an item of its own beside this one, for no OSC 8 target: in a new tab right
     * after this item's tab, or split from this item's pane. A split opens beside the focused pane of the window's shown
     * tab, so this item is selected first when it is not that. An address that is no URI opens nothing. Answers the new
     * item's page, which the opener bridge links to this one.
     */
    private Optional<Page> openItem(String address, TabPlacement placement) {
        if (closed || tab == null) return Optional.empty();
        Optional<URI> link = AddressInput.parse(address);
        if (link.isEmpty()) return Optional.empty();
        if (placement != TabPlacement.NEXT_TAB && !tab.isActive()) tab.select();
        try {
            return Optional.of(items.open(link.get(), placement, Optional.empty(), Optional.of(tab)).page);
        } catch (IllegalStateException closing) { // the windows close, or Jasper quits
            items.context().log().log(System.Logger.Level.WARNING, "A link could not open: " + closing.getMessage(), closing);
            return Optional.empty();
        }
    }

    /** The page's main frame committed a navigation to {@code address}: the item saves it. */
    private void committed(String address) {
        if (closed || tab == null) return;
        AddressInput.parse(address).ifPresent(committed -> tab.setRestore(restore(committed)));
    }

    /** The item's restore state at {@code at}, for the target it was opened for. */
    private Optional<RestoreState> restore(URI at) {
        return BrowserRestore.state(at, target);
    }

    /** The page asks to be shown, as a script's {@code focus()} of its window does: selects the tab and raises its window. */
    private void show() {
        if (!closed && tab != null) tab.select();
    }

    /** The page's address, title, loading state or where it can go changed. */
    private void changed() {
        if (closed) return;
        url = AddressInput.parse(page.address()).orElse(url);
        render();
        present();
    }

    private void event(UiEvent event) {
        if (closed) return;
        switch (event) {
            case UiEvent.Edited edited when edited.id().equals(BrowserToolbar.ADDRESS) -> {
                toolbar.edited(edited.text());
                render();
            }
            case UiEvent.Action action -> act(action.id());
            default -> { }
        }
    }

    private void act(String action) {
        switch (action) {
            case BrowserToolbar.BACK -> goBack();
            case BrowserToolbar.FORWARD -> goForward();
            case BrowserToolbar.RELOAD -> reload();
            case BrowserToolbar.STOP -> stop();
            case BrowserToolbar.ENTER -> toolbar.enter(page.address()).ifPresent(address -> {
                load(address);
                page.focus();
            });
            case BrowserToolbar.ESCAPE -> {
                toolbar.restore();
                render();
                view.selectAll(BrowserToolbar.ADDRESS);
            }
            case BrowserToolbar.SETTINGS -> items.showSettings();
            case LOAD_PAGE -> {
                endWait();
                page.focus();
            }
            default -> { }
        }
    }

    private void render() {
        if (!closed) view.update(spec());
    }

    private void present() {
        if (closed || tab == null) return;
        String address = shown(page.address());
        title = TabTitle.of(page.title(), host(address), title, page.loading());
        String shown = address.length() <= SHOWN_ADDRESS ? address : address.substring(0, SHOWN_ADDRESS) + "…";
        // The globe stays the tab's icon under an image, for one that does not decode.
        var presentation = new ContentPresentation(title, "", BrowserItems.GLOBE, false, shown, "");
        tab.setPresentation(icon().map(presentation::withImage).orElse(presentation));
        tab.setStatus(List.of(new ContentStatus(shown, shown, null)));
    }

    /**
     * The image the tab and the address field show for the page: none while it loads, so the globe shows; Jasper's icon
     * on Jasper's own pages; else the page's favicon, if it has one.
     */
    private Optional<PixelImage> icon() {
        if (page.loading()) return Optional.empty();
        String address = page.address();
        boolean jaspers = address.regionMatches(true, 0, AboutScheme.NAME + ":", 0, AboutScheme.NAME.length() + 1);
        return jaspers ? Optional.of(JasperIcon.IMAGE) : page.favicon();
    }

    private ViewSpec spec() {
        Nodes.Node bar = toolbar.node(shown(page.address()), page.loading(), page.canGoBack(), page.canGoForward(), icon());
        // A waiting item keeps the canvas's slot, hidden: a slot that leaves the view closes its content, the page.
        Nodes.Node canvas = new Nodes.Custom(PAGE, waiting ? HIDDEN : ControlState.READY, CONTENT, Nodes.CustomKind.PREVIEW, "Web page");
        return new ViewSpec(new Nodes.Split(ROOT, ControlState.READY, Nodes.SplitKind.DOCUMENT,
            waiting ? List.of(bar, canvas, placeholder()) : List.of(bar, canvas)));
    }

    /** What a waiting item shows in place of its page: why it waits, and Load Page. */
    private static Nodes.Empty placeholder() {
        return new Nodes.Empty(WAITING, ControlState.READY, new Nodes.Icon(WAITING + ".icon", ControlState.READY, BrowserItems.GLOBE,
            Nodes.Hue.NONE, Nodes.IconSize.EMPTY, "Web page"), "Jasper closed while this page was open.", "",
            List.of(new Nodes.Button(LOAD_PAGE, ControlState.READY, "Load Page", "", Nodes.ButtonKind.PRIMARY, LOAD_PAGE, "")));
    }

    private static Optional<String> host(String address) {
        return AddressInput.parse(address).map(URI::getHost);
    }

    /** Jasper's own icon, which Jasper's pages show: the plugin's copy of the application's 32-pixel PNG, read once needed. */
    private static final class JasperIcon {
        static final PixelImage IMAGE = read();

        private static PixelImage read() {
            try (InputStream in = BrowserItem.class.getResourceAsStream("jasper.png")) {
                if (in == null) throw new IllegalStateException("The Browser plugin has no jasper.png");
                return new PixelImage(in.readAllBytes());
            } catch (IOException unreadable) {
                throw new UncheckedIOException(unreadable);
            }
        }
    }

    /** {@code address} as the item shows it: without a user name or password. */
    private static String shown(String address) {
        if (address.indexOf('@') < 0) return address; // user information needs one; a data: address can run to megabytes
        try {
            return BrowserRestore.withoutUserInfo(new URI(address)).toString();
        } catch (URISyntaxException notAUri) {
            return address;
        }
    }
}
