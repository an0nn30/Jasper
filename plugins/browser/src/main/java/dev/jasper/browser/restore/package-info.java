/**
 * Saving and restoring browser items (spec section 16): {@link dev.jasper.browser.restore.BrowserRestore} writes an
 * item's restore state and reads a saved one back, checking it as untrusted input. Depends on the SDK and the JDK only,
 * so the {@code item} package, which saves each item's state, can use it; it names no Chromium type.
 */
package dev.jasper.browser.restore;
