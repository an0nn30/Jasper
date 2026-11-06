package dev.jasper.browser.store;

import dev.jasper.sdk.Subscription;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Spec section 10's bookmarks: one for each address, with its title, when it was added and its tags. Each method answers
 * through a future, on the database's thread.
 */
public final class BookmarkStore {
    /** The longest tag kept. */
    static final int MAX_TAG = 64;
    /** The most tags a bookmark has. */
    static final int MAX_TAGS = 100;
    /** What parts a bookmark's tags in one column: a control character, which no tag has. */
    private static final String TAG_SEPARATOR = String.valueOf((char) 0x1F);
    /** A bookmark's tags in one column, in the order given. */
    private static final String TAGS = "(SELECT group_concat(t.tag, char(31) ORDER BY t.rowid) FROM bookmark_tags t WHERE t.bookmark = b.id)";
    private static final String BOOKMARK = "SELECT b.id, b.url, b.title, b.added_at, " + TAGS + " FROM bookmarks b";

    /** The orders of the bookmarks page. */
    public enum Sort {
        /** Newest first. */
        DATE_ADDED,
        /** By title, ignoring the case of ASCII letters, and an untitled bookmark by its address. */
        TITLE,
        /** By address, ignoring the case of ASCII letters. */
        URL
    }

    /**
     * A bookmark.
     *
     * @param id its id
     * @param url its address
     * @param title its title; may be empty
     * @param addedAt when it was added, in milliseconds since the epoch
     * @param tags its tags, in the order they were given
     */
    public record Bookmark(long id, String url, String title, long addedAt, List<String> tags) {
        /** Copies the tags. */
        public Bookmark {
            tags = List.copyOf(tags);
        }
    }

    /**
     * A page of bookmarks.
     *
     * @param bookmarks the bookmarks
     * @param more whether more follow
     */
    public record BookmarkPage(List<Bookmark> bookmarks, boolean more) {
        /** Copies the bookmarks. */
        public BookmarkPage {
            bookmarks = List.copyOf(bookmarks);
        }
    }

    private final BrowserDatabase database;
    private final Changes changes = new Changes();

