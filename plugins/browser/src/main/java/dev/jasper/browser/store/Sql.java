package dev.jasper.browser.store;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** The stores' statements: transactions, search patterns and reading results. */
final class Sql {
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
