package dev.jasper.browser.store;

import dev.jasper.browser.store.HistoryStore.HistoryEntry;
import dev.jasper.browser.store.HistoryStore.HistoryPage;
import dev.jasper.browser.store.HistoryStore.Recorded;
import dev.jasper.browser.store.HistoryStore.Visit;
import dev.jasper.sdk.Subscription;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** Spec section 10's history: one entry per page load, and a row per host and path that counts the visits. */
class HistoryStoreTest {
    private final BrowserDatabase database = BrowserDatabase.memory();
    private final HistoryStore history = new HistoryStore(database);

    @AfterEach void close() throws Exception {
        database.close();
        database.closed().get(10, TimeUnit.SECONDS);
    }

    @Test void twoLoadsOfOnePathMakeTwoEntriesAndOneRowWithTheLaterAddress() throws Exception {
        Recorded first = record("https://a.example/p?x=1", Reached.LINK, 1_000);
        Recorded second = record("https://a.example/p?x=2", Reached.TYPED, 2_000);
        assertThat(second.entry()).isNotEqualTo(first.entry());
        assertThat(first.visit()).isEqualTo(new Visit(first.visit().id(), "https://a.example/p?x=1", "", 1, 1_000, 1_000));
        assertThat(second.visit()).isEqualTo(new Visit(first.visit().id(), "https://a.example/p?x=2", "", 2, 1_000, 2_000));
        assertThat(done(history.visits(10))).containsExactly(second.visit());
        HistoryPage page = done(history.list("", Optional.empty(), 10));
        assertThat(page.entries()).containsExactly(
            new HistoryEntry(second.entry(), "https://a.example/p?x=2", "", 2_000, Reached.TYPED),
            new HistoryEntry(first.entry(), "https://a.example/p?x=1", "", 1_000, Reached.LINK));
        assertThat(page.next()).isEmpty();
    }

