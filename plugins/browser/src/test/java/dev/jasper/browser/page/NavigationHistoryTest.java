package dev.jasper.browser.page;

import dev.jasper.browser.page.NavigationHistory.Entry;
import dev.jasper.browser.page.NavigationHistory.History;
import dev.jasper.sdk.ui.MenuItem;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The page's history behind Back's and Forward's hold menus (spec section 6; plan 4b Task 3): DevTools'
 * {@code Page.getNavigationHistory} answer read as untrusted input, within bounds; the menus, nearest entry first, each
 * entry its title or else its address without user information; the menu items' actions read back to their entries; and
 * a page that keeps the history last read and goes to an entry chosen from it. Nothing here needs Chromium.
 */
class NavigationHistoryTest {
    private static final URI EXAMPLE = URI.create("https://example.com");
    // What DevTools answers, members it adds that Jasper does not use included.
    private static final String ANSWER = """
        {"currentIndex":2,"entries":[
         {"id":3,"url":"https://example.com/","userTypedURL":"https://example.com/","title":"Example Domain","transitionType":"typed"},
         {"id":5,"url":"https://ada:secret@example.org/docs","userTypedURL":"https://example.org/docs","title":"","transitionType":"link"},
         {"id":8,"url":"https://example.net/a?b=1#c","userTypedURL":"https://example.net/a","title":"Caf\\u00e9 \\"menu\\"","transitionType":"link"},
         {"id":9,"url":"https://example.edu/","userTypedURL":"https://example.edu/","title":"Example Edu","transitionType":"auto_bookmark"}]}""";

    @Test void readsDevToolsAnswerWithoutUserInformation() {
        History history = NavigationHistory.parse(ANSWER);
        assertThat(history.current()).isEqualTo(2);
        assertThat(history.entries()).extracting(Entry::id).containsExactly(3, 5, 8, 9);
        assertThat(history.entries()).extracting(Entry::url)
            .containsExactly("https://example.com/", "https://example.org/docs", "https://example.net/a?b=1#c", "https://example.edu/");
        assertThat(history.entries()).extracting(Entry::title).containsExactly("Example Domain", "", "Caf\u00e9 \"menu\"", "Example Edu");
    }

    @Test void skipsMembersOfAnyKindItDoesNotUse() {
        String answer = """
            {"added":{"nested":[1,-0,2.5e3,-0.1E-2,123456789012345678901234,true,false,null,"x\\/y",{}]},"currentIndex":0,
             "entries" : [ { "id" : 1 , "url" : "about:blank" , "title" : "" , "extra" : [ [ ] , { "a" : null } ] } ] }""";
        assertThat(NavigationHistory.parse(answer)).isEqualTo(new History(0, List.of(new Entry(1, "about:blank", ""))));
    }

