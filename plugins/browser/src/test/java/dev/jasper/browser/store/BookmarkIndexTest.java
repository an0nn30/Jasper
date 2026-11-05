package dev.jasper.browser.store;

import dev.jasper.browser.store.BookmarkStore.Bookmark;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bookmarks the UI thread reads: none until the index first loads, then each change the store writes. The test's
 * UI thread is the database's own, so the index hears each answer as the database gives it.
 */
class BookmarkIndexTest {
    private final BrowserDatabase database = BrowserDatabase.memory();
    private final Stores stores = new Stores(database, Runnable::run);
    private final BookmarkIndex index = stores.bookmarkIndex();

    @AfterEach void close() throws Exception {
        stores.close();
        stores.closed().get(10, TimeUnit.SECONDS);
    }

    @Test void theIndexReadsNothingBeforeItsFirstLoad() throws Exception {
        done(stores.bookmarks().add("https://a.example/", "A", 1));
        settle();
        assertThat(index.all()).isEmpty();
        assertThat(index.has("https://a.example/")).isFalse();
        index.load();
        settle();
        assertThat(index.all()).extracting(Bookmark::url).containsExactly("https://a.example/");
    }

    @Test void hasFollowsAddAndRemove() throws Exception {
        var told = new AtomicInteger();
        index.onChanged(told::incrementAndGet);
        index.load();
        settle();
        assertThat(told).as("the load").hasValue(1);
        assertThat(index.has("https://a.example/")).isFalse();
        done(stores.bookmarks().add("https://a.example/", "A", 1));
        settle();
        assertThat(index.has("https://a.example/")).isTrue();
        assertThat(index.has("https://ada:secret@a.example/")).as("as stored, without the user's name and password").isTrue();
        assertThat(index.get("https://a.example/")).map(Bookmark::title).contains("A");
        assertThat(index.has("jasper-about:history")).as("an address that is never stored").isFalse();
        done(stores.bookmarks().add("https://b.example/", "B", 2));
        settle();
        assertThat(index.all()).as("newest first").extracting(Bookmark::url).containsExactly("https://b.example/", "https://a.example/");
        done(stores.bookmarks().remove("https://a.example/"));
        settle();
        assertThat(index.has("https://a.example/")).isFalse();
        assertThat(index.get("https://a.example/")).isEmpty();
        assertThat(index.all()).extracting(Bookmark::url).containsExactly("https://b.example/");
        assertThat(told).as("the load and three changes").hasValue(4);
    }

    @Test void aChangeOfTagsReachesTheIndex() throws Exception {
        index.load();
        Bookmark added = done(stores.bookmarks().add("https://a.example/", "A", 1));
        done(stores.bookmarks().tags(added.id(), List.of("work")));
        settle();
        assertThat(index.get("https://a.example/")).map(Bookmark::tags).contains(List.of("work"));
    }

    @Test void clearEmptiesTheIndex() throws Exception {
        done(stores.bookmarks().add("https://a.example/", "A", 1));
        done(stores.bookmarks().add("https://b.example/", "B", 2));
        index.load();
        settle();
        assertThat(index.all()).hasSize(2);
        done(stores.bookmarks().clear());
        settle();
        assertThat(index.all()).isEmpty();
        assertThat(index.has("https://a.example/")).isFalse();
    }

    @Test void theIndexLoadsOnce() throws Exception {
        var told = new AtomicInteger();
        index.onChanged(told::incrementAndGet);
        index.load();
        index.load();
        settle();
        index.load();
        settle();
        assertThat(told).hasValue(1);
    }

    @Test void anEndedListenerHearsNoMore() throws Exception {
        var told = new AtomicInteger();
        var listening = index.onChanged(told::incrementAndGet);
        index.load();
        settle();
        listening.close();
        done(stores.bookmarks().add("https://a.example/", "A", 1));
        settle();
        assertThat(told).hasValue(1);
        assertThat(index.has("https://a.example/")).isTrue();
    }

    /** F13: without SQLite's native library no call answers, and the index stays empty; browsing goes on. */
    @Test void anIndexWhoseDatabaseCannotRunStaysEmpty() throws Exception {
        var failing = new Stores(BrowserDatabase.memory(url -> { throw new UnsatisfiedLinkError("no native library"); }), Runnable::run);
        try {
            var told = new AtomicInteger();
            failing.bookmarkIndex().onChanged(told::incrementAndGet);
            failing.bookmarkIndex().load();
            failing.bookmarks().all().handle((all, failure) -> null).get(10, TimeUnit.SECONDS);
            assertThat(failing.bookmarkIndex().all()).isEmpty();
            assertThat(failing.bookmarkIndex().has("https://a.example/")).isFalse();
            assertThat(told).hasValue(0);
        } finally {
            failing.close();
            failing.closed().get(10, TimeUnit.SECONDS);
        }
    }

    /** Waits for the work given so far, and the reads it started, to run. */
    private void settle() throws Exception {
        done(database.call((Connection connection) -> null));
    }

    private static <T> T done(CompletableFuture<T> future) throws Exception {
        return future.get(10, TimeUnit.SECONDS);
    }
}