    /**
     * H14: a retitle given for a record that has not answered yet runs behind it, as the database runs its calls in order,
     * and lands; one for a record that made no entry, failed, or is no call of this store's gives nothing.
     */
    @Test void aRetitleOfARecordNotYetAnsweredLandsBehindIt() throws Exception {
        var parked = new java.util.concurrent.CountDownLatch(1);
        var released = new java.util.concurrent.CountDownLatch(1);
        database.call(connection -> {
            parked.countDown();
            try {
                released.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return null;
        });
        assertThat(parked.await(10, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Optional<Recorded>> recorded = history.record("https://a.example/", Reached.LINK, 1);
        CompletableFuture<Optional<Visit>> retitled = history.retitle(recorded, "Title");
        assertThat(recorded).as("the record waits").isNotDone();
        released.countDown();
        assertThat(done(retitled)).map(Visit::title).contains("Title");
        assertThat(done(history.list("", Optional.empty(), 10)).entries()).extracting(HistoryEntry::title).containsExactly("Title");
        assertThat(done(history.retitle(history.record("jasper-about:history", Reached.LINK, 2), "None"))).as("no entry").isEmpty();
        assertThat(done(history.retitle(CompletableFuture.failedFuture(new java.sql.SQLException("unavailable")), "None")))
            .as("a failed record").isEmpty();
        assertThat(done(history.retitle(new CompletableFuture<>(), "None"))).as("no record of this store's").isEmpty();
        assertThat(done(history.list("", Optional.empty(), 10)).entries()).extracting(HistoryEntry::title).containsExactly("Title");
    }

    @Test void howAPageWasReachedIsStoredAsItsLowerCaseName() throws Exception {
        long when = 1;
        for (Reached reached : Reached.values()) record("https://a.example/" + reached, reached, when++);
        assertThat(done(database.<List<String>>call(connection -> {
            var stored = new java.util.ArrayList<String>();
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT reached FROM history ORDER BY id")) {
                while (rows.next()) stored.add(rows.getString(1));
            }
            return stored;
        }))).containsExactly("typed", "link", "reload", "back_forward", "redirect", "form_submit", "other");
        assertThat(done(history.list("", Optional.empty(), 10)).entries()).extracting(HistoryEntry::reached)
            .containsExactly(Reached.OTHER, Reached.FORM_SUBMIT, Reached.REDIRECT, Reached.BACK_FORWARD, Reached.RELOAD, Reached.LINK, Reached.TYPED);
    }

    /** A value this version does not know, from a later Jasper or an edited file, reads as {@code OTHER}. */
    @Test void anUnknownWayOfReachingAPageReadsAsOther() throws Exception {
        long id = record("https://a.example/", Reached.LINK, 1).entry();
        done(database.call(connection -> {
            try (var statement = connection.prepareStatement("UPDATE history SET reached = 'teleported' WHERE id = ?")) {
                statement.setLong(1, id);
                return statement.executeUpdate();
            }
        }));
        assertThat(done(history.list("", Optional.empty(), 1)).entries()).extracting(HistoryEntry::reached).containsExactly(Reached.OTHER);
    }

    @Test void anAddressHistoryDoesNotKeepRecordsNothingAndUserInformationIsDropped() throws Exception {
        assertThat(done(history.record("jasper-about:welcome", Reached.TYPED, 1))).isEmpty();
        assertThat(done(history.record("about:blank", Reached.OTHER, 2))).isEmpty();
        Recorded kept = record("https://ada:secret@a.example/", Reached.LINK, 3);
        assertThat(kept.visit().url()).isEqualTo("https://a.example/");
        assertThat(done(history.list("", Optional.empty(), 10)).entries()).extracting(HistoryEntry::url).containsExactly("https://a.example/");
    }

    @Test void retitleChangesTheEntryAndItsRow() throws Exception {
        Recorded recorded = record("https://a.example/", Reached.LINK, 1_000);
        Visit visit = done(history.retitle(recorded.entry(), "Alpha")).orElseThrow();
        assertThat(visit).isEqualTo(new Visit(recorded.visit().id(), "https://a.example/", "Alpha", 1, 1_000, 1_000));
        assertThat(done(history.visits(1))).containsExactly(visit);
        assertThat(done(history.list("", Optional.empty(), 1)).entries()).extracting(HistoryEntry::title).containsExactly("Alpha");
        assertThat(done(history.retitle(recorded.entry() + 1, "Nobody's"))).as("no such entry").isEmpty();
    }

    /** A title is cut to 1,024 characters, never between the two halves of a surrogate pair, and otherwise kept exactly. */
    @Test void aTitleIsCutTo1024Characters() throws Exception {
        long entry = record("https://a.example/", Reached.LINK, 1).entry();
        assertThat(done(history.retitle(entry, "t".repeat(2_000))).orElseThrow().title()).isEqualTo("t".repeat(1_024));
        String split = "t".repeat(1_023) + "\uD83D\uDE00" + "t";
        assertThat(done(history.retitle(entry, split)).orElseThrow().title()).isEqualTo("t".repeat(1_023));
        String hostile = " </script><img src=x onerror=alert(1)>\u2028 ";
        assertThat(done(history.retitle(entry, hostile)).orElseThrow().title()).isEqualTo(hostile);
    }

    /** A late title for an earlier load of the path leaves the row, which shows the later address, its own title. */
    @Test void aLateTitleForAnEarlierAddressOfTheRowLeavesTheRowsTitle() throws Exception {
        Recorded first = record("https://a.example/p?x=1", Reached.LINK, 1_000);
        Recorded second = record("https://a.example/p?x=2", Reached.LINK, 2_000);
        done(history.retitle(second.entry(), "Second"));
        assertThat(done(history.retitle(first.entry(), "First")).orElseThrow().title()).isEqualTo("Second");
        assertThat(done(history.list("", Optional.empty(), 10)).entries()).extracting(HistoryEntry::title).containsExactly("Second", "First");
    }

    /** A row that takes another address has no title until that address's page gives one (review round 1). */
    @Test void aRowThatTakesAnotherAddressHasNoTitleUntilThatPageGivesOne() throws Exception {
        titled("https://a.example/p?x=1", "One", 1_000);
        Recorded second = record("https://a.example/p?x=2", Reached.LINK, 2_000);
        assertThat(second.visit().title()).isEmpty();
        done(history.retitle(second.entry(), "Two"));
        assertThat(record("https://a.example/p?x=2", Reached.RELOAD, 3_000).visit().title()).as("the same address again").isEqualTo("Two");
    }

    /**
     * Review H8: deleting the entry a row shows points the row at the newest entry of its host and path that is left,
     * whose address and title it then shows, and its first visit at the oldest.
     */
    @Test void deletingTheEntryARowShowsPointsTheRowAtTheNewestEntryLeft() throws Exception {
        long one = titled("https://a.example/p?x=1", "One", 1_000);
        titled("https://a.example/p?x=0", "Zero", 1_100);
        record("https://a.example/p/q", Reached.LINK, 5_000); // another path, which the search for the row's entries finds too
        record("https://b.example/", Reached.LINK, 1_500);
        long two = titled("https://a.example/p?x=2", "Two", 2_000);
        assertThat(done(history.delete(two))).isTrue();
        assertThat(done(history.visits(10))).extracting(Visit::url, Visit::title, Visit::count, Visit::firstVisit, Visit::lastVisit)
            .containsExactly(tuple("https://a.example/p/q", "", 1, 5_000L, 5_000L), tuple("https://b.example/", "", 1, 1_500L, 1_500L),
                tuple("https://a.example/p?x=0", "Zero", 2, 1_000L, 1_100L));
        done(history.delete(one));
        assertThat(done(history.visits(10))).extracting(Visit::url, Visit::title, Visit::count, Visit::firstVisit, Visit::lastVisit)
            .last().isEqualTo(tuple("https://a.example/p?x=0", "Zero", 1, 1_100L, 1_100L));
    }

    /** Deleting an older entry leaves the row as it was, apart from its count and its first visit. */
    @Test void deletingAnOlderEntryChangesOnlyTheRowsCountAndFirstVisit() throws Exception {
        long one = titled("https://a.example/p?x=1", "One", 1_000);
        titled("https://a.example/p?x=2", "Two", 2_000);
        done(history.delete(one));
        assertThat(done(history.visits(10))).extracting(Visit::url, Visit::title, Visit::count, Visit::firstVisit, Visit::lastVisit)
            .containsExactly(tuple("https://a.example/p?x=2", "Two", 1, 2_000L, 2_000L));
    }

    /** An address without a path counts under {@code /}, though its text has none: the row finds its entries all the same. */
    @Test void aRowFindsItsEntriesWhenAnAddressHasNoPath() throws Exception {
        titled("https://a.example", "Bare", 1_000);
        long slash = titled("https://a.example/?q=100%25", "Query", 2_000);
        done(history.delete(slash));
        assertThat(done(history.visits(10))).extracting(Visit::url, Visit::title, Visit::count, Visit::lastVisit)
            .containsExactly(tuple("https://a.example", "Bare", 1, 1_000L));
    }

    /** Pages run by a cursor, so an entry recorded between two pages never shifts the second one. */
    @Test void entriesPageNewestFirstAndAnEntryRecordedBetweenPagesNeverShiftsAPage() throws Exception {
        for (int i = 0; i < 120; i++) record("https://a.example/" + i, Reached.LINK, 1_000 + i / 3); // three entries a millisecond
        HistoryPage first = done(history.list("", Optional.empty(), 50));
        assertThat(first.entries()).extracting(HistoryEntry::url).containsExactlyElementsOf(urls(119, 70));
        assertThat(first.next()).isPresent();
        record("https://later.example/", Reached.LINK, 5_000);
        HistoryPage second = done(history.list("", first.next(), 50));
        assertThat(second.entries()).extracting(HistoryEntry::url).containsExactlyElementsOf(urls(69, 20));
        HistoryPage third = done(history.list("", second.next(), 50));
        assertThat(third.entries()).extracting(HistoryEntry::url).containsExactlyElementsOf(urls(19, 0));
        assertThat(third.next()).isEmpty();
        assertThat(done(history.list("", Optional.empty(), 50)).entries()).first().extracting(HistoryEntry::url)
            .isEqualTo("https://later.example/");
    }

    @Test void aPageThatEndsTheHistoryExactlyHasNoNext() throws Exception {
        for (int i = 0; i < 4; i++) record("https://a.example/" + i, Reached.LINK, i);
        HistoryPage page = done(history.list("", Optional.empty(), 2));
        assertThat(done(history.list("", page.next(), 2)).next()).isEmpty();
    }

    @Test void aSearchMatchesAnyOfItsWordsInTheAddressOrTheTitleIgnoringCase() throws Exception {
        long alpha = titled("https://alpha.example/", "First", 1);
        long beta = titled("https://b.example/", "The BETA page", 2);
        titled("https://c.example/", "Gamma", 3);
        assertThat(ids(" alpha  beta ")).containsExactly(beta, alpha);
        assertThat(ids("ALPHA")).containsExactly(alpha);
        assertThat(ids("example")).hasSize(3);
        assertThat(ids("   ")).as("no words").hasSize(3);
        assertThat(ids("delta")).isEmpty();
    }

    /** iTerm2's rule: a search's {@code %}, {@code _} and {@code \} match only themselves. */
    @Test void percentUnderscoreAndBackslashMatchOnlyThemselves() throws Exception {
        long percent = titled("https://p.example/1", "100% sure", 1);
        titled("https://p.example/2", "1000 sure", 2);
        long underscore = titled("https://p.example/3", "a_b", 3);
        titled("https://p.example/4", "axb", 4);
        long backslash = titled("https://p.example/5", "back\\slash", 5);
        assertThat(ids("%")).containsExactly(percent);
        assertThat(ids("0%")).containsExactly(percent);
        assertThat(ids("_")).containsExactly(underscore);
        assertThat(ids("a_b")).containsExactly(underscore);
        assertThat(ids("\\")).containsExactly(backslash);
        assertThat(ids("k\\s")).containsExactly(backslash);
    }

    @Test void deletingAnEntryTakesOneVisitOffItsRowAndDeletingTheLastRemovesTheRow() throws Exception {
        Recorded one = record("https://a.example/p?x=1", Reached.LINK, 1_000);
        Recorded two = record("https://a.example/p?x=2", Reached.LINK, 2_000);
        assertThat(done(history.delete(one.entry()))).isTrue();
        assertThat(done(history.visits(10))).extracting(Visit::count).containsExactly(1);
        assertThat(done(history.list("", Optional.empty(), 10)).entries()).extracting(HistoryEntry::id).containsExactly(two.entry());
        assertThat(done(history.delete(two.entry()))).isTrue();
        assertThat(done(history.visits(10))).isEmpty();
        assertThat(done(history.topSites(10))).isEmpty();
        assertThat(done(history.list("", Optional.empty(), 10)).entries()).isEmpty();
        assertThat(done(history.delete(two.entry()))).as("already deleted").isFalse();
    }

    @Test void clearEmptiesTheEntriesAndTheRows() throws Exception {
        record("https://a.example/", Reached.LINK, 1);
        record("https://b.example/", Reached.LINK, 2);
        done(history.clear());
        assertThat(done(history.list("", Optional.empty(), 10)).entries()).isEmpty();
        assertThat(done(history.visits(10))).isEmpty();
        assertThat(record("https://a.example/", Reached.LINK, 3).visit().count()).as("counting starts again").isEqualTo(1);
    }

    @Test void topSitesAreTheMostVisitedThenTheMostRecentAndVisitsTheMostRecent() throws Exception {
        for (long when : List.of(1L, 2L, 3L)) record("https://a.example/", Reached.LINK, when);
        record("https://b.example/", Reached.LINK, 10);
        for (long when : List.of(4L, 5L, 6L)) record("https://c.example/", Reached.LINK, when);
        assertThat(done(history.topSites(10))).extracting(Visit::url)
            .containsExactly("https://c.example/", "https://a.example/", "https://b.example/");
        assertThat(done(history.topSites(2))).extracting(Visit::url).containsExactly("https://c.example/", "https://a.example/");
        assertThat(done(history.visits(10))).extracting(Visit::url)
            .containsExactly("https://b.example/", "https://c.example/", "https://a.example/");
        assertThat(done(history.visits(1))).extracting(Visit::url).containsExactly("https://b.example/");
    }

    @Test void listenersHearDeleteAndClearOnTheStoresThread() throws Exception {
        var told = new CopyOnWriteArrayList<String>();
        Subscription subscription = history.onChanged(() -> told.add(Thread.currentThread().getName()));
        Recorded recorded = record("https://a.example/", Reached.LINK, 1);
        done(history.retitle(recorded.entry(), "A"));
        assertThat(told).as("recording and retitling").isEmpty();
        done(history.delete(recorded.entry()));
        assertThat(told).containsExactly("jasper-browser-store");
        done(history.delete(recorded.entry()));
        assertThat(told).as("nothing deleted").hasSize(1);
        record("https://a.example/", Reached.LINK, 2);
        done(history.clear());
        assertThat(told).hasSize(2);
        subscription.close();
        record("https://a.example/", Reached.LINK, 3);
        done(history.clear());
        assertThat(told).hasSize(2);
    }

    /** A listener that throws is logged, and neither fails the change nor keeps the others from hearing it. */
    @Test void aListenerThatThrowsDoesNotFailTheChange() throws Exception {
        try (var log = new CapturedLog(Changes.class)) {
            var told = new CopyOnWriteArrayList<String>();
            history.onChanged(() -> { throw new IllegalStateException("a broken listener"); });
            history.onChanged(() -> told.add("heard"));
            long entry = record("https://a.example/", Reached.LINK, 1).entry();
            assertThat(done(history.delete(entry))).isTrue();
            assertThat(told).containsExactly("heard");
            assertThat(log.records()).singleElement().satisfies(record -> {
                assertThat(record.getLevel()).isEqualTo(Level.WARNING);
                assertThat(record.getThrown()).hasMessage("a broken listener");
            });
        }
    }

    @Test void aLimitBelowOneIsRefused() {
        assertThat(history.list("", Optional.empty(), 0)).isCompletedExceptionally();
        assertThat(history.visits(0)).isCompletedExceptionally();
        assertThat(history.topSites(-1)).isCompletedExceptionally();
    }

    private Recorded record(String address, Reached reached, long when) throws Exception {
        return done(history.record(address, reached, when)).orElseThrow();
    }

    private long titled(String address, String title, long when) throws Exception {
        long entry = record(address, Reached.LINK, when).entry();
        done(history.retitle(entry, title));
        return entry;
    }

    private List<Long> ids(String query) throws Exception {
        return done(history.list(query, Optional.empty(), 100)).entries().stream().map(HistoryEntry::id).toList();
    }

    private static List<String> urls(int from, int downTo) {
        return java.util.stream.IntStream.iterate(from, i -> i >= downTo, i -> i - 1).mapToObj(i -> "https://a.example/" + i).toList();
    }

    private static <T> T done(CompletableFuture<T> future) throws Exception {
        return future.get(10, TimeUnit.SECONDS);
    }
}
