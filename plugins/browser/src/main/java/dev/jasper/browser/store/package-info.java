/**
 * History and bookmarks (spec sections 10 and 12), in one SQLite database, {@code browser.sqlite} in the plugin's data
 * folder, which holds the {@code permissions} table of the site permissions too.
 * {@link dev.jasper.browser.store.BrowserDatabase} opens it at its first call, on its own thread, and runs it in memory
 * for the rest of the run when the file cannot be used; {@link dev.jasper.browser.store.HistoryStore} keeps an
 * entry for each page load and counts the visits of each host and path, whose sites Clear Website Data clears, and
 * {@link dev.jasper.browser.store.BookmarkStore} keeps the bookmarks and their tags.
 * {@link dev.jasper.browser.store.Addresses} decides which addresses they keep, without user names and passwords.
 * {@link dev.jasper.browser.store.Stores} holds a database's two stores and the indexes of them the UI thread reads, the
 * bookmarks ({@link dev.jasper.browser.store.BookmarkIndex}) and the 10,000 most recently visited sites
 * ({@link dev.jasper.browser.store.VisitIndex}), which load in the background when first asked and follow the stores'
 * changes.
 * {@link dev.jasper.browser.store.DataFolder} makes the plugin's data folder, which holds the database and Chromium's
 * profile, its owner's alone before either is written there, and first hands the database's files to the plugin, which
 * deletes them when a Clear Website Data that could not erase them marked them.
 * {@code SqliteOpener} is the one class that names an {@code org.sqlite} type, and only the database's thread loads it,
 * so the plugin's start loads no sqlite-jdbc class; {@code SqliteNatives} chooses the folder sqlite-jdbc extracts its
 * native library into. Depends on the {@code restore} package, the SDK, the JDK and sqlite-jdbc, and names no Chromium
 * type.
 */
package dev.jasper.browser.store;
