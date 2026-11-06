package dev.jasper.browser.engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;

/**
 * The clearing Chromium's profile waits for (plan 6 Task 8, and the user's V1): the marker {@code chromium-clear} in
 * the plugin's data directory, holding {@code cookies} or {@code all}, of which {@code all} wins. Clear Website Data
 * marks {@code all}, and the next start that finds it wipes the profile, {@code <data>/chromium}, before Chromium opens
 * it; Clear Cookies before any start marks {@code cookies}, which waits for Chromium, as only Chromium can delete its
 * cookies, and goes once it has. The wipe first moves the profile aside, to {@code chromium-cleared}, and deletes it
 * there: a profile that cannot be moved, as Windows refuses while a file in it is open, keeps its marker and stays
 * whole, and Chromium starts with it as usual; one moved is no longer Chromium's, so its marker goes, and what of it
 * cannot be deleted is deleted at the next start. A marker whose content this Jasper does not know reads as
 * {@code all}. Plain Java and file I/O, never on the UI thread: on Chromium's worker, once the data directory is its
 * owner's alone (plan 5's H37); the marker is written through a temporary file in the same folder, its owner's alone.
 */
final class ProfileWipe {
    /** What the marker asks for. */
    enum Mark {
        /** Nothing: no marker. */
        NONE,
        /** Every cookie, which Chromium deletes as it starts, before any page. */
        COOKIES,
        /** The whole profile, wiped before Chromium starts. */
        ALL
    }

    static final String MARKER = "chromium-clear", PROFILE = "chromium", CLEARED = "chromium-cleared";
    private static final System.Logger LOG = System.getLogger(ProfileWipe.class.getName());

    /** How the wipe moves and deletes: the file system's own, or a test's that refuses. */
    interface Disk {
        /** The file system's own. */
        Disk REAL = new Disk() { };

        /** Moves {@code from} to {@code to}, which must not exist. */
        default void move(Path from, Path to) throws IOException {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        }

        /** Deletes {@code folder} and everything in it. */
        default void deleteTree(Path folder) throws IOException {
            Files.walkFileTree(folder, new SimpleFileVisitor<>() {
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                    if (failure != null) throw failure;
                    Files.delete(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private ProfileWipe() { }

    /**
     * Marks {@code mark} in {@code dataDir}: {@code all} replaces {@code cookies}, and {@code cookies} never replaces
     * {@code all}.
     *
     * @param dataDir the plugin's data directory, which exists and is its owner's alone
     * @param mark {@link Mark#COOKIES} or {@link Mark#ALL}
     * @throws IOException if the marker cannot be written
     */
    static void mark(Path dataDir, Mark mark) throws IOException {
        if (mark == Mark.NONE) throw new IllegalArgumentException("A marker asks for something");
        Mark now = marked(dataDir);
        if (now == Mark.ALL || now == mark) return;
        Path temporary = Files.createTempFile(dataDir, MARKER, ".tmp");
        try {
            Files.writeString(temporary, mark.name().toLowerCase(Locale.ROOT), StandardCharsets.US_ASCII);
            Files.move(temporary, dataDir.resolve(MARKER), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * What {@code dataDir}'s marker asks for.
     *
     * @param dataDir the plugin's data directory
     * @return {@link Mark#NONE} without a marker; {@link Mark#ALL} for one this Jasper cannot read the meaning of
     */
    static Mark marked(Path dataDir) {
        String content;
        try {
            content = Files.readString(dataDir.resolve(MARKER), StandardCharsets.US_ASCII).strip();
        } catch (NoSuchFileException none) {
            return Mark.NONE;
        } catch (IOException | RuntimeException unread) {
            LOG.log(System.Logger.Level.WARNING, "The browser's clearing marker could not be read; it is taken for a wipe", unread);
            return Mark.ALL;
        }
        return content.equals("cookies") ? Mark.COOKIES : Mark.ALL;
    }

    /**
     * Wipes the profile when {@code dataDir} is marked {@code all}, as a start does before Chromium opens it; first,
     * what a wipe left of a profile moved aside is deleted. A {@code cookies} marker waits for Chromium.
     *
     * @param dataDir the plugin's data directory, its owner's alone
     * @return what is still marked: {@link Mark#NONE} once wiped, {@link Mark#ALL} when the profile could not be moved
     *     aside and is whole, {@link Mark#COOKIES} for cookies that wait for Chromium
     */
    static Mark runIfMarked(Path dataDir) {
        return runIfMarked(dataDir, Disk.REAL);
    }

    /** As {@link #runIfMarked(Path)}, moving and deleting through {@code disk}. */
    static Mark runIfMarked(Path dataDir, Disk disk) {
        if (!Files.isDirectory(dataDir)) return Mark.NONE; // no data directory: no profile and no marker
        Path cleared = dataDir.resolve(CLEARED);
        if (Files.exists(cleared)) delete(disk, cleared);
        Mark mark = marked(dataDir);
        if (mark != Mark.ALL) return mark;
        Path profile = dataDir.resolve(PROFILE);
        if (Files.exists(profile)) {
            try {
                disk.move(profile, cleared);
            } catch (IOException | RuntimeException refused) {
                LOG.log(System.Logger.Level.WARNING, "Chromium's profile could not be moved aside to be cleared; it is kept whole, and the "
                    + "next start tries again: " + profile, refused);
                return Mark.ALL;
            }
        }
        // The profile is no longer Chromium's: the marker goes now, so the profile Chromium makes from now on is kept.
        try {
            Files.deleteIfExists(dataDir.resolve(MARKER));
        } catch (IOException | RuntimeException kept) {
            LOG.log(System.Logger.Level.WARNING, "The browser's clearing marker could not be deleted: " + dataDir.resolve(MARKER), kept);
        }
        if (Files.exists(cleared)) delete(disk, cleared);
        return Mark.NONE;
    }

    /**
     * Chromium has deleted its cookies: a {@code cookies} marker goes, and an {@code all} one stays.
     *
     * @param dataDir the plugin's data directory
     * @throws IOException if the marker cannot be deleted
     */
    static void cookiesCleared(Path dataDir) throws IOException {
        if (marked(dataDir) == Mark.COOKIES) Files.deleteIfExists(dataDir.resolve(MARKER));
    }

    /** Deletes a profile moved aside; what cannot be deleted is left for the next start, as it is no longer Chromium's. */
    private static void delete(Disk disk, Path cleared) {
        try {
            disk.deleteTree(cleared);
        } catch (IOException | RuntimeException left) {
            LOG.log(System.Logger.Level.WARNING, "Chromium's cleared profile could not be deleted; the next start tries again: " + cleared, left);
        }
    }
}
