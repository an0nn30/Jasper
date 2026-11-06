package dev.jasper.browser.item;

import dev.jasper.browser.page.PageTestAccess;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.browser.store.HistoryStore;
import dev.jasper.browser.store.HistoryStore.HistoryEntry;
import dev.jasper.browser.store.HistoryStore.Visit;
import dev.jasper.browser.store.MemoryStores;
import dev.jasper.browser.store.Reached;
import dev.jasper.browser.store.Stores;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.Nodes.Suggestion;
import dev.jasper.sdk.ui.TabPlacement;
import dev.jasper.sdk.ui.UiEvent;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * Spec section 10's history as browser items record it, in stores in memory: each main-frame load start of a page Jasper
 * keeps is an entry, which takes the page's titles, a second's worth at a time, and the item's last title lands however
 * it closes; the bookmarks and visits suggest addresses as the field is typed. Pages are made by a stand-in, as a page
 * cannot be made without Chromium; the test plays Chromium's load events. The answers of the stores reach the UI thread
 * as the plugin's do, and the test waits for them ({@link MemoryStores#settle}).
 */
class HistoryRecordingTest {
    private static final String ID = "dev.jasper.browser", A = "https://a.example/", B = "https://b.example/";
    private final MemoryStores memory = new MemoryStores();
    private final Later later = new Later();
    private FakePluginHost host;

    @AfterEach void close() throws Exception {
        if (host != null) onUi(() -> {
            host.close();
            return null;
        });
        memory.close();
    }

    @Test void aLinkLoadIsOneEntryAndATitleChangeRetitlesIt() throws Exception {
        Stores stores = memory.make();
        BrowserItem item = open(items(stores), A);
        onUi(() -> PageTestAccess.loadStarted(item.page(), A, Reached.LINK));
        memory.settle();
        assertThat(entries(stores)).singleElement().satisfies(entry -> {
            assertThat(entry.url()).isEqualTo(A);
            assertThat(entry.title()).isEmpty();
            assertThat(entry.reached()).isEqualTo(Reached.LINK);
        });
        onUi(() -> PageTestAccess.titleChanged(item.page(), "A page"));
        assertThat(onUi(later::waiting)).as("the title waits a second").containsExactly(Duration.ofSeconds(1));
        onUi(later::end);
        memory.settle();
        assertThat(entries(stores)).extracting(HistoryEntry::title).containsExactly("A page");
        assertThat(visits(stores)).extracting(Visit::title).containsExactly("A page");
        assertThat(onUi(() -> host.failures())).isEmpty();
    }

    @Test void jaspersOwnPagesRecordNothingAndAskNothingOfTheDatabase() throws Exception {
        Stores stores = memory.make();
        BrowserItem item = open(items(stores), A);
        onUi(() -> {
            PageTestAccess.loadStarted(item.page(), "jasper-about:welcome", Reached.TYPED);
            PageTestAccess.titleChanged(item.page(), "Welcome");
            return null;
        });
        assertThat(onUi(later::waiting)).as("no title to wait for").isEmpty();
        memory.settle();
        assertThat(MemoryStores.started(stores)).isFalse();
    }

    @Test void twoItemsRecordTheirVisitsApart() throws Exception {
        Stores stores = memory.make();
        BrowserItems items = items(stores);
        BrowserItem first = open(items, A), second = open(items, B);
        onUi(() -> {
            PageTestAccess.loadStarted(first.page(), A, Reached.TYPED);
            PageTestAccess.loadStarted(second.page(), B, Reached.LINK);
            return null;
        });
        memory.settle();
        onUi(() -> {
            PageTestAccess.titleChanged(second.page(), "Bee");
            PageTestAccess.titleChanged(first.page(), "Ay");
            return null;
        });
        onUi(later::end);
        memory.settle();
        assertThat(entries(stores)).extracting(HistoryEntry::url, HistoryEntry::title, HistoryEntry::reached).containsExactlyInAnyOrder(
            tuple(A, "Ay", Reached.TYPED), tuple(B, "Bee", Reached.LINK));
    }

    /** F18: a title the page gives before its load's entry exists lands on that entry once it does. */
    @Test void aTitleBeforeTheRecordCompletesLandsOnceItDoes() throws Exception {
        Stores stores = memory.make();
        BrowserItem item = open(items(stores), A);
        onUi(() -> {
            PageTestAccess.loadStarted(item.page(), A, Reached.LINK);
            PageTestAccess.titleChanged(item.page(), "Early");
            later.end();
            return null;
        });
        memory.settle();
        assertThat(entries(stores)).extracting(HistoryEntry::title).containsExactly("Early");
    }

