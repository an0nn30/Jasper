/**
 * The Browser's command palette scopes (spec section 11): {@link dev.jasper.browser.palette.BookmarksScope} searches
 * the bookmarks and {@link dev.jasper.browser.palette.VisitedScope} the most recently visited sites, each over the
 * UI-thread index {@code store} keeps, so a search does no I/O. {@link dev.jasper.browser.palette.PaletteOpener} opens a
 * row's address in the window's focused browser item, or in a new tab, after the checks the address field makes.
 * Depends on {@code item}, {@code store}, the SDK and the JDK, and names no Chromium type.
 */
package dev.jasper.browser.palette;
