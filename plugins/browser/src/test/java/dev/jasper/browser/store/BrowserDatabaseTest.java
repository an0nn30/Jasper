package dev.jasper.browser.store;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec section 12's one database: opened on its own thread at the first call, and run in memory for the rest of the run,
 * with the file left as it was and one report, when the file cannot be used. Review Focus 3.
 */
class BrowserDatabaseTest {
    @TempDir Path data;
    private final List<BrowserDatabase> opened = new CopyOnWriteArrayList<>();
    private final List<Throwable> unavailable = new CopyOnWriteArrayList<>();
    private final List<String> unavailableOn = new CopyOnWriteArrayList<>();

    /** Every database closes before the folder is deleted: Windows cannot delete an open file. */
    @AfterEach void closeEveryDatabase() throws Exception {
        for (BrowserDatabase database : opened) {
            database.close();
            database.closed().get(10, TimeUnit.SECONDS);
        }
    }

    @Test void aNewFileGetsTheFourTablesAndVersionOne() throws Exception {
        Path file = data.resolve("data").resolve("browser.sqlite");
        BrowserDatabase database = file(file);
        assertThat(done(database.<List<String>>call(connection -> strings(connection,
            "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name"))))
            .containsExactly("bookmark_tags", "bookmarks", "history", "visits");
        assertThat(done(database.<List<String>>call(connection -> strings(connection, "SELECT name FROM sqlite_master WHERE type = 'index' AND sql IS NOT NULL"))))
            .containsExactly("history_by_time");
        assertThat(done(database.<List<String>>call(connection -> List.of(text(connection, "PRAGMA user_version"), text(connection, "PRAGMA journal_mode"),
            text(connection, "PRAGMA synchronous"), text(connection, "PRAGMA foreign_keys"), text(connection, "PRAGMA busy_timeout")))))
            .containsExactly("1", "wal", "1", "1", "2000");
        assertThat(file).isRegularFile();
        assertThat(unavailable).isEmpty();
    }

    @Test void whatIsStoredIsThereWhenTheFileOpensAgain() throws Exception {
        Path file = data.resolve("browser.sqlite");
        BrowserDatabase first = file(file);
        long entry = done(new HistoryStore(first).record("https://a.example/", Reached.LINK, 1)).orElseThrow().entry();
        first.close();
        first.closed().get(10, TimeUnit.SECONDS);
        assertThat(sidecars(file)).as("a clean close leaves no write-ahead log").isEmpty();
        BrowserDatabase again = file(file);
        assertThat(done(new HistoryStore(again).list("", Optional.empty(), 10)).entries()).extracting(HistoryStore.HistoryEntry::id)
            .containsExactly(entry);
        assertThat(unavailable).isEmpty();
    }

    /** Spec section 19: making the database does nothing; its thread, and the file, come with the first call. */
    @Test void theStoreThreadAndTheFileComeOnlyWithTheFirstCall() throws Exception {
        Path file = data.resolve("data").resolve("browser.sqlite");
        BrowserDatabase database = file(file);
        assertThat(database.started()).isFalse();
        assertThat(file.getParent()).doesNotExist();
        Thread thread = done(database.call(connection -> Thread.currentThread()));
        assertThat(database.started()).isTrue();
        assertThat(thread.getName()).isEqualTo("jasper-browser-store");
        assertThat(thread.isDaemon()).as("daemon").isTrue();
        assertThat(thread.isVirtual()).as("a platform thread").isFalse();
        assertThat(done(database.<Thread>call(connection -> Thread.currentThread()))).as("one thread").isSameAs(thread);
    }

    @Test void aFileOfRandomBytesIsLeftAsItWas() throws Exception {
        Path file = data.resolve("browser.sqlite");
        byte[] noise = new byte[8_192];
        new Random(7).nextBytes(noise);
        Files.write(file, noise);
        runsInMemory(file, file, SQLException.class, "SQLITE_NOTADB");
    }

    @Test void aDatabaseFromANewerJasperIsLeftAsItWas() throws Exception {
        Path file = data.resolve("browser.sqlite");
        try (Connection other = other(file)) {
            execute(other, "CREATE TABLE history (id INTEGER PRIMARY KEY, url TEXT NOT NULL)");
            execute(other, "PRAGMA user_version = 2");
        }
        runsInMemory(file, file, SQLException.class, "from a later Jasper (schema version 2)");
    }

    @Test void aReadOnlyFileIsLeftAsItWas() throws Exception {
        Path file = data.resolve("browser.sqlite");
        written(file);
        assertThat(file.toFile().setReadOnly()).isTrue();
        try {
            // Refused before SQLite opens it: SQLite would open it for reading only and leave a write-ahead log beside it.
            runsInMemory(file, file, AccessDeniedException.class, "The file cannot be written");
        } finally {
            assertThat(file.toFile().setWritable(true)).isTrue();
        }
    }

