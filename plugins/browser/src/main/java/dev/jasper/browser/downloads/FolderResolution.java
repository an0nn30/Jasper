package dev.jasper.browser.downloads;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/**
 * The Downloads folder as the Downloads folder setting names it ({@link DownloadFolder}) over the system's own (ruling
 * F3): read and made on the background executor, never on Chromium's thread or the UI thread, since the system's folder
 * is found by file work the first time and a folder on a sleeping drive may stall. Only the folder itself is checked
 * and made, never what is in it, so macOS asks for the Downloads folder at the first download (ruling D4). It keeps the
 * folder, or why a download cannot go there, and the newest resolution asked for wins, whichever finishes last.
 */
final class FolderResolution {
    private final Executor background;
    private final Supplier<Path> system;
    private final Path home;
    // Guards which resolution asked for is the newest, and which was kept.
    private final Object lock = new Object();
    private long asked, kept;
    private volatile Resolved resolved;

    /**
     * The Downloads folder, or why a download cannot go there.
     *
     * @param folder the folder the setting names
     * @param refusal why no download can go there, for its notification; null when one can
     */
    record Resolved(Path folder, String refusal) {
        /** Requires the folder. */
        Resolved {
            Objects.requireNonNull(folder, "folder");
        }
    }

    /**
     * A resolution that reads nothing until it is asked to.
     *
     * @param background where the folder is read and made: the plugin's background executor
     * @param system the system's Downloads folder, {@code Platform.downloadsFolder()}, whose first read does file work
     * @param home the home folder, which {@code ~} names
     */
    FolderResolution(Executor background, Supplier<Path> system, Path home) {
        this.background = Objects.requireNonNull(background, "background");
        this.system = Objects.requireNonNull(system, "system");
        this.home = Objects.requireNonNull(home, "home");
    }

    /**
     * Reads and makes the folder {@code value} names, on the background executor; nothing once it no longer takes work.
     * Any thread.
     *
     * @param value the Downloads folder setting
     */
    void resolve(String value) {
        long number;
        synchronized (lock) {
            number = ++asked;
        }
        try {
            background.execute(() -> resolved(number, value));
        } catch (RejectedExecutionException stopped) {
            // The plugin has stopped.
        }
    }

    /**
     * The folder as last read and made. Any thread.
     *
     * @return the folder, or why no download can go there; null before the first resolution has run
     */
    Resolved now() {
        return resolved;
    }

    /**
     * The Downloads folder: as last read and made, whether or not a download can go there; before the first resolution
     * has run, {@code Downloads} in the home folder, which nothing creates. Any thread.
     *
     * @return the folder
     */
    Path folder() {
        Resolved now = resolved;
        return now == null ? home.resolve("Downloads") : now.folder();
    }

    /** Reads and makes the folder; background executor. */
    private void resolved(long number, String value) {
        Resolved result;
        try {
            result = made(DownloadFolder.resolve(value, system.get(), home));
        } catch (RuntimeException unavailable) {
            result = new Resolved(folder(), Downloads.reason(unavailable));
        }
        synchronized (lock) {
            if (number <= kept) return;
            kept = number;
            resolved = result;
        }
    }

    /** The folder, made when missing: only it is checked and made, never what is in it. */
    private static Resolved made(Path folder) {
        try {
            if (!Files.isDirectory(folder)) Files.createDirectories(folder);
            return new Resolved(folder, null);
        } catch (FileAlreadyExistsException file) {
            return new Resolved(folder, "\u201C" + folder + "\u201D is a file, not a folder.");
        } catch (IOException | SecurityException failure) {
            return new Resolved(folder, "Jasper could not create the folder \u201C" + folder + "\u201D: " + Downloads.reason(failure));
        }
    }
}