    /** Anything that is not such an answer gives no history, and never an exception. {@code StrictJsonTest} has the grammar. */
    @Test void anythingElseGivesNoHistory() {
        String entry = "{\"id\":1,\"url\":\"https://example.com/\",\"title\":\"\"}";
        for (String text : new String[] {null, "", "   ", "[]", "{}", "null", "not json", "{\"currentIndex\":0}",
            "{\"entries\":[" + entry + "]}",
            "{\"currentIndex\":0,\"entries\":[]}",
            "{\"currentIndex\":0,\"entries\":[" + entry + "]} x",
            "{\"currentIndex\":0,\"currentIndex\":0,\"entries\":[" + entry + "]}",
            "{\"currentIndex\":1,\"entries\":[" + entry + "]}",
            "{\"currentIndex\":-1,\"entries\":[" + entry + "]}",
            "{\"currentIndex\":0.0,\"entries\":[" + entry + "]}",
            "{\"currentIndex\":\"0\",\"entries\":[" + entry + "]}",
            "{\"currentIndex\":0,\"entries\":{}}",
            "{\"currentIndex\":0,\"entries\":[" + entry + "," + entry + "]}",
            "{\"currentIndex\":0,\"entries\":[{\"url\":\"https://example.com/\",\"title\":\"\"}]}",
            "{\"currentIndex\":0,\"entries\":[{\"id\":\"1\",\"url\":\"https://example.com/\",\"title\":\"\"}]}",
            "{\"currentIndex\":0,\"entries\":[{\"id\":1.5,\"url\":\"https://example.com/\",\"title\":\"\"}]}",
            "{\"currentIndex\":0,\"entries\":[{\"id\":2147483648,\"url\":\"https://example.com/\",\"title\":\"\"}]}",
            "{\"currentIndex\":0,\"entries\":[{\"id\":1,\"url\":null,\"title\":\"\"}]}",
            "{\"currentIndex\":0,\"entries\":[{\"id\":1,\"url\":\"https://example.com/\"}]}",
            "{\"currentIndex\":0,\"entries\":[{\"id\":1,\"url\":\"https://example.com/\",\"title\":\"open",
            "{\"currentIndex\":0,\"entries\":[" + entry + ",]}"})
            assertThat(NavigationHistory.parse(text)).as(String.valueOf(text)).isEqualTo(History.EMPTY);
    }

    @Test void anAnswerPastItsBoundsGivesNoHistory() {
        assertThat(NavigationHistory.parse(ANSWER + " ".repeat(NavigationHistory.MAX_TEXT - ANSWER.length())).entries()).hasSize(4);
        assertThat(NavigationHistory.parse(ANSWER + " ".repeat(NavigationHistory.MAX_TEXT - ANSWER.length() + 1)))
            .as("too long").isEqualTo(History.EMPTY);
        assertThat(NavigationHistory.parse(entries(NavigationHistory.MAX_ENTRIES)).entries()).hasSize(NavigationHistory.MAX_ENTRIES);
        assertThat(NavigationHistory.parse(entries(NavigationHistory.MAX_ENTRIES + 1))).as("too many entries").isEqualTo(History.EMPTY);
        // The answer is one level, its entries a second and each entry a third: an added member may nest up to the limit.
        String deepest = "[".repeat(NavigationHistory.MAX_DEPTH - 1) + "]".repeat(NavigationHistory.MAX_DEPTH - 1);
        assertThat(NavigationHistory.parse(ANSWER.replaceFirst("\\{", "{\"x\":" + deepest + ",")).entries()).hasSize(4);
        assertThat(NavigationHistory.parse(ANSWER.replaceFirst("\\{", "{\"x\":[" + deepest + "],")))
            .as("nested too deep").isEqualTo(History.EMPTY);
        assertThat(NavigationHistory.parse(ANSWER.replaceFirst("\\{", "{\"x\":" + "[".repeat(100_000) + ",")))
            .as("no stack overflow").isEqualTo(History.EMPTY);
    }

    /** An entry keeps at most {@link NavigationHistory#KEPT} characters of its title and of its address without user information. */
    @Test void anEntryKeepsALimitedTitleAndAddress() {
        String host = "https://" + "h".repeat(NavigationHistory.KEPT) + ".example/";
        var entry = new Entry(1, "https://" + "u".repeat(3_000) + ":secret@" + host.substring("https://".length()), "t".repeat(5_000));
        assertThat(entry.url()).isEqualTo(host.substring(0, NavigationHistory.KEPT));
        assertThat(entry.title()).isEqualTo("t".repeat(NavigationHistory.KEPT));
        String pair = "\uD83D\uDE00";
        assertThat(new Entry(1, "", "t".repeat(NavigationHistory.KEPT - 1) + pair).title())
            .as("a character is never split").isEqualTo("t".repeat(NavigationHistory.KEPT - 1));
    }

