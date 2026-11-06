package dev.jasper.browser;

import dev.jasper.browser.api.MarkdownPreviews;
import dev.jasper.browser.dialogs.PageDialogs;
import dev.jasper.browser.engine.AboutQueries;
import dev.jasper.browser.engine.AboutScheme;
import dev.jasper.browser.engine.Chromium;
import dev.jasper.browser.engine.PreviewScheme;
import dev.jasper.browser.engine.RunMarker;
import dev.jasper.browser.item.BookmarkPanel;
import dev.jasper.browser.item.BrowserItems;
import dev.jasper.browser.item.ErrorPage;
import dev.jasper.browser.item.SourcePage;
import dev.jasper.browser.item.WebMenu;
import dev.jasper.browser.links.PageNavigation;
import dev.jasper.browser.palette.BookmarksScope;
import dev.jasper.browser.palette.PaletteOpener;
import dev.jasper.browser.palette.VisitedScope;
import dev.jasper.browser.pages.AboutPages;
import dev.jasper.browser.pages.BookmarksPage;
import dev.jasper.browser.pages.HistoryPage;
import dev.jasper.browser.pages.Onboarding;
import dev.jasper.browser.pages.OnboardingPages;
import dev.jasper.browser.pages.WelcomePage;
import dev.jasper.browser.preview.PreviewService;
import dev.jasper.browser.restore.BrowserRestore;
import dev.jasper.browser.routing.BrowserRole;
import dev.jasper.browser.routing.WebAddressItems;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.browser.store.BrowserDatabase;
import dev.jasper.browser.store.Stores;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.PluginMenu;
import dev.jasper.sdk.ui.SplitItemSpec;
import dev.jasper.sdk.ui.StandardMenu;
import dev.jasper.sdk.ui.TabPlacement;
import java.awt.EventQueue;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * The Browser: web pages as Jasper items. Its start registers New Browser Tab, New Browser Window and the Browser split
 * item, the globe they show, the Web menu, Settings › Browser, the Bookmarks notification group, the welcome page and the onboarding pages, the
 * history page and its queries, the Bookmarks and Browser History palette scopes, the error page with its Try Again and Continue actions, View Source's page, the Browser
 * as Jasper's web browser, the terminal's Open URL in Split items and the restorer of saved browser items, publishes
 * the Markdown previews' service ({@link MarkdownPreviews}), and makes Chromium's engine, which starts Chromium only
 * when a browser item or a Markdown preview first shows, so starting the plugin, and restoring its items, loads no
 * Chromium class. After a run that ended while Chromium ran, the items it restores wait to be loaded. It makes the
 * items' history and bookmarks too, in {@code browser.sqlite} in its data folder, which opens only once an item first
 * shows or its address field is edited, so the start opens no database and loads no sqlite-jdbc class
 * (spec section 19).
 */
public class BrowserPlugin implements Plugin {
    static final String NEW_TAB = "dev.jasper.browser.new_tab", NEW_WINDOW = "dev.jasper.browser.new_window";
    /** The Browser split item: Split Right with Browser and Split Down with Browser. */
    static final SplitItemSpec SPLIT = SplitItemSpec.of("dev.jasper.browser.browser", "Browser").withIcon(BrowserItems.GLOBE);
    private PluginContext context;
    private ExecutorService worker;
    private Chromium chromium;
    private PreviewService previews;
    private BrowserItems items;
    private Stores stores;
    private SourcePage sources;
    private boolean running;

    /** Created by the runtime. */
    public BrowserPlugin() { }

