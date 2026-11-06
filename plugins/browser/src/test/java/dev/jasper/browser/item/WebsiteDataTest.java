package dev.jasper.browser.item;

import dev.jasper.browser.permissions.Decision;
import dev.jasper.browser.permissions.Permission;
import dev.jasper.browser.store.BrowserDatabase;
import dev.jasper.browser.store.CapturedLog;
import dev.jasper.browser.store.DataFolder;
import dev.jasper.browser.store.HistoryStore;
import dev.jasper.browser.store.MemoryStores;
import dev.jasper.browser.store.Reached;
import dev.jasper.browser.store.Stores;
import java.awt.EventQueue;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plan 6 Task 8: Clear Cookies and Clear Website Data for the persistent store. Clear Website Data gathers the sites to
 * clear first, then has Chromium clear them, then empties the persistent store's history, bookmarks and site
 * permissions, and compacts the database so nothing cleared stays in its file or its log; /dev/null stores are
 * untouched. Chromium is a recording stand-in here, as none can start in a test.
 */
class WebsiteDataTest {
    private static final String MARKER = "jasper-cleared-marker-7f3a";
    @TempDir Path folder;
    private final MemoryStores memory = new MemoryStores();
    private final List<Set<String>> chromiumAsked = new CopyOnWriteArrayList<>();
    private final List<String> told = new CopyOnWriteArrayList<>();
    private final List<URI> open = new CopyOnWriteArrayList<>();
    private volatile CompletableFuture<Void> chromium = CompletableFuture.completedFuture(null), cookies = CompletableFuture.completedFuture(null);
    private int cookiesAsked;
    private DataStores stores;
    private Stores plugins;
    // The clearing's warnings, kept off the console.
    private CapturedLog log;

    @BeforeEach void listen() {
        log = new CapturedLog(WebsiteData.class.getName());
    }

    @AfterEach void close() throws Exception {
        log.close();
        if (stores != null) onUi(() -> {
            stores.close();
            return null;
        });
        if (plugins != null) plugins.closed().get(10, TimeUnit.SECONDS);
        memory.close();
    }

    /** F20: the newest 1,000 history origins, so {@code origins} gives each scheme and host once, newest first. */
    @Test void historyGivesEachOriginOnceNewestFirst() throws Exception {
        var database = BrowserDatabase.memory();
        try {
            var history = new HistoryStore(database);
            done(history.record("https://a.example/one", Reached.LINK, 1_000));
            done(history.record("https://a.example/two?q=1", Reached.LINK, 2_000));
            done(history.record("http://a.example/", Reached.TYPED, 3_000));
            done(history.record("https://B.example:8443/x", Reached.LINK, 4_000));
            done(history.record("https://a.example/one", Reached.LINK, 5_000));
            done(history.record("file:///tmp/page.html", Reached.TYPED, 6_000));
            assertThat(done(history.origins(10))).containsExactly("file://", "https://a.example", "https://b.example:8443", "http://a.example");
            assertThat(done(history.origins(2))).containsExactly("file://", "https://a.example");
            assertThat(history.origins(0)).isCompletedExceptionally();
            done(history.clear());
            assertThat(done(history.origins(10))).isEmpty();
        } finally {
            database.close();
            database.closed().get(10, TimeUnit.SECONDS);
        }
    }

    /**
     * The sites are gathered before anything is erased, from the history and the persistent store's open items, as web
     * origins; Chromium is cleared first, and only once it has the database is emptied.
     */
    @Test void theSitesAreGatheredFirstAndChromiumIsClearedBeforeTheDatabase() throws Exception {
        DataStore persistent = persistent(memory.make());
        Stores kept = persistent.stores();
        done(kept.history().record("https://visited.example/page", Reached.LINK, 1_000));
        done(kept.history().record("http://visited.example:8080/", Reached.LINK, 2_000));
        done(kept.history().record("https://visited.example:443/again", Reached.LINK, 3_000));
        done(kept.history().record("file:///tmp/local.html", Reached.TYPED, 4_000));
        done(kept.bookmarks().add("https://bookmarked.example/", "Bookmarked", 1_000));
        done(persistent.permissions().set("https://camera.example", Permission.CAMERA, Decision.ALLOWED));
        open.addAll(List.of(URI.create("https://open.example/a?b=c"), URI.create("jasper-about:welcome"), URI.create("file:///tmp/x.html")));
        chromium = new CompletableFuture<>();

        CompletableFuture<Void> cleared = onUi(() -> data(persistent).clearAll());
        await(() -> !chromiumAsked.isEmpty());
        assertThat(chromiumAsked).singleElement().asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.collection(String.class))
            .as("F20's live set: the history's origins and the open items', web origins only")
            .containsExactlyInAnyOrder("https://visited.example", "http://visited.example:8080", "https://open.example");
        memory.settle();
        assertThat(done(kept.history().visits(10))).as("Chromium has not cleared yet: nothing is erased").hasSize(4);
        assertThat(done(kept.bookmarks().all())).hasSize(1);
        assertThat(persistent.permissions().all()).hasSize(1);
        assertThat(cleared).isNotDone();