    @Test void backListsTheEntriesBeforeTheCurrentNearestFirstAndForwardThoseAfter() {
        History history = new History(2, List.of(entry(1, "One"), entry(2, "Two"), entry(3, "Three"), entry(4, "Four"), entry(5, "Five")));
        assertThat(NavigationHistory.backMenu(history)).extracting(MenuItem::label).containsExactly("Two", "One");
        assertThat(NavigationHistory.forwardMenu(history)).extracting(MenuItem::label).containsExactly("Four", "Five");
        for (MenuItem item : NavigationHistory.backMenu(history)) {
            assertThat(item.enabled()).isTrue();
            assertThat(item.separator()).isFalse();
            assertThat(item.icon()).isEmpty();
            assertThat(item.shortcut()).isEmpty();
            assertThat(item.children()).isEmpty();
            assertThat(item.id()).isEqualTo(item.action());
        }
        assertThat(NavigationHistory.backMenu(history)).extracting(item -> NavigationHistory.entryId(item.action()))
            .containsExactly(OptionalInt.of(2), OptionalInt.of(1));
        assertThat(NavigationHistory.forwardMenu(history)).extracting(item -> NavigationHistory.entryId(item.action()))
            .containsExactly(OptionalInt.of(4), OptionalInt.of(5));
    }

    /** P23: each menu lists the twelve nearest entries, as Chrome's do; an entry with nothing to show leaves its place. */
    @Test void eachMenuListsTheTwelveNearestEntries() {
        var entries = new ArrayList<Entry>();
        for (int id = 1; id <= 30; id++) entries.add(entry(id, "Entry " + id));
        History history = new History(15, entries);
        assertThat(ids(NavigationHistory.backMenu(history))).isEqualTo(range(15, 4));
        assertThat(ids(NavigationHistory.forwardMenu(history))).isEqualTo(range(17, 28));
        entries.set(14, new Entry(15, "", ""));
        entries.set(16, new Entry(17, "", ""));
        History gaps = new History(15, entries);
        assertThat(ids(NavigationHistory.backMenu(gaps))).as("twelve items, not twelve entries").isEqualTo(range(14, 3));
        assertThat(ids(NavigationHistory.forwardMenu(gaps))).isEqualTo(range(18, 29));
    }

    @Test void atEitherEndThatMenuIsEmpty() {
        List<Entry> entries = List.of(entry(1, "One"), entry(2, "Two"));
        assertThat(NavigationHistory.forwardMenu(new History(1, entries))).as("an empty forward list").isEmpty();
        assertThat(NavigationHistory.backMenu(new History(1, entries))).extracting(MenuItem::label).containsExactly("One");
        assertThat(NavigationHistory.backMenu(new History(0, entries))).isEmpty();
        assertThat(NavigationHistory.forwardMenu(new History(0, entries))).extracting(MenuItem::label).containsExactly("Two");
        assertThat(NavigationHistory.backMenu(History.EMPTY)).isEmpty();
        assertThat(NavigationHistory.forwardMenu(History.EMPTY)).isEmpty();
    }

    /** Spec section 6 and R46: an entry is its title, or its address without a user name or password when it has none. */
    @Test void anEntryWithoutATitleShowsItsAddressWithoutUserInformation() {
        History history = new History(0, List.of(entry(1, "Here"),
            new Entry(2, "https://example.org/", "   "),
            new Entry(3, "https://ada:secret@example.org/{x}|^", ""),
            new Entry(4, "http://ada@example.org:8080?q=a@b", ""),
            new Entry(5, "data:text/plain,a//b@c", ""),
            new Entry(6, "mailto:ada@example.com", ""),
            new Entry(7, "", ""),
            new Entry(8, "https://example.org/titled", " A\tB\n\n C\u0007 ")));
        assertThat(NavigationHistory.forwardMenu(history)).extracting(MenuItem::label).containsExactly("https://example.org/",
            "https://example.org/{x}|^", "http://example.org:8080?q=a@b", "data:text/plain,a//b@c", "mailto:ada@example.com", "A B C");
        assertThat(NavigationHistory.forwardMenu(history)).as("an entry with nothing to show has no item")
            .noneMatch(item -> NavigationHistory.entryId(item.action()).equals(OptionalInt.of(7)));
    }

