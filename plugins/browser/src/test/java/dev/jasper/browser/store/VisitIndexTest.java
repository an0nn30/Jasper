package dev.jasper.browser.store;

import dev.jasper.browser.store.HistoryStore.Recorded;
import dev.jasper.browser.store.HistoryStore.Visit;
import java.sql.Connection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The visited sites the UI thread reads: the 10,000 most recent rows once the index loads, each visit an item records,
 * and the rows again after a delete or a clear. The test's UI thread is the database's own.
 */
class VisitIndexTest {
    private final BrowserDatabase database = BrowserDatabase.memory();
    private final Stores stores = new Stores(database, Runnable::run);
    private final VisitIndex index = stores.visitIndex();

    @AfterEach void close() throws Exception {
        stores.close();
        stores.closed().get(10, TimeUnit.SECONDS);
    }

    @Test void theIndexLoadsTheRowsMostRecentFirst() throws Exception {
        Recorded a = record("https://a.example/", 1_000);
        Recorded b = record("https://b.example/", 2_000);
        Recorded again = record("https://a.example/?q", 3_000);
        settle();
        assertThat(index.all()).as("before the first load").isEmpty();
        index.load();
        settle();
        assertThat(index.all()).containsExactly(again.visit(), b.visit());
        assertThat(again.visit().id()).isEqualTo(a.visit().id());
    }

    @Test void tenThousandAndOneVisitsKeepTheNewestTenThousand() throws Exception {
        rows(10_001);
        index.load();
        settle();
        assertThat(index.all()).hasSize(VisitIndex.MAX);
        assertThat(index.all().getFirst().lastVisit()).isEqualTo(10_001);
        assertThat(index.all().getLast().lastVisit()).as("the oldest left out").isEqualTo(2);
        Recorded newer = record("https://new.example/", 20_000);
        index.recorded(newer.visit());
        assertThat(index.all()).hasSize(VisitIndex.MAX);
        assertThat(index.all().getFirst()).isEqualTo(newer.visit());
        assertThat(index.all().getLast().lastVisit()).as("the oldest drops out").isEqualTo(3);
    }

    @Test void aRecordedVisitTakesItsRowsPlace() throws Exception {
        Recorded a = record("https://a.example/", 1_000);
        Recorded b = record("https://b.example/", 2_000);
        index.load();
        settle();
        var told = new AtomicInteger();
        index.onChanged(told::incrementAndGet);
        Recorded again = record("https://a.example/", 3_000);
        index.recorded(again.visit());
        assertThat(index.all()).containsExactly(again.visit(), b.visit());
        Visit retitled = done(stores.history().retitle(again.entry(), "A")).orElseThrow();
        index.recorded(retitled);
        assertThat(index.all()).containsExactly(retitled, b.visit());
        assertThat(index.all().getFirst().title()).isEqualTo("A");
        Visit older = new Visit(b.visit().id(), b.visit().url(), "B", b.visit().count(), b.visit().firstVisit(), b.visit().lastVisit());
        index.recorded(older);
        assertThat(index.all()).as("in its place by time").containsExactly(retitled, older);
        assertThat(told).hasValue(3);
        assertThat(a.visit().id()).isEqualTo(again.visit().id());
    }

    @Test void aVisitRecordedBeforeTheFirstLoadIsLeftToIt() throws Exception {
        Recorded a = record("https://a.example/", 1_000);
        index.recorded(a.visit());
        assertThat(index.all()).isEmpty();
        index.load();
        settle();
        assertThat(index.all()).containsExactly(a.visit());
    }

    @Test void aDeleteAndAClearReloadTheIndex() throws Exception {
        Recorded a = record("https://a.example/", 1_000);
        record("https://b.example/", 2_000);
        index.load();
        settle();
        var told = new AtomicInteger();
        index.onChanged(told::incrementAndGet);
        done(stores.history().delete(a.entry()));
        settle();
        assertThat(index.all()).extracting(Visit::url).containsExactly("https://b.example/");
        done(stores.history().clear());
        settle();
        assertThat(index.all()).isEmpty();
        assertThat(told).hasValue(2);
    }

    @Test void theIndexLoadsOnce() throws Exception {
        record("https://a.example/", 1_000);
        var told = new AtomicInteger();
        index.onChanged(told::incrementAndGet);
        index.load();
        index.load();
        settle();
        assertThat(told).hasValue(1);
    }

    private Recorded record(String address, long when) throws Exception {
        return done(stores.history().record(address, Reached.LINK, when)).orElseThrow();
    }

    /** Rows for {@code count} paths, the first visited at 1 and each later one a millisecond later. */
    private void rows(int count) throws Exception {
        done(database.call(connection -> Sql.transaction(connection, c -> {
            try (var insert = c.prepareStatement("INSERT INTO visits (host, path, url, count, first_visit, last_visit) VALUES (?, ?, ?, 1, ?, ?)")) {
                for (int at = 1; at <= count; at++) {
                    insert.setString(1, "a.example");
                    insert.setString(2, "/" + at);
                    insert.setString(3, "https://a.example/" + at);
                    insert.setLong(4, at);
                    insert.setLong(5, at);
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            return null;
        })));
    }

    /** Waits for the work given so far, and the reads it started, to run. */
    private void settle() throws Exception {
        done(database.call((Connection connection) -> null));
    }

    private static <T> T done(CompletableFuture<T> future) throws Exception {
        return future.get(10, TimeUnit.SECONDS);
    }
}
