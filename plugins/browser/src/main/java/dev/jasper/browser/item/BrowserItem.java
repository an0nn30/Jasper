package dev.jasper.browser.item;

import dev.jasper.browser.engine.AboutScheme;
import dev.jasper.browser.page.DevTools;
import dev.jasper.browser.page.DialogPlace;
import dev.jasper.browser.page.MenuPlace;
import dev.jasper.browser.page.NavigationButton;
import dev.jasper.browser.page.NavigationHistory;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.pages.BookmarksPage;
import dev.jasper.browser.pages.HistoryPage;
import dev.jasper.browser.restore.BrowserRestore;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.browser.store.BookmarkStore.Bookmark;
import dev.jasper.browser.store.Stores;
import dev.jasper.sdk.RestoreState;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.notifications.NotificationGroup;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.ContentCommand;
import dev.jasper.sdk.ui.ContentPresentation;
import dev.jasper.sdk.ui.ContentSpec;
import dev.jasper.sdk.ui.ContentStatus;
import dev.jasper.sdk.ui.ContentTab;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.CustomContent;
import dev.jasper.sdk.ui.InsertedText;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.PixelImage;
import dev.jasper.sdk.ui.TabPlacement;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.event.HierarchyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.swing.JComponent;

/**
 * One page in one pane: a content tab whose one view holds the toolbar above the page's canvas, a custom content of
 * kind PREVIEW. The item asks for its page, and so for Chromium, the first time its view shows, not when it opens. Its
 * tab shows the page's title (as {@link TabTitle} gives it), its icon and, as its location and status, the page's
 * address, which the address field shows too, each without a user name or password. The icon, in the tab and at the
 * field's left, is the page's favicon once the page has loaded, Jasper's own icon on Jasper's {@code jasper-about:}
 * pages, and the globe while the page loads and whenever it has no favicon (spec sections 5 and 6). As the user types
 * into the address field, it lists the field's {@link Suggestions} at once, with the bookmarks and visited sites that
 * match ({@link StoreSuggestions}), and asks the chosen engine for its own 150 ms after the last keystroke, each
 * keystroke superseding the request before; Tab or Enter on a suggestion loads it, and Enter with none shown loads what
 * the text resolves to, each then focusing the page. The bookmarks and visits load the first time the item shows or its
 * field is edited, and the list follows them as they change. Its {@link HistoryRecorder} records each page load in
 * history (spec section 10). Its content commands
 * {@code copy} and {@code paste} are what Jasper's Copy and Paste run in it, and {@code find}, {@code find-next} and
 * {@code find-previous}, on the keys of Editor tabs, what Jasper's Find, Find Next and Find Previous run: its
 * {@link BrowserFindBar}, the view's last row. Its {@code font-bigger}, {@code font-smaller} and {@code font-reset},
 * what Jasper's Increase, Decrease and Reset Font Size run, as in Editor tabs, zoom the page a {@link Zoom} step up or
 * down, which the item saves as its own zoom, or back to Page zoom, which leaves it none; on Windows and Linux,
 * Ctrl+wheel over the page steps the zoom too, unsaved. Chromium keeps one zoom per host, so two items on one host that
 * show at once share the zoom set last (an engine-forced gap). The mouse's back and forward buttons over the page go
 * back and forward, and an entry chosen from Back's or Forward's hold menu goes to that entry of the page's history,
 * while their Show Full History loads Jasper's history page ({@link #showHistory}). The menu button's Add Bookmark
 * bookmarks the page at once and opens its {@link BookmarkPanel}, Remove Bookmark removes the page's bookmark and says
 * "Bookmark Removed", and Manage Bookmarks and History load Jasper's bookmarks and history pages. It
 * saves its URL, its own zoom and its target with Jasper's workspace ({@link BrowserRestore}): what it loads first until
 * it shows, then where each committed navigation takes its page.
 * Restored after a run that ended while Chromium ran, an item waits: in place of the canvas it says that Jasper closed
 * while the page was open, and it asks for its page only once the user loads it, with its Load Page button, Enter in the
 * address field, Reload or {@link #load}.
 * A main-frame load that fails shows Jasper's {@link ErrorPage} in place of the page (spec section 14), as its
 * {@link ErrorPages} decide: while it shows, the field, the tab and the restore state keep the address that failed, the
 * tab is called by the error's title, Back, Reload, Try Again and Continue act as Chrome's do, and Enter in the untouched
 * field loads the failed address as typed text would. While the page last committed is at an https address of a host
 * the user went on to past a certificate error, the toolbar says "Not secure". View Source, from the page's right-click
 * menu, shows the page's source in the item at {@code jasper-about:source} (spec section 10), as its {@link SourcePages}
 * capture it; the item saves a source page without its token. Inspect Element, from the same menu, shows the page's
 * DevTools, inspecting the element under the pointer, in the item's one {@link DevToolsItem}, a pane below its own,
 * which closes with it. Text another plugin inserts into the item, such as a snippet from the command palette (spec
 * section 15), goes into the page's focused field ({@link Page#insertIntoField}), then Return when asked. When the
 * page's script finds no field it reaches focused, as with the focus in a cross-origin frame, the text is typed where the
 * focus is ({@link Page#insertText}), then Return when asked; the clipboard is never touched. While Copy on select is on
 * ({@code Platform.copyOnSelect()}, spec section 15), each selection the user finishes in the page is copied
 * ({@link Page#onSelectionGesture}, {@link Page#selectedText}), except one in a frame of another origin. UI thread.
 */
