package dev.jasper.browser.store;

import dev.jasper.browser.store.BookmarkStore.Bookmark;
import dev.jasper.browser.store.BookmarkStore.BookmarkPage;
import dev.jasper.browser.store.BookmarkStore.Sort;
import dev.jasper.sdk.Subscription;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Spec section 10's bookmarks: one per address, with tags, listed by a search, a sort and the tags chosen. */
class BookmarkStoreTest {
    private final BrowserDatabase database = BrowserDatabase.memory();
    private final BookmarkStore bookmarks = new BookmarkStore(database);

    @AfterEach void close() throws Exception {
        database.close();
        database.closed().get(10, TimeUnit.SECONDS);
    }

    @Test void anAddressHasOneBookmark() throws Exception {
        Bookmark first = add("https://a.example/", "A", 1_000);
        assertThat(first).isEqualTo(new Bookmark(first.id(), "https://a.example/", "A", 1_000, List.of()));
        assertThat(add("https://a.example/", "Another title", 2_000)).as("the same address again").isEqualTo(first);
        assertThat(add("https://ada:secret@a.example/", "With a password", 3_000)).as("kept without user information").isEqualTo(first);
        assertThat(done(bookmarks.all())).containsExactly(first);
    }

    @Test void anAddressBookmarksDoNotKeepIsRefused() throws Exception {
        for (String address : List.of("jasper-about:bookmarks", "about:blank", "javascript:1"))
            assertThatThrownBy(() -> done(bookmarks.add(address, "x", 1))).as(address)
                .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(IllegalArgumentException.class);
        assertThat(done(bookmarks.all())).isEmpty();
    }

    @Test void aTitleIsCutTo1024CharactersAndOtherwiseKeptExactly() throws Exception {
        assertThat(add("https://a.example/", "t".repeat(2_000), 1).title()).isEqualTo("t".repeat(1_024));
        String hostile = " </script><img src=x onerror=alert(1)>\u2028 ";
        assertThat(add("https://b.example/", hostile, 2).title()).isEqualTo(hostile);
    }

    @Test void removeDeleteAndClear() throws Exception {
        add("https://a.example/", "A", 1);
        Bookmark b = add("https://b.example/", "B", 2);
        Bookmark c = add("https://c.example/", "C", 3);
        done(bookmarks.tags(b.id(), List.of("work")));
        assertThat(done(bookmarks.remove("https://a.example/"))).isTrue();
        assertThat(done(bookmarks.remove("https://a.example/"))).as("already removed").isFalse();
        assertThat(done(bookmarks.remove("about:blank"))).as("never kept").isFalse();
        assertThat(done(bookmarks.delete(b.id()))).isTrue();
        assertThat(done(bookmarks.delete(b.id()))).as("already deleted").isFalse();
        assertThat(done(bookmarks.allTags())).as("a deleted bookmark's tags").isEmpty();
        assertThat(done(bookmarks.all())).containsExactly(c);
        done(bookmarks.tags(c.id(), List.of("home")));
        done(bookmarks.clear());
        assertThat(done(bookmarks.all())).isEmpty();
        assertThat(done(bookmarks.allTags())).isEmpty();
    }

    @Test void removeFindsTheBookmarkByItsAddressWithoutUserInformation() throws Exception {
        add("https://a.example/", "A", 1);
        assertThat(done(bookmarks.remove("https://ada:secret@a.example/"))).isTrue();
        assertThat(done(bookmarks.all())).isEmpty();
    }

    /** Task 6b's token rule: trimmed, nonblank, at most 64 characters, no control character, distinct ignoring case. */
    @Test void tagsAreTrimmedNonblankShortFreeOfControlCharactersAndDistinctIgnoringCase() throws Exception {
        Bookmark bookmark = add("https://a.example/", "A", 1);
        assertThat(done(bookmarks.tags(bookmark.id(), List.of(" Work ", "work", "", "x".repeat(65)))).tags()).containsExactly("Work");
        assertThat(done(bookmarks.tags(bookmark.id(), List.of("x".repeat(64), "  ", "a\tb", "line\nbreak", "zeta", "Alpha", "ALPHA"))).tags())
            .as("kept in the order given").containsExactly("x".repeat(64), "zeta", "Alpha");
        assertThat(done(bookmarks.all())).singleElement().extracting(Bookmark::tags).isEqualTo(List.of("x".repeat(64), "zeta", "Alpha"));
        assertThat(done(bookmarks.tags(bookmark.id(), List.of())).tags()).isEmpty();
    }

