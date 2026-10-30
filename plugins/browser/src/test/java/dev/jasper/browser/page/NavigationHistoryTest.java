package dev.jasper.browser.page;

import dev.jasper.browser.page.NavigationHistory.Entry;
import dev.jasper.browser.page.NavigationHistory.History;
import dev.jasper.sdk.ui.MenuItem;
import java.net.URI;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;
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

    /** Anything that is not such an answer gives no history, and never an exception. */
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
            "{\"currentIndex\":0,\"entries\":[{\"id\":01,\"url\":\"https://example.com/\",\"title\":\"\"}]}",
            "{\"currentIndex\":0,\"entries\":[{\"id\":1,\"url\":null,\"title\":\"\"}]}",
            "{\"currentIndex\":0,\"entries\":[{\"id\":1,\"url\":\"https://example.com/\"}]}",
            "{\"currentIndex\":0,\"entries\":[{\"id\":1,\"url\":\"https://example.com/\",\"title\":\"a\nb\"}]}",
            "{\"currentIndex\":0,\"entries\":[{\"id\":1,\"url\":\"https://example.com/\",\"title\":\"\\x\"}]}",
            "{\"currentIndex\":0,\"entries\":[{\"id\":1,\"url\":\"https://example.com/\",\"title\":\"\\u12\"}]}",
            "{\"currentIndex\":0,\"entries\":[{\"id\":1,\"url\":\"https://example.com/\",\"title\":\"\\u\uFF10\uFF10\uFF14\uFF11\"}]}",
            "{\"currentIndex\":0,\"entries\":[{\"id\":1,\"url\":\"https://example.com/\",\"title\":\"open",
            "{\"currentIndex\":0,\"entries\":[" + entry + "],}",
            "{\"currentIndex\":0,\"entries\":[" + entry + ",]}",
            "{\"currentIndex\":0,\"entries\":[" + entry + "], \"x\":tru}",
            "{\"currentIndex\":0,\"entries\":[" + entry + "], \"x\":-}",
            "{\"currentIndex\":0,\"entries\":[" + entry + "], \"x\":1.}",
            "{\"currentIndex\":0,\"entries\":[" + entry + "], \"x\":1e}"})
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
        SwingUtilities.invokeAndWait(() -> {
            var page = new Page(EXAMPLE, () -> { }, PageTestAccess.noFavicons());
            var navigations = new AtomicInteger();
            page.onNavigate(navigations::incrementAndGet);
            page.historyRead(new History(1, List.of(entry(1, "One"), entry(2, "Two"), entry(3, "Three"))));
            page.goToHistoryEntry(1);
            assertThat(navigations).as("no browser yet").hasValue(0);
            PageTestAccess.readyWithoutBrowser(page);
            page.goToHistoryEntry(2);
            page.goToHistoryEntry(9);
            assertThat(navigations).as("where it is, and a stale entry").hasValue(0);
            // The page here has no browser, so going there fails once the page has told its listeners.
            assertThatThrownBy(() -> page.goToHistoryEntry(3)).isInstanceOf(NullPointerException.class);
            assertThat(navigations).hasValue(1);
            page.close();
            page.goToHistoryEntry(1);
            assertThat(navigations).as("nothing after the close").hasValue(1);
        });
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