    @Override public void start(PluginContext context) {
        this.context = context;
        Path data = context.dataDirectory();
        // Chromium runs in Jasper's process, so a fatal Chromium error ends Jasper while Chromium runs, which leaves its
        // marker. Restored items then wait to be loaded, as loading them by themselves would start Chromium again.
        boolean endedWhileRunning = RunMarker.clear(data);
        // Chromium starts on a platform thread of the plugin's own: its pre-initialisation needs the UI thread free, and
        // it must not hold one of the SDK's virtual threads. The executor makes no thread until a page asks for Chromium.
        worker = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("jasper-browser-chromium").factory());
        var about = new AboutScheme();
        // The Markdown previews' origins, which read nothing and make no random source until a preview is first made.
        var origins = new PreviewScheme();
        chromium = new Chromium(data, worker, about, origins, this::startFailed);
        // Spec section 18: the Editor's Chromium Markdown preview. A preview asks for Chromium only once it first shows.
        previews = new PreviewService(origins, chromium, context.background());
        context.services().publish(MarkdownPreviews.class, previews);
        BrowserSettings.Declared settings = BrowserSettings.declare(context.settings());
        // Spec section 12: history and bookmarks in one database, made with no I/O; it opens at its first use. Why it cannot
        // open is told on its own thread.
        var database = BrowserDatabase.file(data.resolve("browser.sqlite"), cause -> EventQueue.invokeLater(() -> storesUnavailable(cause)));
        stores = new Stores(database, EventQueue::invokeLater);
        // Spec section 15: "Bookmark Removed" and "Bookmark Deleted" post in a group of their own, which Settings lists.
        context.notifications().group(BookmarkPanel.GROUP);
        // A page is made when its item first shows: the client's handlers are installed then, before any page is made on
        // it, as naming their types loads Chromium's classes. Jasper's own pages ask Java through a router of their own,
        // read and answered on the background executor.
        Chromium engine = chromium;
        // Spec section 1: whether the onboarding is finished, read from its marker on the background executor.
        var onboarding = new Onboarding(data.resolve("onboarding-finished"), context.background());
        var items = new BrowserItems(context, settings::read, stores, page -> {
            PageNavigation.prepare(engine);
            PageDialogs.prepare(engine);
            AboutQueries.prepare(engine, about, context.background());
            // Before the first page asks for the welcome page, so that Chromium's IO thread only reads the answer from memory.
            onboarding.load();
            page.start(engine);
        });
        this.items = items;
        about.registerAction(WelcomePage.SETTINGS, () -> { if (running) items.showSettings(); });
        // Jasper's pages built from their templates, in the workspace theme. Spec section 10: the history and bookmarks
        // pages, whose queries are answered from the history and the bookmarks; the asking browser is not needed yet.
        var pages = new AboutPages(context.appearance(), about);
        var history = stores.history();
        // The welcome page, which sends the user to the onboarding until it is finished, and the onboarding's pages. No
        // setting is offered on the setup page yet.
        WelcomePage.register(about, pages, history, onboarding);
        new OnboardingPages(pages, about, onboarding, System.getProperty("os.name", "").startsWith("Mac"), List.of(), EventQueue::invokeLater).register();
        about.register(HistoryPage.NAME, () -> pages.render(HistoryPage.NAME, Map.of()));
        about.registerQueries(HistoryPage.NAME, (browser, request) -> HistoryPage.answer(history, request));
        var bookmarks = stores.bookmarks();
        about.register(BookmarksPage.NAME, () -> pages.render(BookmarksPage.NAME, Map.of()));
        about.registerQueries(BookmarksPage.NAME, (browser, request) -> BookmarksPage.answer(bookmarks, request));
        // Spec section 14: a failed load's page, and what its Try Again and Continue ask of the item whose page it is.
        var errors = new ErrorPage(context.appearance());
        about.register(ErrorPage.NAME, errors::html);
        about.registerAction(ErrorPage.TRY_AGAIN, address -> { if (running) items.tryAgain(address); });
        about.registerAction(ErrorPage.PROCEED, address -> { if (running) items.proceed(address); });
        // Spec section 10: View Source's page, fetched again or as its item captured it, and never built on Chromium's IO
        // thread (P5).
        var sources = new SourcePage(context.appearance(), context.background(), items::capturedSource);
        this.sources = sources;
        about.registerLater(SourcePage.NAME, SourcePage.WAIT, sources::html);
        // Spec section 5: New Browser Tab and New Browser Window, in the File menu and the palette, with no default key.
        context.actions().register(ActionSpec.of(NEW_TAB, "New Browser Tab").withIcon(BrowserItems.GLOBE)
                .withKeywords(List.of("browser", "web", "page", "new", "tab")),
            invoked -> openNew(items, settings, TabPlacement.NEW_TAB, invoked.window()));
        context.actions().register(ActionSpec.of(NEW_WINDOW, "New Browser Window").withIcon(BrowserItems.GLOBE)
                .withKeywords(List.of("browser", "web", "page", "new", "window")),
            invoked -> openNew(items, settings, TabPlacement.NEW_WINDOW, invoked.window()));
        // The host's Split submenus offer Browser Right and Browser Down.
        context.windows().splitItem(SPLIT, (shown, placement) -> openNew(items, settings, placement, shown));
        PluginMenu file = context.menus().standard(StandardMenu.FILE);
        file.add(NEW_TAB);
        file.add(NEW_WINDOW);
        WebMenu.register(context, items);
        // Spec section 11: bookmarks and visited sites in the command palette. They read the indexes, which load when the
        // palette first asks, so the start reads nothing.
        var opener = new PaletteOpener(items, engine::unavailable, this::tell);
        context.palette().register(new BookmarksScope(stores.bookmarkIndex(), opener::open));
        context.palette().register(new VisitedScope(stores.visitIndex(), opener::open));
        // Spec section 9: the links Jasper opens come to the Browser first.
        var role = new BrowserRole(context, items, settings, engine::state);
        context.platform().browser(role);
        WebAddressItems.register(context, role, settings);
        // Spec section 16: a saved item comes back at once, and its page loads the first time it shows, or, after a run
        // that ended while Chromium ran, once the user loads it.
        context.windows().restorer(BrowserRestore.KIND, request -> {
            items.restore(request.state(), settings.read().startAddress(), request.window(), endedWhileRunning);
            return Optional.empty();
        });
        running = true;
    }

    /**
     * Jasper quits. The items give history their pages' last titles, then the history and bookmarks close: their database
     * takes no more work and closes once the work given before has run. The items' favicon fetches and View Source's
     * fetches end. On the UI thread {@link Chromium#close()} only starts Chromium's dispose, and every Markdown preview
     * still open is then unavailable. The database's end and Chromium's are waited for on the plugin's background
     * executor: Jasper's quit waits for that work within its own shutdown grace, so quitting holds briefly for them and
     * never hangs on them.
     */
    @Override public void stop() {
        running = false;
        if (items != null) items.close();
        if (stores != null) stores.close();
        if (sources != null) sources.close();
        chromium.close();
        // Once closed, Chromium is unavailable for the rest of the run.
        if (previews != null) chromium.unavailable().ifPresent(previews::unavailable);
        try {
            context.background().execute(chromium::close);
            if (stores != null) {
                CompletableFuture<Void> storesClosed = stores.closed();
                context.background().execute(() -> awaitClosed(storesClosed));
            }
        } catch (RejectedExecutionException closed) {
            // Nothing holds the quit for Chromium's end or the database's, which come on their own.
        }
        worker.shutdown();
    }

    /**
     * History and bookmarks cannot be kept in their file: the reason goes to the plugin's log and, while the plugin runs,
     * to one notification. They are kept in memory until Jasper quits, or not at all. UI thread.
     */
    void storesUnavailable(Throwable cause) {
        context.log().log(System.Logger.Level.WARNING, "Browser history and bookmarks are unavailable: " + reason(cause), cause);
        if (running) context.notifications().group().warning("Browser history and bookmarks are unavailable", reason(cause));
    }

    /** The plugin's history and bookmarks. */
    Stores stores() {
        return stores;
    }

    /** Waits, on the background executor, until the database has closed; Jasper's quit may interrupt the wait. */
    private static void awaitClosed(CompletableFuture<Void> closed) {
        try {
            closed.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException never) {
            // closed() completes normally: a database whose close failed has logged why.
        }
    }

    /**
     * Chromium cannot serve pages: the reason goes to the plugin's log and, while the plugin runs, to one notification,
     * and every live Markdown preview is unavailable. UI thread.
     */
    void startFailed(Throwable cause) {
        context.log().log(System.Logger.Level.WARNING, "The browser could not start: " + reason(cause), cause);
        tell(cause);
        if (previews != null) previews.unavailable(cause);
    }

    /** The plugin's Chromium. */
    Chromium chromium() {
        return chromium;
    }

    /**
     * New Browser Tab, New Browser Window and the split item: a new browser item at the start page, in or from
     * {@code window}. Once Chromium cannot serve pages, no page could show, so the notification of why is posted again
     * instead.
     */
    private void openNew(BrowserItems items, BrowserSettings.Declared settings, TabPlacement placement, WindowOwner window) {
        Optional<Throwable> unavailable = chromium.unavailable();
        if (unavailable.isPresent()) tell(unavailable.get());
        else items.open(settings.read().startAddress(), placement, Optional.empty(), Optional.of(window));
    }

    /** The notification that the browser could not start, with {@code cause}'s reason, while the plugin runs. */
    private void tell(Throwable cause) {
        if (running) context.notifications().group().error("The browser could not start", reason(cause));
    }

    private static String reason(Throwable cause) {
        return cause.getMessage() == null || cause.getMessage().isBlank() ? cause.toString() : cause.getMessage();
    }
}
