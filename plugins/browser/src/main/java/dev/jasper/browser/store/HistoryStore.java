package dev.jasper.browser.store;

import dev.jasper.sdk.Subscription;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Spec section 10's history: an entry for each top-level page load, and a row for each host and path that counts the
 * visits and keeps the first and last. Nothing expires. Each method answers through a future, on the database's thread.
 */
public final class HistoryStore {
    /** The longest title kept. */
    static final int MAX_TITLE = 1_024;
    private static final String VISIT = "SELECT id, url, title, count, first_visit, last_visit FROM visits";

    /**
     * A load recorded.
     *
     * @param entry its entry's id
     * @param visit its row, with the visit counted
     */
    public record Recorded(long entry, Visit visit) { }

    /**
     * A host and path's row: how often it was visited and when.
     *
     * @param id the row's id
     * @param url the address of its latest visit
     * @param title the title of its latest address, empty until it has one
     * @param count its visits
     * @param firstVisit its first visit, in milliseconds since the epoch
     * @param lastVisit its latest visit, in milliseconds since the epoch
     */
    public record Visit(long id, String url, String title, int count, long firstVisit, long lastVisit) { }

    /**
     * One page load.
     *
     * @param id the entry's id
     * @param url the page's address
     * @param title the page's title, empty until it has one
     * @param visitedAt when the load started, in milliseconds since the epoch
     * @param reached how the page was reached
     */
    public record HistoryEntry(long id, String url, String title, long visitedAt, Reached reached) { }

    /**
     * Where a page of entries ends: the next page holds the entries older than this one.
     *
     * @param visitedAt the last entry's time
     * @param id the last entry's id, which orders entries of the same time
     */
    public record Cursor(long visitedAt, long id) { }

    /**
     * A page of entries, newest first.
     *
     * @param entries the entries
     * @param next where the next page starts; empty when no entry follows
     */
    public record HistoryPage(List<HistoryEntry> entries, Optional<Cursor> next) {
        /** Copies the entries. */
        public HistoryPage {
            entries = List.copyOf(entries);
            Objects.requireNonNull(next, "next");
        }
    }

    private final BrowserDatabase database;
    private final Changes changes = new Changes();

