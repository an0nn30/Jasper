package dev.jasper.browser.store;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The Browser's one database (spec section 12), for history and bookmarks, on a thread of its own. Making one does no
 * I/O and starts no thread: the first {@link #call} starts its thread, {@code jasper-browser-store}, which opens the
 * database and then runs every call in turn.
 *
 * <p>A file that cannot be used is left as it was, and the database runs in memory for the rest of the run instead:
 * one the plugin cannot read or write, that fails SQLite's quick check, that a later Jasper wrote ({@code user_version}
 * above 1), or that another program holds past the 2-second busy timeout. The file's checks come before anything is
 * written to it, so a file that fails one is never changed. When SQLite's native library does not load there is no
 * database at all, in a file or in memory, and every call fails. Either way the file's database is told why once.
 */
public final class BrowserDatabase implements AutoCloseable {
    /**
     * Work on the database's connection, on its thread.
     *
     * @param <T> what the work gives
     */
    @FunctionalInterface public interface Work<T> {
        /**
         * Does the work.
         *
         * @param connection the database's connection, in auto-commit mode
         * @return what the work gives
         * @throws SQLException if a statement fails
         */
        T run(Connection connection) throws SQLException;
    }

    /**
     * Readies SQLite's native library and opens connections to {@code jdbc:sqlite:} addresses; the test seam for a native
     * library that does not load, or a folder for it that cannot be made.
     */
    @FunctionalInterface interface Opener {
        /** Points sqlite-jdbc at the folder its native library is extracted into, before the first connection. */
        default void extractNatives() throws IOException {
            SqliteNatives.extractInto(SqliteNatives.folder());
        }

        Connection open(String url) throws SQLException;
    }

    private static final System.Logger LOG = System.getLogger(BrowserDatabase.class.getName());
    /** The schema, one statement each, every table {@code IF NOT EXISTS} so that a later plan adds its own the same way. */
    private static final List<String> SCHEMA = List.of(
        "CREATE TABLE IF NOT EXISTS history (id INTEGER PRIMARY KEY, url TEXT NOT NULL, title TEXT NOT NULL DEFAULT '',"
            + " visited_at INTEGER NOT NULL, reached TEXT NOT NULL)",
        "CREATE INDEX IF NOT EXISTS history_by_time ON history (visited_at DESC, id DESC)",
        "CREATE TABLE IF NOT EXISTS visits (id INTEGER PRIMARY KEY, host TEXT NOT NULL, path TEXT NOT NULL, url TEXT NOT NULL,"
            + " title TEXT NOT NULL DEFAULT '', count INTEGER NOT NULL, first_visit INTEGER NOT NULL, last_visit INTEGER NOT NULL,"
            + " UNIQUE (host, path))",
        "CREATE TABLE IF NOT EXISTS bookmarks (id INTEGER PRIMARY KEY, url TEXT NOT NULL UNIQUE, title TEXT NOT NULL DEFAULT '',"
            + " added_at INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS bookmark_tags (bookmark INTEGER NOT NULL REFERENCES bookmarks (id) ON DELETE CASCADE,"
            + " tag TEXT NOT NULL COLLATE NOCASE, PRIMARY KEY (bookmark, tag))");
    /** The schema's version; a file with a higher one is a later Jasper's and is left alone. */
    private static final int VERSION = 1;
    private static final String MEMORY = "jdbc:sqlite::memory:";

    private final Optional<Path> file;
    private final Consumer<Throwable> unavailable;
    private final Opener opener;
    private final Object lock = new Object();
    private final CompletableFuture<Void> closed = new CompletableFuture<>();
    private ExecutorService thread; // under lock; made by the first call
    private boolean closing; // under lock
    // The store thread's alone:
    private boolean opened;
    private Connection connection;
    private Throwable failure;

    private BrowserDatabase(Optional<Path> file, Consumer<Throwable> unavailable, Opener opener) {
        this.file = file;
        this.unavailable = Objects.requireNonNull(unavailable, "unavailable");
        this.opener = Objects.requireNonNull(opener, "opener");
    }

    /**
     * The database in {@code file}, opened at the first call.
     *
     * @param file the database's file; its folder is made if it is missing
     * @param unavailable told once, on the database's thread, why the file cannot be used, and then the database runs in
     *     memory; or why SQLite cannot run at all, and then every call fails
     * @return the database, not yet opened
     */
    public static BrowserDatabase file(Path file, Consumer<Throwable> unavailable) {
        return file(file, unavailable, BrowserDatabase::sqlite);
    }

    static BrowserDatabase file(Path file, Consumer<Throwable> unavailable, Opener opener) {
        return new BrowserDatabase(Optional.of(file), unavailable, opener);
    }

    /**
     * A database in memory, gone when it closes.
     *
     * @return the database, not yet opened
     */
    public static BrowserDatabase memory() {
        return memory(BrowserDatabase::sqlite);
    }

    static BrowserDatabase memory(Opener opener) {
        return new BrowserDatabase(Optional.empty(), failure -> { }, opener);
    }

    /** Opens a connection through {@link SqliteOpener}, which only this call, on the database's thread, loads. */
    private static Connection sqlite(String url) throws SQLException {
        return SqliteOpener.open(url);
    }

