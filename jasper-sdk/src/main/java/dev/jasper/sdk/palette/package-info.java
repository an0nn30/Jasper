/**
 * Command-palette scopes contributed by plugins. A scope is one kind of searchable thing: it answers a
 * query with rows, runs one of its verbs on a row and may show a small form first. Every method runs
 * on the UI thread and does no I/O; the palette shows at most {@code PaletteQuery.maxResults()} rows,
 * scrolling once there are more than 15. Depends on {@code dev.jasper.sdk} and {@code dev.jasper.sdk.terminal}.
 */
package dev.jasper.sdk.palette;
