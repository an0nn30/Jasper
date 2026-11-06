package dev.jasper.browser.permissions;

import dev.jasper.browser.store.BrowserDatabase;
import dev.jasper.browser.store.CapturedLog;
import dev.jasper.browser.store.DatabaseClosedException;
import dev.jasper.browser.store.DatabaseFixtures;
import dev.jasper.browser.store.DataFolder;
import dev.jasper.browser.store.HistoryStore;
import dev.jasper.browser.store.Stores;
import java.awt.EventQueue;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Spec section 12's site permissions over a browser database: read at once from their cache on any thread, written on
 * the database's thread, kept in its {@code permissions} table across a reopen, and still held when a write fails.
 */
class PermissionStoreTest {
    private static final String A = "https://a.test", B = "https://b.test";
    @TempDir Path folder;
    private final List<BrowserDatabase> databases = new ArrayList<>();
    private final AtomicLong clock = new AtomicLong(1_000);
    // Why each write that failed was not saved, as the store tells it on the UI thread.
    private final List<Throwable> unsaved = new CopyOnWriteArrayList<>();
    private final List<Boolean> unsavedOnUi = new CopyOnWriteArrayList<>();

    @AfterEach void closeDatabases() throws Exception {
        for (BrowserDatabase database : databases) database.close();
        for (BrowserDatabase database : databases) database.closed().get(30, TimeUnit.SECONDS);
    }

