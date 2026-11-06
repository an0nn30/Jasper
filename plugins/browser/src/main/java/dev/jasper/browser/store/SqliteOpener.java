package dev.jasper.browser.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Properties;

/**
 * Opens sqlite-jdbc's connections. The one class of the plugin that names an {@code org.sqlite} type, so that only the
 * store's thread, which calls it, loads sqlite-jdbc: classes the plugin's start links, {@link BrowserDatabase} among
 * them, name none, and the verifier loads none for them.
 */
final class SqliteOpener {
    private SqliteOpener() { }

    /**
     * A connection to {@code url}, from a driver made for it. The driver sqlite-jdbc registers as it loads is deregistered
     * again, so {@link DriverManager} does not hold the plugin's class loader.
     *
     * @param url a {@code jdbc:sqlite:} address
     * @return the open connection
     * @throws SQLException if it cannot be opened, among others when SQLite's native library does not load
     */
    static Connection open(String url) throws SQLException {
        var driver = new org.sqlite.JDBC();
        try {
            return driver.connect(url, new Properties());
        } finally {
            for (var registered : Collections.list(DriverManager.getDrivers()))
                if (registered instanceof org.sqlite.JDBC) DriverManager.deregisterDriver(registered);
        }
    }
}
