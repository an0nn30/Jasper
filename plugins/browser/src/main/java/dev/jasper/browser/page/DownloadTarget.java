package dev.jasper.browser.page;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Where a download Chromium starts goes, as the {@code downloads} package answers a {@link DownloadStart}: the file
 * {@link PageDownloads} continues it into, and whether Chromium asks the user first.
 *
 * @param file the absolute file the download is written to; with {@code ask}, where the save chooser starts
 * @param ask whether Chromium shows the save chooser, whose choice then names the file
 */
public record DownloadTarget(Path file, boolean ask) {
    /** Requires the file. */
    public DownloadTarget {
        Objects.requireNonNull(file, "file");
    }
}
