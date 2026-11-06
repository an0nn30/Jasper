/**
 * Strict JSON. {@link dev.jasper.browser.json.StrictJson} reads what a page sends through the {@code links} package's
 * opener bridge, what a Markdown preview's page sends the {@code preview} package and what DevTools answers to the
 * {@code page} package, as one object, and a search engine's suggestions for the {@code item} package, as one array, all
 * untrusted, within a nesting bound, and writes the string literals the {@code links}, {@code page}, {@code preview} and
 * {@code pages} packages put into page scripts, and the answers Jasper's own pages are given. Depends on the JDK only and
 * names no Chromium type.
 */
package dev.jasper.browser.json;
