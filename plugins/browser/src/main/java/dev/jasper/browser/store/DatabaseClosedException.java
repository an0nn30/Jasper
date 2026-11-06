package dev.jasper.browser.store;

/**
 * Why {@link BrowserDatabase#call} refuses work once the database is closing, as a /dev/null group's does with its last
 * item and every database at the plugin's stop, and why a store over it refuses work once its data store has closed:
 * thrown for nothing else, so a store can tell this refusal apart from an {@link IllegalStateException} that SQLite's
 * driver throws.
 */
public final class DatabaseClosedException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    /** The refusal. */
    public DatabaseClosedException() {
        super("The browser's database is closed");
    }
}