    @Test void aLongLabelIsCutWithAnEllipsis() {
        String fits = "t".repeat(NavigationHistory.LABEL);
        assertThat(label(fits)).isEqualTo(fits);
        assertThat(label(fits + "t")).isEqualTo("t".repeat(NavigationHistory.LABEL - 1) + "\u2026");
        assertThat(label("t".repeat(NavigationHistory.LABEL - 2) + "\uD83D\uDE00" + "tail"))
            .as("a character is never split").isEqualTo("t".repeat(NavigationHistory.LABEL - 2) + "\u2026");
        var untitled = new History(1, List.of(new Entry(1, "https://" + "e".repeat(200) + ".example/", ""), entry(2, "Here")));
        assertThat(NavigationHistory.backMenu(untitled).getFirst().label()).as("an address too")
            .isEqualTo("https://" + "e".repeat(NavigationHistory.LABEL - 1 - "https://".length()) + "\u2026");
    }

    @Test void onlyAHistoryMenuItemsActionReadsBackToItsEntry() {
        assertThat(NavigationHistory.entryId(NavigationHistory.backMenu(new History(1, List.of(entry(42, "Answer"), entry(43, "Here"))))
            .getFirst().action())).hasValue(42);
        String prefix = NavigationHistory.ENTRY_ACTION;
        assertThat(NavigationHistory.entryId(prefix + "-3")).hasValue(-3);
        for (String action : new String[] {"browser.back", "browser.forward", "browser.settings", prefix, prefix + "x", prefix + "+4",
            prefix + "04", prefix + " 4", prefix + "4 ", prefix + "2147483648", "x" + prefix + "4"})
            assertThat(NavigationHistory.entryId(action)).as(action).isEmpty();
    }

    @Test void aHistoryJumpsOnlyToAnEntryOtherThanTheCurrent() {
        History history = new History(1, List.of(entry(1, "One"), entry(2, "Two"), entry(3, "Three")));
        assertThat(history.jumpsTo(1)).isTrue();
        assertThat(history.jumpsTo(3)).isTrue();
        assertThat(history.jumpsTo(2)).as("where the page is").isFalse();
        assertThat(history.jumpsTo(4)).as("not in the history").isFalse();
        assertThat(History.EMPTY.jumpsTo(1)).isFalse();
    }

