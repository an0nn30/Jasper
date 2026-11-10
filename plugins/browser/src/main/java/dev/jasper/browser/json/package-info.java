/**
 * Strict JSON. {@link dev.jasper.browser.json.StrictJson} reads what a page sends through the {@code links} package's
 * opener bridge, what a Markdown preview's page sends the {@code preview} package, what DevTools answers to the
 * {@code page} package and what a page's tools report to the {@code tools} package, as one object, and a search
 * engine's suggestions for the {@code item} package and a content-blocker list for the {@code adblock} package, as one
 * array, all untrusted, within a nesting bound, and writes the string literals the {@code links}, {@code page},
 * {@code preview}, {@code pages} and {@code tools} packages put into page scripts, the answers Jasper's own pages are
 * given, and the {@code adblock} package's compiled list. Depends on the JDK only and names no Chromium type.
 */
package dev.jasper.browser.json;