    /**
     * History in {@code database}.
     *
     * @param database the database
     */
    public HistoryStore(BrowserDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    /**
     * Records a page load: an entry, and a visit counted on its host and path's row, which takes the load's address.
     *
     * @param address the page's address
     * @param reached how it was reached
     * @param when when its load started, in milliseconds since the epoch
     * @return the entry and its row; empty for an address {@link Addresses#storable} does not keep
     */
    public CompletableFuture<Optional<Recorded>> record(String address, Reached reached, long when) {
        Objects.requireNonNull(reached, "reached");
        Optional<String> stored = Addresses.storable(address);
        if (stored.isEmpty()) return CompletableFuture.completedFuture(Optional.empty());
        String url = stored.get();
        Addresses.VisitKey key = Addresses.visitKey(url);
        return database.call(connection -> Optional.of(Sql.transaction(connection, c -> {
            long entry;
            try (var insert = c.prepareStatement("INSERT INTO history (url, visited_at, reached) VALUES (?, ?, ?) RETURNING id")) {
                insert.setString(1, url);
                insert.setLong(2, when);
                insert.setString(3, reached.stored());
                entry = Sql.single(insert, rows -> rows.getLong(1)).orElseThrow();
            }
            try (var upsert = c.prepareStatement("INSERT INTO visits (host, path, url, count, first_visit, last_visit) VALUES (?, ?, ?, 1, ?, ?)"
                    + " ON CONFLICT (host, path) DO UPDATE SET count = count + 1, last_visit = excluded.last_visit, url = excluded.url")) {
                upsert.setString(1, key.host());
                upsert.setString(2, key.path());
                upsert.setString(3, url);
                upsert.setLong(4, when);
                upsert.setLong(5, when);
                upsert.executeUpdate();
            }
            return new Recorded(entry, visit(c, key).orElseThrow());
        })));
    }

    /**
     * Gives an entry its page's title, and its row too while the row shows the entry's address: a late title for an
     * earlier address of the same path leaves the row's own.
     *
     * @param entry the entry's id
     * @param title the title, cut to 1,024 characters
     * @return the entry's row; empty when the entry or its row is gone
     */
    public CompletableFuture<Optional<Visit>> retitle(long entry, String title) {
        String kept = cut(title);
        return database.call(connection -> Sql.transaction(connection, c -> {
            Optional<String> url;
            try (var update = c.prepareStatement("UPDATE history SET title = ? WHERE id = ? RETURNING url")) {
                update.setString(1, kept);
                update.setLong(2, entry);
                url = Sql.single(update, rows -> rows.getString(1));
            }
            if (url.isEmpty()) return Optional.empty();
            Addresses.VisitKey key = Addresses.visitKey(url.get());
            try (var update = c.prepareStatement("UPDATE visits SET title = ? WHERE host = ? AND path = ? AND url = ?")) {
                update.setString(1, kept);
                update.setString(2, key.host());
                update.setString(3, key.path());
                update.setString(4, url.get());
                update.executeUpdate();
            }
            return visit(c, key);
        }));
    }

    /**
     * A page of entries, newest first. A page runs from a cursor, so entries recorded between two pages never shift the
     * second. The query's words, split at white space, each match anywhere in an entry's address or title, ignoring the
     * case of ASCII letters; an entry matches any of them, and their {@code %}, {@code _} and {@code \} match only
     * themselves.
     *
     * @param query the words searched for; blank for every entry
     * @param before the cursor the page starts after; empty for the newest entries
     * @param limit the most entries the page holds, at least 1
     * @return the page
     */
    public CompletableFuture<HistoryPage> list(String query, Optional<Cursor> before, int limit) {
        Objects.requireNonNull(before, "before");
        if (limit < 1) return CompletableFuture.failedFuture(new IllegalArgumentException("A page holds at least one entry"));
        List<String> words = Stream.of(query.strip().split("\\s+")).filter(word -> !word.isEmpty()).map(Sql::containing).toList();
        var sql = new StringBuilder("SELECT id, url, title, visited_at, reached FROM history WHERE 1");
        if (!words.isEmpty())
            sql.append(" AND (").append(String.join(" OR ", Collections.nCopies(words.size(), "url LIKE ? ESCAPE '\\' OR title LIKE ? ESCAPE '\\'"))).append(')');
        if (before.isPresent()) sql.append(" AND (visited_at, id) < (?, ?)");
        sql.append(" ORDER BY visited_at DESC, id DESC LIMIT ?");
        return database.call(connection -> {
            try (var select = connection.prepareStatement(sql.toString())) {
                int at = 1;
                for (String word : words) {
                    select.setString(at++, word);
                    select.setString(at++, word);
                }
                if (before.isPresent()) {
                    select.setLong(at++, before.get().visitedAt());
                    select.setLong(at++, before.get().id());
                }
                select.setLong(at, limit + 1L);
                List<HistoryEntry> entries = Sql.all(select, rows -> new HistoryEntry(rows.getLong(1), rows.getString(2), rows.getString(3),
                    rows.getLong(4), Reached.of(rows.getString(5))));
                if (entries.size() <= limit) return new HistoryPage(entries, Optional.empty());
                HistoryEntry last = entries.get(limit - 1);
                return new HistoryPage(entries.subList(0, limit), Optional.of(new Cursor(last.visitedAt(), last.id())));
            }
        });
    }

    /**
     * Deletes an entry and takes its visit off its row, which goes once it counts none: a site whose entries are all
     * deleted leaves Top Sites, the suggestions and the palette.
     *
     * @param id the entry's id
     * @return whether there was such an entry
     */
    public CompletableFuture<Boolean> delete(long id) {
        return database.call(connection -> {
            boolean deleted = Sql.transaction(connection, c -> {
                Optional<String> url;
                try (var delete = c.prepareStatement("DELETE FROM history WHERE id = ? RETURNING url")) {
                    delete.setLong(1, id);
                    url = Sql.single(delete, rows -> rows.getString(1));
                }
                if (url.isEmpty()) return false;
                Addresses.VisitKey key = Addresses.visitKey(url.get());
                for (String sql : List.of("UPDATE visits SET count = count - 1 WHERE host = ? AND path = ?",
                        "DELETE FROM visits WHERE host = ? AND path = ? AND count <= 0")) {
                    try (var statement = c.prepareStatement(sql)) {
                        statement.setString(1, key.host());
                        statement.setString(2, key.path());
                        statement.executeUpdate();
                    }
                }
                return true;
            });
            if (deleted) changes.tell();
            return deleted;
        });
    }

    /**
     * Deletes every entry and every row.
     *
     * @return completes once they are gone
     */
    public CompletableFuture<Void> clear() {
        return database.call(connection -> {
            Sql.transaction(connection, c -> {
                try (var statement = c.createStatement()) {
                    statement.executeUpdate("DELETE FROM history");
                    statement.executeUpdate("DELETE FROM visits");
                }
                return null;
            });
            changes.tell();
            return null;
        });
    }

    /**
     * The rows most recently visited first.
     *
     * @param limit the most rows given, at least 1
     * @return the rows
     */
    public CompletableFuture<List<Visit>> visits(int limit) {
        return visits(" ORDER BY last_visit DESC, id DESC", limit);
    }

    /**
     * The rows most visited first, and of those visited as often, the most recent first.
     *
     * @param limit the most rows given, at least 1
     * @return the rows
     */
    public CompletableFuture<List<Visit>> topSites(int limit) {
        return visits(" ORDER BY count DESC, last_visit DESC, id DESC", limit);
    }

    /**
     * Tells {@code listener}, on the database's thread, after each {@link #delete} that deleted an entry and each
     * {@link #clear}. A recorded or retitled load is told through its future.
     *
     * @param listener the listener
     * @return ends the listening
     */
    public Subscription onChanged(Runnable listener) {
        return changes.add(listener);
    }

    private CompletableFuture<List<Visit>> visits(String order, int limit) {
        if (limit < 1) return CompletableFuture.failedFuture(new IllegalArgumentException("At least one row is asked for"));
        return database.call(connection -> {
            try (var select = connection.prepareStatement(VISIT + order + " LIMIT ?")) {
                select.setInt(1, limit);
                return Sql.all(select, HistoryStore::visit);
            }
        });
    }

    private static Optional<Visit> visit(Connection connection, Addresses.VisitKey key) throws SQLException {
        try (var select = connection.prepareStatement(VISIT + " WHERE host = ? AND path = ?")) {
            select.setString(1, key.host());
            select.setString(2, key.path());
            return Sql.single(select, HistoryStore::visit);
        }
    }

    private static Visit visit(ResultSet rows) throws SQLException {
        return new Visit(rows.getLong(1), rows.getString(2), rows.getString(3), rows.getInt(4), rows.getLong(5), rows.getLong(6));
    }

    /** {@code title} cut to {@link #MAX_TITLE} characters, never between the halves of a surrogate pair. */
    static String cut(String title) {
        if (title.length() <= MAX_TITLE) return title;
        int end = Character.isHighSurrogate(title.charAt(MAX_TITLE - 1)) ? MAX_TITLE - 1 : MAX_TITLE;
        return title.substring(0, end);
    }
}