    /** A page of a real database damaged past its header: SQLite opens it, and its quick check finds the damage. */
    @Test void aFileThatFailsTheQuickCheckIsLeftAsItWas() throws Exception {
        Path file = data.resolve("browser.sqlite");
        written(file);
        byte[] bytes = Files.readAllBytes(file);
        int pageSize = (bytes[16] & 0xFF) << 8 | bytes[17] & 0xFF;
        assertThat(bytes[pageSize]).as("page 2, the history table's, is a table leaf").isEqualTo((byte) 13);
        bytes[pageSize + 7] = 5; // the page's count of fragmented free bytes, which the quick check compares with the page
        Files.write(file, bytes);
        runsInMemory(file, file, SQLException.class, "failed SQLite's quick check");
    }

    /**
     * A connection that cannot keep a write-ahead log is refused. The seam hands the file's open an in-memory
     * connection, whose journal can never be WAL; a real file cannot be made to refuse without a VFS of its own.
     */
    @Test void aConnectionThatCannotKeepAWriteAheadLogRunsInMemory() throws Exception {
        Path file = data.resolve("browser.sqlite");
        BrowserDatabase database = BrowserDatabase.file(file, this::unavailable, url -> SqliteOpener.open("jdbc:sqlite::memory:"));
        opened.add(database);
        HistoryStore history = new HistoryStore(database);
        long entry = done(history.record("https://a.example/", Reached.LINK, 1)).orElseThrow().entry();
        assertThat(done(history.list("", Optional.empty(), 10)).entries()).extracting(HistoryStore.HistoryEntry::id).containsExactly(entry);
        assertThat(unavailable).singleElement().isInstanceOf(SQLException.class).extracting(Throwable::getMessage).asString()
            .contains("cannot keep a write-ahead log").contains("(memory)");
        assertThat(file).doesNotExist();
    }

    @Test void aDataPathWhoseFolderIsARegularFileRunsInMemory() throws Exception {
        Path blocker = data.resolve("data");
        Files.writeString(blocker, "not a folder");
        runsInMemory(blocker.resolve("browser.sqlite"), blocker, FileAlreadyExistsException.class, blocker.toString());
    }

