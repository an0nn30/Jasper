package dev.jasper.browser.store;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** The stores' statements: transactions, search patterns and reading results. */
final class Sql {
    /** The most characters of a search that count. */
    static final int SEARCH_LENGTH = 256;
    /** The most words of a search that count, of those characters. */
    static final int SEARCH_WORDS = 8;
    // A word of a search: what white space separates, as the stores split a search into its words.
    private static final Pattern WORD = Pattern.compile("\\S+");

    private Sql() { }

    /**
     * Reads one row of a result.
     *
     * @param <T> what the row gives
     */
    @FunctionalInterface interface Row<T> {
        T read(ResultSet rows) throws SQLException;
    }

    /**
     * Runs {@code work} in one transaction on {@code connection}: committed if it returns, rolled back if it throws.
     *
     * @param connection a connection in auto-commit mode, as {@link BrowserDatabase.Work} is given it
     * @param work the work
     * @param <T> what it gives
     * @return what it gave
     * @throws SQLException if the work or the commit fails
     */
    static <T> T transaction(Connection connection, BrowserDatabase.Work<T> work) throws SQLException {
        connection.setAutoCommit(false);
        try {
            T result = work.run(connection);
            connection.commit();
            return result;
        } catch (SQLException | RuntimeException | Error failure) {
            try {
                connection.rollback();
            } catch (SQLException rollback) {
                failure.addSuppressed(rollback);
            }
            throw failure;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /**
     * A {@code LIKE} pattern, for {@code ESCAPE '\'}, that finds {@code text} anywhere: its {@code %}, {@code _} and
     * {@code \} match only themselves. SQLite's {@code LIKE} ignores the case of ASCII letters only.
     */
    static String containing(String text) {
        return "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    /**
     * What a search of history or bookmarks counts of {@code query} (plan 5's H16), so that a pasted text neither fails
     * the search nor holds the database: its first {@value #SEARCH_LENGTH} characters, or one fewer where a
     * character's two halves would part, then the first {@value #SEARCH_WORDS} words of those, as typed, with the white
     * space between them and none at either end. Words past the eighth are ignored.
     *
     * @param query a search as typed
     * @return what of it counts; empty for one without words
     */
    static String searched(String query) {
        String cut = query.length() <= SEARCH_LENGTH ? query
            : query.substring(0, Character.isHighSurrogate(query.charAt(SEARCH_LENGTH - 1)) ? SEARCH_LENGTH - 1 : SEARCH_LENGTH);
        Matcher word = WORD.matcher(cut);
        int end = 0;
        for (int words = 0; words < SEARCH_WORDS && word.find(); words++) end = word.end();
        return cut.substring(0, end).strip();
    }

    /** Runs a statement that gives no rows. */
    static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    /** The first row {@code statement} gives, read; empty when it gives none. */
    static <T> Optional<T> single(PreparedStatement statement, Row<T> row) throws SQLException {
        try (var rows = statement.executeQuery()) {
            return rows.next() ? Optional.of(row.read(rows)) : Optional.empty();
        }
    }

    /** Every row {@code statement} gives, read, in order. */
    static <T> List<T> all(PreparedStatement statement, Row<T> row) throws SQLException {
        var read = Stream.<T>builder();
        try (var rows = statement.executeQuery()) {
            while (rows.next()) read.add(row.read(rows));
        }
        return read.build().toList();
    }
}
