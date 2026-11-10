package dev.jasper.browser.page;

/**
 * A download's state as Chromium reports it in an update, in plain values: what {@code PageDownloads} reads from a
 * download item, for the {@code downloads} package. Chromium has no interrupted state of its own, so an update neither
 * in progress, complete nor cancelled is an interrupted download.
 *
 * @param browser the identifier of the browser that started the download, as Chromium gives it
 * @param id the download's identifier, unique within that browser's request context
 * @param inProgress whether the download is still under way
 * @param complete whether the whole file has arrived
 * @param canceled whether the download was cancelled
 * @param received the bytes received so far
 * @param total the file's size in bytes; zero or less while it is unknown
 * @param fullPath the file being written: while the download runs, often Chromium's intermediate
 *     {@code <target>.crdownload}, and at completion the target; empty while Chromium has named none
 */
public record DownloadProgress(int browser, int id, boolean inProgress, boolean complete, boolean canceled, long received, long total,
                               String fullPath) {
    /** Reads a missing path as empty. */
    public DownloadProgress {
        fullPath = fullPath == null ? "" : fullPath;
    }
}