    /** The permissions, in their order, with their tokens, labels and prompts, and the decisions with theirs. */
    @Test void eachPermissionAndDecisionHasItsTokenLabelAndPrompt() {
        assertThat(Permission.values()).extracting(Permission::token, Permission::label).containsExactly(tuple("camera", "Camera"),
            tuple("microphone", "Microphone"), tuple("camera-microphone", "Camera and Microphone"), tuple("notifications", "Notifications"),
            tuple("audio", "Audio Playback"));
        assertThat(List.of(Permission.CAMERA, Permission.MICROPHONE, Permission.CAMERA_AND_MICROPHONE, Permission.NOTIFICATIONS))
            .extracting(Permission::heading, permission -> permission.asks("https://a.test")).containsExactly(
                tuple("Allow Camera", "The website https://a.test wants to use your camera."),
                tuple("Allow Microphone", "The website https://a.test wants to use your microphone."),
                tuple("Allow Camera and Microphone", "The website https://a.test wants to use your camera and microphone."),
                tuple("Allow Notifications", "The website https://a.test wants to send you notifications."));
        assertThatThrownBy(Permission.AUDIO_PLAYBACK::heading).as("the audio bar asks instead").isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> Permission.AUDIO_PLAYBACK.asks(A)).isInstanceOf(UnsupportedOperationException.class);
        for (Permission permission : Permission.values()) assertThat(Permission.ofToken(permission.token())).contains(permission);
        assertThat(Permission.ofToken("location")).isEmpty();
        assertThat(Permission.ofToken("CAMERA")).isEmpty();
        assertThat(Permission.ofToken(null)).isEmpty();
        assertThat(Decision.values()).extracting(Decision::token, Decision::label).containsExactly(tuple("allowed", "Allowed"), tuple("blocked", "Blocked"));
        assertThat(Decision.ofToken("blocked")).contains(Decision.BLOCKED);
        assertThat(Decision.ofToken("ask")).isEmpty();
        assertThat(AudioMode.values()).containsExactly(AudioMode.ALLOW, AudioMode.BLOCK, AudioMode.ASK);
        assertThat(new PermissionRequest(A, Permission.CAMERA)).isEqualTo(new PermissionRequest(A, Permission.CAMERA));
        assertThatThrownBy(() -> new PermissionRequest(null, Permission.CAMERA)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Grant(A, Permission.CAMERA, null, Instant.EPOCH, Instant.EPOCH)).isInstanceOf(NullPointerException.class);
    }

    @Test void setGivesTheDecisionAtOnceAndASecondSetKeepsWhenItWasMade() throws Exception {
        PermissionStore store = store(memory());
        assertThat(store.decision(A, Permission.CAMERA)).isEmpty();
        CompletableFuture<Void> written = store.set(A, Permission.CAMERA, Decision.ALLOWED);
        assertThat(store.decision(A, Permission.CAMERA)).as("at once, before the row is written").contains(Decision.ALLOWED);
        done(written);
        clock.set(2_000);
        done(store.set(A, Permission.CAMERA, Decision.BLOCKED));
        assertThat(store.of(A)).containsExactly(new Grant(A, Permission.CAMERA, Decision.BLOCKED, Instant.ofEpochMilli(1_000), Instant.ofEpochMilli(2_000)));
        assertThat(store.decision(A, Permission.MICROPHONE)).isEmpty();
        assertThat(store.decision(B, Permission.CAMERA)).isEmpty();
    }

    @Test void everyGrantListsByOriginThenInThePermissionsOrder() throws Exception {
        PermissionStore store = store(memory());
        done(store.set(B, Permission.NOTIFICATIONS, Decision.BLOCKED));
        done(store.set(A, Permission.AUDIO_PLAYBACK, Decision.ALLOWED));
        done(store.set(A, Permission.CAMERA, Decision.ALLOWED));
        done(store.set(B, Permission.MICROPHONE, Decision.ALLOWED));
        done(store.set(A, Permission.CAMERA_AND_MICROPHONE, Decision.BLOCKED));
        assertThat(store.all()).extracting(Grant::origin, Grant::permission).containsExactly(
            tuple(A, Permission.CAMERA), tuple(A, Permission.CAMERA_AND_MICROPHONE),
            tuple(A, Permission.AUDIO_PLAYBACK), tuple(B, Permission.MICROPHONE),
            tuple(B, Permission.NOTIFICATIONS));
        assertThat(store.of(B)).extracting(Grant::permission).containsExactly(Permission.MICROPHONE, Permission.NOTIFICATIONS);
        assertThat(store.of("https://c.test")).isEmpty();
    }

    @Test void audioPlaybackGivesTheAudioMode() throws Exception {
        PermissionStore store = store(memory());
        assertThat(store.audioMode(A)).isEqualTo(AudioMode.ASK);
        done(store.set(A, Permission.AUDIO_PLAYBACK, Decision.ALLOWED));
        assertThat(store.audioMode(A)).isEqualTo(AudioMode.ALLOW);
        done(store.set(A, Permission.AUDIO_PLAYBACK, Decision.BLOCKED));
        assertThat(store.audioMode(A)).isEqualTo(AudioMode.BLOCK);
        store.revoke(A, Permission.AUDIO_PLAYBACK);
        assertThat(store.audioMode(A)).isEqualTo(AudioMode.ASK);
        assertThat(store.audioMode(B)).isEqualTo(AudioMode.ASK);
    }

    @Test void eachRemovalReturnsTheOriginsItAffected() throws Exception {
        BrowserDatabase database = memory();
        PermissionStore store = store(database);
        done(store.set(A, Permission.CAMERA, Decision.ALLOWED));
        done(store.set(A, Permission.NOTIFICATIONS, Decision.BLOCKED));
        done(store.set(B, Permission.MICROPHONE, Decision.ALLOWED));
        assertThat(store.revoke(A, Permission.MICROPHONE)).as("nothing to revoke").isEmpty();
        assertThat(store.revoke(A, Permission.CAMERA)).containsExactly(A);
        assertThat(store.revoke(A, Permission.CAMERA)).as("revoked already").isEmpty();
        assertThat(store.clearSite("https://c.test")).isEmpty();
        assertThat(store.all()).extracting(Grant::origin).containsExactly(A, B);
        assertThat(reread(database).all()).as("the rows follow").extracting(Grant::origin, Grant::permission).containsExactly(
            tuple(A, Permission.NOTIFICATIONS), tuple(B, Permission.MICROPHONE));
        assertThat(store.clearSite(A)).containsExactly(A);
        assertThat(store.all()).extracting(Grant::origin).containsExactly(B);
        done(store.set(A, Permission.CAMERA, Decision.ALLOWED));
        PermissionStore.Cleared cleared = store.clearAll();
        assertThat(cleared.sites()).containsExactlyInAnyOrder(A, B);
        done(cleared.written());
        assertThat(store.all()).isEmpty();
        assertThat(store.clearAll().sites()).isEmpty();
        assertThat(reread(database).all()).as("the rows go too").isEmpty();
    }

    @Test void theRowsAreReadAgainAfterTheFileReopens() throws Exception {
        BrowserDatabase first = file();
        PermissionStore store = store(first);
        done(store.set(A, Permission.CAMERA, Decision.ALLOWED));
        clock.set(5_000);
        done(store.set(A, Permission.CAMERA, Decision.BLOCKED));
        done(store.set(B, Permission.NOTIFICATIONS, Decision.ALLOWED));
        List<Grant> kept = store.all();
        first.close();
        first.closed().get(30, TimeUnit.SECONDS);

        PermissionStore again = store(file());
        assertThat(again.all()).as("nothing read before it loads").isEmpty();
        done(again.load());
        assertThat(again.all()).isEqualTo(kept);
        assertThat(again.of(A)).containsExactly(new Grant(A, Permission.CAMERA, Decision.BLOCKED, Instant.ofEpochMilli(1_000), Instant.ofEpochMilli(5_000)));
        assertThat(again.load()).as("it loads once").isSameAs(again.load());
    }

    /** Plan 6's F32: a file plan 5 made, without the table, gets it as it opens, and keeps its history. */
    @Test void aDatabasePlanFiveMadeGetsTheTableAndKeepsItsRows() throws Exception {
        DatabaseFixtures.plan5(folder, "https://kept.example/");

        BrowserDatabase opened = file();
        assertThat(done(opened.<Set<String>>call(PermissionStoreTest::tables))).as("the table is made as it opens").contains("permissions", "history", "bookmarks");
        PermissionStore store = store(opened);
        done(store.load());
        assertThat(store.all()).isEmpty();
        done(store.set(A, Permission.MICROPHONE, Decision.ALLOWED));
        assertThat(reread(opened).all()).extracting(Grant::permission).containsExactly(Permission.MICROPHONE);
        assertThat(done(new Stores(opened, Runnable::run).history().list("", Optional.empty(), 10)).entries())
            .extracting(HistoryStore.HistoryEntry::url).as("plan 5's rows stay").containsExactly("https://kept.example/");
    }

    /**
     * Review item 2: a change and its row are one step, so another change made to the same site at that moment cannot
     * land in the database in the other order: here the set's listener, told as it changes the cache, clears the site
     * at once, as another thread could, while the database thread is parked; both rows queue, and the database ends as
     * the cache does.
     */
    @Test void aChangeQueuesItsRowInTheOrderItChangesTheCache() throws Exception {
        BrowserDatabase database = memory();
        var meanwhile = new java.util.concurrent.atomic.AtomicReference<Runnable>();
        var store = new PermissionStore(database, task -> {
            Runnable other = meanwhile.getAndSet(null);
            if (other != null) other.run();
            EventQueue.invokeLater(task);
        }, failure -> { }, clock::get);
        store.onChanged(() -> { });
        Runnable release = park(database);
        meanwhile.set(() -> store.clearSite(A));
        CompletableFuture<Void> set = store.set(A, Permission.CAMERA, Decision.ALLOWED);
        assertThat(meanwhile).as("the clear ran as the set told its change").hasValue(null);
        release.run();
        done(set);
        assertThat(store.all()).as("the clear came last").isEmpty();
        assertThat(reread(database).all()).as("and so did its row").isEmpty();
    }

    /**
     * Review item 3: a closed store's permissions go from its cache, its rows untouched, and it takes no change after;
     * a load that comes later brings none back.
     */
    @Test void discardEmptiesTheCacheWithoutWritingAndTakesNoLaterChange() throws Exception {
        BrowserDatabase database = memory();
        PermissionStore store = store(database);
        done(store.set(A, Permission.CAMERA, Decision.ALLOWED));
        var told = new AtomicInteger();
        store.onChanged(told::incrementAndGet);
        store.discard();
        assertThat(store.all()).isEmpty();
        assertThat(store.decision(A, Permission.CAMERA)).isEmpty();
        assertThat(store.set(B, Permission.MICROPHONE, Decision.ALLOWED)).as("no change after").failsWithin(10, TimeUnit.SECONDS);
        assertThat(store.clearAll().sites()).isEmpty();
        assertThat(store.clearAll().written()).as("no write after").failsWithin(10, TimeUnit.SECONDS);
        assertThatThrownBy(() -> done(store.load())).isInstanceOf(ExecutionException.class);
        settle();
        assertThat(store.all()).as("a later load brings none back").isEmpty();
        assertThat(told).as("a closing store's items are not drawn again").hasValue(0);
        assertThat(reread(database).all()).as("nothing written: the rows are as they were").extracting(Grant::origin).containsExactly(A);
    }

    /**
     * Review item 4: only the database's own refusal as it closes is a dropped write; an {@code IllegalStateException}
     * that SQLite's driver throws is a failed write, logged and told.
     */
    @Test void anIllegalStateExceptionFromTheDriverIsAFailedWrite() throws Exception {
        BrowserDatabase database = DatabaseFixtures.throwingFor(sql -> sql.startsWith("INSERT INTO permissions"));
        databases.add(database);
        PermissionStore store = store(database);
        try (var log = new CapturedLog(PermissionStore.class.getName())) {
            CompletableFuture<Void> written = store.set(A, Permission.CAMERA, Decision.ALLOWED);
            assertThatThrownBy(() -> done(written)).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(IllegalStateException.class);
            settle();
            assertThat(unsaved).singleElement().isInstanceOf(IllegalStateException.class)
                .satisfies(cause -> assertThat(cause).hasMessageStartingWith("The driver failed"));
            assertThat(log.records()).extracting(LogRecord::getLevel).containsExactly(Level.WARNING);
        }
        assertThat(store.decision(A, Permission.CAMERA)).contains(Decision.ALLOWED);
    }

    /**
     * Review round 1's item 7: Clear All answers its write too, so Clear Website Data fails when the rows' deletion
     * fails; the cache is empty all the same, and the failure is told once.
     */
    @Test void clearAllAnswersItsWriteWhichFailsWhenTheDeleteFails() throws Exception {
        BrowserDatabase database = memory();
        PermissionStore store = store(database);
        done(store.set(A, Permission.CAMERA, Decision.ALLOWED));
        done(database.call(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TEMP TRIGGER refused BEFORE DELETE ON permissions BEGIN SELECT RAISE(ABORT, 'refused'); END");
            }
            return null;
        }));
        try (var log = new CapturedLog(PermissionStore.class.getName())) {
            PermissionStore.Cleared cleared = store.clearAll();
            assertThat(cleared.sites()).containsExactly(A);
            assertThatThrownBy(() -> done(cleared.written())).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(SQLException.class);
            settle();
            assertThat(store.all()).isEmpty();
            assertThat(unsaved).singleElement().isInstanceOf(SQLException.class);
            assertThat(log.records()).extracting(LogRecord::getLevel).containsExactly(Level.WARNING);
        }
    }

    /** Parks {@code database}'s thread until the answer is run, so the work given meanwhile queues behind it. */
    private static Runnable park(BrowserDatabase database) throws Exception {
        var parked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        database.call(connection -> {
            parked.countDown();
            try {
                if (!release.await(30, TimeUnit.SECONDS)) throw new SQLException("never released");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new SQLException("interrupted", interrupted);
            }
            return null;
        });
        assertThat(parked.await(10, TimeUnit.SECONDS)).as("the database's thread parked").isTrue();
        return release::countDown;
    }

    /**
     * Changes made after the rows were asked for and before they arrive stand: the rows read before a revoke and a set
     * landed are read with those changes made again over them.
     */
    @Test void aChangeMadeWhileTheRowsLoadStandsOnceTheyHaveLoaded() throws Exception {
        BrowserDatabase database = memory();
        done(store(database).set(A, Permission.CAMERA, Decision.ALLOWED));
        PermissionStore store = store(database);
        Runnable release = park(database);
        CompletableFuture<Void> loaded = store.load();
        assertThat(store.revoke(A, Permission.CAMERA)).as("not loaded yet, so not known").isEmpty();
        CompletableFuture<Void> set = store.set(B, Permission.MICROPHONE, Decision.BLOCKED);
        release.run();
        done(loaded);
        done(set);
        assertThat(store.all()).extracting(Grant::origin, Grant::permission).containsExactly(tuple(B, Permission.MICROPHONE));
        assertThat(reread(database).all()).extracting(Grant::origin).containsExactly(B);
    }

    @Test void listenersAreToldOnTheUiThreadAfterEachChangeThatChangedSomething() throws Exception {
        PermissionStore store = store(memory());
        var told = new AtomicInteger();
        var onUi = new CopyOnWriteArrayList<Boolean>();
        var listening = store.onChanged(() -> {
            onUi.add(EventQueue.isDispatchThread());
            told.incrementAndGet();
        });
        done(store.set(A, Permission.CAMERA, Decision.ALLOWED));
        settle();
        assertThat(told).hasValue(1);
        store.revoke(A, Permission.MICROPHONE);
        store.clearSite(B);
        settle();
        assertThat(told).as("nothing changed").hasValue(1);
        store.revoke(A, Permission.CAMERA);
        settle();
        assertThat(told).hasValue(2);
        listening.close();
        store.clearAll();
        done(store.set(A, Permission.CAMERA, Decision.ALLOWED));
        settle();
        assertThat(told).hasValue(2);
        assertThat(onUi).containsOnly(true);
    }

    /** Plan 6's F33: a write the database refuses because it has closed, as a /dev/null group's has, is dropped and logged. */
    @Test void aWriteToAClosedDatabaseIsDroppedAtDebugAndTheCacheKeepsIt() throws Exception {
        BrowserDatabase database = memory();
        PermissionStore store = store(database);
        database.close();
        Logger logger = Logger.getLogger(PermissionStore.class.getName());
        Level level = logger.getLevel();
        logger.setLevel(Level.ALL);
        try (var log = new CapturedLog(PermissionStore.class.getName())) {
            CompletableFuture<Void> written = store.set(A, Permission.CAMERA, Decision.ALLOWED);
            assertThatThrownBy(() -> done(written)).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(DatabaseClosedException.class);
            store.revoke(B, Permission.CAMERA);
            settle();
            assertThat(store.decision(A, Permission.CAMERA)).contains(Decision.ALLOWED);
            assertThat(unsaved).as("no error for a closed store").isEmpty();
            assertThat(log.records()).isNotEmpty().allSatisfy(record -> assertThat(record.getLevel()).isEqualTo(Level.FINE));
        } finally {
            logger.setLevel(level);
        }
    }

    /** A write SQLite refuses is logged and told once, on the UI thread, and the cache keeps the new value. */
    @Test void aFailedWriteIsLoggedAndToldOnceAndTheCacheKeepsTheNewValue() throws Exception {
        BrowserDatabase database = memory();
        PermissionStore store = store(database);
        done(database.call(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TEMP TRIGGER refused BEFORE INSERT ON permissions BEGIN SELECT RAISE(ABORT, 'refused'); END");
            }
            return null;
        }));
        try (var log = new CapturedLog(PermissionStore.class.getName())) {
            CompletableFuture<Void> written = store.set(A, Permission.NOTIFICATIONS, Decision.BLOCKED);
            assertThatThrownBy(() -> done(written)).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(SQLException.class);
            settle();
            assertThat(store.decision(A, Permission.NOTIFICATIONS)).contains(Decision.BLOCKED);
            assertThat(unsaved).singleElement().isInstanceOf(SQLException.class);
            assertThat(unsavedOnUi).containsExactly(true);
            assertThat(log.records()).extracting(LogRecord::getLevel, LogRecord::getMessage)
                .containsExactly(tuple(Level.WARNING, "Site permissions could not be saved"));
        }
        assertThat(PermissionStore.UNSAVED).isEqualTo("Site permissions could not be saved");
    }

    @Test void aDatabaseThatCannotLoadFailsTheLoadAndKeepsWhatWasSetSince() throws Exception {
        BrowserDatabase database = memory();
        PermissionStore store = store(database);
        done(store.set(A, Permission.CAMERA, Decision.ALLOWED));
        database.close();
        try (var log = new CapturedLog(PermissionStore.class.getName())) {
            assertThatThrownBy(() -> done(store.load())).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(IllegalStateException.class);
            assertThat(log.records()).as("a closed store's, for debugging only").noneMatch(record -> record.getLevel().intValue() > Level.FINE.intValue());
        }
        assertThat(store.decision(A, Permission.CAMERA)).contains(Decision.ALLOWED);
    }

    /** The table's names, as SQLite lists them. */
    private static Set<String> tables(java.sql.Connection connection) throws SQLException {
        var names = new java.util.TreeSet<String>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'")) {
            while (rows.next()) names.add(rows.getString(1));
        }
        return names;
    }

    /** A new store over the same database, its rows loaded. */
    private PermissionStore reread(BrowserDatabase database) throws Exception {
        PermissionStore again = store(database);
        done(again.load());
        return again;
    }

    private PermissionStore store(BrowserDatabase database) {
        return new PermissionStore(database, EventQueue::invokeLater, failure -> {
            unsavedOnUi.add(EventQueue.isDispatchThread());
            unsaved.add(failure);
        }, clock::get);
    }

    private BrowserDatabase memory() {
        BrowserDatabase database = BrowserDatabase.memory();
        databases.add(database);
        return database;
    }

    private BrowserDatabase file() {
        BrowserDatabase database = BrowserDatabase.file(new DataFolder(folder), cause -> { throw new AssertionError("unusable", cause); });
        databases.add(database);
        return database;
    }

    /** Waits until the UI thread has run what it was handed. */
    private static void settle() throws Exception {
        EventQueue.invokeAndWait(() -> { });
    }

    private static <T> T done(CompletableFuture<T> future) throws Exception {
        return future.get(10, TimeUnit.SECONDS);
    }
}
