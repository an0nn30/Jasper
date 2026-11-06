package dev.jasper.browser.store;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.function.Predicate;

/**
 * Databases a test needs as they would be found: a file as plan 5 left it, before plan 6's {@code permissions} table,
 * and a database in memory whose connection throws an unchecked exception for some statements, as a driver's bug would.
 */
public final class DatabaseFixtures {
    /** Plan 5's schema, as {@code BrowserDatabase.SCHEMA} read at {@code ef3347601}. */
    private static final List<String> PLAN_5 = List.of(
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

    private DatabaseFixtures() { }

    /**
     * Writes {@code browser.sqlite} in {@code folder} as plan 5 made it, schema version 1 in a write-ahead log, with one
     * history entry, {@code url}.
     *
     * @param folder the data folder
     * @param url the history entry's address
     * @throws Exception if SQLite cannot write it
     */
    public static void plan5(Path folder, String url) throws Exception {
        SqliteNatives.extractInto(SqliteNatives.folder());
        try (Connection connection = SqliteOpener.open("jdbc:sqlite:" + folder.resolve(DataFolder.DATABASE).toAbsolutePath())) {
            Sql.execute(connection, "PRAGMA journal_mode = WAL");
            for (String statement : PLAN_5) Sql.execute(connection, statement);
            Sql.execute(connection, "PRAGMA user_version = 1");
            try (var insert = connection.prepareStatement("INSERT INTO history (url, visited_at, reached) VALUES (?, ?, ?)")) {
                insert.setString(1, url);
                insert.setLong(2, 1_000);
                insert.setString(3, Reached.LINK.stored());
                insert.executeUpdate();
            }
        }
    }

    /**
     * Writes {@code browser.sqlite} in {@code folder} as a file that is not a database, so a database asked for it runs in
     * memory instead.
     *
     * @param folder the data folder
     * @throws Exception if the file cannot be written
     */
    public static void notADatabase(Path folder) throws Exception {
        byte[] noise = new byte[8_192];
        new java.util.Random(7).nextBytes(noise);
        java.nio.file.Files.write(folder.resolve(DataFolder.DATABASE), noise);
    }

    /**
     * A database in memory, its schema made as usual, whose connection throws {@code IllegalStateException} for each
     * statement {@code refused} names, as it is prepared; every other call goes to SQLite.
     *
     * @param refused given a statement's SQL, whether its preparation throws
     * @return the database, not yet opened
     */
    public static BrowserDatabase throwingFor(Predicate<String> refused) {
        return BrowserDatabase.memory(url -> {
            Connection real = SqliteOpener.open(url);
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("prepareStatement") && args != null && args[0] instanceof String sql && refused.test(sql))
                        throw new IllegalStateException("The driver failed: " + sql);
                    try {
                        return method.invoke(real, args);
                    } catch (InvocationTargetException thrown) {
                        throw thrown.getCause();
                    }
                });
        });
    }
}
