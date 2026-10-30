package dev.jasper.browser.engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * The mark that Chromium starts or runs: the empty file {@code chromium.running} in the plugin's data directory, there
 * from the moment Chromium's start enters JCEF until Jasper closes Chromium or Chromium has ended. Chromium runs in
 * Jasper's process, so when Jasper ends meanwhile, as a fatal Chromium error ends it, the file stays, and the plugin's
 * next start finds it with {@link #clear}. A marker that cannot be written or deleted is logged as a warning and fails
 * nothing. It names no Chromium type, so the plugin's start can use it.
 */
public final class RunMarker {
    private static final String NAME = "chromium.running";
    private static final System.Logger LOG = System.getLogger(RunMarker.class.getName());
    private final Path file;
    // Guarded by this: Chromium is closed or has ended, so the marker is not written again.
    private boolean stopped;

    /** The marker of a Chromium whose data directory is {@code dataDir}. */
    RunMarker(Path dataDir) {
        file = Objects.requireNonNull(dataDir, "dataDir").resolve(NAME);
    }

    /**
     * Deletes the marker the last run left in {@code dataDir}: whether it was there, so whether that run ended while
     * Chromium started or ran. The plugin's start asks this before it makes {@link Chromium}.
     *
     * @param dataDir the plugin's data directory
     * @return whether the marker was there, deleted or not
     */
    public static boolean clear(Path dataDir) {
        return new RunMarker(dataDir).delete();
    }

    /** Chromium's start enters JCEF: writes the marker, unless Chromium is closed or has ended already. Any thread. */
    synchronized void started() {
        if (stopped) return;
        try {
            Files.write(file, new byte[0]);
        } catch (IOException | RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "The browser's run marker could not be written: " + file, failure);
        }
    }

    /** Jasper closes Chromium, or Chromium has ended: deletes the marker, which is not written again. Any thread. */
    synchronized void stopped() {
        stopped = true;
        delete();
    }

    /** Deletes the marker: whether it was there, deleted or not. */
    private boolean delete() {
        try {
            return Files.deleteIfExists(file);
        } catch (IOException | RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "The browser's run marker could not be deleted: " + file, failure);
            return Files.exists(file);
        }
    }
}
