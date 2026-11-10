/**
 * Downloads (spec section 10): files go, without asking, to the Downloads folder, and the Downloads menu lists them.
 * {@link dev.jasper.browser.downloads.DownloadFolder} reads the Downloads folder setting, and
 * {@link dev.jasper.browser.downloads.DownloadNames} turns a suggested name into a safe one and reserves a free name in
 * the folder, numbered as iTerm2 numbers a clash. {@link dev.jasper.browser.downloads.DownloadList} keeps the downloads
 * by their {@link dev.jasper.browser.downloads.DownloadKey} as the {@code page} package's
 * {@link dev.jasper.browser.page.DownloadProgress} updates them, each reporting one outcome, and
 * {@link dev.jasper.browser.downloads.DownloadsMenu} makes the Downloads menu's entries from them. Depends on the
 * {@code page} and {@code json} packages, the SDK and the JDK, and names no Chromium type;
 * {@link dev.jasper.browser.BrowserPlugin} owns the downloads' lifetime.
 */
package dev.jasper.browser.downloads;
