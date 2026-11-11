package dev.jasper.browser.item;

import dev.jasper.browser.page.PageTestAccess;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.browser.store.BookmarkStore.Bookmark;
import dev.jasper.browser.store.BrowserDatabase;
import dev.jasper.browser.store.DataFolder;
import dev.jasper.browser.store.MemoryStores;
import dev.jasper.browser.store.Reached;
import dev.jasper.browser.store.Stores;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.MenuItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.TabPlacement;
import dev.jasper.sdk.ui.UiEvent;
import java.awt.EventQueue;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Spec section 10's bookmarks from the menu button: Add Bookmark stores the page at once and opens the Edit Bookmark
 * panel, a popover from the menu button, where tags are edited and saved or the bookmark is deleted after a question;
 * Remove Bookmark removes it. Each item here is a stand-in's, its stores in memory, as the plugin's own would open its
 * file; nothing shows a page, so nothing asks Chromium for one. The stores' answers reach the UI thread as the plugin's
 * do, and the test waits for them ({@link MemoryStores#settle}).
 */
class BookmarkPanelTest {
    private static final String ID = "dev.jasper.browser", A = "https://a.example/", B = "https://b.example/";
    private static final String GROUP = "dev.jasper.browser.bookmarks";
    private final MemoryStores memory = new MemoryStores();
    private FakePluginHost host;
    private PluginContext context;
    private BrowserItems items;
    private BrowserItem item;
    private FakeKitView view;

    @AfterEach void close() throws Exception {
        if (host != null) onUi(() -> {
            host.close();
            return null;
        });
        memory.close();
    }

    @Test void addBookmarkStoresThePageAndShowsThePanelWithItsTitleAndAddress() throws Exception {
        Stores stores = open(A);
        onUi(() -> {
            PageTestAccess.titleChanged(item.page(), "A page");
            assertThat(menu().getFirst().label()).isEqualTo("Add Bookmark");
            view.choose(BrowserToolbar.MENU, BrowserToolbar.BOOKMARK_ADD);
        });
        memory.settle();
        assertThat(bookmarks(stores)).singleElement().satisfies(bookmark -> {
            assertThat(bookmark.url()).isEqualTo(A);
            assertThat(bookmark.title()).isEqualTo("A page");
            assertThat(bookmark.tags()).isEmpty();
        });
        onUi(() -> {
            FakeKitView panel = panel();
            var form = (Nodes.Form) panel.spec().root();
            assertThat(form.title()).isEqualTo("Edit Bookmark");
            Nodes.Field title = (Nodes.Field) Views.node(form, BookmarkPanel.TITLE), url = (Nodes.Field) Views.node(form, BookmarkPanel.URL);
            assertThat(title.value()).isEqualTo("A page");
            assertThat(title.readOnly()).isTrue();
            assertThat(url.value()).isEqualTo(A);
            assertThat(url.readOnly()).isTrue();
            var tags = (Nodes.TokenField) Views.node(form, BookmarkPanel.TAGS);
            assertThat(tags.hint()).isEqualTo("Enter tags…");
            assertThat(tags.tokens()).isEmpty();
            assertThat(rowLabels(form)).containsExactly("Title", "URL", "Tags");
            assertThat(form.actions()).extracting(Nodes.Button::label, Nodes.Button::kind).containsExactly(
                tuple("Delete Bookmark", Nodes.ButtonKind.DESTRUCTIVE_GHOST),
                tuple("Save", Nodes.ButtonKind.PRIMARY));
            assertThat(panel.focused()).as("the tags take the focus").contains(BookmarkPanel.TAGS);
            assertThat(menu().getFirst().label()).as("the menu offers to remove it now").isEqualTo("Remove Bookmark");
            assertThat(host.failures()).isEmpty();
        });
    }

    @Test void saveWritesTheTagsAndClosesThePanel() throws Exception {
        Stores stores = open(A);
        onUi(() -> view.choose(BrowserToolbar.MENU, BrowserToolbar.BOOKMARK_ADD));
        memory.settle();
        onUi(() -> {
            FakeKitView panel = panel();
            panel.type(BookmarkPanel.TAGS, "work, news,");
            assertThat(panel.tokens(BookmarkPanel.TAGS)).containsExactly("work", "news");
            panel.emit(new UiEvent.Action(BookmarkPanel.SAVE));
            assertThat(host.popoverViews(ID)).as("Save closes the panel").isEmpty();
        });
        memory.settle();
        assertThat(bookmarks(stores)).singleElement().satisfies(bookmark -> assertThat(bookmark.tags()).containsExactly("work", "news"));
        assertThat(onUi(() -> host.notifications().lines())).isEmpty();
    }

    /** H23 and H25: the tags typed before Delete Bookmark are kept, so its Cancel leaves the bookmark as the panel showed it. */
    @Test void deleteBookmarkAsksAndCancelKeepsTheBookmarkWithTheTagsTypedBeforeIt() throws Exception {
        Stores stores = open(A);
        onUi(() -> view.choose(BrowserToolbar.MENU, BrowserToolbar.BOOKMARK_ADD));
        memory.settle();
        onUi(() -> {
            panel().type(BookmarkPanel.TAGS, "work,");
            panel().emit(new UiEvent.Action(BookmarkPanel.DELETE));
            assertThat(host.popoverViews(ID)).as("the panel closes first").isEmpty();
        });
        onUi(() -> {
            FakeKitView question = host.windowView(ID, "Delete Bookmark").orElseThrow();
            assertThat(host.dialogPresentation(ID, "Delete Bookmark")).contains(DialogSpec.Presentation.OVERLAY);
            assertThat(context.windows().hasModal(item.tab())).as("a modal over the item's window").isTrue();
            assertThat(host.windows()).containsExactly("dialog|Delete Bookmark|true");
            var form = (Nodes.Form) question.spec().root();
            assertThat(((Nodes.Text) Views.node(form, BookmarkPanel.QUESTION)).text()).isEqualTo("Are you sure you want to delete this bookmark?");
            assertThat(form.actions()).extracting(Nodes.Button::label, Nodes.Button::kind).containsExactly(
                tuple("Cancel", Nodes.ButtonKind.SECONDARY),
                tuple("Delete", Nodes.ButtonKind.PRIMARY));
            assertThat(form.cancelAction()).as("Escape is Cancel").isEqualTo(BookmarkPanel.CANCEL_DELETE);
            question.emit(new UiEvent.Action(BookmarkPanel.CANCEL_DELETE));
            assertThat(host.windowView(ID, "Delete Bookmark")).isEmpty();
        });
        memory.settle();
        assertThat(bookmarks(stores)).singleElement().satisfies(bookmark -> {
            assertThat(bookmark.url()).isEqualTo(A);
            assertThat(bookmark.tags()).isEqualTo(List.of("work"));
        });
        assertThat(onUi(() -> host.notifications().lines())).isEmpty();
    }

    /** The question is the item's tab's: it closes with the tab, and nothing is deleted. */
    @Test void theDeleteQuestionClosesWithTheItemsTab() throws Exception {
        Stores stores = open(A);
        onUi(() -> view.choose(BrowserToolbar.MENU, BrowserToolbar.BOOKMARK_ADD));
        memory.settle();
        onUi(() -> panel().emit(new UiEvent.Action(BookmarkPanel.DELETE)));
        onUi(() -> {
            assertThat(host.windowView(ID, "Delete Bookmark")).isPresent();
            item.close();
            assertThat(host.windowView(ID, "Delete Bookmark")).as("closed with the tab").isEmpty();
        });
        memory.settle();
        assertThat(bookmarks(stores)).extracting(Bookmark::url).containsExactly(A);
    }

    /** H23: Escape, the form's cancel, closes the panel and keeps the bookmark's tags as they were. */
    @Test void escapeClosesThePanelAndDiscardsTheTagsTyped() throws Exception {
        Stores stores = open(A);
        onUi(() -> view.choose(BrowserToolbar.MENU, BrowserToolbar.BOOKMARK_ADD));
        memory.settle();
        var writes = writes(stores);
        onUi(() -> {
            FakeKitView panel = panel();
            panel.type(BookmarkPanel.TAGS, "work,");
            assertThat(((Nodes.Form) panel.spec().root()).cancelAction()).as("Escape").isEqualTo(BookmarkPanel.CANCEL);
            panel.emit(new UiEvent.Action(BookmarkPanel.CANCEL));
            assertThat(host.popoverViews(ID)).isEmpty();
        });
        memory.settle();
        assertThat(bookmarks(stores)).singleElement().satisfies(bookmark -> assertThat(bookmark.tags()).isEmpty());
        assertThat(writes).as("nothing written").hasValue(0);
    }

    /** H23: the page's next load start keeps the tags the panel shows, then closes it, and so does the item's close. */
    @Test void aLoadStartAndTheItemsCloseKeepTheTagsShown() throws Exception {
        Stores stores = open(A);
        onUi(() -> view.choose(BrowserToolbar.MENU, BrowserToolbar.BOOKMARK_ADD));
        memory.settle();
        var writes = writes(stores);
        onUi(() -> {
            panel().type(BookmarkPanel.TAGS, "work,");
            PageTestAccess.loadStarted(item.page(), B, Reached.LINK);
            assertThat(host.popoverViews(ID)).isEmpty();
        });
        memory.settle();
        assertThat(bookmarks(stores)).singleElement().satisfies(bookmark -> assertThat(bookmark.tags()).containsExactly("work"));
        assertThat(writes).as("one write").hasValue(1);
        onUi(() -> {
            PageTestAccess.addressChanged(item.page(), B);
            view.choose(BrowserToolbar.MENU, BrowserToolbar.BOOKMARK_ADD);
        });
        memory.settle();
        onUi(() -> {
            panel().type(BookmarkPanel.TAGS, "news,");
            item.close();
        });
        memory.settle();
        assertThat(bookmarks(stores)).filteredOn(bookmark -> bookmark.url().equals(B)).singleElement()
            .satisfies(bookmark -> assertThat(bookmark.tags()).containsExactly("news"));
    }

    /**
     * H23: a click outside, which closes the popover as its host does, keeps the tags shown. The panel is opened here as
     * its item opens it, and closed through {@link BookmarkPanel#close}, what the host's dismissal amounts to.
     */
    @Test void aClickOutsideKeepsTheTagsShown() throws Exception {
        Stores stores = open(A);
        Bookmark bookmark = stores.bookmarks().add(A, "A page", 1L).get(10, TimeUnit.SECONDS);
        onUi(() -> {
            BookmarkPanel shown = BookmarkPanel.open(context, view.anchor(BrowserToolbar.MENU), stores, bookmark,
                context.notifications().group(BookmarkPanel.GROUP), item.page(), item.tab());
            panel().type(BookmarkPanel.TAGS, "work, news,");
            shown.close();
            assertThat(host.popoverViews(ID)).isEmpty();
        });
        memory.settle();
        assertThat(bookmarks(stores)).singleElement().satisfies(kept -> assertThat(kept.tags()).containsExactly("work", "news"));
    }

    /** H23: a panel that closes with the tags it opened with writes nothing, as each write reads the bookmarks again. */
    @Test void aCloseWithTheTagsUnchangedWritesNothing() throws Exception {
        Stores stores = open(A);
        onUi(() -> view.choose(BrowserToolbar.MENU, BrowserToolbar.BOOKMARK_ADD));
        memory.settle();
        var writes = writes(stores);
        onUi(() -> {
            panel().type(BookmarkPanel.TAGS, "work,");
            panel().press(BookmarkPanel.TAGS, "BACK_SPACE");
            assertThat(panel().tokens(BookmarkPanel.TAGS)).as("typed, then removed").isEmpty();
            PageTestAccess.loadStarted(item.page(), B, Reached.LINK);
        });
        memory.settle();
        assertThat(writes).as("the load start's close").hasValue(0);
        onUi(() -> {
            PageTestAccess.addressChanged(item.page(), B);
            view.choose(BrowserToolbar.MENU, BrowserToolbar.BOOKMARK_ADD);
        });
        memory.settle();
        writes.set(0);
        onUi(() -> panel().emit(new UiEvent.Action(BookmarkPanel.SAVE)));
        memory.settle();
        assertThat(writes).as("an unchanged Save").hasValue(0);
        assertThat(bookmarks(stores)).allSatisfy(bookmark -> assertThat(bookmark.tags()).isEmpty());
    }

    /**
     * Plan 5's final review: at quit, or as the plugin is turned off, the host stops the plugin before it closes its
     * popovers, and the stop closes the stores; so the items close an open panel first, as the stop closes them, and the
     * bookmark keeps the tags shown (H23). The stores keep a file here, which a second database reads once they closed.
     */
    @Test void aPanelOpenAsThePluginStopsKeepsItsTagsBeforeTheStoresClose(@TempDir Path data) throws Exception {
        Stores stores = new Stores(BrowserDatabase.file(new DataFolder(data), cause -> { }), EventQueue::invokeLater);
        try {
            open(A, stores);
            onUi(() -> view.choose(BrowserToolbar.MENU, BrowserToolbar.BOOKMARK_ADD));
            stores.bookmarks().all().get(10, TimeUnit.SECONDS);
            onUi(() -> { });
            onUi(() -> panel().type(BookmarkPanel.TAGS, "work,"));
            onUi(() -> {
                // As BrowserPlugin.stop does.
                items.close();
                stores.close();
            });
            stores.closed().get(10, TimeUnit.SECONDS);
        } finally {
            stores.close();
            stores.closed().get(10, TimeUnit.SECONDS);
        }
        Stores again = new Stores(BrowserDatabase.file(new DataFolder(data), cause -> { }), EventQueue::invokeLater);
        try {
            assertThat(again.bookmarks().all().get(10, TimeUnit.SECONDS)).singleElement()
                .satisfies(bookmark -> assertThat(bookmark.tags()).containsExactly("work"));
        } finally {
            again.close();
            again.closed().get(10, TimeUnit.SECONDS);
        }
        onUi(() -> assertThat(host.popoverViews(ID)).as("the panel closed with the items").isEmpty());
    }

    /** Add Bookmark for a page bookmarks never keep, which its disabled item would not report, stores nothing and opens nothing. */
    @Test void aRefusedAddOpensNoPanelAndStoresNothing() throws Exception {
        Stores stores = open("jasper-about:welcome");
        onUi(() -> {
            assertThat(menu().getFirst().enabled()).isFalse();
            view.emit(new UiEvent.Action(BrowserToolbar.BOOKMARK_ADD));
        });
        memory.settle();
        assertThat(bookmarks(stores)).isEmpty();
        onUi(() -> {
            assertThat(host.popoverViews(ID)).isEmpty();
            assertThat(host.failures()).isEmpty();
        });
    }

    @Test void deleteBookmarkAsksThenDeletesItAndSaysSo() throws Exception {
        Stores stores = open(A);
        onUi(() -> view.choose(BrowserToolbar.MENU, BrowserToolbar.BOOKMARK_ADD));
        memory.settle();
        onUi(() -> panel().emit(new UiEvent.Action(BookmarkPanel.DELETE)));
        onUi(() -> host.windowView(ID, "Delete Bookmark").orElseThrow().emit(new UiEvent.Action(BookmarkPanel.CONFIRM_DELETE)));
        memory.settle();
        assertThat(bookmarks(stores)).isEmpty();
        onUi(() -> {
            assertThat(host.windowView(ID, "Delete Bookmark")).isEmpty();
            assertThat(host.notifications().lines()).as("its title only").containsExactly(GROUP + "|INFO|Bookmark Deleted|");
            assertThat(menu().getFirst().label()).isEqualTo("Add Bookmark");
            assertThat(host.failures()).isEmpty();
        });
    }

    @Test void theNextLoadStartAndTheItemsCloseCloseThePanel() throws Exception {
        open(A);
        onUi(() -> view.choose(BrowserToolbar.MENU, BrowserToolbar.BOOKMARK_ADD));
        memory.settle();
        onUi(() -> {
            assertThat(host.popoverViews(ID)).hasSize(1);
            PageTestAccess.loadStarted(item.page(), B, Reached.LINK);
            assertThat(host.popoverViews(ID)).as("a load start").isEmpty();
        });
        memory.settle();
        onUi(() -> {
            PageTestAccess.addressChanged(item.page(), B);
            view.choose(BrowserToolbar.MENU, BrowserToolbar.BOOKMARK_ADD);
        });
        memory.settle();
        onUi(() -> {
            assertThat(host.popoverViews(ID)).hasSize(1);
            item.close();
            assertThat(host.popoverViews(ID)).as("the item's close").isEmpty();
            assertThat(host.failures()).isEmpty();
        });
    }

    /** F35: a page that starts a new load before the bookmark is stored gets no panel, which would edit the wrong page. */
    @Test void aLoadStartBeforeTheBookmarkIsStoredOpensNoPanel() throws Exception {
        Stores stores = open(A);
        Runnable release = memory.hold(stores);
        onUi(() -> {
            view.choose(BrowserToolbar.MENU, BrowserToolbar.BOOKMARK_ADD);
            PageTestAccess.loadStarted(item.page(), B, Reached.LINK);
        });
        release.run();
        memory.settle();
        assertThat(bookmarks(stores)).extracting(Bookmark::url).as("it is stored all the same").containsExactly(A);
        onUi(() -> {
            assertThat(host.popoverViews(ID)).isEmpty();
            assertThat(host.failures()).isEmpty();
        });
    }

    /** While the item shows its error page, the failed address is bookmarked with no title, never the error's. */
    @Test void anErrorPagesBookmarkHasTheFailedAddressAndNoTitle() throws Exception {
        Stores stores = open(A);
        onUi(() -> {
            PageTestAccess.loadFailed(item.page(), B, "ERR_NAME_NOT_RESOLVED");
            PageTestAccess.titleChanged(item.page(), "Server Not Found");
            view.choose(BrowserToolbar.MENU, BrowserToolbar.BOOKMARK_ADD);
        });
        memory.settle();
        assertThat(bookmarks(stores)).singleElement().satisfies(bookmark -> {
            assertThat(bookmark.url()).isEqualTo(B);
            assertThat(bookmark.title()).isEmpty();
        });
    }

    @Test void removeBookmarkSaysSoAndTheMenuOffersAddBookmarkAgain() throws Exception {
        Stores stores = open(A);
        stores.bookmarks().add(A, "A page", 1L).get(10, TimeUnit.SECONDS);
        onUi(() -> stores.bookmarkIndex().load()); // as the item's first showing loads it
        memory.settle();
        onUi(() -> {
            assertThat(menu().getFirst().label()).isEqualTo("Remove Bookmark");
            view.choose(BrowserToolbar.MENU, BrowserToolbar.BOOKMARK_REMOVE);
        });
        memory.settle();
        assertThat(bookmarks(stores)).isEmpty();
        onUi(() -> {
            assertThat(host.notifications().lines()).as("its title only").containsExactly(GROUP + "|INFO|Bookmark Removed|");
            assertThat(menu().getFirst().label()).isEqualTo("Add Bookmark");
            assertThat(host.popoverViews(ID)).isEmpty();
            assertThat(host.failures()).isEmpty();
        });
    }

    /** A stand-in Browser's item at {@code address}, with stores in memory, which the test's fields hold; its stores. */
    private Stores open(String address) throws Exception {
        return open(address, memory.make());
    }

    /** As {@link #open(String)}, with {@code stores}. */
    private Stores open(String address, Stores stores) throws Exception {
        onUi(() -> {
            host = new FakePluginHost();
            var settings = new AtomicReference<BrowserSettings.Declared>();
            host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(), plugin -> {
                context = plugin;
                settings.set(BrowserSettings.declare(plugin.settings()));
            });
            // Title waits never end, and the engine is never asked: neither is what these tests are about.
            items = new BrowserItems(context, settings.get()::read, new DataStores(stores, () -> false), Optional.empty(), page -> { }, (delay, task) -> () -> { },
                request -> new CompletableFuture<>());
            item = items.open(URI.create(address), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            String tabTitle = Optional.ofNullable(URI.create(address).getHost()).orElse(TabTitle.UNTITLED);
            view = host.contentView(ID, tabTitle).orElseThrow();
            return null;
        });
        return stores;
    }

    /** The one panel shown. UI thread. */
    private FakeKitView panel() {
        assertThat(host.popoverViews(ID)).hasSize(1);
        return host.popoverViews(ID).getFirst();
    }

    /** The menu button's items. UI thread. */
    private List<MenuItem> menu() {
        return ((Nodes.MenuButton) Views.node(view.spec().root(), BrowserToolbar.MENU)).items();
    }

    private static List<String> rowLabels(Nodes.Form form) {
        return form.sections().stream().filter(Nodes.Card.class::isInstance).flatMap(card -> ((Nodes.Card) card).children().stream())
            .filter(Nodes.FormRow.class::isInstance).map(row -> ((Nodes.FormRow) row).label()).toList();
    }

    /** Counts the bookmark store's writes from now on: each add, tags, removal or deletion that changed something. */
    private static AtomicInteger writes(Stores stores) {
        var writes = new AtomicInteger();
        stores.bookmarks().onChanged(writes::incrementAndGet);
        return writes;
    }

    private static List<Bookmark> bookmarks(Stores stores) throws Exception {
        return stores.bookmarks().all().get(10, TimeUnit.SECONDS);
    }

    private static <T> T onUi(Callable<T> task) throws Exception {
        var result = new AtomicReference<T>();
        var failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(task.call());
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        if (failure.get() instanceof Error error) throw error;
        if (failure.get() instanceof Exception exception) throw exception;
        return result.get();
    }

    private static void onUi(Runnable task) throws Exception {
        onUi(() -> {
            task.run();
            return null;
        });
    }
}
