package dev.jasper.browser.downloads;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

/**
 * One download in the Downloads menu, as {@link DownloadList} keeps it.
 *
 * @param serial the download's number in its list, from 1 in the order downloads started; the menu's ids carry it
 * @param key Chromium's name for the download, which its updates carry
 * @param name the file's name, as the menu and the notifications show it
 * @param file where the file goes, in the Downloads folder or where the user chose
 * @param source the address the file comes from
 * @param state how the download stands
 * @param received the bytes received so far
 * @param total the file's size in bytes; zero while it is unknown
 * @param openable whether Open is offered once it has finished: false when the downloaded-file mark failed on macOS or
 *     Windows (ruling F6), and while it is not finished
 */
public record Download(long serial, DownloadKey key, String name, Path file, URI source, State state, long received, long total,
                       boolean openable) {
    /** How a download stands. */
    public enum State {
        /** Under way, or complete and being marked: Stop is offered, and it cannot be removed from the list. */
        RUNNING,
        /** Complete and marked: Open, Show and Remove from List are offered. */
        FINISHED,
        /** Interrupted: Chromium never resumes it. */
        FAILED,
        /** Stopped by the user, or as Jasper quit. */
        STOPPED
    }

    /** Requires every value. */
    public Download {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(state, "state");
    }
}
