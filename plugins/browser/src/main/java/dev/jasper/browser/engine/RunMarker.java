package dev.jasper.browser.engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * The mark that Chromium runs: the empty file {@code chromium.running} in the plugin's data directory, there from the
 * moment Chromium runs until it has ended. Chromium runs in Jasper's process, so when Jasper ends while Chromium runs, as
 * a fatal Chromium error ends it, the file stays, and the plugin's next start finds it with {@link #clear}. A marker
 * that cannot be written or deleted is logged as a warning and fails nothing. It names no Chromium type, so the plugin's
 * start can use it.
 */
public final class RunMarker {
    private static final String NAME = "chromium.running";
    private static final System.Logger LOG = System.getLogger(RunMarker.class.getName());
    private final Path file;

    /** The marker of a Chromium whose data directory is {@code dataDir}. */
    RunMarker(Path dataDir) {
        file = Objects.requireNonNull(dataDir, "dataDir").resolve(NAME);
    }

    /**
     * Deletes the marker the last run left in {@code dataDir}: whether it was there, so whether that run ended while
     * Chromium ran. The plugin's start asks this before it makes {@link Chromium}.
     *
     * @param dataDir the plugin's data directory
     * @return whether the marker was there, deleted or not
     */
    public static boolean clear(Path dataDir) {
        Path left = Objects.requireNonNull(dataDir, "dataDir").resolve(NAME);
        try {
            return Files.deleteIfExists(left);
        } catch (IOException | RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "The browser's run marker could not be deleted: " + left, failure);
            return Files.exists(left);
        }
    }

    /** Chromium runs: writes the marker. */
    void running() {
        try {
            Files.write(file, new byte[0]);
        } catch (IOException | RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "The browser's run marker could not be written: " + file, failure);
        }
    }

    /** Chromium has ended: deletes the marker. */
    void ended() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException | RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "The browser's run marker could not be deleted: " + file, failure);
        }
    }
}