    /**
     * Bookmarks in {@code database}.
     *
     * @param database the database
     */
    public BookmarkStore(BrowserDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    /**
     * Bookmarks an address.
     *
     * @param address the page's address
     * @param title its title, cut to 1,024 characters as history's are
     * @param when when it is added, in milliseconds since the epoch
     * @return the new bookmark, or the address's bookmark when it has one already; failed with an
     *     {@link IllegalArgumentException} for an address {@link Addresses#storable} does not keep
     */
    public CompletableFuture<Bookmark> add(String address, String title, long when) {
        String kept = HistoryStore.cut(title);
        Optional<String> stored = Addresses.storable(address);
        if (stored.isEmpty()) return CompletableFuture.failedFuture(new IllegalArgumentException("Jasper does not bookmark this address"));
        return database.call(connection -> {
            int added;
            try (var insert = connection.prepareStatement("INSERT INTO bookmarks (url, title, added_at) VALUES (?, ?, ?) ON CONFLICT (url) DO NOTHING")) {
                insert.setString(1, stored.get());
                insert.setString(2, kept);
                insert.setLong(3, when);
                added = insert.executeUpdate();
            }
            Bookmark bookmark;
            try (var select = connection.prepareStatement(BOOKMARK + " WHERE b.url = ?")) {
                select.setString(1, stored.get());
                bookmark = Sql.single(select, BookmarkStore::bookmark).orElseThrow();
            }
            if (added > 0) changes.tell();
            return bookmark;
        });
    }

    /**
     * Removes an address's bookmark.
     *
     * @param address the page's address
     * @return whether it had one
     */
    public CompletableFuture<Boolean> remove(String address) {
        Optional<String> stored = Addresses.storable(address);
        if (stored.isEmpty()) return CompletableFuture.completedFuture(false);
        return written("DELETE FROM bookmarks WHERE url = ?", stored.get());
    }

    /**
     * Deletes a bookmark.
     *
     * @param id its id
     * @return whether there was one
     */
    public CompletableFuture<Boolean> delete(long id) {
        return written("DELETE FROM bookmarks WHERE id = ?", id);
    }

    /**
     * Deletes every bookmark.
     *
     * @return completes once they are gone
     */
    public CompletableFuture<Void> clear() {
        return database.call(connection -> {
            Sql.execute(connection, "DELETE FROM bookmarks");
            changes.tell();
            return null;
        });
    }

    /**
     * Gives a bookmark these tags in place of its own, those {@link #keptTags} keeps.
     *
     * @param id the bookmark's id
     * @param tags its tags
     * @return the bookmark; failed with a {@link NoSuchElementException} when it is gone
     */
    public CompletableFuture<Bookmark> tags(long id, List<String> tags) {
        List<String> kept = keptTags(tags);
        return database.call(connection -> {
            Bookmark bookmark = Sql.transaction(connection, c -> {
                try (var delete = c.prepareStatement("DELETE FROM bookmark_tags WHERE bookmark = ?")) {
                    delete.setLong(1, id);
                    delete.executeUpdate();
                }
                try (var insert = c.prepareStatement("INSERT INTO bookmark_tags (bookmark, tag) SELECT id, ? FROM bookmarks WHERE id = ?")) {
                    for (String tag : kept) {
                        insert.setString(1, tag);
                        insert.setLong(2, id);
                        insert.executeUpdate();
                    }
                }
                return bookmark(c, id).orElseThrow(() -> new NoSuchElementException("The bookmark is gone"));
            });
            changes.tell();
            return bookmark;
        });
    }

    /**
     * Every bookmark, newest first.
     *
     * @return the bookmarks
     */
    public CompletableFuture<List<Bookmark>> all() {
        return database.call(connection -> {
            try (var select = connection.prepareStatement(BOOKMARK + order(Sort.DATE_ADDED))) {
                return Sql.all(select, BookmarkStore::bookmark);
            }
        });
    }

    /**
     * Every tag a bookmark has, once each, as first written, and sorted, ignoring the case of ASCII letters.
     *
     * @return the tags
     */
    public CompletableFuture<List<String>> allTags() {
        return database.call(connection -> {
            try (var select = connection.prepareStatement("SELECT tag FROM bookmark_tags ORDER BY rowid")) {
                var tags = new TreeMap<String, String>();
                for (String tag : Sql.all(select, rows -> rows.getString(1))) tags.putIfAbsent(folded(tag), tag);
                return List.copyOf(tags.values());
            }
        });
    }

    /**
     * A page of bookmarks. The trimmed query matches anywhere in a bookmark's title, address or one of its tags, ignoring
     * the case of ASCII letters, and its {@code %}, {@code _} and {@code \} match only themselves. Only the query's first
     * 256 characters count, and of those its first 8 words, as typed ({@code Sql.searched}). A bookmark must have every
     * tag chosen.
     *
     * @param query what is searched for; blank for every bookmark
     * @param sort their order
     * @param tags the tags chosen, matched ignoring case; empty for every bookmark
     * @param offset how many of them come before the page, at least 0
     * @param limit the most the page holds, at least 1
     * @return the page
     */
    public CompletableFuture<BookmarkPage> list(String query, Sort sort, Set<String> tags, int offset, int limit) {
        Objects.requireNonNull(sort, "sort");
        List<String> chosen = List.copyOf(tags);
        if (offset < 0 || limit < 1) return CompletableFuture.failedFuture(new IllegalArgumentException("A page starts at 0 or later and holds at least one bookmark"));
        String searched = Sql.searched(query);
        var sql = new StringBuilder(BOOKMARK).append(" WHERE 1");
        if (!searched.isEmpty())
            sql.append(" AND (b.title LIKE ?1 ESCAPE '\\' OR b.url LIKE ?1 ESCAPE '\\'")
                .append(" OR EXISTS (SELECT 1 FROM bookmark_tags t WHERE t.bookmark = b.id AND t.tag LIKE ?1 ESCAPE '\\'))");
        sql.append(" AND EXISTS (SELECT 1 FROM bookmark_tags t WHERE t.bookmark = b.id AND t.tag = ?)".repeat(chosen.size()));
        sql.append(order(sort)).append(" LIMIT ? OFFSET ?");
        return database.call(connection -> {
            try (var select = connection.prepareStatement(sql.toString())) {
                int at = 1;
                if (!searched.isEmpty()) select.setString(at++, Sql.containing(searched));
                for (String tag : chosen) select.setString(at++, tag);
                select.setLong(at++, limit + 1L);
                select.setInt(at, offset);
                List<Bookmark> found = Sql.all(select, BookmarkStore::bookmark);
                return found.size() <= limit ? new BookmarkPage(found, false) : new BookmarkPage(found.subList(0, limit), true);
            }
        });
    }

    /**
     * Tells {@code listener}, on the database's thread, after each write that changed a bookmark.
     *
     * @param listener the listener
     * @return ends the listening
     */
    public Subscription onChanged(Runnable listener) {
        return changes.add(listener);
    }

    /**
     * The tag rule: each tag is trimmed, and kept only when it is nonblank, at most 64 characters and free of control
     * characters; one that matches an earlier one ignoring the case of ASCII letters, as SQLite's {@code NOCASE} does, is
     * dropped, so "Work" and "work" are one tag and "\u00C4pfel" and "\u00E4pfel" two; and the first 100 are kept, in the
     * order given. The SDK's token field keeps its tokens by the same rule.
     *
     * @param tags the tags given
     * @return those kept
     */
    public static List<String> keptTags(List<String> tags) {
        var seen = new HashSet<String>();
        var kept = new ArrayList<String>();
        for (String given : tags) {
            String tag = given.strip();
            if (kept.size() < MAX_TAGS && !tag.isEmpty() && tag.length() <= MAX_TAG && tag.chars().noneMatch(Character::isISOControl)
                    && seen.add(folded(tag)))
                kept.add(tag);
        }
        return List.copyOf(kept);
    }

    /** {@code tag} with its ASCII letters in lower case, as SQLite's {@code NOCASE} compares it. */
    private static String folded(String tag) {
        char[] chars = tag.toCharArray();
        for (int i = 0; i < chars.length; i++)
            if (chars[i] >= 'A' && chars[i] <= 'Z') chars[i] = (char) (chars[i] + ('a' - 'A'));
        return new String(chars);
    }

    /** Runs a delete of at most one bookmark, and tells the listeners when it deleted one. */
    private CompletableFuture<Boolean> written(String sql, Object key) {
        return database.call(connection -> {
            boolean changed;
            try (var statement = connection.prepareStatement(sql)) {
                statement.setObject(1, key);
                changed = statement.executeUpdate() > 0;
            }
            if (changed) changes.tell();
            return changed;
        });
    }

    private static String order(Sort sort) {
        return switch (sort) {
            case DATE_ADDED -> " ORDER BY b.added_at DESC, b.id DESC";
            case TITLE -> " ORDER BY CASE WHEN b.title = '' THEN b.url ELSE b.title END COLLATE NOCASE, b.id";
            case URL -> " ORDER BY b.url COLLATE NOCASE, b.id";
        };
    }

    private static Optional<Bookmark> bookmark(Connection connection, long id) throws SQLException {
        try (var select = connection.prepareStatement(BOOKMARK + " WHERE b.id = ?")) {
            select.setLong(1, id);
            return Sql.single(select, BookmarkStore::bookmark);
        }
    }

    private static Bookmark bookmark(ResultSet rows) throws SQLException {
        String tags = rows.getString(5);
        return new Bookmark(rows.getLong(1), rows.getString(2), rows.getString(3), rows.getLong(4),
            tags == null ? List.of() : Stream.of(tags.split(TAG_SEPARATOR)).toList());
    }
}
