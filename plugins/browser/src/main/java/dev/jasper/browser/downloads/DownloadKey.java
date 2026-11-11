package dev.jasper.browser.downloads;

/**
 * A download, as Chromium tells its updates apart: Chromium's download id, within the browser that started it. Ids are
 * unique within a request context, and plan 6's /dev/null groups each have one of their own, so the browser's
 * identifier, unique in the process, keeps one group's ids from another's (ruling F21).
 *
 * @param browser the identifier of the browser that started the download
 * @param id Chromium's id for the download
 */
public record DownloadKey(int browser, int id) {
}