    /** F18: titles within the second coalesce, and an item that closes within it gives its visit the last. */
    @Test void titlesWithinASecondCoalesceAndAnItemThatClosesWithinItGivesTheLast() throws Exception {
        Stores stores = memory.make();
        BrowserItems items = items(stores);
        BrowserItem item = open(items, A);
        onUi(() -> PageTestAccess.loadStarted(item.page(), A, Reached.LINK));
        memory.settle();
        onUi(() -> {
            PageTestAccess.titleChanged(item.page(), "(1) Inbox");
            PageTestAccess.titleChanged(item.page(), "(2) Inbox");
            assertThat(later.waiting()).as("one wait for both").hasSize(1);
            PageTestAccess.titleChanged(item.page(), "(3) Inbox");
            item.close();
            assertThat(later.waiting()).as("the close ends the wait").isEmpty();
            return null;
        });
        memory.settle();
        assertThat(entries(stores)).extracting(HistoryEntry::title).containsExactly("(3) Inbox");
        assertThat(visits(stores)).extracting(Visit::title).containsExactly("(3) Inbox");
        BrowserItem quick = open(items, B);
        onUi(() -> {
            PageTestAccess.loadStarted(quick.page(), B, Reached.LINK);
            PageTestAccess.titleChanged(quick.page(), "Quick");
            quick.close();
            return null;
        });
        memory.settle();
        assertThat(entries(stores)).as("closed before its entry existed").extracting(HistoryEntry::url, HistoryEntry::title)
            .contains(tuple(B, "Quick"));
    }

    @Test void aLaterTitleWaitsItsOwnSecond() throws Exception {
        Stores stores = memory.make();
        BrowserItem item = open(items(stores), A);
        onUi(() -> PageTestAccess.loadStarted(item.page(), A, Reached.LINK));
        memory.settle();
        onUi(() -> {
            PageTestAccess.titleChanged(item.page(), "One");
            later.end();
            PageTestAccess.titleChanged(item.page(), "One");
            assertThat(later.waiting()).as("the same title again").isEmpty();
            PageTestAccess.titleChanged(item.page(), "Two");
            assertThat(later.waiting()).hasSize(1);
            return null;
        });
        memory.settle();
        assertThat(entries(stores)).extracting(HistoryEntry::title).containsExactly("One");
        onUi(later::end);
        memory.settle();
        assertThat(entries(stores)).extracting(HistoryEntry::title).containsExactly("Two");
    }

    @Test void aNewLoadGivesTheVisitBeforeItsTitleFirst() throws Exception {
        Stores stores = memory.make();
        BrowserItem item = open(items(stores), A);
        onUi(() -> {
            PageTestAccess.loadStarted(item.page(), A, Reached.TYPED);
            PageTestAccess.titleChanged(item.page(), "First");
            PageTestAccess.loadStarted(item.page(), B, Reached.LINK);
            assertThat(later.waiting()).as("the first visit's title went with the second load").isEmpty();
            return null;
        });
        memory.settle();
        assertThat(entries(stores)).extracting(HistoryEntry::url, HistoryEntry::title)
            .containsExactly(tuple(B, ""), tuple(A, "First"));
    }

    @Test void thePluginsStopGivesEachVisitItsLastTitle() throws Exception {
        Stores stores = memory.make();
        BrowserItems items = items(stores);
        BrowserItem item = open(items, A);
        onUi(() -> PageTestAccess.loadStarted(item.page(), A, Reached.LINK));
        memory.settle();
        onUi(() -> {
            PageTestAccess.titleChanged(item.page(), "Last");
            items.close();
            assertThat(later.waiting()).isEmpty();
            return null;
        });
        memory.settle();
        assertThat(entries(stores)).extracting(HistoryEntry::title).containsExactly("Last");
    }

    /**
     * F19: a load whose document fails before it ends, as Chromium's error page for it does, leaves no entry, and its
     * row as it was before; whether the failure comes before or after its entry exists.
     */
    @Test void aLoadThatFailsBeforeItsDocumentEndsLeavesHistoryAsItWas() throws Exception {
        Stores stores = memory.make();
        BrowserItem item = open(items(stores), A);
        for (String title : List.of("One", "Two")) {
            onUi(() -> {
                PageTestAccess.loadStarted(item.page(), A, Reached.LINK);
                PageTestAccess.titleChanged(item.page(), title);
                later.end();
                PageTestAccess.loadEnded(item.page());
                return null;
            });
            memory.settle();
        }
        List<HistoryEntry> entries = entries(stores);
        List<Visit> visits = visits(stores);
        assertThat(visits).singleElement().satisfies(visit -> assertThat(visit.count()).isEqualTo(2));
        onUi(() -> PageTestAccess.loadStarted(item.page(), A, Reached.RELOAD));
        memory.settle();
        assertThat(entries(stores)).hasSize(3);
        onUi(() -> PageTestAccess.loadFailed(item.page(), A, "ERR_CONNECTION_REFUSED"));
        memory.settle();
        assertThat(entries(stores)).isEqualTo(entries);
        assertThat(visits(stores)).isEqualTo(visits);
        onUi(() -> {
            PageTestAccess.loadStarted(item.page(), B, Reached.TYPED);
            PageTestAccess.titleChanged(item.page(), "Gone");
            PageTestAccess.loadFailed(item.page(), B, "ERR_NAME_NOT_RESOLVED");
            later.end();
            return null;
        });
        memory.settle();
        assertThat(entries(stores)).as("failed before its entry existed").isEqualTo(entries);
        assertThat(visits(stores)).isEqualTo(visits);
    }