    /**
     * Review H10: tags ignore the case of ASCII letters only, as SQLite's {@code NOCASE} does, so "\u00C4pfel" and "\u00E4pfel" are
     * two tags, and each one's chip finds its own bookmark.
     */
    @Test void tagsIgnoreTheCaseOfAsciiLettersOnly() throws Exception {
        Bookmark bookmark = add("https://a.example/", "A", 1);
        assertThat(done(bookmarks.tags(bookmark.id(), List.of("\u00C4pfel", "\u00E4pfel", "APFEL", "apfel"))).tags())
            .containsExactly("\u00C4pfel", "\u00E4pfel", "APFEL");
        Bookmark upper = tagged("https://x.example/", 2, "\u00C4pfel");
        Bookmark lower = tagged("https://y.example/", 3, "\u00E4pfel");
        done(bookmarks.delete(bookmark.id()));
        assertThat(done(bookmarks.allTags())).containsExactly("\u00C4pfel", "\u00E4pfel");
        assertThat(list("", Sort.DATE_ADDED, Set.of("\u00C4pfel"))).containsExactly(upper);
        assertThat(list("", Sort.DATE_ADDED, Set.of("\u00E4pfel"))).containsExactly(lower);
        assertThat(BookmarkStore.keptTags(List.of("Work", "wORK", "\u00C4pfel", "\u00E4pfel"))).as("the rule the token field shares")
            .containsExactly("Work", "\u00C4pfel", "\u00E4pfel");
    }

    @Test void aBookmarkHasAtMost100Tags() throws Exception {
        Bookmark bookmark = add("https://a.example/", "A", 1);
        List<String> many = IntStream.range(0, 101).mapToObj(i -> "tag" + i).toList();
        assertThat(done(bookmarks.tags(bookmark.id(), many)).tags()).containsExactlyElementsOf(many.subList(0, 100));
    }

    @Test void taggingABookmarkThatIsGoneFails() throws Exception {
        assertThatThrownBy(() -> done(bookmarks.tags(42, List.of("work"))))
            .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(NoSuchElementException.class);
    }

    @Test void allTagsAreListedOnceEachSortedIgnoringCase() throws Exception {
        done(bookmarks.tags(add("https://x.example/", "X", 1).id(), List.of("beta", "Alpha")));
        done(bookmarks.tags(add("https://y.example/", "Y", 2).id(), List.of("alpha", "Gamma")));
        assertThat(done(bookmarks.allTags())).containsExactly("Alpha", "beta", "Gamma");
    }

    @Test void theThreeSorts() throws Exception {
        Bookmark banana = add("https://b.example/", "banana", 1);
        Bookmark cherry = add("https://a.example/", "Cherry", 2);
        Bookmark apple = add("https://c.example/", "apple", 3);
        Bookmark untitled = add("https://d.example/", "", 4);
        assertThat(list("", Sort.DATE_ADDED, Set.of())).containsExactly(untitled, apple, cherry, banana);
        assertThat(list("", Sort.TITLE, Set.of())).as("ignoring case, and an untitled bookmark by its address")
            .containsExactly(apple, banana, cherry, untitled);
        assertThat(list("", Sort.URL, Set.of())).containsExactly(cherry, banana, apple, untitled);
        assertThat(done(bookmarks.all())).as("newest first").containsExactly(untitled, apple, cherry, banana);
    }

    /** The plan's call: a bookmark must have every tag chosen. */
    @Test void aTagFilterNeedsEveryChosenTag() throws Exception {
        Bookmark x = tagged("https://x.example/", 1, "a", "b");
        Bookmark y = tagged("https://y.example/", 2, "a");
        Bookmark z = tagged("https://z.example/", 3, "b", "c");
        assertThat(list("", Sort.DATE_ADDED, Set.of("a", "b"))).containsExactly(x);
        assertThat(list("", Sort.DATE_ADDED, Set.of("A"))).as("ignoring case").containsExactly(y, x);
        assertThat(list("", Sort.DATE_ADDED, Set.of("c", "a"))).isEmpty();
        assertThat(list("", Sort.DATE_ADDED, Set.of())).containsExactly(z, y, x);
    }

    @Test void theQueryMatchesTheTitleTheAddressOrATagIgnoringCase() throws Exception {
        Bookmark recipes = done(bookmarks.tags(add("https://food.example/", "Recipes", 1).id(), List.of("Cooking")));
        Bookmark news = add("https://paper.example/", "News", 2);
        assertThat(list("cook", Sort.DATE_ADDED, Set.of())).containsExactly(recipes);
        assertThat(list("PAPER", Sort.DATE_ADDED, Set.of())).containsExactly(news);
        assertThat(list("  news ", Sort.DATE_ADDED, Set.of())).as("trimmed").containsExactly(news);
        assertThat(list("example", Sort.DATE_ADDED, Set.of())).hasSize(2);
        assertThat(list("%", Sort.DATE_ADDED, Set.of())).as("a % matches only itself").isEmpty();
        assertThat(list("cook", Sort.DATE_ADDED, Set.of("news"))).as("and the tags chosen").isEmpty();
    }