public final class BrowserItem implements AutoCloseable {
    static final String ROOT = "browser", PAGE = "browser.page", CONTENT = "browser.page.content";
    /** A waiting item's placeholder, and its Load Page button and the action it reports. */
    static final String WAITING = "browser.waiting", LOAD_PAGE = "browser.waiting.load";
    private static final ControlState HIDDEN = new ControlState(false, true, false, "");
    // How long after the last keystroke the engine is asked for suggestions.
    private static final Duration SUGGESTION_DELAY = Duration.ofMillis(150);
    private static final Runnable NOTHING = () -> { };
    // Each item's own: reopening a content key selects the tab that has it.
    private static final String KEY = "browser:";
    // The longest address the tab's location and status show; a data: address can run to megabytes.
    private static final int SHOWN_ADDRESS = 2_000;
    private final BrowserItems items;
    // The history and bookmarks the item records into and suggests from, read once, as it opens.
    private final Stores stores;
    private final Optional<String> target;
    private final Page page;
    private final ErrorPages errors;
    private final SourcePages sources;
    private final HistoryRecorder history;
    private final BrowserToolbar toolbar = new BrowserToolbar(this::storeSuggestions);
    // The item's listening to its bookmarks and visits, which ends as it closes.
    private final Subscription bookmarksChanged, visitsChanged;
    private final BrowserFindBar findBar;
    private final UiView view;
    private ContentTab tab;
    // The item's DevTools item while it is open; null before the first Inspect Element and once it closes.
    private DevToolsItem devTools;
    private URI url;
    // Where the item's restore state says it is: what it loads first until it shows, then where its page committed.
    private URI saved;
    // The zoom the page shows at, in percent; and the zoom the item saves, the one the user chose, or none for Page zoom.
    private int zoom;
    private OptionalInt ownZoom;
    private String title;
    private boolean waiting, asked, closed;
    // How many main-frame loads the page has started: a bookmark's panel opens only if none has since Add Bookmark.
    private long loadStarts;
    // Cancels the wait for the typing to pause; and the engine's answer the field waits for, or null.
    private Runnable typingPause = NOTHING;
    private CompletableFuture<List<String>> suggestion;