    /** F18 and H14: a title waiting for its entry when the plugin stops lands before the stores close. */
    @Test void aTitleWaitingForItsEntryAtThePluginsStopLandsBeforeTheStoresClose() throws Exception {
        Stores stores = memory.make();
        BrowserItems items = items(stores);
        BrowserItem item = open(items, A);
        Runnable release = memory.hold(stores);
        onUi(() -> {
            PageTestAccess.loadStarted(item.page(), A, Reached.LINK);
            PageTestAccess.titleChanged(item.page(), "Last");
            items.close();
            return null;
        });
        CompletableFuture<HistoryStore.HistoryPage> listed = stores.history().list("", Optional.empty(), 10);
        stores.close();
        release.run();
        assertThat(listed.get(10, TimeUnit.SECONDS).entries()).extracting(HistoryEntry::url, HistoryEntry::title)
            .containsExactly(tuple(A, "Last"));
    }

    /**
     * Review item 1: a document that stops loading, as Stop, Escape or {@code window.stop()} make it, fails with
     * {@code ERR_ABORTED} before its load ends, shows no error page and keeps its entry.
     */
    @Test void aDocumentThatStopsLoadingKeepsItsEntry() throws Exception {
        Stores stores = memory.make();
        BrowserItem item = open(items(stores), A);
        onUi(() -> PageTestAccess.loadStarted(item.page(), A, Reached.LINK));
        memory.settle();
        onUi(() -> PageTestAccess.loadFailed(item.page(), A, "ERR_ABORTED"));
        memory.settle();
        onUi(() -> {
            PageTestAccess.loadStarted(item.page(), B, Reached.LINK);
            PageTestAccess.loadFailed(item.page(), B, "ERR_ABORTED");
            return null;
        });
        memory.settle();
        assertThat(entries(stores)).extracting(HistoryEntry::url).containsExactly(B, A);
    }

    /**
     * A failure that is not the document's own leaves its entry, though it shows an error page: one after the document
     * loaded, as a reload offline gives before it commits, and one of another address, as a link that fails before it
     * commits gives while the document still loads.
     */
    @Test void aFailureAfterTheDocumentLoadedOrOfAnotherAddressKeepsTheEntry() throws Exception {
        Stores stores = memory.make();
        BrowserItem item = open(items(stores), A);
        onUi(() -> {
            PageTestAccess.loadStarted(item.page(), A, Reached.LINK);
            PageTestAccess.loadEnded(item.page());
            return null;
        });
        memory.settle();
        onUi(() -> PageTestAccess.loadFailed(item.page(), A, "ERR_INTERNET_DISCONNECTED"));
        onUi(() -> PageTestAccess.loadStarted(item.page(), B, Reached.LINK));
        memory.settle();
        onUi(() -> PageTestAccess.loadFailed(item.page(), "https://c.example/", "ERR_NAME_NOT_RESOLVED"));
        memory.settle();
        assertThat(entries(stores)).extracting(HistoryEntry::url).containsExactly(B, A);
    }

    /** Spec section 19: an item that is not shown asks nothing of the database; once it first shows, its indexes load. */
    @Test void theIndexesLoadWhenTheItemFirstShows() throws Exception {
        Stores stores = memory.make();
        BrowserItem item = open(items(stores), A);
        memory.settle();
        assertThat(MemoryStores.started(stores)).as("an item that is not shown").isFalse();
        onUi(() -> {
            var parent = new JPanel();
            parent.addNotify();
            try {
                parent.add(item.pageView());
                assertThat(MemoryStores.started(stores)).as("shown").isTrue();
            } finally {
                parent.removeNotify();
            }
            return null;
        });
        memory.settle();
    }

    @Test void theIndexesLoadWhenTheFieldIsFirstEdited() throws Exception {
        Stores stores = memory.make();
        open(items(stores), A);
        FakeKitView view = onUi(() -> host.contentView(ID, "a.example").orElseThrow());
        memory.settle();
        assertThat(MemoryStores.started(stores)).isFalse();
        onUi(() -> {
            view.type(BrowserToolbar.ADDRESS, "e");
            return null;
        });
        assertThat(MemoryStores.started(stores)).as("its field edited").isTrue();
        memory.settle();
    }

