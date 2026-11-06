/**
 * Site permissions (spec section 12): what each site may do, the camera, the microphone, both together, notifications
 * and audio playback ({@link dev.jasper.browser.permissions.Permission}), as the user allowed or blocked it
 * ({@link dev.jasper.browser.permissions.Decision}). {@link dev.jasper.browser.permissions.Origins} names the site a
 * page's address belongs to, and whether it is a secure context's, and
 * {@link dev.jasper.browser.permissions.PermissionStore} keeps one data store's decisions, a
 * {@link dev.jasper.browser.permissions.Grant} for each, in the {@code permissions} table of that store's database,
 * read from a cache on any thread. Depends on the {@code store} package's database, the SDK and the JDK; it names no
 * Chromium type, and no type of the {@code page}, {@code links}, {@code dialogs}, {@code item} or {@code pages}
 * packages, which use it.
 */
package dev.jasper.browser.permissions;