    private BrowserItem(BrowserItems items, URI url, Optional<String> target, OptionalInt zoom, boolean waiting) {
        this.items = items;
        stores = items.stores();
        this.target = target;
        this.url = url;
        saved = url;
        ownZoom = zoom;
        this.waiting = waiting;
        page = new Page(url, this::changed, items.favicons());
        showZoom(zoom.orElseGet(() -> items.settings().pageZoom()));
        page.onNavigationButton(this::navigationButton);
        page.onMagnify(this::magnify);
        page.onOpenItem(this::openItem);
        page.onCloseItem(this::close);
        page.onShowItem(this::show);
        page.onAddressChange(this::committed);
        page.onLoadStart(address -> loadStarts++);
        page.onSelectionGesture(this::selectionFinished);
        errors = new ErrorPages(page, items.certificates(), this::load);
        page.onLoadFailure(failure -> { if (!closed) errors.failed(failure); });
        sources = new SourcePages(page, this::load);
        history = new HistoryRecorder(page, stores, items::later);
        title = TabTitle.of("", host(url.toString()), "", false);
        findBar = new BrowserFindBar(() -> closed ? Optional.empty() : Optional.of(page.find()), this::render, this::focusFind);
        view = items.context().kit().create(spec(), this::event);
        view.registerContent(new CustomContent(CONTENT, Nodes.CustomKind.PREVIEW, page.view(), page));
        page.view().addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && page.view().isShowing()) shown();
        });
        // The item draws again as the bookmarks change, and as the visits do while something is typed, for the field's list.
        bookmarksChanged = stores.bookmarkIndex().onChanged(this::render);
        visitsChanged = stores.visitIndex().onChanged(() -> { if (toolbar.typing()) render(); });
    }

    /**
     * Opens a browser item at {@code url}. Its page loads the first time the item shows.
     *
     * @param items the plugin's items, which keep it while it is open
     * @param url what it loads
     * @param placement where it opens
     * @param target the OSC 8 target it is for, or empty
     * @param window the window it opens from, or empty for the most recently active one
     * @return the item, at Page zoom
     */
    public static BrowserItem open(BrowserItems items, URI url, TabPlacement placement, Optional<String> target,
                                   Optional<WindowOwner> window) {
        return open(items, url, placement, target, window, OptionalInt.empty(), false);
    }

    /**
     * As {@link #open(BrowserItems, URI, TabPlacement, Optional, Optional)}, at {@code zoom}, a zoom of its own from
     * {@link Zoom#MIN} to {@link Zoom#MAX}, or empty for Page zoom; with {@code waiting}, the item waits.
     */
    static BrowserItem open(BrowserItems items, URI url, TabPlacement placement, Optional<String> target,
                            Optional<WindowOwner> window, OptionalInt zoom, boolean waiting) {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(zoom, "zoom");
        var item = new BrowserItem(items, url, target, zoom, waiting);
        item.attach(placement, window);
        return item;
    }

    private void attach(TabPlacement placement, Optional<WindowOwner> window) {
        var spec = new ContentSpec(KEY + UUID.randomUUID(), title);
        try {
            tab = items.context().windows().openTab(restore(url).map(spec::withRestore).orElse(spec), window.orElse(null), view, placement);
        } catch (RuntimeException | Error failure) {
            bookmarksChanged.close();
            visitsChanged.close();
            view.close();
            throw failure;
        }
        tab.setFocusTarget(this::focusContent);
        // Copy, Paste and the font size commands have no key of their own: Jasper's run them, on the keys the keymap gives
        // those, as in Editor tabs. Find's are the Editor's, which on macOS are Jasper's own.
        tab.setCommands(List.of(new ContentCommand("copy", "Copy", null, true, this::copy),
            new ContentCommand("paste", "Paste", null, true, this::paste),
            new ContentCommand("find", "Find…", BrowserFindBar.findKey(), true, findBar::open),
            new ContentCommand("find-next", "Find Next", BrowserFindBar.findAgainKey(false), true, findBar::next),
            new ContentCommand("find-previous", "Find Previous", BrowserFindBar.findAgainKey(true), true, findBar::previous),
            new ContentCommand("font-bigger", "Increase Font Size", null, true, () -> zoomTo(Zoom.step(zoom, 1))),
            new ContentCommand("font-smaller", "Decrease Font Size", null, true, () -> zoomTo(Zoom.step(zoom, -1))),
            new ContentCommand("font-reset", "Reset Font Size", null, true, this::resetZoom)));
        tab.setTextInsertion(Optional.of(this::insert));
        tab.onClosed(this::closed);
        // The page's alerts, confirmations and file choosers show over this item's tab, and close with it; so does its
        // right-click menu, which copies with the plugin's clipboard and searches with the engine chosen as it opens.
        page.setDialogPlace(new DialogPlace(items.context().kit(), items.context().windows(), tab));
        page.setMenuPlace(new MenuPlace(tab, items.context().platform()::copy, this::engineName, this::search, errors::canGoBack,
            this::goBack, this::reload, this::viewSource, this::inspect));
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
        endSuggestions();
        page.load(url);
        if (!asked && tab != null) save(url);
        endWait();
    }

    /** Goes back in the page's history: from an error page, to the page before the failed load ({@link ErrorPages#back}). */
    public void goBack() {
        if (!closed) errors.back();
    }

    /** Goes forward in the page's history. */
    public void goForward() {
        if (!closed) page.forward();
    }

    /** Loads the page again, or on an error page what failed; a waiting item stops waiting and loads its address. */
    public void reload() {
        if (closed) return;
        if (waiting) endWait();
        else errors.reload();
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

    /**
     * Text another plugin inserts into the item, as the tab's insertion receives it (H30). It goes into the page's
     * focused field, then Return when {@code inserted} asks. When the page's script answers that no field it reaches has
     * the focus, the text is typed where the focus is, then Return when asked. Each answer comes back to the UI thread,
     * and an item closed by then does nothing. Any other outcome does nothing more: a page without its browser yet, or a
     * DevTools call that is late, refused or answered with an error, as the text may still go in.
     */
    private void insert(InsertedText inserted) {
        if (closed) return;
        String text = inserted.text();
        page.insertIntoField(text).whenComplete((taken, failure) -> EventQueue.invokeLater(() -> {
            if (closed) return;
            if (failure != null) {
                items.context().log().log(System.Logger.Level.DEBUG, "Text could not be put into a page's field", failure);
            } else if (taken) {
                if (inserted.submit()) page.pressReturn();
            } else {
                page.insertText(text).whenComplete((typed, notTyped) -> EventQueue.invokeLater(() -> {
                    if (closed) return;
                    if (notTyped != null) items.context().log().log(System.Logger.Level.DEBUG, "Text could not be typed into a page", notTyped);
                    else if (inserted.submit()) page.pressReturn();
                }));
            }
        }));
    }

    /**
     * The user finished a selection in the page. While Copy on select is on, the page's selection is read, and its text,
     * if any, is copied on the UI thread, if Copy on select is still on and the item still open by then. A selection in a
     * frame of another origin is not copied (F2): Chromium's copy there would run that frame's scripts, which could put
     * anything on the clipboard; Copy copies it. A read that fails copies nothing.
     */
    private void selectionFinished() {
        if (closed || items.closed() || !items.context().platform().copyOnSelect()) return;
        page.selectedText().whenComplete((selection, failure) -> EventQueue.invokeLater(() -> {
            if (closed || items.closed()) return;
            if (failure != null) {
                items.context().log().log(System.Logger.Level.DEBUG, "A page's selection could not be read", failure);
            } else if (selection instanceof Page.Selection.Text selected && !selected.text().isEmpty()
                && items.context().platform().copyOnSelect()) {
                items.context().platform().copy(selected.text());
            }
        }));
    }

    /**
     * Loads Jasper's history page, and the page takes the focus, as Web › History and the hold menus' Show Full History
     * do.
     */
    public void showHistory() {
        if (!closed) loadAndFocus(HistoryPage.ADDRESS);
    }

    /** Loads Jasper's bookmarks page, and the page takes the focus, as the menu button's Manage Bookmarks does. */
    void showBookmarks() {
        if (!closed) loadAndFocus(BookmarksPage.ADDRESS);
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

    /**
     * An error page's Try Again with {@code token}: while the item shows its error page of that token, it loads what
     * failed there again.
     */
    void tryAgain(String token) {
        if (!closed) errors.tryAgain(token);
    }

    /**
     * An error page's Continue with {@code token}: while the item shows its error page whose Continue has that token, and
     * only once, the host and certificate the item kept for it are allowed and it loads again.
     */
    void proceed(String token) {
        if (!closed) errors.proceed(token);
    }

    /**
     * View Source: the page's HTML is captured, and the item loads the page's source page ({@link SourcePages#show}).
     */
    void viewSource() {
        if (!closed) sources.show();
    }

    /**
     * Inspect Element at {@code at} on the page's canvas: the page's DevTools at that point, shown in the item's DevTools
     * item ({@link #showDevTools}); none before the page's browser is ready.
     */
    void inspect(Point at) {
        if (!closed) page.inspect(at).ifPresent(this::showDevTools);
    }

    /**
     * Shows {@code tools}, the page's DevTools: in the item's DevTools item, which is selected, or, while it has none
     * open, in a new one below the item's pane. DevTools that no item can show close.
     */
    void showDevTools(DevTools tools) {
        if (closed || tab == null) {
            tools.close();
            return;
        }
        if (devTools != null && !devTools.closed()) {
            devTools.show(tools);
            return;
        }
        DevToolsItem opened;
        try {
            opened = DevToolsItem.open(items.context(), tab, tools, title);
        } catch (IllegalStateException closing) { // the windows close, or Jasper quits
            items.context().log().log(System.Logger.Level.WARNING, "DevTools could not open: " + closing.getMessage(), closing);
            return;
        }
        if (opened.closed()) return; // DevTools Chromium could not open
        devTools = opened;
        // A closed DevTools item, its canvas and Chromium's browser are let go.
        opened.tab().onClosed(() -> { if (devTools == opened) devTools = null; });
    }

    /** The item's DevTools item while it is open. */
    Optional<DevToolsItem> devToolsItem() {
        return Optional.ofNullable(devTools);
    }

    /**
     * Whether the item, or its DevTools item, is the item of the focused pane in {@code window}'s shown tab: the Web
     * menu acts on the item from either.
     */
    boolean focusedIn(WindowHandle window) {
        return focused(tab, window) || devTools != null && !devTools.closed() && focused(devTools.tab(), window);
    }

    private static boolean focused(ContentTab tab, WindowHandle window) {
        return tab.isActive() && tab.window().id().equals(window.id());
    }

    /**
     * The HTML the item captured for View Source under {@code token} for the page at {@code address}.
     *
     * @param token the token a source page's address carries
     * @param address the page's address, without user information
     * @return the HTML; empty when the item holds no such capture
     */
    Optional<String> capturedSource(String token, String address) {
        return closed ? Optional.empty() : sources.source(token, address);
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

    /** The item's find bar. */
    BrowserFindBar findBar() {
        return findBar;
    }

    /** The item's page. */
    Page page() {
        return page;
    }

    private void shown() {
        if (asked || closed || waiting) return;
        asked = true;
        loadIndexes();
        items.start(page);
    }

    /** The bookmarks and visits load, the first time any item asks. */
    private void loadIndexes() {
        stores.bookmarkIndex().load();
        stores.visitIndex().load();
    }

    /** The rows of the bookmarks and visits for {@code typed}, as their indexes hold them. */
    private List<Nodes.Suggestion> storeSuggestions(String typed) {
        return StoreSuggestions.of(typed, stores.bookmarkIndex().all(), stores.visitIndex().all());
    }

    /** The plugin stops: the page's last title lands in history, before the stores close. */
    void flushHistory() {
        history.flush();
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

    /** The find bar gives the focus to its query field, the field's text selected, or as it closes to the page. */
    private void focusFind(String id) {
        if (closed) return;
        if (id.equals(PAGE)) {
            focusContent();
            return;
        }
        view.focus(id);
        view.selectAll(id);
    }

    private void closed() {
        closed = true;
        history.flush();
        bookmarksChanged.close();
        visitsChanged.close();
        endSuggestions();
        sources.close();
        if (devTools != null) devTools.close();
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

    /**
     * The page's main frame committed a navigation to {@code address}: the item saves it, or what failed for its error
     * page, and keeps what it saved for an error page that is none of its own. The toolbar shows whether the page now
     * committed is not secure.
     */
    private void committed(String address) {
        if (closed || tab == null) return;
        errors.committed(address).flatMap(AddressInput::parse).ifPresent(this::save);
        render();
    }

    /** The item saves that it is at {@code at}, with its own zoom and the target it was opened for. */
    private void save(URI at) {
        saved = at;
        tab.setRestore(restore(at));
    }

    /**
     * The item's restore state at {@code at}, with its own zoom, for the target it was opened for: a source page without
     * its token, which ends with the item.
     */
    private Optional<RestoreState> restore(URI at) {
        return BrowserRestore.state(SourcePage.saved(at), target, ownZoom);
    }

    /** Increase or Decrease Font Size: the page shows at {@code percent}, which the item saves as its own zoom. */
    private void zoomTo(int percent) {
        if (closed) return;
        showZoom(percent);
        ownZoom = OptionalInt.of(percent);
        save(saved);
    }

    /** Reset Font Size: the page shows at Page zoom, and the item saves no zoom of its own, so it comes back at Page zoom. */
    private void resetZoom() {
        if (closed) return;
        showZoom(items.settings().pageZoom());
        ownZoom = OptionalInt.empty();
        save(saved);
    }

    /** Ctrl+wheel over the page: it shows a step bigger, 1, or smaller, -1, which the item does not save. */
    private void magnify(int direction) {
        if (!closed) showZoom(Zoom.step(zoom, direction));
    }

    private void showZoom(int percent) {
        zoom = percent;
        page.setZoomLevel(Zoom.cefLevel(percent));
    }

    /** The chosen search engine's name, as the page's right-click menu says it in Search with (P26). */
    private String engineName() {
        BrowserSettings now = items.settings();
        return now.searchEngine().displayName(now);
    }

    /** A search for {@code terms} with the chosen search engine. */
    private URI search(String terms) {
        BrowserSettings now = items.settings();
        return now.searchEngine().search(terms, now);
    }

    /** The page asks to be shown, as a script's {@code focus()} of its window does: selects the tab and raises its window. */
    private void show() {
        if (!closed && tab != null) tab.select();
    }

    /** The page's address, title, loading state or where it can go changed. */
    private void changed() {
        if (closed) return;
        history.titled(page.title());
        url = AddressInput.parse(errors.address()).orElse(url);
        render();
        present();
    }

    private void event(UiEvent event) {
        if (closed || findBar.event(event)) return;
        switch (event) {
            case UiEvent.Edited edited when edited.id().equals(BrowserToolbar.ADDRESS) -> {
                loadIndexes();
                toolbar.edited(edited.text());
                suggest(edited.text());
                render();
            }
            case UiEvent.SuggestionChosen chosen when chosen.fieldId().equals(BrowserToolbar.ADDRESS) ->
                Suggestions.target(chosen.suggestionId(), items.settings()).ifPresent(this::loadAndFocus);
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
            case BrowserToolbar.ENTER -> toolbar.enter(errors.address(), items.settings()).ifPresent(this::loadAndFocus);
            case BrowserToolbar.ESCAPE -> {
                toolbar.restore();
                endSuggestions();
                render();
                view.selectAll(BrowserToolbar.ADDRESS);
            }
            case BrowserToolbar.SETTINGS -> items.showSettings();
            case BrowserToolbar.SHOW_HISTORY, BrowserToolbar.HISTORY -> showHistory();
            case BrowserToolbar.BOOKMARKS -> showBookmarks();
            case BrowserToolbar.BOOKMARK_ADD -> addBookmark();
            case BrowserToolbar.BOOKMARK_REMOVE -> removeBookmark();
            case LOAD_PAGE -> {
                endWait();
                page.focus();
            }
            // An entry of Back's or Forward's hold menu.
            default -> NavigationHistory.entryId(action).ifPresent(page::goToHistoryEntry);
        }
    }

    /**
     * Add Bookmark: the page is bookmarked at once, at the address the item shows, with its title, or none while the item
     * shows its error page; then, unless the item has closed or its page has started another load meanwhile, the
     * bookmark's {@link BookmarkPanel} opens from the menu button. The bookmarks load, if they have not, so the menu
     * offers Remove Bookmark once the bookmark is stored.
     */
    private void addBookmark() {
        loadIndexes();
        long started = loadStarts;
        String title = errors.shown().isPresent() ? "" : page.title();
        CompletableFuture<Bookmark> added = stores.bookmarks().add(errors.address(), title, System.currentTimeMillis());
        added.whenComplete((bookmark, failure) -> EventQueue.invokeLater(() -> {
            if (failure != null) {
                items.context().log().log(System.Logger.Level.DEBUG, "A page could not be bookmarked", failure);
                return;
            }
            if (!closed && !items.closed() && tab != null && loadStarts == started) openPanel(bookmark);
        }));
    }

    /** The bookmark's panel, from the menu button; none while the button cannot anchor one. */
    private void openPanel(Bookmark bookmark) {
        try {
            BookmarkPanel.open(items.context(), view.anchor(BrowserToolbar.MENU), stores, bookmark, bookmarkNotices(), page, tab);
        } catch (IllegalArgumentException | IllegalStateException unanchored) { // the view or its menu button cannot anchor now
            items.context().log().log(System.Logger.Level.DEBUG, "The bookmark's panel could not open", unanchored);
        }
    }

    /** Remove Bookmark: the page's bookmark is removed, and once it is gone "Bookmark Removed" says so, its title only. */
    private void removeBookmark() {
        stores.bookmarks().remove(errors.address()).whenComplete((removed, failure) -> EventQueue.invokeLater(() -> {
            if (failure != null) {
                items.context().log().log(System.Logger.Level.DEBUG, "A bookmark could not be removed", failure);
                return;
            }
            if (removed && !items.closed()) bookmarkNotices().info("Bookmark Removed", "");
        }));
    }

    /** The group the bookmark notifications post in, which the plugin declared as it started: the same group again. */
    private NotificationGroup bookmarkNotices() {
        return items.context().notifications().group(BookmarkPanel.GROUP);
    }

    /** The address field loads {@code address}, and the page takes the focus. */
    private void loadAndFocus(URI address) {
        load(address);
        page.focus();
    }

    /**
     * The field's text is now {@code typed}: the request before is superseded, and unless P24 keeps the text from the
     * engine, the engine is asked once the typing pauses.
     */
    private void suggest(String typed) {
        endSuggestions();
        Optional<URI> request = Suggestions.request(typed, items.settings());
        if (request.isEmpty()) return;
        toolbar.suggesting();
        typingPause = items.later(SUGGESTION_DELAY, () -> askEngine(request.get()));
    }

    /** The typing paused: the engine is asked, and its answer, while the field still waits for it, joins the list. */
    private void askEngine(URI request) {
        typingPause = NOTHING;
        if (closed || items.closed()) return;
        CompletableFuture<List<String>> answer = items.suggest(request);
        suggestion = answer;
        answer.whenComplete((suggestions, failure) -> EventQueue.invokeLater(() -> {
            if (answer != suggestion || closed) return;
            suggestion = null;
            toolbar.suggested(failure == null ? suggestions : List.of());
            render();
        }));
    }

    /** The field waits for the engine no more: the pause and the request under way end, and a late answer is dropped. */
    private void endSuggestions() {
        typingPause.run();
        typingPause = NOTHING;
        CompletableFuture<List<String>> asked = suggestion;
        suggestion = null;
        if (asked != null) asked.cancel(true);
    }

    private void render() {
        if (!closed) view.update(spec());
    }

    private void present() {
        if (closed || tab == null) return;
        String address = shown(errors.address());
        title = TabTitle.of(page.title(), host(address), title, page.loading());
        if (devTools != null) devTools.retitle(title);
        String shown = address.length() <= SHOWN_ADDRESS ? address : address.substring(0, SHOWN_ADDRESS) + "…";
        // The globe stays the tab's icon under an image, for one that does not decode.
        var presentation = new ContentPresentation(title, "", BrowserItems.GLOBE, false, shown, "");
        tab.setPresentation(icon().map(presentation::withImage).orElse(presentation));
        tab.setStatus(List.of(new ContentStatus(shown, shown, null)));
    }

    /**
     * The image the tab and the address field show for the page: none while it loads or shows an error page, so the
     * globe shows; Jasper's icon on Jasper's own pages; else the page's favicon, if it has one.
     */
    private Optional<PixelImage> icon() {
        if (page.loading() || errors.shown().isPresent()) return Optional.empty();
        String address = page.address();
        boolean jaspers = address.regionMatches(true, 0, AboutScheme.NAME + ":", 0, AboutScheme.NAME.length() + 1);
        return jaspers ? Optional.of(JasperIcon.IMAGE) : page.favicon();
    }

    private ViewSpec spec() {
        Nodes.Node bar = toolbar.node(shown(errors.address()), page.loading(), errors.canGoBack(), page.canGoForward(), icon(),
            page.history(), items.settings(), errors.notSecure(), Optional.of(stores.bookmarkIndex().has(errors.address())));
        // A waiting item keeps the canvas's slot, hidden: a slot that leaves the view closes its content, the page.
        Nodes.Node canvas = new Nodes.Custom(PAGE, waiting ? HIDDEN : ControlState.READY, CONTENT, Nodes.CustomKind.PREVIEW, "Web page");
        return new ViewSpec(new Nodes.Split(ROOT, ControlState.READY, Nodes.SplitKind.DOCUMENT,
            waiting ? List.of(bar, canvas, placeholder(), findBar.node()) : List.of(bar, canvas, findBar.node())));
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
