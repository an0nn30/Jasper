package dev.jasper.browser.page;

import dev.jasper.browser.adblock.FilterList;
import dev.jasper.browser.engine.Chromium;
import dev.jasper.browser.store.Reached;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.ui.PixelImage;
import dev.jasper.sdk.ui.TabPlacement;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.event.HierarchyEvent;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.LongSupplier;
import java.util.function.ObjIntConsumer;
import java.util.function.Predicate;
import javax.swing.JComponent;
import javax.swing.Timer;
import org.cef.CefClient;
import org.cef.browser.CefFrame;
import org.cef.browser.CefRequestContext;

/**
 * One page as its item drives it, in plain types: the canvas it is drawn on, where it is, its title and favicon,
 * whether it loads, where it can go and its history, its find in page, its zoom, its failed loads and the certificates
 * it may go on past, the HTML it shows, what the user selects in it, its {@link DevTools}, its {@link PagePrivacy} hooks,
 * and what it is asked to do. Its Chromium browser is made only once {@link #start} asks for it, the first time its item
 * shows; until then the page holds the address it will load and the zoom it will show at, and asks nothing of Chromium.
 * Its browser is made in its browsing group's {@link PageContext}: Chromium's global context, or a /dev/null group's
 * own in memory. Every method runs on the UI thread, and so does every callback it makes, except where a method says
 * otherwise. This is the one class that makes a {@link PageView}: the page's, and each of its DevTools'.
 *
 * <p><b>Downloads.</b> Save Link As… and Save Image As… have the page's next download of their address ask where it
 * goes ({@link #askNextDownload}, {@link DownloadAsk}) and start it ({@link #startDownload}). A page closes at once but
 * for its browser, which its close hold keeps while one of its downloads runs ({@link #setCloseHold}, plan 7's F1): it
 * loads {@code about:blank} meanwhile, ending the page's scripts and sound but not its downloads, and closes once they
 * end; {@link PageDownloadState} holds the ask, the start and the hold. Its item hears when its first navigation
 * becomes a download ({@link #onFirstNavigationDownload}, F28), and its downloads when a save chooser for it is
 * cancelled ({@link #onSaveCancelled}, F1.3).
 *
 * <p><b>Logins.</b> Its item's password manager runs a login script in each http or https document of the page's main
 * frame as it starts loading, and answers the page's login queries later, once each ({@link #handleLogins},
 * {@link #loginQuery}, {@link PageQuery}); {@link PageLoginHooks} holds the script and the handler, and the page's
 * close cancels the queries not answered (plan 8). Each HTTP sign-in Chromium asks for one of the page's requests goes
 * to its item, to answer once ({@link #onSignIn}, {@link SignIn}), judged against the page's top address: its main
 * frame's latest navigation request, after its redirects, or its latest commit, whichever came last
 * ({@link #topAddress()}), and stamped with that navigation's number and whether the page's user activation or Jasper
 * began it ({@link #latestNavigation()}); and so does each final main-frame response's status
 * ({@link #onMainFrameResponse}).
 */
public final class Page implements AutoCloseable {
    /** The longest {@link #source} waits for Chromium's HTML. */
    static final long SOURCE_WAIT_SECONDS = 10;
    /** How long after a title change the page reads its history, once for the title changes meanwhile. */
    static final int TITLE_READ_MILLIS = 250;
    private static final System.Logger LOG = System.getLogger(Page.class.getName());
    private final PageView view = new PageView();
    private final PagePrivacy privacy = new PagePrivacy(this::inEveryFrame);
    private final Runnable changed;
    private final Favicons favicons;
    // Where the answers of the page's DevTools runs are read: the plugin's background executor.
    private final Executor background;
    // The request context the browser is made with, and what else must be ready first, as its item gives it.
    private final PageContext context;
    private CompletableFuture<?> before = CompletableFuture.completedFuture(null);
    // Told on Chromium's thread as Chromium makes and closes the page's browser, with its identifier; and that identifier
    // while the browser lives.
    private volatile IntConsumer browserMade = browser -> { }, browserClosed = browser -> { };
    private volatile OptionalInt browserId = OptionalInt.empty();
    private final Function<Page, CompletableFuture<NavigationHistory.History>> historyReader;
    private ObjIntConsumer<Page> historyJumper;
    // Given the page, reads the HTML its main frame shows: Chromium's, or a test's.
    private Function<Page, CompletableFuture<Optional<String>>> sourceReader = page -> chromiumSource(page.readyBrowser());
    // Given the page and a text, puts the text into the field the user is typing in, or types it where the focus is; given
    // the page, presses Return in it: through its browser's DevTools, or a test's.
    private BiFunction<Page, String, CompletableFuture<Insertion>> fieldInserter =
        (page, typed) -> page.pageText().map(text -> text.insertIntoField(typed)).orElseGet(Page::noBrowser);
    private BiFunction<Page, String, CompletableFuture<Void>> textInserter =
        (page, typed) -> page.pageText().map(text -> text.insertText(typed)).orElseGet(Page::noBrowser);
    private Consumer<Page> returnPresser = page -> page.pageText().ifPresent(PageText::pressReturn);
    // Given the page, loads it again: through its browser, or a test's.
    private Consumer<Page> reloader = Page::reloadBrowser;
    // Given the page, reads what is selected in it, through its browser's DevTools; or a test's.
    private Function<Page, CompletableFuture<Selection>> selectionReader =
        page -> page.pageText().map(PageText::selectedText).orElseGet(() -> CompletableFuture.completedFuture(new Selection.None()));
    // Given the page, runs Chromium's copy in its focused frame; or a test's.
    private Consumer<Page> copier = page -> page.onFocusedFrame(CefFrame::copy);
    // The page's text insertion and selection reads through its browser's DevTools, made the first time either is asked
    // for once the browser is ready; null before.
    private PageText pageText;
    private final List<Consumer<String>> loadStarts = new CopyOnWriteArrayList<>();
    private final List<Runnable> selectionGestures = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<String, Reached>> visits = new CopyOnWriteArrayList<>();
    private final List<Consumer<NavigationButton>> navigationButtons = new CopyOnWriteArrayList<>();
    private final List<IntConsumer> magnifies = new CopyOnWriteArrayList<>();
    private final List<Runnable> closes = new CopyOnWriteArrayList<>();
    private final List<Runnable> navigations = new CopyOnWriteArrayList<>();
    private final List<Runnable> dialogResets = new CopyOnWriteArrayList<>();
    private final List<Consumer<String>> addressChanges = new CopyOnWriteArrayList<>();
    private final List<Consumer<LoadFailure>> loadFailures = new CopyOnWriteArrayList<>();
    // Which certificates Chromium may go on past; read on Chromium's thread.
    private volatile Predicate<ServerCertificate> certificates = certificate -> false;
    // The certificate last refused for a main-frame load, and its address, until that address's failure takes it.
    private ServerCertificate refused;
    private String refusedUrl;
    private BiFunction<String, TabPlacement, Optional<Page>> opener = (url, placement) -> Optional.empty();
    private Runnable itemCloser = () -> { }, itemShower = () -> { };
    // Plan 7: told when the page's first navigation became a download (F28), its item's; and when a save chooser for the
    // page was cancelled (F1.3), its downloads', kept after the close, as a closing tab cancels the chooser it shows.
    private Runnable firstNavigationDownload = () -> { }, saveCancelled = () -> { };
    // Plan 7: the page's own part of its downloads: its ask, how they start, its close hold and its browser's ends.
    private final PageDownloadState downloadState = new PageDownloadState();
    // Plan 7: what the page gives the tools: their scripts, its load ends, their reports and its find's top frame.
    private final PageToolHooks toolHooks = new PageToolHooks(this);
    // Plan 8: what the page gives its password manager: its login script and the handler of its queries.
    private final PageLoginHooks loginHooks = new PageLoginHooks();
    // The time its navigations' marks and activations are read at: the system's, or a test's. Read on Chromium's
    // thread.
    private volatile LongSupplier clock = System::currentTimeMillis;
    // Where the page's dialogs and file choosers show, and where its right-click menu shows; its item gives them.
    private DialogPlace dialogPlace;
    private MenuPlace menuPlace;
    // What the page loads once its browser can: first its start, then the last address asked for before that.
    private URI requested;
    // Where the page is, as Chromium last reported it, or what it was last asked to load since.
    private String address;
    // The document the main frame shows, as Chromium last reported a navigation there committed; empty before the
    // first. Read on Chromium's thread.
    private volatile String committed = "";
    private String title = "";
    // The favicon of the document the main frame shows; the icon that document named, null until it names one; and the
    // fetch of that icon while it runs. The document is counted by the main frame's load starts, as a script's pushState
    // changes the address and not the document.
    private Optional<PixelImage> favicon = Optional.empty();
    private long document;
    // Whether the main frame's document has finished loading: from its load start until its load ends, not.
    private boolean documentLoaded;
    private URI declaredIcon;
    private CompletableFuture<Optional<PixelImage>> iconFetch;
    // The history as last read; whether a read runs, and whether the history changed since that read began.
    private NavigationHistory.History history = NavigationHistory.History.EMPTY;
    private boolean readingHistory, historyStale;
    // Reads the history a moment after the title changed: a page that animates its title would read it on each change.
    private final Timer titleRead = new Timer(TITLE_READ_MILLIS, event -> readHistory());
    private boolean loading, canGoBack, canGoForward, started, ready, closed;
    // The zoom the page shows at, as Chromium's level; read on Chromium's thread as each main-frame navigation commits.
    private volatile double zoomLevel;
    // The page's find in page, made the first time it is asked for.
    private PageFind find;
    // The DevTools made for the page last, until they end; null before and after.
    private DevTools devTools;
    // Given DevTools and a point, has Chromium make their browser through the page's and answers what closes it, or empty
    // while the page's browser is not ready: a test's, or null for Chromium's. Null by default, so that making a page
    // loads no DevTools class.
    private BiFunction<DevTools, Point, Optional<Runnable>> devToolsOpener;
    private PageBrowser browser;
    // The address the browser was made with, which it loads by itself.
    private String made;