        chromium.complete(null);
        done(cleared);
        assertThat(done(kept.history().visits(10))).isEmpty();
        assertThat(done(kept.history().list("", java.util.Optional.empty(), 10)).entries()).isEmpty();
        assertThat(done(kept.bookmarks().all())).isEmpty();
        assertThat(persistent.permissions().all()).isEmpty();
        long permissionRows = done(kept.database().call(connection -> count(connection, "permissions")));
        assertThat(permissionRows).isZero();
        assertThat(told).isEmpty();
    }

    /** Only the persistent store is emptied: a /dev/null group's history, bookmarks and permissions stay. */
    @Test void aDevNullStoreIsUntouched() throws Exception {
        DataStore persistent = persistent(memory.make());
        DataStore devNull = onUi(() -> stores.newDevNull());
        done(persistent.stores().history().record("https://kept.example/", Reached.LINK, 1_000));
        done(devNull.stores().history().record("https://private.example/", Reached.LINK, 1_000));
        done(devNull.stores().bookmarks().add("https://private.example/", "Private", 1_000));
        done(devNull.permissions().set("https://private.example", Permission.MICROPHONE, Decision.BLOCKED));
        done(onUi(() -> data(persistent).clearAll()));
        assertThat(done(persistent.stores().history().visits(10))).isEmpty();
        assertThat(done(devNull.stores().history().visits(10))).hasSize(1);
        assertThat(done(devNull.stores().bookmarks().all())).hasSize(1);
        assertThat(devNull.permissions().all()).hasSize(1);
        assertThat(chromiumAsked).singleElement().asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.collection(String.class))
            .as("a /dev/null group's sites are not Chromium's persistent store's").containsExactly("https://kept.example");
    }

    /**
     * F17: the database is compacted, and its write-ahead log checkpointed and truncated, so no page of a deleted row
     * stays in {@code browser.sqlite} or {@code browser.sqlite-wal}.
     */
    @Test void nothingClearedStaysInTheDatabaseFileOrItsLog() throws Exception {
        Path data = Files.createDirectories(folder.resolve("data"));
        plugins = new Stores(BrowserDatabase.file(new DataFolder(data), cause -> told.add("unavailable: " + cause)), EventQueue::invokeLater);
        DataStore persistent = persistent(plugins);
        for (int each = 0; each < 50; each++)
            done(plugins.history().record("https://" + MARKER + ".example/page" + each, Reached.LINK, 1_000 + each));
        done(plugins.bookmarks().add("https://" + MARKER + ".example/", MARKER, 1_000));
        done(persistent.permissions().set("https://" + MARKER + ".example", Permission.CAMERA, Decision.ALLOWED));
        assertThat(holds(data)).as("the marker is on disk before the clearing").isTrue();

        done(onUi(() -> data(persistent).clearAll()));
        assertThat(holds(data)).as("nothing cleared stays in the file or its log").isFalse();
        Path log = data.resolve("browser.sqlite-wal");
        if (Files.exists(log)) assertThat(Files.size(log)).as("the log is truncated").isZero();
        long free = done(plugins.database().call(connection -> number(connection, "PRAGMA freelist_count")));
        assertThat(free).as("VACUUM leaves no free page").isZero();
        assertThat(told).isEmpty();
    }

    /** A clearing that fails posts "Website data could not be cleared" with its cause, and the database is not erased. */
    @Test void aFailedClearingIsToldAndErasesNothing() throws Exception {
        DataStore persistent = persistent(memory.make());
        done(persistent.stores().history().record("https://kept.example/", Reached.LINK, 1_000));
        chromium = CompletableFuture.failedFuture(new java.util.concurrent.TimeoutException("Chromium did not clear its data within 60 seconds"));
        CompletableFuture<Void> cleared = onUi(() -> data(persistent).clearAll());
        assertThat(failure(cleared)).hasMessage("Chromium did not clear its data within 60 seconds");
        flushUi();
        assertThat(told).containsExactly("Website data could not be cleared|Chromium did not clear its data within 60 seconds");
        assertThat(log.records()).singleElement().satisfies(record -> {
            assertThat(record.getLevel()).isEqualTo(java.util.logging.Level.WARNING);
            assertThat(record.getMessage()).isEqualTo("Website data could not be cleared");
        });
        assertThat(done(persistent.stores().history().visits(10))).as("not erased").hasSize(1);
    }

    /**
     * A database that cannot be used keeps Chromium's clearing from nothing: Chromium clears the open items' sites, the
     * next start's wipe the rest, and the erase that fails is told.
     */
    @Test void aDatabaseThatCannotBeUsedStillHasChromiumClearedAndIsTold() throws Exception {
        DataStore persistent = persistent(memory.make());
        persistent.stores().close();
        open.add(URI.create("https://open.example/"));
        CompletableFuture<Void> cleared = onUi(() -> data(persistent).clearAll());
        assertThat(failure(cleared)).isInstanceOf(dev.jasper.browser.store.DatabaseClosedException.class);
        flushUi();
        assertThat(chromiumAsked).singleElement().asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.collection(String.class))
            .containsExactly("https://open.example");
        assertThat(told).singleElement().asString().startsWith("Website data could not be cleared|");
        assertThat(log.records()).extracting(java.util.logging.LogRecord::getMessage)
            .containsExactly("The history's sites could not be read; the next start's wipe clears them", "Website data could not be cleared");
    }

    @Test void clearCookiesAsksChromiumAndAFailureIsTold() throws Exception {
        DataStore persistent = persistent(memory.make());
        done(onUi(() -> data(persistent).clearCookies()));
        assertThat(cookiesAsked).isEqualTo(1);
        assertThat(told).isEmpty();
        cookies = CompletableFuture.failedFuture(new IllegalStateException("Chromium's thread did not take the work"));
        CompletableFuture<Void> refused = onUi(() -> data(persistent).clearCookies());
        assertThat(failure(refused)).hasMessage("Chromium's thread did not take the work");
        flushUi();
        assertThat(told).containsExactly("Cookies could not be cleared|Chromium's thread did not take the work");
        assertThat(log.records()).singleElement().extracting(java.util.logging.LogRecord::getMessage).isEqualTo("Cookies could not be cleared");
        assertThat(chromiumAsked).as("cookies only").isEmpty();
    }

    /** The persistent data store over {@code persistentStores}, made as the plugin makes it, on the UI thread. */
    private DataStore persistent(Stores persistentStores) throws Exception {
        return onUi(() -> {
            stores = new DataStores(persistentStores, () -> false);
            return stores.persistent();
        });
    }

    /** The clearing of {@code persistent}, with Chromium's two clearings recorded. */
    private WebsiteData data(DataStore persistent) {
        return new WebsiteData(() -> {
            cookiesAsked++;
            return cookies;
        }, origins -> {
            chromiumAsked.add(Set.copyOf(origins));
            return chromium;
        }, persistent, () -> List.copyOf(open), Runnable::run, (title, cause) -> told.add(title + "|" + cause.getMessage()));
    }

    /** Whether the database's file or its log holds the marker's bytes. */
    private static boolean holds(Path data) throws Exception {
        byte[] marker = MARKER.getBytes(StandardCharsets.US_ASCII);
        for (String name : List.of("browser.sqlite", "browser.sqlite-wal")) {
            Path file = data.resolve(name);
            if (Files.exists(file) && indexOf(Files.readAllBytes(file), marker) >= 0) return true;
        }
        return false;
    }

    private static int indexOf(byte[] bytes, byte[] part) {
        outer:
        for (int at = 0; at <= bytes.length - part.length; at++) {
            for (int each = 0; each < part.length; each++) if (bytes[at + each] != part[each]) continue outer;
            return at;
        }
        return -1;
    }

    private static long count(java.sql.Connection connection, String table) throws java.sql.SQLException {
        return number(connection, "SELECT COUNT(*) FROM " + table);
    }

    private static long number(java.sql.Connection connection, String sql) throws java.sql.SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static <T> T done(CompletableFuture<T> future) throws Exception {
        return future.get(10, TimeUnit.SECONDS);
    }

    private static Throwable failure(CompletableFuture<?> future) {
        try {
            future.get(10, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException failed) {
            return failed.getCause();
        } catch (Exception other) {
            throw new AssertionError(other);
        }
        throw new AssertionError("the clearing did not fail");
    }

    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > until) throw new AssertionError("not within 10 seconds");
            Thread.sleep(10);
        }
    }

    private static <T> T onUi(Callable<T> work) throws Exception {
        var result = new AtomicReference<T>();
        var thrown = new AtomicReference<Exception>();
        EventQueue.invokeAndWait(() -> {
            try {
                result.set(work.call());
            } catch (Exception failure) {
                thrown.set(failure);
            }
        });
        if (thrown.get() != null) throw thrown.get();
        return result.get();
    }

    private static void flushUi() throws Exception {
        EventQueue.invokeAndWait(() -> { });
    }
}