    /**
     * Plan 5's H16: a search counts only its first 256 characters, then only their first 8 words, as typed, so that a
     * pasted text neither fails the search nor holds the database.
     */
    @Test void aSearchIsBoundedTo256CharactersAndThenEightWords() throws Exception {
        Bookmark eight = add("https://a.example/", "w1 w2  w3 w4 w5 w6 w7 w8 and more", 1);
        Bookmark cut = add("https://b.example/", "y".repeat(256), 2);
        Bookmark pasted = add("https://c.example/", "a a a a a a a a", 3);
        add("https://d.example/", "y".repeat(255), 4);
        assertThat(list("w1 w2  w3 w4 w5 w6 w7 w8 w9", Sort.DATE_ADDED, Set.of())).as("a ninth word is ignored").containsExactly(eight);
        assertThat(list("y".repeat(300), Sort.DATE_ADDED, Set.of())).as("cut at 256 characters").containsExactly(cut);
        String huge = "a ".repeat(32_768);
        assertThat(huge).hasSize(65_536);
        assertThat(list(huge, Sort.DATE_ADDED, Set.of())).as("tens of thousands of words").containsExactly(pasted);
    }

    @Test void pagesRunByOffsetAndSayWhetherMoreFollow() throws Exception {
        for (int i = 0; i < 5; i++) add("https://a.example/" + i, "Page " + i, i);
        BookmarkPage first = done(bookmarks.list("", Sort.DATE_ADDED, Set.of(), 0, 2));
        assertThat(first.bookmarks()).extracting(Bookmark::url).containsExactly("https://a.example/4", "https://a.example/3");
        assertThat(first.more()).isTrue();
        assertThat(done(bookmarks.list("", Sort.DATE_ADDED, Set.of(), 2, 2)).more()).isTrue();
        BookmarkPage last = done(bookmarks.list("", Sort.DATE_ADDED, Set.of(), 4, 2));
        assertThat(last.bookmarks()).extracting(Bookmark::url).containsExactly("https://a.example/0");
        assertThat(last.more()).isFalse();
        assertThat(done(bookmarks.list("", Sort.DATE_ADDED, Set.of(), 3, 2)).more()).as("a page that ends the list exactly").isFalse();
        assertThat(bookmarks.list("", Sort.DATE_ADDED, Set.of(), -1, 2)).isCompletedExceptionally();
        assertThat(bookmarks.list("", Sort.DATE_ADDED, Set.of(), 0, 0)).isCompletedExceptionally();
    }

    @Test void listenersHearEachWrite() throws Exception {
        var told = new CopyOnWriteArrayList<String>();
        Subscription subscription = bookmarks.onChanged(() -> told.add(Thread.currentThread().getName()));
        Bookmark bookmark = add("https://a.example/", "A", 1);
        assertThat(told).containsExactly("jasper-browser-store");
        add("https://a.example/", "A", 2);
        assertThat(told).as("nothing added").hasSize(1);
        done(bookmarks.tags(bookmark.id(), List.of("work")));
        assertThat(told).hasSize(2);
        done(bookmarks.remove("https://a.example/"));
        assertThat(told).hasSize(3);
        done(bookmarks.remove("https://a.example/"));
        assertThat(told).as("nothing removed").hasSize(3);
        done(bookmarks.delete(add("https://b.example/", "B", 3).id()));
        assertThat(told).hasSize(5);
        add("https://c.example/", "C", 4);
        done(bookmarks.clear());
        assertThat(told).hasSize(7);
        subscription.close();
        add("https://d.example/", "D", 5);
        assertThat(told).hasSize(7);
    }

    private Bookmark add(String address, String title, long when) throws Exception {
        return done(bookmarks.add(address, title, when));
    }

    private Bookmark tagged(String address, long when, String... tags) throws Exception {
        return done(bookmarks.tags(add(address, address, when).id(), List.of(tags)));
    }

    private List<Bookmark> list(String query, Sort sort, Set<String> tags) throws Exception {
        BookmarkPage page = done(bookmarks.list(query, sort, tags, 0, 50));
        assertThat(page.more()).isFalse();
        return page.bookmarks();
    }

    private static <T> T done(CompletableFuture<T> future) throws Exception {
        return future.get(10, TimeUnit.SECONDS);
    }
}