    @Test void aHistoryHasItsCurrentEntryAndUniqueIds() {
        assertThatThrownBy(() -> new History(0, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new History(1, List.of(entry(1, "One")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new History(-1, List.of(entry(1, "One")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new History(0, List.of(entry(1, "One"), entry(1, "Again")))).isInstanceOf(IllegalArgumentException.class);
        assertThat(History.EMPTY).isEqualTo(new History(-1, List.of()));
    }

    /** A page keeps the history last read, and tells its item only when that changes; a failed read leaves none. */
    @Test void aPageKeepsTheHistoryLastReadAndTellsOnlyAChange() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var changes = new AtomicInteger();
            var page = new Page(EXAMPLE, changes::incrementAndGet, PageTestAccess.noFavicons());
            assertThat(page.history()).isEqualTo(History.EMPTY);
            History history = new History(1, List.of(entry(1, "One"), entry(2, "Two")));
            page.historyRead(history);
            assertThat(page.history()).isEqualTo(history);
            assertThat(changes).hasValue(1);
            page.historyRead(new History(1, List.of(entry(1, "One"), entry(2, "Two"))));
            assertThat(changes).as("the same history").hasValue(1);
            page.historyRead(History.EMPTY);
            assertThat(page.history()).as("a read that failed").isEqualTo(History.EMPTY);
            assertThat(changes).hasValue(2);
            page.close();
            page.historyRead(history);
            assertThat(page.history()).as("nothing after the close").isEqualTo(History.EMPTY);
            assertThat(changes).hasValue(2);
        });
    }

    /**
     * Choosing an entry sends the page there as Jasper's own navigation, which its listeners hear before it goes (4a's
     * R41 and R42), as Back and Forward do. An entry the history no longer has, and the page's current one, do nothing.
     */
    @Test void aPageGoesToAnEntryOfItsHistoryAsJasperSendsItElsewhere() throws Exception {
        var seam = new Seam();
        onUiThread(() -> {
            Page page = seam.page(() -> { });
            page.onNavigate(() -> seam.told.add("navigate"));
            page.historyRead(new History(1, List.of(entry(1, "One"), entry(2, "Two"), entry(3, "Three"))));
            page.goToHistoryEntry(2);
            page.goToHistoryEntry(9);
            assertThat(seam.told).as("where it is, and a stale entry").isEmpty();
            page.goToHistoryEntry(3);
            assertThat(seam.told).containsExactly("navigate", "go to 3");
            assertThat(seam.pages).containsExactly(page);
            page.close();
            page.goToHistoryEntry(1);
            assertThat(seam.told).as("nothing after the close").hasSize(2);
            return null;
        });
    }

    /** P1: a title change, a committed navigation and a main-frame load end each read the history again. */
    @Test void eachChangeOfThePageReadsItsHistory() throws Exception {
        var seam = new Seam();
        Page page = onUiThread(() -> seam.page(() -> { }));
        History first = new History(0, List.of(entry(1, "One"))), second = new History(1, List.of(entry(1, "One"), entry(2, "Two")));
        onUiThread(() -> PageTestAccess.titleChanged(page, "One"));
        assertThat(seam.reads).hasSize(1);
        seam.reads.getLast().complete(first);
        assertThat(onUiThread(page::history)).isEqualTo(first);
        onUiThread(() -> PageTestAccess.addressChanged(page, "https://example.com/2"));
        assertThat(seam.reads).hasSize(2);
        seam.reads.getLast().complete(second);
        assertThat(onUiThread(page::history)).isEqualTo(second);
        onUiThread(page::loadEnded);
        assertThat(seam.reads).hasSize(3);
        seam.reads.getLast().complete(second);
        assertThat(onUiThread(page::history)).isEqualTo(second);
        assertThat(seam.pages).hasSize(3).containsOnly(page);
        onUiThread(page::close);
    }

    /** One read runs at a time: any number of changes while it runs cause exactly one more read once it has answered. */
    @Test void changesDuringAReadCauseOneMoreRead() throws Exception {
        var seam = new Seam();
        var changes = new AtomicInteger();
        Page page = onUiThread(() -> seam.page(changes::incrementAndGet));
        History first = new History(0, List.of(entry(1, "One"))), second = new History(1, List.of(entry(1, "One"), entry(2, "Two")));
        onUiThread(() -> PageTestAccess.titleChanged(page, "One"));
        onUiThread(() -> {
            PageTestAccess.titleChanged(page, "Two");
            PageTestAccess.addressChanged(page, "https://example.com/2");
            page.loadEnded();
            return null;
        });
        assertThat(seam.reads).as("one at a time").hasSize(1);
        seam.reads.getFirst().complete(first);
        assertThat(onUiThread(page::history)).isEqualTo(first);
        assertThat(seam.reads).as("exactly one more").hasSize(2);
        seam.reads.getLast().complete(second);
        assertThat(onUiThread(page::history)).isEqualTo(second);
        assertThat(seam.reads).as("nothing changed since").hasSize(2);
        onUiThread(page::close);
    }

    /** A read that fails leaves no history, and the next change reads again. */
    @Test void aReadThatFailsLeavesNoHistoryAndTheNextChangeReadsAgain() throws Exception {
        var seam = new Seam();
        Page page = onUiThread(() -> seam.page(() -> { }));
        History first = new History(0, List.of(entry(1, "One")));
        onUiThread(() -> PageTestAccess.titleChanged(page, "One"));
        seam.reads.getLast().complete(first);
        assertThat(onUiThread(page::history)).isEqualTo(first);
        onUiThread(() -> PageTestAccess.titleChanged(page, "Two"));
        seam.reads.getLast().completeExceptionally(new IllegalStateException("DevTools broke"));
        assertThat(onUiThread(page::history)).isEqualTo(History.EMPTY);
        onUiThread(() -> PageTestAccess.titleChanged(page, "Three"));
        assertThat(seam.reads).as("the failed read ended").hasSize(3);
        onUiThread(page::close);
    }

    /** Without its browser ready, a page has no history to read and goes nowhere. */
    @Test void withoutABrowserThereIsNoHistoryAndNoJump() throws Exception {
        assertThat(NavigationHistory.read(null)).isCompletedWithValue(History.EMPTY);
        NavigationHistory.goTo(null, 3);
        Page page = onUiThread(() -> new Page(EXAMPLE, () -> { }, PageTestAccess.noFavicons()));
        onUiThread(() -> PageTestAccess.titleChanged(page, "Example Domain"));
        assertThat(onUiThread(page::history)).isEqualTo(History.EMPTY);
        onUiThread(() -> {
            page.historyRead(new History(1, List.of(entry(1, "One"), entry(2, "Here"))));
            page.goToHistoryEntry(1);
            return null;
        });
        onUiThread(page::close);
    }

    /** The page's history reads and jumps, for a page made with it: each read answers when the test completes it. */
    private static final class Seam {
        final List<CompletableFuture<History>> reads = new CopyOnWriteArrayList<>();
        final List<Page> pages = new CopyOnWriteArrayList<>();
        final List<String> told = new CopyOnWriteArrayList<>();

        Page page(Runnable changed) {
            return new Page(EXAMPLE, changed, PageTestAccess.noFavicons(), page -> {
                pages.add(page);
                var read = new CompletableFuture<History>();
                reads.add(read);
                return read;
            }, (page, id) -> {
                pages.add(page);
                told.add("go to " + id);
            });
        }
    }

    /** Runs {@code task} on the UI thread, after everything already posted there, such as a read's answer. */
    private static <T> T onUiThread(Callable<T> task) throws Exception {
        var result = new AtomicReference<T>();
        var failure = new AtomicReference<Exception>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(task.call());
            } catch (Exception thrown) {
                failure.set(thrown);
            }
        });
        if (failure.get() != null) throw failure.get();
        return result.get();
    }

    private static void onUiThread(Runnable task) throws Exception {
        SwingUtilities.invokeAndWait(task);
    }

    private static List<Integer> ids(List<MenuItem> menu) {
        return menu.stream().map(item -> NavigationHistory.entryId(item.action()).orElseThrow()).toList();
    }

    /** The ids from {@code from} to {@code to}, both included, in that order. */
    private static List<Integer> range(int from, int to) {
        int step = from <= to ? 1 : -1;
        return IntStream.iterate(from, id -> id != to + step, id -> id + step).boxed().toList();
    }

    private static Entry entry(int id, String title) {
        return new Entry(id, "https://example.com/" + id, title);
    }

    /** The label of an entry titled {@code title}. */
    private static String label(String title) {
        return NavigationHistory.backMenu(new History(1, List.of(new Entry(1, "https://example.com/", title), entry(2, "Here"))))
            .getFirst().label();
    }

    /** DevTools' answer with {@code count} entries, the first current. */
    private static String entries(int count) {
        return "{\"currentIndex\":0,\"entries\":[" + String.join(",", IntStream.rangeClosed(1, count)
            .mapToObj(id -> "{\"id\":" + id + ",\"url\":\"https://example.com/" + id + "\",\"title\":\"\"}").toList()) + "]}";
    }
}