    /** Windows ignores a folder's read-only attribute, so this case is POSIX's alone (F14). */
    @Test @DisabledOnOs(OS.WINDOWS) void aReadOnlyFolderLeavesTheFileAsItWas() throws Exception {
        Path folder = Files.createDirectory(data.resolve("data")), file = folder.resolve("browser.sqlite");
        written(file);
        Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString("r-x------"));
        try {
            runsInMemory(file, file, AccessDeniedException.class, "The folder cannot be written");
        } finally {
            Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString("rwx------"));
        }
    }

    /** Another Jasper, or any program, that holds the file past the 2-second busy timeout. */
    @Test void aFileAnotherConnectionHoldsPastTheBusyTimeoutIsLeftAsItWas() throws Exception {
        Path file = data.resolve("browser.sqlite");
        try (Connection other = other(file)) {
            execute(other, "CREATE TABLE elsewhere (x)");
            execute(other, "BEGIN EXCLUSIVE");
            try {
                runsInMemory(file, file, SQLException.class, "SQLITE_BUSY");
            } finally {
                execute(other, "ROLLBACK");
            }
        }
        assertThat(sidecars(file)).isEmpty();
    }

    /** F13: without SQLite's native library there is no database at all, yet every call answers. */
    @Test void whenSqliteCannotLoadEveryCallFailsAndTheFailureIsReportedOnce() throws Exception {
        Path file = data.resolve("browser.sqlite");
        BrowserDatabase database = BrowserDatabase.file(file, this::unavailable, url -> {
            throw new SQLException("Error opening connection", new UnsatisfiedLinkError("no sqlitejdbc in java.library.path"));
        });
        opened.add(database);
        for (int i = 0; i < 2; i++)
            assertThatThrownBy(() -> done(database.call(connection -> "never"))).isInstanceOf(ExecutionException.class)
                .cause().isInstanceOf(SQLException.class).rootCause().isInstanceOf(UnsatisfiedLinkError.class);
        assertThat(unavailable).singleElement().satisfies(cause -> assertThat(cause).rootCause().isInstanceOf(UnsatisfiedLinkError.class));
        assertThat(unavailableOn).containsExactly("jasper-browser-store");
        assertThat(file).doesNotExist();
        BrowserDatabase memory = BrowserDatabase.memory(url -> { throw new SQLException("Error opening connection"); });
        opened.add(memory);
        assertThatThrownBy(() -> done(memory.call(connection -> "never"))).isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(SQLException.class);
    }

    /** Review Focus 3: quitting with writes queued never hangs, and they all land. */
    @Test void closeReturnsAtOnceAndLetsEveryQueuedWriteLandBeforeTheDatabaseCloses() throws Exception {
        Path file = data.resolve("browser.sqlite");
        BrowserDatabase database = file(file);
        HistoryStore history = new HistoryStore(database);
        var hold = new CountDownLatch(1);
        CompletableFuture<Boolean> held = database.call(connection -> {
            try {
                return hold.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                throw new SQLException(interrupted);
            }
        });
        var writes = new CopyOnWriteArrayList<CompletableFuture<Optional<HistoryStore.Recorded>>>();
        for (int i = 0; i < 10; i++) writes.add(history.record("https://a.example/" + i, Reached.LINK, i));
        long start = System.nanoTime();
        database.close();
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).as("close() waits for nothing").isLessThan(1_000);
        assertThat(database.closed()).isNotDone();
        CompletableFuture<String> late = database.call(connection -> "never");
        assertThat(late).as("a call after close").isCompletedExceptionally();
        assertThatThrownBy(late::join).hasCauseInstanceOf(IllegalStateException.class);
        hold.countDown();
        database.closed().get(10, TimeUnit.SECONDS);
        assertThat(held).isCompletedWithValue(true);
        assertThat(writes).allSatisfy(write -> assertThat(write.getNow(Optional.empty())).isPresent());
        BrowserDatabase again = file(file);
        assertThat(done(new HistoryStore(again).list("", Optional.empty(), 50)).entries()).hasSize(10);
    }

    /** A folder for SQLite's native library that cannot be made is logged once, at INFO, and SQLite runs all the same. */
    @Test void aNativeFolderThatCannotBeMadeIsLoggedOnceAndSqliteRunsAllTheSame() throws Exception {
        var noFolder = new BrowserDatabase.Opener() {
            @Override public void extractNatives() throws IOException { throw new IOException("the folder cannot be made"); }
            @Override public Connection open(String url) throws SQLException { return SqliteOpener.open(url); }
        };
        try (var log = new CapturedLog(BrowserDatabase.class)) {
            BrowserDatabase database = BrowserDatabase.file(data.resolve("browser.sqlite"), this::unavailable, noFolder);
            opened.add(database);
            for (int i = 0; i < 2; i++) assertThat(done(database.<String>call(connection -> text(connection, "PRAGMA journal_mode")))).isEqualTo("wal");
            assertThat(log.records()).singleElement().satisfies(record -> {
                assertThat(record.getLevel()).isEqualTo(java.util.logging.Level.INFO);
                assertThat(record.getThrown()).hasMessage("the folder cannot be made");
            });
        }
        assertThat(unavailable).isEmpty();
    }

    /** H37: the first open makes the data folder its owner's alone, so no other user of the computer reads the history. */
    @Test @DisabledOnOs(OS.WINDOWS) void theFirstOpenMakesTheDataFolderItsOwnersAlone() throws Exception {
        Path folder = Files.createDirectory(data.resolve("data"));
        Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString("rwxr-xr-x"));
        BrowserDatabase database = BrowserDatabase.file(new DataFolder(folder), this::unavailable);
        opened.add(database);
        assertThat(permissions(folder)).as("made, not opened").isEqualTo("rwxr-xr-x");
        done(database.call(connection -> null));
        assertThat(permissions(folder)).isEqualTo("rwx------");
        assertThat(folder.resolve("browser.sqlite")).isRegularFile();
        assertThat(unavailable).isEmpty();
    }

    /**
     * H37: a file an earlier run left readable by others is its owner's alone before SQLite opens it, and the write-ahead
     * log and shared memory SQLite then makes beside it, which take the file's permissions, are too.
     */
    @Test @DisabledOnOs(OS.WINDOWS) void aFileOthersCouldReadIsItsOwnersAloneBeforeItOpens() throws Exception {
        Path file = data.resolve("browser.sqlite");
        written(file);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));
        BrowserDatabase database = file(file);
        assertThat(done(database.<String>call(connection -> text(connection, "PRAGMA journal_mode")))).isEqualTo("wal");
        assertThat(permissions(file)).isEqualTo("rw-------");
        for (String suffix : List.of("-wal", "-shm"))
            assertThat(permissions(file.resolveSibling(file.getFileName() + suffix))).as(suffix).isEqualTo("rw-------");
        assertThat(unavailable).isEmpty();
    }

    /** H37: a folder whose permissions cannot be set is logged once, as a warning, and the database opens all the same. */
    @Test void aDataFolderWhosePermissionsCannotBeSetIsLoggedOnceAndStillOpens() throws Exception {
        var folder = new DataFolder(data, path -> { throw new IOException("Operation not permitted"); });
        try (var log = new CapturedLog(DataFolder.class)) {
            BrowserDatabase database = BrowserDatabase.file(folder, this::unavailable);
            opened.add(database);
            assertThat(done(database.<String>call(connection -> text(connection, "PRAGMA journal_mode")))).isEqualTo("wal");
            folder.restrict(); // as Chromium's start would, later
            assertThat(log.records()).singleElement().satisfies(record -> {
                assertThat(record.getLevel()).isEqualTo(java.util.logging.Level.WARNING);
                assertThat(record.getThrown()).hasMessage("Operation not permitted");
            });
        }
        assertThat(data.resolve("browser.sqlite")).isRegularFile();
        assertThat(unavailable).isEmpty();
    }

    @Test void closingBeforeAnyCallStartsNoThreadAndClosesAtOnce() {
        BrowserDatabase database = file(data.resolve("browser.sqlite"));
        database.close();
        database.close();
        assertThat(database.closed()).isCompleted();
        assertThat(database.started()).isFalse();
        assertThat(database.call(connection -> "never")).isCompletedExceptionally();
        assertThat(data.resolve("browser.sqlite")).doesNotExist();
    }

    @Test void aFailedCallFailsItsFutureAndTheNextCallRuns() throws Exception {
        BrowserDatabase database = BrowserDatabase.memory();
        opened.add(database);
        assertThatThrownBy(() -> done(database.call(connection -> { throw new SQLException("a bad statement"); })))
            .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(SQLException.class);
        assertThatThrownBy(() -> done(database.call(connection -> { throw new StackOverflowError(); })))
            .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(StackOverflowError.class);
        assertThat(done(database.<String>call(connection -> "next"))).isEqualTo("next");
    }

    /**
     * The database runs in memory, {@code kept}'s bytes are unchanged, no journal or write-ahead log is left beside
     * {@code file}, and the failure, a {@code because} whose message holds {@code saying}, was reported once, on the
     * store's thread.
     */
    private void runsInMemory(Path file, Path kept, Class<? extends Throwable> because, String saying) throws Exception {
        byte[] before = Files.readAllBytes(kept);
        BrowserDatabase database = BrowserDatabase.file(file, this::unavailable);
        opened.add(database);
        HistoryStore history = new HistoryStore(database);
        long entry = done(history.record("https://a.example/", Reached.LINK, 1)).orElseThrow().entry();
        assertThat(done(history.list("", Optional.empty(), 10)).entries()).extracting(HistoryStore.HistoryEntry::id).containsExactly(entry);
        assertThat(done(database.<String>call(connection -> text(connection, "PRAGMA journal_mode")))).as("in memory").isEqualTo("memory");
        assertThat(done(database.<String>call(connection -> text(connection, "PRAGMA foreign_keys")))).isEqualTo("1");
        assertThat(kept).hasBinaryContent(before);
        assertThat(sidecars(file)).isEmpty();
        assertThat(unavailable).singleElement().isInstanceOf(because).extracting(Throwable::getMessage).asString().contains(saying);
        assertThat(unavailableOn).containsExactly("jasper-browser-store");
    }

    /** A database an earlier run wrote and closed. */
    private static void written(Path file) throws Exception {
        var failures = new CopyOnWriteArrayList<Throwable>();
        BrowserDatabase earlier = BrowserDatabase.file(file, failures::add);
        done(new BookmarkStore(earlier).add("https://a.example/", "A", 1));
        earlier.close();
        earlier.closed().get(10, TimeUnit.SECONDS);
        assertThat(failures).isEmpty();
        assertThat(sidecars(file)).isEmpty();
    }

    private void unavailable(Throwable cause) {
        unavailable.add(cause);
        unavailableOn.add(Thread.currentThread().getName());
    }

    private BrowserDatabase file(Path file) {
        BrowserDatabase database = BrowserDatabase.file(file, this::unavailable);
        opened.add(database);
        return database;
    }

    /** A connection of the test's own, as another program's. */
    private static Connection other(Path file) throws Exception {
        SqliteNatives.extractInto(SqliteNatives.folder());
        return SqliteOpener.open("jdbc:sqlite:" + file.toAbsolutePath());
    }

    private static String permissions(Path path) throws IOException {
        return PosixFilePermissions.toString(Files.getPosixFilePermissions(path));
    }

    private static List<String> sidecars(Path file) {
        return List.of("-wal", "-journal").stream().map(suffix -> file.resolveSibling(file.getFileName() + suffix))
            .filter(Files::exists).map(Path::toString).toList();
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String text(Connection connection, String sql) throws SQLException {
        return strings(connection, sql).getFirst();
    }

    private static List<String> strings(Connection connection, String sql) throws SQLException {
        var found = new CopyOnWriteArrayList<String>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            while (rows.next()) found.add(rows.getString(1));
        }
        return List.copyOf(found);
    }

    private static <T> T done(CompletableFuture<T> future) throws Exception {
        return future.get(10, TimeUnit.SECONDS);
    }
}