    @Test void bookmarksAndVisitsSuggestAsTheFieldIsTypedAndAChosenOneLoads() throws Exception {
        Stores stores = memory.make();
        stores.bookmarks().add("https://github.com/x", "Repository", 1).get(10, TimeUnit.SECONDS);
        stores.history().record("https://gitlab.example/", Reached.LINK, 2).get(10, TimeUnit.SECONDS);
        BrowserItem item = open(items(stores), A);
        FakeKitView view = onUi(() -> host.contentView(ID, "a.example").orElseThrow());
        onUi(() -> {
            view.type(BrowserToolbar.ADDRESS, "git");
            return null;
        });
        memory.settle();
        List<Suggestion> rows = onUi(() -> Views.field(view, BrowserToolbar.ADDRESS).suggestions());
        assertThat(rows).as("the indexes' rows as they load, after the typed text's").extracting(Suggestion::id, Suggestion::detail)
            .endsWith(tuple("navigate:https://github.com/x", "Bookmark"),
                tuple("navigate:https://gitlab.example/", "Visited 1 time"));
        stores.bookmarks().add("https://gitea.example/", "Tea", 3).get(10, TimeUnit.SECONDS);
        memory.settle();
        assertThat(onUi(() -> Views.field(view, BrowserToolbar.ADDRESS).suggestions())).as("a bookmark added while typing")
            .extracting(Suggestion::id).contains("navigate:https://gitea.example/");
        onUi(() -> {
            view.emit(new UiEvent.SuggestionChosen(BrowserToolbar.ADDRESS, "navigate:https://github.com/x", false));
            return null;
        });
        assertThat(onUi(() -> item.page().address())).isEqualTo("https://github.com/x");
    }

    @Test void theVisitIndexHearsEachVisitAndItsTitle() throws Exception {
        Stores stores = memory.make();
        BrowserItem item = open(items(stores), A);
        FakeKitView view = onUi(() -> host.contentView(ID, "a.example").orElseThrow());
        onUi(() -> {
            view.type(BrowserToolbar.ADDRESS, "a");
            return null;
        });
        memory.settle();
        onUi(() -> PageTestAccess.loadStarted(item.page(), A, Reached.LINK));
        memory.settle();
        assertThat(onUi(() -> stores.visitIndex().all())).extracting(Visit::url, Visit::title)
            .containsExactly(tuple(A, ""));
        onUi(() -> {
            PageTestAccess.titleChanged(item.page(), "Titled");
            later.end();
            return null;
        });
        memory.settle();
        assertThat(onUi(() -> stores.visitIndex().all())).extracting(Visit::url, Visit::title)
            .containsExactly(tuple(A, "Titled"));
    }

    /**
     * The items of a stand-in plugin, the test's one, on the test's fake host, with {@code stores}; their delays wait for
     * {@link #later} and their engines suggest nothing.
     */
    private BrowserItems items(Stores stores) throws Exception {
        return onUi(() -> {
            host = new FakePluginHost();
            var context = new AtomicReference<PluginContext>();
            var settings = new AtomicReference<BrowserSettings.Declared>();
            host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(), plugin -> {
                context.set(plugin);
                settings.set(BrowserSettings.declare(plugin.settings()));
            });
            return new BrowserItems(context.get(), settings.get()::read, stores, page -> { }, later, request -> new CompletableFuture<>());
        });
    }

    private BrowserItem open(BrowserItems items, String address) throws Exception {
        return onUi(() -> items.open(URI.create(address), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty()));
    }

    private static List<HistoryEntry> entries(Stores stores) throws Exception {
        return stores.history().list("", Optional.empty(), 100).get(10, TimeUnit.SECONDS).entries();
    }

    private static List<Visit> visits(Stores stores) throws Exception {
        return stores.history().visits(100).get(10, TimeUnit.SECONDS);
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

    /** The UI thread's delays, which wait until the test ends them. UI thread. */
    private static final class Later implements BiFunction<Duration, Runnable, Runnable> {
        private final List<Delayed> waiting = new ArrayList<>();

        private record Delayed(Duration delay, Runnable task) { }

        @Override public Runnable apply(Duration delay, Runnable task) {
            var delayed = new Delayed(delay, task);
            waiting.add(delayed);
            return () -> waiting.remove(delayed);
        }

        List<Duration> waiting() {
            return waiting.stream().map(Delayed::delay).toList();
        }

        /** Each delay waiting ends, and its task runs. */
        void end() {
            var due = List.copyOf(waiting);
            waiting.clear();
            due.forEach(delayed -> delayed.task().run());
        }
    }
}