    /**
     * A page that will load {@code url}, with its canvas and no browser yet.
     *
     * @param url what it loads first
     * @param changed told whenever its address, title, favicon, loading state, where it can go or its history changes
     * @param favicons fetches the favicons its documents name
     * @param background the plugin's background executor, where the answers of its DevTools runs are read
     * @param context the request context its browser is made with: its browsing group's
     */
    public Page(URI url, Runnable changed, Favicons favicons, Executor background, PageContext context) {
        // Functions of the page, not of its browser's type: making a page loads no Chromium class.
        this(url, changed, favicons, background, context, page -> NavigationHistory.read(page.readyBrowser()),
            (page, id) -> NavigationHistory.goTo(page.readyBrowser(), id, page::historyEntryRefused));
    }

    /**
     * A page that reads its history with {@code historyReader} and goes to an entry of it with {@code historyJumper}.
     *
     * @param url what it loads first
     * @param changed told whenever its address, title, favicon, loading state, where it can go or its history changes
     * @param favicons fetches the favicons its documents name
     * @param background where the answers of its DevTools runs are read
     * @param context the request context its browser is made with
     * @param historyReader given the page, reads its history, as {@link NavigationHistory#read} reads its browser's
     * @param historyJumper given the page and an entry's id, goes there, as {@link NavigationHistory#goTo} does
     */
    Page(URI url, Runnable changed, Favicons favicons, Executor background, PageContext context,
         Function<Page, CompletableFuture<NavigationHistory.History>> historyReader, ObjIntConsumer<Page> historyJumper) {
        requested = Objects.requireNonNull(url, "url");
        address = url.toString();
        this.changed = Objects.requireNonNull(changed, "changed");
        this.favicons = Objects.requireNonNull(favicons, "favicons");
        this.background = Objects.requireNonNull(background, "background");
        this.context = Objects.requireNonNull(context, "context");
        this.historyReader = Objects.requireNonNull(historyReader, "historyReader");
        this.historyJumper = Objects.requireNonNull(historyJumper, "historyJumper");
        titleRead.setRepeats(false);
        view.onNavigationButton(this::navigationButton);
        view.onMagnify(this::magnified);
        // Chromium keeps one zoom per host, which another page on the same host may have changed meanwhile.
        view.addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && view.isShowing()) showZoom();
        });
    }

    /**
     * The canvas the page is drawn on, for its item's custom content.
     *
     * @return the canvas
     */
    public JComponent view() {
        return view;
    }

    /**
     * The page's privacy hooks, through which its site's camera, microphone and notification requests, and its
     * notifications, reach its item, and its item's stored decisions reach the page. Any thread.
     *
     * @return the hooks, the same each time
     */
    public PagePrivacy privacy() {
        return privacy;
    }

    /**
     * Makes the page's browser: asks {@code chromium} for its client, starting Chromium when no page has yet, and for its
     * group's request context, and makes the browser in that context once the client, the context and what
     * {@link #makeAfter} gave are all ready, which then loads what the page was last asked to. Only the browser waits for
     * them: Chromium's start waits for none. The client's display, load, keyboard, context-menu and permission handlers,
     * and the hooks that run its frames' privacy scripts and ad blocking's element hiding, are installed before any page
     * is made on it. Once; nothing after {@link #close()}.
     *
     * @param chromium the plugin's Chromium
     * @param filters the plugin's filter list, whose rule set active the page's frames hide elements as
     */
    public void start(Chromium chromium, FilterList filters) {
        Objects.requireNonNull(chromium, "chromium");
        if (started || closed) return;
        started = true;
        PageEvents.prepare(chromium);
        PageMenu.prepare(chromium);
        MediaAccess.prepare(chromium);
        FrameScripts.prepare(chromium, filters);
        CompletableFuture<CefClient> client = chromium.client();
        CompletableFuture<CefRequestContext> inContext = context.context(chromium);
        // What the item waits for counts once done, however it ended.
        CompletableFuture<?> ready = before.handle((done, failure) -> null);
        CompletableFuture.allOf(client, inContext, ready).whenComplete((all, failure) -> {
            if (failure == null) {
                EventQueue.invokeLater(() -> make(chromium, client.join(), inContext.join()));
                return;
            }
            // A context object handed out for a browser that is not made goes back at once (V12).
            if (!inContext.isCompletedExceptionally()) unused(inContext.join());
            // A client that fails, the plugin tells once; a context that fails while Chromium runs, the log.
            if (!client.isCompletedExceptionally() && chromium.state() != Chromium.State.STOPPED)
                LOG.log(System.Logger.Level.WARNING, "A page's request context could not be made", failure);
        });
    }

    /**
     * Has the page make its browser only once {@code ready} has completed too, however it completes, besides its client
     * and its context: what its first document must already find, such as its store's site permissions. Asking for
     * Chromium waits for nothing. Give it before {@link #start}; the last one given counts.
     *
     * @param ready what the browser waits for
     */
    public void makeAfter(CompletableFuture<?> ready) {
        before = Objects.requireNonNull(ready, "ready");
    }

    /**
     * The identifier Chromium gave the page's browser, which Jasper's pages' queries carry. Any thread.
     *
     * @return the identifier, from the moment Chromium has made the browser until it has closed it; empty otherwise
     */
    public OptionalInt browserId() {
        return browserId;
    }

    /**
     * Has {@code made} told the identifier Chromium gives the page's browser, as Chromium makes it and before any
     * document of it can ask Jasper anything, and {@code closed} the same identifier once Chromium has closed it: both on
     * Chromium's thread, where they must return at once and must not throw. The page's item gives them before
     * {@link #start}; they replace the ones before, and the page's close keeps them, as its browser closes after it.
     *
     * @param made told the identifier of the browser Chromium made
     * @param closed told the identifier of the browser Chromium closed
     */
    public void onBrowser(IntConsumer made, IntConsumer closed) {
        browserMade = Objects.requireNonNull(made, "made");
        browserClosed = Objects.requireNonNull(closed, "closed");
    }

    /**
     * Whether the page's browsing group keeps its data in memory, as a /dev/null group's does: what its store says. Any
     * thread.
     *
     * @return whether it does
     */
    public boolean inMemory() {
        return context.inMemory();
    }

    /**
     * The request context of the page's browsing group, which says whether it keeps its data in memory and tells as its
     * group's store closes it ({@link PageContext#onClosed}). Any thread.
     *
     * @return the context the page's browser is made in
     */
    public PageContext context() {
        return context;
    }

    /**
     * The allowance a download the page starts counts in, for the one download an allowance may start without a user
     * activation (plan 7's D1): it changes with each load Jasper starts for the user ({@link #load}, D20) and with each
     * main-frame document that starts while the page has a user activation ({@link #documentStarting}, D46), and only
     * grows. Read on Chromium's thread.
     *
     * @return the allowance's number
     */
    long downloadDocument() {
        return downloadState.allowance();
    }

    /**
     * The page's main frame starts loading a document at {@code nowMillis} (plan 7's D46): one that starts while the page
     * has a user activation, as a click on a link starts it, gives its downloads a fresh allowance; one the page starts
     * by itself, as a meta refresh, {@code location.reload()} or a {@code location.href} without a gesture starts it,
     * keeps the count, as Chrome keeps its limiter's state. Chromium's thread.
     *
     * @param nowMillis the time now, for the page's activation, which this reads without consuming it
     */
    void documentStarting(long nowMillis) {
        if (activation().active(nowMillis)) downloadState.renew();
    }

    /**
     * The page's user activation, which its canvas keeps. Any thread.
     *
     * @return the activation
     */
    Activation activation() {
        return view.activation();
    }

    /** Chromium made the page's browser, whose identifier is {@code id}. Chromium's thread. */
    void browserMade(int id) {
        browserId = OptionalInt.of(id);
        browserMade.accept(id);
    }

    /** Chromium closed the page's browser. Chromium's thread. */
    void browserClosed() {
        OptionalInt id = browserId;
        browserId = OptionalInt.empty();
        if (id.isPresent()) browserClosed.accept(id.getAsInt());
    }

    /**
     * Whether {@link #start} has been asked.
     *
     * @return whether the page asked for its browser
     */
    public boolean started() {
        return started;
    }

    /**
     * Loads {@code url}. Before the page has its browser, it is what the browser loads first. Only Jasper's own UI loads a
     * page so, for the user, so the load gives the page a fresh download allowance (plan 7's D20).
     *
     * @param url the address
     */
    public void load(URI url) {
        Objects.requireNonNull(url, "url");
        if (closed) return;
        // Plan 7's D20: a load Jasper starts for the user gives the page a fresh download allowance.
        downloadState.renew();
        requested = url;
        address = url.toString();
        navigated();
        if (ready) browser.loadURL(address);
        changed.run();
    }

    /**
     * Loads {@code errorPage}, one of Jasper's error pages, in place of a load that failed, as {@link #load} would, but
     * without telling those who asked {@link #onNavigate}: the page shows what became of where it was going, and goes
     * nowhere else.
     *
     * @param errorPage the error page's address
     */
    public void showError(URI errorPage) {
        Objects.requireNonNull(errorPage, "errorPage");
        if (closed) return;
        requested = errorPage;
        address = errorPage.toString();
        if (ready) browser.loadURL(address);
        changed.run();
    }

    /** Goes back in the page's history, when it can: a page with nothing before this entry does nothing. */
    public void back() {
        if (!ready || closed || !canGoBack) return;
        navigated();
        browser.goBack();
    }

    /** Goes forward in the page's history, when it can: a page with nothing after this entry does nothing. */
    public void forward() {
        if (!ready || closed || !canGoForward) return;
        navigated();
        browser.goForward();
    }

    /**
     * Goes to entry {@code id} of the page's history, as Back's and Forward's hold menus choose it: an entry of the
     * history as last read ({@link #history()}) other than the current one. An entry it no longer has and the current
     * one do nothing; a page has no history to go to before its browser is ready. Those who asked {@link #onNavigate}
     * are told before the page goes, so they are told too when Chromium then refuses the entry, as it does one dropped
     * from the history after the last read, and the page stays where it is.
     *
     * @param id the entry's id
     */
    public void goToHistoryEntry(int id) {
        if (closed || !history.jumpsTo(id)) return;
        navigated();
        historyJumper.accept(this, id);
    }

    /**
     * Shows the page at {@code level}, Chromium's zoom level: 0 is 100%, and each level up is 1.2 times the size. Before
     * the page has its browser, the level its browser takes once made. Chromium keeps one zoom per host, shared by every
     * page on it, and shows it as a new document loads, so the page shows its own again as each main-frame load starts
     * and each time its canvas shows. Two pages on one host that show at once share one zoom, the one set last: JCEF
     * gives no zoom of a page's own. Nothing after {@link #close()}.
     *
     * @param level the zoom level
     * @throws IllegalArgumentException for a level that is not finite
     */
    public void setZoomLevel(double level) {
        if (!Double.isFinite(level)) throw new IllegalArgumentException("Not a zoom level: " + level);
        if (closed) return;
        zoomLevel = level;
        showZoom();
    }

    /** Loads the page again; nothing before the page has its browser or after {@link #close()}. */
    public void reload() {
        if (!closed) reloader.accept(this);
    }

    private void reloadBrowser() {
        if (ready) browser.reload();
    }

    /**
     * Stops the page loading: a navigation Jasper started for the user that has not made its request yet makes none,
     * so its mark is dropped (plan 8's L44), as Chromium reports nothing for a navigation stopped while the old
     * document's {@code beforeunload} runs.
     */
    public void stop() {
        loginHooks.dropMark();
        if (ready && !closed) browser.stopLoad();
    }

    /**
     * Chromium refused the history entry Jasper sent the page to ({@link #goToHistoryEntry}), so that navigation makes
     * no request: its mark is dropped (plan 8's L44). Any thread.
     */
    void historyEntryRefused() {
        loginHooks.dropMark();
    }

    /** Cuts the selection of the page's focused frame, as its Edit menu's Cut would. */
    public void cut() {
        onFocusedFrame(CefFrame::cut);
    }

    /**
     * Copies the selection of the page's focused frame, as its Edit menu's Copy would: Chromium's copy, which runs that
     * frame's {@code copy} handlers, so its scripts may put what they like on the clipboard, as in Chrome.
     */
    public void copy() {
        if (!closed) copier.accept(this);
    }

    /** Pastes the clipboard into the page's focused frame, as its Edit menu's Paste would. */
    public void paste() {
        onFocusedFrame(CefFrame::paste);
    }

    /**
     * Puts {@code text} into the field the user is typing in on the page, as text another plugin inserts into its item
     * asks (spec section 15). {@link PageText} runs {@code insert-text.js} in an isolated world of the main frame. The
     * script follows the focus through same-origin frames and open shadow roots to an {@code <input>} of a type that takes
     * text (text, search, url, tel, email, number or password), a {@code <textarea>} or an editable element, neither
     * disabled nor read-only, and inserts the text there as typing would. It completes on any thread.
     *
     * @param text the text
     * @return what became of the text ({@link Insertion}). It fails before the page has its browser, after
     *     {@link #close()}, and when a DevTools call is late, refused or answered with an error, as the text may still go
     *     in or nothing can tell
     */
    public CompletableFuture<Insertion> insertIntoField(String text) {
        Objects.requireNonNull(text, "text");
        if (closed) return noBrowser();
        CompletableFuture<Insertion> inserted = fieldInserter.apply(this, text);
        return inserted == null ? noBrowser() : inserted;
    }

    /**
     * Types {@code text} into whatever has the page's focus, a cross-origin frame's field included, as an input method
     * commits text: DevTools' {@code Input.insertText} ({@link PageText}), without the clipboard. It is trusted input, so
     * the page gets user activation from it, as from the user's own typing. It completes on any thread.
     *
     * @param text the text
     * @return done once DevTools has answered; failed before the page has its browser, after {@link #close()}, and when
     *     the call is late, refused or answered with an error
     */
    public CompletableFuture<Void> insertText(String text) {
        Objects.requireNonNull(text, "text");
        if (closed) return noBrowser();
        CompletableFuture<Void> typed = textInserter.apply(this, text);
        return typed == null ? noBrowser() : typed;
    }

    /**
     * Presses Return in the page, where its focus is, as the user would, after text inserted into its item asks for it:
     * Return's key down, with its text, then its key up whatever the down's answer, through DevTools ({@link PageText}).
     * The key events are trusted input, so the page gets user activation from them, as from the user's own Return.
     * Nothing before the page has its browser or after {@link #close()}.
     */
    public void pressReturn() {
        if (!closed) returnPresser.accept(this);
    }

    /** What became of text put into the field the user is typing in, as {@link #insertIntoField} answers (H38). */
    public enum Insertion {
        /** The focused field took the text. */
        INSERTED,
        /**
         * The focus is where Jasper's world cannot reach: in a frame of another origin, or inside a closed shadow root,
         * which the script tells by a focused element that could host one and cannot take the focus itself.
         */
        UNREACHABLE,
        /**
         * The focus takes no text: nothing is focused, or the page's body is; a button, a link, a checkbox or any other
         * element that takes no text; a field disabled or read-only; or a number field or an editable element whose
         * document refused the text.
         */
        NOT_EDITABLE
    }

    /** What is selected in a page, as {@link #selectedText} reads it. */
    public sealed interface Selection permits Selection.Text, Selection.InFrame, Selection.Unchanged, Selection.None {
        /**
         * Text the user selected in the page, or in its focused field.
         *
         * @param text the text; empty when nothing is selected
         */
        record Text(String text) implements Selection {
            /** Checks that the text is given. */
            public Text {
                Objects.requireNonNull(text, "text");
            }
        }

        /** The focus is in a frame of another origin, whose selection Jasper does not read. */
        record InFrame() implements Selection { }

        /** The selection has not changed since the last read in its document or field (H35, H36). */
        record Unchanged() implements Selection { }

        /**
         * Nothing that can be copied: no browser yet, the page closed, a password field focused or one CSS masks, changed
         * or not, a field whose type has no selection, or a selection of more than 1,048,576 characters, which
         * {@code PageText} reads at most.
         */
        record None() implements Selection { }
    }

    /**
     * What the user has selected in the page, as copy on select reads it (spec section 15): {@code selection.js}, which
     * {@link PageText} runs in Jasper's isolated world of the main frame, follows the focus through same-origin frames and open
     * shadow roots. A focused {@code <input>} or {@code <textarea>} gives the selected part of its value, but a password
     * field or one CSS masks never does, and anything else gives the selection of the deepest same-origin document, its
     * non-breaking spaces as spaces; the focus in a cross-origin frame gives {@link Selection.InFrame}. A selection gives
     * its text only when it changed since the last read there, as a {@code selectionchange} told Jasper's world (H35),
     * and {@link Selection.Unchanged} otherwise; the first read in a document or field gives it. Each read that runs
     * counts the change as read, one whose answer comes too late included. No page script can steer what
     * the script reads, but a page can change its own selection, during a drag or before the read, and that is what is
     * read. It completes on any thread.
     *
     * @return what is selected; {@link Selection.None} before the page has its browser and after {@link #close()}. It
     *     fails when a DevTools call is late, refused or answered with an error, or its answer cannot be read
     */
    public CompletableFuture<Selection> selectedText() {
        if (closed) return CompletableFuture.completedFuture(new Selection.None());
        CompletableFuture<Selection> read = selectionReader.apply(this);
        return read == null ? CompletableFuture.completedFuture(new Selection.None()) : read;
    }

    /**
     * Selects all of the page's focused frame, as its Edit menu's Select All would, as the page menu's Select All asks: a
     * selection gesture, which those who asked {@link #onSelectionGesture} hear as they hear the canvas's.
     */
    public void selectAll() {
        onFocusedFrame(CefFrame::selectAll);
        if (!closed) view.selectionGesture();
    }

    /** Opens Chromium's print dialog for the page. */
    public void print() {
        if (ready && !closed) browser.print();
    }

    /** Gives the page the keyboard focus. */
    public void focus() {
        view.requestFocusInWindow();
    }

    /**
     * Where the page is: its main frame's address, or what it was last asked to load.
     *
     * @return the address, as Chromium writes it
     */
    public String address() {
        return address;
    }

    /**
     * The address of the document the page's main frame shows, as Chromium last reported a navigation there committed:
     * what the page itself may open from, which decides what Jasper opens for it with its own rights
     * ({@link LinkSchemes#opensFrom}). Unlike {@link #address()}, never where the page was only asked to go. Any thread.
     *
     * @return the address, as Chromium writes it; empty before the first navigation commits
     */
    public String committedAddress() {
        return committed;
    }

    /**
     * The page's title.
     *
     * @return the title, or empty while the page has none
     */
    public String title() {
        return title;
    }

    /**
     * The favicon of the document the page's main frame shows, fetched once that document named it, while the page is
     * still where it was fetched for. A main frame's load start clears it.
     *
     * @return the favicon, or empty while the page has none
     */
    public Optional<PixelImage> favicon() {
        return favicon;
    }

    /**
     * The document the page's main frame showed at {@code document} names its favicon at {@code icon}, as the page's
     * script reports through the opener bridge: the page fetches it, and the favicon is the page's if it arrives while
     * the page still shows that document, whatever its scripts' {@code pushState} made of its address since (plan 4b's
     * W5). Each document names one: a request after the first, one from an address the page is no longer at, a
     * fragment aside, and any after {@link #close()} are ignored. The bridge has checked {@code icon}, which any script
     * in the page could have sent.
     *
     * @param icon the icon's address
     * @param document the address of the document that named it
     */
    public void faviconDeclared(URI icon, String document) {
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(document, "document");
        if (closed || declaredIcon != null || !samePage(document, address)) return;
        URI page;
        try {
            page = new URI(document);
        } catch (URISyntaxException notAUri) {
            return;
        }
        declaredIcon = icon;
        long declaredIn = this.document;
        CompletableFuture<Optional<PixelImage>> fetch = favicons.fetch(icon, page);
        iconFetch = fetch;
        fetch.whenComplete((image, failure) -> EventQueue.invokeLater(() -> {
            if (fetch != iconFetch) return; // dropped by a load start or the close
            iconFetch = null;
            faviconArrived(declaredIn, failure == null ? image : Optional.empty());
        }));
    }

    /**
     * The HTML of the document the page's main frame shows now, as Chromium serializes it, the page's scripts' changes
     * with it: what View Source shows of a page it does not fetch again. It completes on any thread, never with an
     * exception: empty before the page has its browser, after {@link #close()}, and when Chromium gives nothing within
     * {@value #SOURCE_WAIT_SECONDS} seconds.
     *
     * @return the HTML, to come
     */
    public CompletableFuture<Optional<String>> source() {
        if (closed) return CompletableFuture.completedFuture(Optional.empty());
        CompletableFuture<Optional<String>> read = sourceReader.apply(this);
        return read == null ? CompletableFuture.completedFuture(Optional.empty()) : read;
    }

    /**
     * Chromium's DevTools for the page, inspecting the element at {@code at} on its canvas, as Inspect Element asks (spec
     * section 10): new DevTools, with a canvas of their own, whose browser Chromium makes once that canvas is in a window
     * and the DevTools made for the page before have closed, which their item closes as it shows these. None before the
     * page's browser is ready, and none after {@link #close()}.
     *
     * @param at the point, on the page's canvas
     * @return the DevTools, or empty
     */
    public Optional<DevTools> inspect(Point at) {
        Objects.requireNonNull(at, "at");
        return ready && !closed ? Optional.of(devTools(at)) : Optional.empty();
    }

    /**
     * The page's find in page, which searches the frames of its browser once it has one: none before.
     *
     * @return the find, made the first time it is asked for
     */
    public PageFind find() {
        if (find == null) {
            find = PageFind.of(this);
            toolHooks.searchedBy(find);
            if (closed) find.close();
        }
        return find;
    }

    /**
     * Has the page's find search its top frame alone while {@code when} says so, as its item's reader mode asks while
     * the article shows over the page ({@link PageFind#searchTopFrameOnly}). It replaces the one before.
     *
     * @param when read as each search starts, on the UI thread
     */
    public void findTopFrameOnly(BooleanSupplier when) {
        toolHooks.topFrameOnly(when);
    }

    /**
     * What the page shows to search has changed, as reader mode opened or closed: its find's search under way ends and
     * its highlights go ({@link PageFind#restart}). Nothing before the find is made, or after {@link #close()}.
     */
    public void restartFind() {
        if (!closed && find != null) find.restart();
    }

    /**
     * A frame of the page answers a search of its find, as {@code find.js} reports through the opener bridge, which has
     * checked the answer's shape and bounds; the find takes it only for its search under way and from a frame it
     * reached. Nothing before the page's find is made, or after {@link #close()}.
     *
     * @param frame the identifier of the frame that answered, as Chromium names it
     * @param search the search the answer is for
     * @param count the frame's matches
     * @param invalid whether the frame found the query no regular expression
     */
    public void findAnswered(String frame, long search, int count, boolean invalid) {
        Objects.requireNonNull(frame, "frame");
        if (!closed && find != null) find.answered(frame, search, count, invalid);
    }

    /**
     * A frame of the page answers its find's request for the selection, as {@code find.js} reports through the opener
     * bridge; the find takes it only for its request under way and from the frame it asked.
     *
     * @param frame the identifier of the frame that answered
     * @param search the request the answer is for
     * @param selection the frame's selected text
     */
    public void findSelected(String frame, long search, String selection) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(selection, "selection");
        if (!closed && find != null) find.selected(frame, search, selection);
    }

    /**
     * Whether the page loads.
     *
     * @return whether it loads
     */
    public boolean loading() {
        return loading;
    }

    /**
     * Whether the page can go back.
     *
     * @return whether its history has an entry before this one
     */
    public boolean canGoBack() {
        return canGoBack;
    }

    /**
     * Whether the page can go forward.
     *
     * @return whether its history has an entry after this one
     */
    public boolean canGoForward() {
        return canGoForward;
    }

    /**
     * The page's history, as read from Chromium after each main-frame load end, committed navigation and title change.
     *
     * @return the history last read; {@link NavigationHistory.History#EMPTY} before the first read and after a read
     *     that failed
     */
    public NavigationHistory.History history() {
        return history;
    }

    /**
     * Tells {@code listener} each time the mouse's back or forward button is released over the page, on Windows and
     * Linux. The page itself never sees those buttons.
     *
     * @param listener told which, on the UI thread
     * @return the registration; the page's close ends it
     */
    public Subscription onNavigationButton(Consumer<NavigationButton> listener) {
        Objects.requireNonNull(listener, "listener");
        navigationButtons.add(listener);
        return () -> navigationButtons.remove(listener);
    }

    /**
     * Tells {@code listener} each step Ctrl+wheel over the page magnifies it by, on Windows and Linux. There the page
     * itself never sees Ctrl+wheel, so Chromium's own Ctrl+wheel zoom never applies; on macOS it is the page's.
     *
     * @param listener told 1 for a step bigger, -1 for a step smaller, on the UI thread
     * @return the registration; the page's close ends it
     */
    public Subscription onMagnify(IntConsumer listener) {
        Objects.requireNonNull(listener, "listener");
        magnifies.add(listener);
        return () -> magnifies.remove(listener);
    }

    /**
     * Tells {@code listener} each time the user has finished a selection in the page, as copy on select copies after it:
     * 100 ms after the last of the selection gestures {@link PageView} counts, the mouse's and the keyboard's. A change
     * the page's scripts make to the selection is none. The canvas times gestures only while something listens.
     *
     * @param listener told on the UI thread
     * @return the registration; the page's close ends it
     */
    public Subscription onSelectionGesture(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        if (closed) return () -> { };
        selectionGestures.add(listener);
        view.onSelectionGesture(this::selectionGestured);
        return () -> {
            selectionGestures.remove(listener);
            if (selectionGestures.isEmpty()) view.endSelectionGestures();
        };
    }

    /**
     * Tells {@code listener} each time the page's main frame starts loading, with the address it loads.
     *
     * @param listener told on the UI thread
     * @return the registration; the page's close ends it
     */
    public Subscription onLoadStart(Consumer<String> listener) {
        Objects.requireNonNull(listener, "listener");
        loadStarts.add(listener);
        return () -> loadStarts.remove(listener);
    }

    /**
     * Tells {@code listener} each time the page's main frame finishes loading its document, as Chromium reports the
     * load's end ({@link #documentLoaded}).
     *
     * @param listener told on the UI thread
     * @return the registration; the page's close ends it
     */
    public Subscription onLoadEnd(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        return closed ? () -> { } : toolHooks.onLoadEnd(listener);
    }

    /**
     * Tells {@code listener} each report the {@code tools} package's scripts, such as {@code reader.js}, send through the
     * {@code links} package's opener bridge from the page's top frame, as {@link #toolReported} hands them on. Any script
     * of the page can send one, so each is untrusted.
     *
     * @param listener told the bridge op and the request as the page sent it, a JSON object, on the UI thread
     * @return the registration; the page's close ends it
     */
    public Subscription onToolReport(BiConsumer<String, String> listener) {
        Objects.requireNonNull(listener, "listener");
        return closed ? () -> { } : toolHooks.onReport(listener);
    }

    /**
     * The page's top frame sent a report of its tools through the opener bridge, which has checked only its op and
     * length: those who asked {@link #onToolReport} are told. Nothing after {@link #close()}. UI thread.
     *
     * @param op the bridge op, such as {@code reader}
     * @param json the request, as the page sent it
     */
    public void toolReported(String op, String json) {
        Objects.requireNonNull(op, "op");
        Objects.requireNonNull(json, "json");
        if (!closed) toolHooks.reported(op, json);
    }

    /**
     * Runs {@code script}, which Jasper wrote, in the document the page's main frame shows now, under the name
     * {@code url} in the page's console and stack traces, then releases the frame: as the {@code tools} package's page
     * scripts run. Nothing before the page's browser is ready, after {@link #close()}, or in a main frame at a challenge
     * provider's address ({@link ChallengeFrames}) or on one of Jasper's own pages, which Jasper's page scripts stay out
     * of.
     *
     * @param script the script
     * @param url the address it goes by, such as {@code jasper-about:reader.js}
     */
    public void run(String script, String url) {
        Objects.requireNonNull(script, "script");
        Objects.requireNonNull(url, "url");
        if (!closed) toolHooks.run(script, url);
    }

    /**
     * Has {@code handler} answer the page's login queries, which the {@code links} package's opener bridge hands it
     * ({@link #loginQuery}), and has each of its main frames run {@code script} as an {@code http} or {@code https}
     * document starts loading, from now on until the page's close: there and then on Chromium's thread, so it runs
     * before the document's own scripts, as a rule, and never in a challenge provider's frame or one of Jasper's own
     * pages. The page's item gives them once, when it has a password manager (plan 8). Nothing after {@link #close()}.
     *
     * @param script the login script, which Jasper wrote
     * @param handler given each of the page's queries, on the UI thread, to answer later, once
     * @throws IllegalStateException when the page has been given a handler already
     */
    public void handleLogins(String script, Consumer<PageQuery> handler) {
        Objects.requireNonNull(script, "script");
        Objects.requireNonNull(handler, "handler");
        loginHooks.handle(script, handler);
    }

    /**
     * A login query of the page's, from its top frame: its handler is handed it ({@link #handleLogins}), or it fails
     * {@link PageQuery#REFUSED}, "This page has no password manager.", when the page has none or has closed. The page's
     * close cancels each query its handler has not answered.
     *
     * @param query the query, the page's
     * @throws IllegalArgumentException for another page's query
     */
    public void loginQuery(PageQuery query) {
        Objects.requireNonNull(query, "query");
        if (query.page() != this) throw new IllegalArgumentException("A login query of another page");
        loginHooks.query(query);
    }

    /**
     * The login script a main frame runs as it starts loading the document at {@code address}, as
     * {@link PageLoginHooks#script} gives it. Chromium's thread.
     */
    Optional<String> loginScript(String address) {
        return loginHooks.script(address);
    }

    /**
     * A main-frame navigation of a page, as Chromium's IO thread noted its request (plan 8's L31, L35, L42). Its
     * redirects keep what its request noted.
     *
     * @param number the number of the page's main-frame navigation requests up to it: each new request raises it, a
     *     redirect does not; 0 before the first
     * @param activated whether the page had a user activation as its request started, as a link the user clicks or a
     *     key the user presses gives one, from a press or key since the main frame last started loading a document
     * @param byJasper whether its request is the one Jasper's own navigation for the user made: the first main-frame
     *     request within 10 s after it ({@link #onNavigate}), with nothing between that ends that navigation first: no
     *     address change or load start of the main frame, no end of another request than the latest, no download start
     *     of another address, no Stop and no history entry refused
     */
    public record Navigation(long number, boolean activated, boolean byJasper) {
        /** No navigation yet. */
        public static final Navigation NONE = new Navigation(0, false, false);
    }

    /**
     * Chromium's IO thread notes that the page's main frame starts a navigation request for {@code url}: a new
     * navigation ({@link #latestNavigation()}), begun with the page's user activation or not, as the activation reads
     * now, unconsumed, and Jasper's or not, as it takes Jasper's mark; and the page's top address until the next
     * request, redirect or commit ({@link #topAddress()}). Any thread.
     *
     * @param url the request's address, as Chromium names it; a null address is not noted, but the request counts
     */
    public void mainFrameRequested(String url) {
        mainFrameRequested(url, clock.getAsLong());
    }

    /** As {@link #mainFrameRequested(String)}, at {@code nowMillis}. Any thread. */
    void mainFrameRequested(String url, long nowMillis) {
        loginHooks.mainFrameRequested(url, activation(), nowMillis);
    }

    /**
     * Chromium's thread notes that the page's main frame starts loading a document: a user activation from before it
     * begins no later navigation, and a mark of Jasper's navigation that no request took is dropped (plan 8's L42).
     * Chromium's thread.
     */
    void mainFrameStarting() {
        loginHooks.mainFrameStarting(clock.getAsLong());
    }

    /** Has the page read the time for its navigations' marks and activations from {@code clock}: a test's. */
    void useClock(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Chromium's IO thread notes that a request of the page's main-frame navigation is redirected to {@code url} (F6):
     * the same navigation, and the page's top address until the next request, redirect or commit. Any thread.
     *
     * @param url the address it is redirected to, as Chromium names it; a null is ignored
     */
    public void mainFrameRedirected(String url) {
        loginHooks.mainFrameRedirected(url);
    }

    /**
     * The number of the page's main-frame navigation requests so far, as Chromium's IO thread noted them: each new one
     * counts, its redirects do not ({@link #mainFrameRequested}, {@link #mainFrameRedirected}), so an HTTP sign-in and
     * the responses that follow it can be told to belong to one navigation (plan 8's L31). Any thread.
     *
     * @return the number; 0 before the first
     */
    public long navigation() {
        return loginHooks.navigation().number();
    }

    /**
     * The page's latest main-frame navigation, as Chromium's IO thread noted its request: its number
     * ({@link #navigation()}), whether it began with the page's user activation and whether its request took Jasper's
     * mark, as Jasper's own navigation's, read together. Any thread.
     *
     * @return the navigation; {@link Navigation#NONE} before the first
     */
    public Navigation latestNavigation() {
        return loginHooks.navigation();
    }

    /**
     * Chromium's thread notes that the page's main frame committed {@code url}, before the UI thread hears of it
     * ({@link #addressChanged}): the page's top address at once, until the next request or commit (L23). Any thread.
     */
    void mainFrameCommitted(String url) {
        loginHooks.mainFrameCommitted(url);
    }

    /**
     * Chromium's thread notes that the page's main-frame request for {@code url} ended without a commit, as it failed
     * or became a download: while it is the top address, the commit before it is the top address again (L24). Any
     * thread.
     */
    void mainFrameRequestEnded(String url) {
        loginHooks.mainFrameRequestEnded(url);
    }

    /**
     * The address an HTTP sign-in of the page is judged against ({@link SignIn#topAddress()}): that of its main frame's
     * latest navigation request ({@link #mainFrameRequested}), after its redirects ({@link #mainFrameRedirected}), or
     * of its latest commit, whichever came last, as Chromium's threads noted them; a request that failed or became a
     * download falls back to the commit before it. Before either, the committed address ({@link #committedAddress()}).
     * A request that has not committed counts, as a main frame's own challenge comes before its commit; a commit with
     * no request of its own, as a document restored without one, counts too, so it never inherits the request before it
     * (L23). Any thread.
     *
     * @return the address, as Chromium writes it; empty before the page has either
     */
    public String topAddress() {
        return loginHooks.topAddress(committed);
    }

    /**
     * Tells {@code listener} each HTTP sign-in Chromium asks for one of the page's requests ({@link #signIn}), to
     * answer once, later: Chromium's request waits until it is. The page's item listens, with or without a password
     * manager (plan 8).
     *
     * @param listener told on the UI thread
     * @return the registration; the page's close ends it
     */
    public Subscription onSignIn(Consumer<SignIn> listener) {
        Objects.requireNonNull(listener, "listener");
        return loginHooks.onSignIn(listener);
    }

    /**
     * Chromium asks for an HTTP sign-in for one of the page's requests, which the {@code links} package's request
     * handler has made and posted: those who asked {@link #onSignIn} are told it. It is cancelled at once when no one
     * has asked, after {@link #close()}, or when one of them throws, so that no request waits on nothing. UI thread.
     *
     * @param signIn the sign-in, not yet answered
     */
    public void signIn(SignIn signIn) {
        Objects.requireNonNull(signIn, "signIn");
        loginHooks.signIn(signIn);
    }

    /**
     * Tells {@code listener} the address and HTTP status of each final response to a navigation of the page's main
     * frame, as Chromium reports it ({@link #mainFrameResponded}): a redirect is none.
     *
     * @param listener told the response's address and status, on the UI thread
     * @return the registration; the page's close ends it
     */
    public Subscription onMainFrameResponse(BiConsumer<String, Integer> listener) {
        Objects.requireNonNull(listener, "listener");
        return loginHooks.onMainFrameResponse(listener);
    }

    /**
     * The page's main frame got the final response to a navigation, as the {@code links} package's opener bridge reads
     * it on Chromium's IO thread and posts it: those who asked {@link #onMainFrameResponse} are told. Nothing after
     * {@link #close()}. UI thread.
     *
     * @param url the response's address, as Chromium names it
     * @param status its HTTP status, such as 200 or 401
     */
    public void mainFrameResponded(String url, int status) {
        Objects.requireNonNull(url, "url");
        loginHooks.mainFrameResponded(url, status);
    }

    /**
     * Tells {@code listener} each time the page's main frame starts loading a document, as {@link #onLoadStart} does, with
     * how the load reached it: each of the page's visits, for its item's history.
     *
     * @param listener told the address it loads and how, on the UI thread
     * @return the registration; the page's close ends it
     */
    public Subscription onVisit(BiConsumer<String, Reached> listener) {
        Objects.requireNonNull(listener, "listener");
        visits.add(listener);
        return () -> visits.remove(listener);
    }

    /**
     * Whether the document the page's main frame shows has finished loading, as Chromium reported its load's end: false
     * from each main-frame load start until that load ends.
     *
     * @return whether it has
     */
    public boolean documentLoaded() {
        return documentLoaded;
    }

    /**
     * Tells {@code listener} each time Chromium reports a new address for the page's main frame: a navigation that
     * commits, to another document or within this one. A load that only starts, or is asked for, tells nothing.
     *
     * @param listener told the address, as Chromium writes it, on the UI thread
     * @return the registration; the page's close ends it
     */
    public Subscription onAddressChange(Consumer<String> listener) {
        Objects.requireNonNull(listener, "listener");
        if (closed) return () -> { };
        addressChanges.add(listener);
        return () -> addressChanges.remove(listener);
    }

    /**
     * Tells {@code listener} each time a load of the page's main frame fails, as Chromium reports it, a load the user or
     * a download stopped among them. The failure carries the certificate Jasper refused for that address, if it did
     * ({@link #certificateRefused}).
     *
     * @param listener told on the UI thread
     * @return the registration; the page's close ends it
     */
    public Subscription onLoadFailure(Consumer<LoadFailure> listener) {
        Objects.requireNonNull(listener, "listener");
        if (closed) return () -> { };
        loadFailures.add(listener);
        return () -> loadFailures.remove(listener);
    }

    /**
     * Has Chromium go on past a certificate it could not verify only when {@code allowed} says so; until then it goes on
     * past none. It replaces the one before.
     *
     * @param allowed told the certificate on Chromium's thread; it must answer at once and must not throw
     */
    public void allowCertificates(Predicate<ServerCertificate> allowed) {
        Objects.requireNonNull(allowed, "allowed");
        if (!closed) certificates = allowed;
    }

    /**
     * Whether Chromium may go on past {@code certificate}, which it could not verify for a load of the page. Any
     * thread: it reads only the volatile answer {@link #allowCertificates} gave, which {@link #close()} replaces with one
     * that allows nothing.
     *
     * @param certificate the certificate
     * @return whether the page's item allows it; false after {@link #close()}
     */
    public boolean allowsCertificate(ServerCertificate certificate) {
        Objects.requireNonNull(certificate, "certificate");
        return certificates.test(certificate);
    }

    /**
     * Chromium was told not to go on past {@code certificate} for the load of {@code url}: the failure of that load that
     * follows carries it. Nothing after {@link #close()}.
     *
     * @param url the address whose load met it, as Chromium gave it
     * @param certificate the certificate
     */
    public void certificateRefused(String url, ServerCertificate certificate) {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(certificate, "certificate");
        if (closed) return;
        refusedUrl = url;
        refused = certificate;
    }

    /**
     * Has {@code opener} open each link or popup of the page that goes to an item of its own, beside the page's item: the
     * page's item sets it. It replaces the one before.
     *
     * @param opener told the address Chromium gave and where the new item goes, on the UI thread; it answers the new
     *     item's page, or empty when it opened none
     */
    public void onOpenItem(BiFunction<String, TabPlacement, Optional<Page>> opener) {
        this.opener = Objects.requireNonNull(opener, "opener");
    }

    /**
     * A link or popup of the page goes to an item of its own: tells the page's opener. Nothing after {@link #close()}.
     *
     * @param url the address, as Chromium gave it
     * @param placement where the new item goes: next to the page's tab, or split from its pane
     * @return the new item's page, or empty when none opened
     */
    public Optional<Page> openItem(String url, TabPlacement placement) {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(placement, "placement");
        return closed ? Optional.empty() : Objects.requireNonNull(opener.apply(url, placement), "opened");
    }

    /**
     * Has {@code closer} close the page's item, as Close Pane would, when the page asks: the page's item sets it. It
     * replaces the one before.
     *
     * @param closer closes the item, on the UI thread
     */
    public void onCloseItem(Runnable closer) {
        itemCloser = Objects.requireNonNull(closer, "closer");
    }

    /**
     * A link the page's DevTools open in a new tab, as a URL clicked in their Console: it opens in the tab next to the
     * page's, as the page's right-click menu opens a link, when the document the page shows could open it
     * ({@link #committedAddress()}, {@link LinkSchemes#opensFrom}, 4a's R36), as what the DevTools show comes from the
     * page. Nothing after {@link #close()}.
     *
     * @param url the address, as Chromium gave it
     */
    public void openFromDevTools(String url) {
        Objects.requireNonNull(url, "url");
        if (!closed && LinkSchemes.opensFrom(committed, url)) openItem(url, TabPlacement.NEXT_TAB);
    }

    /** The page asks to close its item, as a script's {@code window.close()} does. Nothing after {@link #close()}. */
    public void closeItem() {
        if (!closed) itemCloser.run();
    }

    /**
     * Whether Chromium holds back the close of {@code page}'s browser, as its {@code DoClose} asks: false lets Chromium
     * destroy the browser at once. A close Java asked for, Jasper's close of the page or JCEF's of its client's browsers,
     * goes ahead. Any other comes from the page itself, a script's {@code window.close()}: it is held back, and the
     * page's item closes on the UI thread, as Chrome closes the tab, which closes the browser in turn. Any thread.
     *
     * @param asked whether Java asked the browser to close
     * @param page the browser's page, or null before it has one
     * @param ui runs a task on the UI thread
     * @return whether Chromium holds the close back
     */
    static boolean holdsClose(boolean asked, Page page, Consumer<Runnable> ui) {
        if (asked) return false;
        if (page != null) ui.accept(page::closeItem);
        return true;
    }

    /**
     * Has the page's next download of {@code url} ask where it goes, in Jasper's save chooser, as Save Link As… and Save
     * Image As… ask before they start it (spec section 10, ruling F32): the page's first start of that address within
     * 10 s, or its first start of any within 1 s, as a redirect changes the address. Every other download goes into the
     * Downloads folder without asking. Nothing after {@link #close()}.
     *
     * @param url the address about to be downloaded
     */
    public void askNextDownload(String url) {
        Objects.requireNonNull(url, "url");
        if (!closed) downloadState.askNext(url);
    }

    /**
     * Has Chromium download {@code url} in the page's browser, as Save Link As… and Save Image As… do, so the download is
     * the page's, in its browsing group. Nothing before the page has its browser or after {@link #close()}.
     *
     * @param url the address, as Chromium gave it
     */
    public void startDownload(String url) {
        Objects.requireNonNull(url, "url");
        if (!closed) downloadState.start(this, url);
    }

    /** Whether the page's next download asks where it goes, which the download handler takes. Any thread. */
    DownloadAsk downloadAsk() {
        return downloadState.ask();
    }

    /**
     * Has {@code hold} decide when the page's browser closes (plan 7's F1): the page's close asks it first, before it
     * does anything else, and closes the browser at once if the answer is complete; otherwise the browser loads
     * {@code about:blank}, which ends the page's scripts and sound but not its downloads, and closes once the answer
     * completes, normally or not, on the UI thread, once. A hold that throws closes the browser at once. Once Chromium
     * has stopped, its own close has ended every browser, and a held one is not closed again. Give it before
     * {@link #start}; it replaces the one before.
     *
     * @param hold given the page as it closes, answers when its browser may close, on any thread
     */
    public void setCloseHold(Function<Page, CompletableFuture<Void>> hold) {
        downloadState.hold(hold);
    }

    /**
     * Has {@code listener} told, on the UI thread, when the page's first main-frame navigation became a download, before
     * the page committed any document (plan 7's F28): the page's item sets it, and closes if a page opened it, as Chrome
     * closes such a tab. It replaces the one before.
     *
     * @param listener told on the UI thread
     */
    public void onFirstNavigationDownload(Runnable listener) {
        firstNavigationDownload = Objects.requireNonNull(listener, "listener");
    }

    /**
     * The page's downloads gave one of its downloads a target while the page had committed no document, so its first
     * navigation became a download: tells the page's item while the page still has none. Nothing after {@link #close()}.
     */
    public void firstNavigationDownloaded() {
        if (!closed && committed.isEmpty()) firstNavigationDownload.run();
    }

    /**
     * Has {@code listener} told, on the UI thread, each time a save chooser Chromium asked for the page is cancelled, also
     * after the page closed, as closing its item cancels the chooser it shows (plan 7's F1.3): the page's downloads set
     * it, and end the count of the download that asked, as Chromium may tell nothing of one cancelled before it began. It
     * replaces the one before.
     *
     * @param listener told on the UI thread
     */
    public void onSaveCancelled(Runnable listener) {
        saveCancelled = Objects.requireNonNull(listener, "listener");
    }

    /** A save chooser Chromium asked for the page was cancelled, and its callback with it: tells the page's downloads. */
    public void saveCancelled() {
        saveCancelled.run();
    }

    /**
     * Has {@code shower} select the page's item in its window and raise that window, when the page asks: the page's item
     * sets it. It replaces the one before.
     *
     * @param shower shows the item, on the UI thread
     */
    public void onShowItem(Runnable shower) {
        itemShower = Objects.requireNonNull(shower, "shower");
    }

    /** The page asks to show its item, as a script's {@code focus()} of its window does. Nothing after {@link #close()}. */
    public void showItem() {
        if (!closed) itemShower.run();
    }

    /**
     * Has the page's dialogs and file choosers show at {@code place}: the page's item gives it once it has its tab. It
     * replaces the one before.
     *
     * @param place over the item's tab, with the plugin's kit and windows
     */
    public void setDialogPlace(DialogPlace place) {
        Objects.requireNonNull(place, "place");
        if (!closed) dialogPlace = place;
    }

    /**
     * Where the page's dialogs and file choosers show.
     *
     * @return the place its item gave, or empty before that and after {@link #close()}
     */
    public Optional<DialogPlace> dialogPlace() {
        return closed ? Optional.empty() : Optional.ofNullable(dialogPlace);
    }

    /**
     * Has the page's right-click menu show at {@code place}: the page's item gives it once it has its tab. It replaces the
     * one before.
     *
     * @param place over the item's tab, with the plugin's clipboard and the chosen search engine
     */
    public void setMenuPlace(MenuPlace place) {
        Objects.requireNonNull(place, "place");
        if (!closed) menuPlace = place;
    }

    /**
     * Where the page's right-click menu shows.
     *
     * @return the place its item gave, or empty before that and after {@link #close()}
     */
    public Optional<MenuPlace> menuPlace() {
        return closed ? Optional.empty() : Optional.ofNullable(menuPlace);
    }

    /**
     * Tells {@code listener} each time Chromium cancels the page's dialogs, as it does when a new document replaces the
     * page's: a dialog shown or waiting to show for the page is then no longer asked.
     *
     * @param listener told on the UI thread
     * @return the registration; the page's close ends it
     */
    public Subscription onDialogsReset(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        if (closed) return () -> { };
        dialogResets.add(listener);
        return () -> dialogResets.remove(listener);
    }

    /** Chromium cancels the page's dialogs: tells those who asked {@link #onDialogsReset}. Nothing after {@link #close()}. */
    public void dialogsReset() {
        if (closed) return;
        for (Runnable listener : dialogResets) listener.run();
    }

    /**
     * Tells {@code listener} each time Jasper, not the page's document, sends the page elsewhere: {@link #load}, or a
     * {@link #back}, {@link #forward} or {@link #goToHistoryEntry} the page's history allows, as the user's address field,
     * Web menu, toolbar and mouse buttons do; a history entry Chromium then refuses has told it too. Chromium puts such a
     * page in a window group of its own, apart from its opener and its popups.
     *
     * @param listener told on the UI thread, before the page goes
     * @return the registration; the page's close ends it
     */
    public Subscription onNavigate(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        if (closed) return () -> { };
        navigations.add(listener);
        return () -> navigations.remove(listener);
    }

    /**
     * Tells {@code listener} once, when the page closes with its item; at once when it has closed already.
     *
     * @param listener told on the UI thread
     * @return the registration
     */
    public Subscription onClose(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        if (closed) {
            listener.run();
            return () -> { };
        }
        closes.add(listener);
        return () -> closes.remove(listener);
    }

    /**
     * Loads {@code url} in the page itself, as a plain click on a link to it would: for a modified click Chromium would
     * have opened elsewhere. Nothing before the page has its browser or after {@link #close()}.
     *
     * @param url the address, as Chromium gave it
     */
    public void follow(String url) {
        Objects.requireNonNull(url, "url");
        if (ready && !closed) browser.loadURL(url);
    }

    /**
     * Closes the page's browser, which ends only this page, tells those who asked {@link #onClose}, and stops telling
     * anything of it. Once. Its close hold is asked first ({@link #setCloseHold}): while it holds, the browser loads
     * {@code about:blank} instead and closes once the hold completes.
     */
    @Override public void close() {
        if (closed) return;
        closed = true;
        // Plan 7's F1: the page's downloads hear first that it closes, so none of its downloads starts after this, and
        // answer when its browser may close.
        CompletableFuture<Void> held = downloadState.held(this);
        loadStarts.clear();
        toolHooks.close();
        loginHooks.close();
        selectionGestures.clear();
        view.endSelectionGestures();
        visits.clear();
        navigationButtons.clear();
        magnifies.clear();
        navigations.clear();
        dialogResets.clear();
        addressChanges.clear();
        loadFailures.clear();
        refused = null;
        refusedUrl = null;
        certificates = certificate -> false;
        opener = (url, placement) -> Optional.empty();
        itemCloser = () -> { };
        itemShower = () -> { };
        firstNavigationDownload = () -> { };
        dialogPlace = null;
        menuPlace = null;
        privacy.close();
        dropFaviconFetch();
        titleRead.stop();
        if (find != null) find.close();
        if (devTools != null) devTools.close();
        view.setHoverUrl("");
        downloadState.end(this, held);
        List<Runnable> told = List.copyOf(closes);
        closes.clear();
        for (Runnable listener : told) listener.run();
    }

    /**
     * Runs {@code command} on the frame that has the page's focus, then releases only this handle to the frame; nothing
     * before the page has its browser or after its close.
     */
    private void onFocusedFrame(Consumer<CefFrame> command) {
        CefFrame frame = ready && !closed ? browser.getFocusedFrame() : null;
        if (frame == null) return;
        try {
            command.accept(frame);
        } finally {
            frame.dispose();
        }
    }

    /** Whether {@link #run} runs a script in a main frame at {@code address}, as {@link PageToolHooks#runsScriptsIn} says. */
    static boolean runsScriptsIn(String address) {
        return PageToolHooks.runsScriptsIn(address);
    }

    /**
     * Jasper sends the page elsewhere: its next main-frame request is marked Jasper's (plan 8's L42), and those who
     * asked {@link #onNavigate} are told.
     */
    private void navigated() {
        loginHooks.jaspersNavigation(clock.getAsLong());
        for (Runnable listener : navigations) listener.run();
    }

    /** The mouse's back or forward button was released over the page. */
    private void navigationButton(NavigationButton button) {
        if (closed) return;
        for (Consumer<NavigationButton> listener : navigationButtons) listener.accept(button);
    }

    /** Ctrl+wheel over the page magnifies it a step bigger, 1, or smaller, -1. */
    private void magnified(int direction) {
        if (closed) return;
        for (IntConsumer listener : magnifies) listener.accept(direction);
    }

    /** The user finished a selection in the page, as its canvas tells. */
    void selectionGestured() {
        if (closed) return;
        for (Runnable listener : selectionGestures) listener.run();
    }

    /** The browser, once it hears what it is asked, shows the page at its zoom. */
    private void showZoom() {
        if (ready && !closed) browser.setZoomLevel(zoomLevel);
    }

    /**
     * The client and the request context are ready: makes the browser in that context, null for Chromium's global one,
     * which loads what the page was last asked to. A page closed meanwhile, or a Chromium that has stopped, makes none,
     * and the context object handed out for it goes back at once ({@link Chromium#handBack}), which after Chromium's
     * sweep makes no native call.
     */
    void make(Chromium chromium, CefClient client, CefRequestContext requestContext) {
        downloadState.madeBy(chromium);
        if (closed || chromium.state() == Chromium.State.STOPPED) {
            unused(requestContext);
            return;
        }
        made = requested.toString();
        PageBrowser page = new PageBrowser(client, made, requestContext, new PageRenderer(view), view);
        page.owner(this);
        browser = page;
        page.attach();
    }

    /** Whether the page has made its browser. */
    boolean hasBrowser() {
        return browser != null;
    }

    /** The page's browser once made, whether or not it hears what it is asked yet; null before. */
    PageBrowser browser() {
        return browser;
    }

    /**
     * A context object handed out for a browser that is not made goes back: JCEF, which disposes a browser's object as
     * the browser closes, never has it. Null, Chromium's global context, needs nothing.
     */
    private static void unused(CefRequestContext context) {
        Chromium.handBack(context);
    }

    /** The browser's native browser exists: from now on it hears what it is asked, and loads what it missed. */
    void created(PageBrowser created) {
        if (closed || created != browser) return;
        ready = true;
        showZoom();
        String next = requested.toString();
        if (!next.equals(made)) browser.loadURL(next);
    }

    /** The main frame's address changed: a navigation there committed. */
    void addressChanged(String url) {
        if (closed || url == null) return;
        address = url;
        committed = url;
        changed.run();
        for (Consumer<String> listener : addressChanges) listener.accept(url);
        readHistory();
    }

    /** The page's title changed: the history, whose entries carry titles, is read a moment later (plan 4b's W9). */
    void titleChanged(String value) {
        if (closed) return;
        title = value == null ? "" : value;
        changed.run();
        if (!titleRead.isRunning()) titleRead.start();
    }

    /** The main frame finished loading its document. */
    void loadEnded() {
        if (closed) return;
        documentLoaded = true;
        readHistory();
        toolHooks.loadEnded();
    }

    /**
     * A load of the main frame failed: those who asked {@link #onLoadFailure} are told, with the certificate refused for
     * {@code url}, which this failure takes.
     */
    void loadFailed(String url, String code) {
        if (closed || url == null) return;
        Optional<ServerCertificate> certificate = Optional.empty();
        if (refused != null && url.equals(refusedUrl)) {
            certificate = Optional.of(refused);
            refused = null;
            refusedUrl = null;
        }
        var failure = new LoadFailure(url, code == null ? "" : code, certificate);
        for (Consumer<LoadFailure> listener : loadFailures) listener.accept(failure);
    }

    /**
     * Reads the page's history again, as it may have changed: at once, or once the read that runs ends, so that one read
     * runs at a time however often the page changes.
     */
    private void readHistory() {
        titleRead.stop(); // this read covers the title changed so far
        if (closed) return;
        if (readingHistory) {
            historyStale = true;
            return;
        }
        readingHistory = true;
        historyStale = false;
        historyReader.apply(this).whenComplete((read, failure) -> EventQueue.invokeLater(() ->
            historyRead(failure == null && read != null ? read : NavigationHistory.History.EMPTY)));
    }

    /** The zoom the page shows at, as Chromium's level. Any thread. */
    double zoomLevel() {
        return zoomLevel;
    }

    /** The page's browser once it hears what it is asked; null before. */
    PageBrowser readyBrowser() {
        return ready ? browser : null;
    }

    /**
     * Runs {@code script} in each of the page's frames but challenge frames; nothing before its browser is ready. The
     * page hands itself over, not its browser, so that loading this class loads no Chromium type.
     */
    private void inEveryFrame(String script) {
        FrameScripts.everyFrame(this, script);
    }

    /**
     * New DevTools for the page at {@code at}, on a new canvas, waiting for those made before; whether Chromium can make
     * their browser is theirs to find out ({@link #inspect} makes them only for a ready page).
     */
    DevTools devTools(Point at) {
        DevTools made = new DevTools(this, new PageView(), at, devTools);
        devTools = made;
        made.onEnd(() -> { if (devTools == made) devTools = null; });
        if (closed) made.close();
        return made;
    }

    /**
     * Has Chromium make {@code tools}' browser, inspecting the element at {@code at} on the page's canvas, through the
     * page's browser: what closes it, or empty while the page's browser is not ready or the page has closed.
     */
    Optional<Runnable> openDevTools(DevTools tools, Point at) {
        if (devToolsOpener != null) return Objects.requireNonNull(devToolsOpener.apply(tools, at), "opened");
        PageBrowser target = readyBrowser();
        if (target == null || closed) return Optional.empty();
        target.openDevTools(tools, at);
        return Optional.of(target::closeDevTools);
    }

    /** Has the page ask for its DevTools' browsers with {@code replacement} from now on, as a test's recording one. */
    void useDevToolsOpener(BiFunction<DevTools, Point, Optional<Runnable>> replacement) {
        devToolsOpener = Objects.requireNonNull(replacement, "replacement");
    }

    /** Has the page go to its history's entries with {@code replacement} from now on, as a test's recording one. */
    void useHistoryJumper(ObjIntConsumer<Page> replacement) {
        historyJumper = Objects.requireNonNull(replacement, "replacement");
    }

    /** Has the page read the HTML it shows with {@code replacement} from now on, as a test's. */
    void useSourceReader(Function<Page, CompletableFuture<Optional<String>>> replacement) {
        sourceReader = Objects.requireNonNull(replacement, "replacement");
    }

    /** Has the page put text into its focused field with {@code replacement} from now on, as a test's answering one. */
    void useFieldInserter(BiFunction<Page, String, CompletableFuture<Insertion>> replacement) {
        fieldInserter = Objects.requireNonNull(replacement, "replacement");
    }

    /** Has the page type text where its focus is with {@code replacement} from now on, as a test's answering one. */
    void useTextInserter(BiFunction<Page, String, CompletableFuture<Void>> replacement) {
        textInserter = Objects.requireNonNull(replacement, "replacement");
    }

    /** Has the page load again with {@code replacement} from now on, as a test's recording one. */
    void useReloader(Consumer<Page> replacement) {
        reloader = Objects.requireNonNull(replacement, "replacement");
    }

    /** Has the page run the scripts asked of it ({@link #run}) with {@code replacement} from now on, as a test's recording one. */
    void useScriptRunner(BiConsumer<String, String> replacement) {
        toolHooks.useScriptRunner(replacement);
    }

    /** Has the page press Return with {@code replacement} from now on, as a test's recording one. */
    void useReturnPresser(Consumer<Page> replacement) {
        returnPresser = Objects.requireNonNull(replacement, "replacement");
    }

    /** Has the page read what is selected in it with {@code replacement} from now on, as a test's answering one. */
    void useSelectionReader(Function<Page, CompletableFuture<Selection>> replacement) {
        selectionReader = Objects.requireNonNull(replacement, "replacement");
    }

    /** Has the page run text insertions and selection reads with {@code replacement} from now on, as a test's. */
    void usePageText(PageText replacement) {
        pageText = Objects.requireNonNull(replacement, "replacement");
    }

    /** Has the page run Chromium's copy with {@code replacement} from now on, as a test's recording one. */
    void useCopier(Consumer<Page> replacement) {
        copier = Objects.requireNonNull(replacement, "replacement");
    }

    /** Has the page start its downloads with {@code replacement} from now on, as a test's recording one. */
    void useDownloader(BiConsumer<Page, String> replacement) {
        downloadState.useDownloader(replacement);
    }

    /**
     * Has the page load {@code about:blank} in its browser with {@code blank}, and close its browser with {@code close},
     * from now on, as a test's recording ones.
     */
    void useBrowserEnding(Consumer<Page> blank, Consumer<Page> close) {
        downloadState.useBrowserEnding(blank, close);
    }

    /**
     * The page's text insertion and selection reads through its browser's DevTools, made the first time either is asked
     * for: none before the browser is ready, after {@link #close()}, or for a browser without a DevTools client.
     */
    private Optional<PageText> pageText() {
        PageBrowser ready = readyBrowser();
        if (ready == null || closed) return Optional.empty();
        if (pageText == null) pageText = PageText.of(ready, background).orElse(null);
        return Optional.ofNullable(pageText);
    }

    /** What text inserted into a page without its browser gives: a failure, so its item does nothing more. */
    private static <T> CompletableFuture<T> noBrowser() {
        return CompletableFuture.failedFuture(new IllegalStateException("The page has no browser to insert text into"));
    }

    /** Has the page find with {@code replacement} from now on, as a test's recording one. */
    void useFind(PageFind replacement) {
        find = Objects.requireNonNull(replacement, "replacement");
        toolHooks.searchedBy(find);
    }

    /**
     * A read of the page's history gave {@code read}, or {@link NavigationHistory.History#EMPTY} when it failed: the
     * page's history from now on, and the page reads it again if it changed since the read began.
     */
    void historyRead(NavigationHistory.History read) {
        readingHistory = false;
        if (closed) return;
        if (!read.equals(history)) {
            history = read;
            changed.run();
        }
        if (historyStale) readHistory();
    }

    /** The page started or stopped loading, and where it can go changed. */
    void loadingChanged(boolean isLoading, boolean back, boolean forward) {
        if (closed) return;
        loading = isLoading;
        canGoBack = back;
        canGoForward = forward;
        changed.run();
    }

    /**
     * The main frame started loading {@code url}, reached {@code reached}: the page has no title until the new document
     * gives one, whatever Chromium reports for a document without one, and no favicon until the new document names one. A
     * favicon still being fetched for the document before is dropped, and the find's search ends.
     */
    void loadStarted(String url, Reached reached) {
        if (closed) return;
        if (find != null) find.dropped();
        document++;
        if (pageText != null) pageText.newDocument(); // the world it ran in went with the document
        documentLoaded = false;
        dropFaviconFetch();
        declaredIcon = null;
        boolean cleared = !title.isEmpty() || favicon.isPresent();
        title = "";
        favicon = Optional.empty();
        if (cleared) changed.run();
        String started = url == null ? "" : url;
        for (Consumer<String> listener : loadStarts) listener.accept(started);
        for (BiConsumer<String, Reached> listener : visits) listener.accept(started, reached);
    }

    /** The icon the current document named, or empty before it names one. */
    Optional<URI> declaredFavicon() {
        return Optional.ofNullable(declaredIcon);
    }

    /** Whether the page fetches a favicon. */
    boolean fetchingFavicon() {
        return iconFetch != null;
    }

    /**
     * Which document the main frame shows, counted by its load starts: what changed since a count was read tells a load
     * start came in between, as Save Page As checks after its chooser (plan 7's F26). UI thread.
     *
     * @return the count
     */
    public long document() {
        return document;
    }

    /** A favicon fetched for the {@code fetchedFor}th document arrived: it is the page's while the page shows that one. */
    void faviconArrived(long fetchedFor, Optional<PixelImage> image) {
        if (closed || image.isEmpty() || fetchedFor != document) return;
        favicon = image;
        changed.run();
    }

    private void dropFaviconFetch() {
        CompletableFuture<Optional<PixelImage>> fetch = iconFetch;
        iconFetch = null;
        if (fetch != null) fetch.cancel(true);
    }

    /** The HTML {@code browser}'s main frame shows, as Chromium's {@code getSource} gives it; none without a browser. */
    private static CompletableFuture<Optional<String>> chromiumSource(PageBrowser browser) {
        var source = new CompletableFuture<Optional<String>>();
        if (browser == null) {
            source.complete(Optional.empty());
            return source;
        }
        source.completeOnTimeout(Optional.empty(), SOURCE_WAIT_SECONDS, TimeUnit.SECONDS);
        browser.getSource(html -> source.complete(Optional.ofNullable(html)));
        return source;
    }

    /** Whether two addresses are the same page: the same but for their fragments. */
    private static boolean samePage(String one, String other) {
        return withoutFragment(one).equals(withoutFragment(other));
    }

    private static String withoutFragment(String address) {
        int hash = address.indexOf('#');
        return hash < 0 ? address : address.substring(0, hash);
    }

    /** The address of the link under the pointer, or empty. Any thread. */
    void hovered(String url) {
        if (!closed) view.setHoverUrl(url);
    }
}