    /**
     * Runs {@code work} on the database's thread, after every call made before it.
     *
     * @param work the work
     * @param <T> what it gives
     * @return what it gave; failed with what it threw, with an {@link SQLException} when no database could open, and with
     *     an {@link IllegalStateException} once the database is closing
     */
    public <T> CompletableFuture<T> call(Work<T> work) {
        Objects.requireNonNull(work, "work");
        var result = new CompletableFuture<T>();
        synchronized (lock) {
            if (closing) return CompletableFuture.failedFuture(new IllegalStateException("The browser's database is closed"));
            if (thread == null) thread = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("jasper-browser-store").factory());
            thread.execute(() -> run(work, result));
        }
        return result;
    }

    /** Whether the first call has started the database's thread. */
    boolean started() {
        synchronized (lock) {
            return thread != null;
        }
    }

    /** Refuses later calls and returns at once; the calls made before still run, then the connection closes. */
    @Override public void close() {
        synchronized (lock) {
            if (closing) return;
            closing = true;
            if (thread == null) {
                closed.complete(null);
                return;
            }
            thread.execute(this::closeConnection);
            thread.shutdown();
        }
    }

    /**
     * Completes once {@link #close} was called, every call made before it has run and the connection has closed.
     *
     * @return the future
     */
    public CompletableFuture<Void> closed() {
        return closed;
    }

    private <T> void run(Work<T> work, CompletableFuture<T> result) {
        try {
            result.complete(work.run(connection()));
        } catch (Throwable thrown) { // An Error too must reach the caller, or its future never completes.
            result.completeExceptionally(thrown);
        }
    }

    private Connection connection() throws SQLException {
        if (!opened) {
            opened = true;
            open();
        }
        if (connection == null) throw new SQLException("The browser's database could not open", failure);
        return connection;
    }

    private void open() {
        try {
            opener.extractNatives();
        } catch (IOException | RuntimeException notMade) {
            // sqlite-jdbc then extracts its library into java.io.tmpdir itself: slower, and still working.
            LOG.log(System.Logger.Level.INFO, "SQLite's native library goes to the temporary folder: Jasper's own folder for it"
                + " could not be used", notMade);
        }
        if (file.isPresent()) {
            try {
                connection = fileConnection(file.get());
                return;
            } catch (Throwable unusable) { // As in run: an UnsatisfiedLinkError from the native library too.
                failure = unusable;
                tell(unusable);
            }
        }
        try {
            connection = prepared(opener.open(MEMORY), false);
        } catch (Throwable unusable) {
            if (failure == null) failure = unusable; else failure.addSuppressed(unusable);
        }
    }

    private Connection fileConnection(Path file) throws IOException, SQLException {
        Path absolute = file.toAbsolutePath(), folder = absolute.getParent();
        Files.createDirectories(folder);
        // SQLite opens a file it cannot write for reading only and fails only at the first write, so a folder or file
        // that cannot be written is refused here, before SQLite makes anything beside it.
        if (!Files.isWritable(folder)) throw new AccessDeniedException(folder.toString(), null, "The folder cannot be written");
        if (Files.exists(absolute) && !Files.isWritable(absolute)) throw new AccessDeniedException(absolute.toString(), null, "The file cannot be written");
        return prepared(opener.open("jdbc:sqlite:" + absolute), true);
    }

    /**
     * Readies a newly opened connection: its checks first, each of which writes nothing, then its settings and schema. A
     * connection that fails is closed.
     */
    private static Connection prepared(Connection connection, boolean file) throws SQLException {
        try {
            Sql.execute(connection, "PRAGMA busy_timeout = 2000");
            String check = text(connection, "PRAGMA quick_check");
            if (!"ok".equals(check)) throw new SQLException("The database failed SQLite's quick check: " + check);
            int version = Integer.parseInt(text(connection, "PRAGMA user_version"));
            if (version > VERSION) throw new SQLException("The database is from a later Jasper (schema version " + version + ")");
            Sql.execute(connection, "BEGIN IMMEDIATE"); // A write lock taken and given back: a file that cannot be written fails here.
            Sql.execute(connection, "ROLLBACK");
            if (file) {
                String journal = text(connection, "PRAGMA journal_mode = WAL");
                if (!"wal".equalsIgnoreCase(journal)) throw new SQLException("SQLite cannot keep a write-ahead log for the database (" + journal + ")");
            }
            Sql.execute(connection, "PRAGMA synchronous = NORMAL");
            Sql.execute(connection, "PRAGMA foreign_keys = ON");
            Sql.transaction(connection, schema -> {
                for (String statement : SCHEMA) Sql.execute(schema, statement);
                Sql.execute(schema, "PRAGMA user_version = " + VERSION);
                return null;
            });
            return connection;
        } catch (SQLException | RuntimeException | Error thrown) {
            try {
                connection.close();
            } catch (SQLException close) {
                thrown.addSuppressed(close);
            }
            throw thrown;
        }
    }

    private void tell(Throwable cause) {
        try {
            unavailable.accept(cause);
        } catch (RuntimeException thrown) {
            LOG.log(System.Logger.Level.WARNING, "The browser could not report that its database is unavailable", thrown);
        }
    }

    private void closeConnection() {
        try {
            if (connection != null) connection.close();
        } catch (SQLException thrown) {
            LOG.log(System.Logger.Level.WARNING, "The browser's database did not close cleanly", thrown);
        } finally {
            connection = null;
            closed.complete(null);
        }
    }

    /** The first column of the first row {@code sql} gives. */
    private static String text(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            if (!rows.next()) throw new SQLException("SQLite gave no answer to " + sql);
            return rows.getString(1);
        }
    }
}
