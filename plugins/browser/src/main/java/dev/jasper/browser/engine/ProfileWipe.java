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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The clearing Chromium's profile waits for (plan 6 Task 8, and the user's V1): the marker {@code chromium-clear} in
 * the plugin's data directory, holding {@code cookies} or {@code all}, of which {@code all} wins, and with
 * {@code database} when the browser's database is to go too. Clear Website Data marks {@code all}, and the next start
 * that finds it wipes the profile, {@code <data>/chromium}, before Chromium opens it, and empties Chromium's log, whose
 * lines may name the sites; Clear Cookies before any start marks {@code cookies}, which waits for Chromium, as only
 * Chromium can delete its cookies, and goes once it has. A Clear Website Data whose database ran in memory, as its file
 * could not be used, marks {@code database} too (ruling V25): the data folder's first use, before the database opens,
 * deletes the database's files ({@link #wipeMarkedDatabase}). The wipe first moves the profile aside, under a name of
 * its own ({@code chromium-cleared-} and a number), and deletes it there: a profile that cannot be moved, as Windows refuses while a
 * file in it is open, keeps its marker and stays whole, and Chromium starts with it as usual; one moved is no longer
 * Chromium's, so its mark goes, and what of it cannot be deleted is deleted at a later start, in nobody's way meanwhile.
 * A marker whose content this Jasper does not know reads as {@code all}. Plain Java and file I/O, never on the UI
 * thread: on Chromium's worker, or the database's thread, once the data directory is its owner's alone (plan 5's
 * H37); the marker is written through a temporary file in the same folder, its owner's alone.
 */
public final class ProfileWipe {
    /** What the marker asks of Chromium's profile. */
    enum Mark {
        /** Nothing. */
        NONE,
        /** Every cookie, which Chromium deletes as it starts, before any page. */
        COOKIES,
        /** The whole profile, wiped before Chromium starts. */
        ALL
    }

    static final String MARKER = "chromium-clear", PROFILE = "chromium", CLEARED = "chromium-cleared", LOG_FILE = "chromium.log";
    private static final String DATABASE = "database";
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

    /** What the marker holds: what is asked of the profile, and whether of the database too. */
    private record Content(Mark profile, boolean database) {
        static final Content NOTHING = new Content(Mark.NONE, false);
    }

    private ProfileWipe() { }

    /**
     * Marks {@code mark} in {@code dataDir}: {@code all} replaces {@code cookies}, and {@code cookies} never replaces
     * {@code all}; a database marked stays marked.
     *
     * @param dataDir the plugin's data directory, which exists and is its owner's alone
     * @param mark {@link Mark#COOKIES} or {@link Mark#ALL}
     * @throws IOException if the marker cannot be written
     */
    static void mark(Path dataDir, Mark mark) throws IOException {
        mark(dataDir, mark, false);
    }

    /**
     * As {@link #mark(Path, Mark)}, with the database's files marked too when {@code database}.
     *
     * @param dataDir the plugin's data directory, which exists and is its owner's alone
     * @param mark {@link Mark#COOKIES} or {@link Mark#ALL}
     * @param database whether the database's files go at the next start, before the database opens
     * @throws IOException if the marker cannot be written
     */
    static void mark(Path dataDir, Mark mark, boolean database) throws IOException {
        if (mark == Mark.NONE) throw new IllegalArgumentException("A marker asks for something of the profile");
        Content now = read(dataDir);
        var wanted = new Content(mark.compareTo(now.profile()) > 0 ? mark : now.profile(), now.database() || database);
        if (!wanted.equals(now)) write(dataDir, wanted);
    }

    /**
     * What {@code dataDir}'s marker asks of the profile.
     *
     * @param dataDir the plugin's data directory
     * @return {@link Mark#NONE} without a marker or a mark of the profile; {@link Mark#ALL} for one this Jasper cannot
     *     read the meaning of
     */
    static Mark marked(Path dataDir) {
        return read(dataDir).profile();
    }

    /**
     * Whether {@code dataDir}'s marker asks for the database's files to go.
     *
     * @param dataDir the plugin's data directory
     * @return whether it does
     */
    static boolean databaseMarked(Path dataDir) {
        return read(dataDir).database();
    }

    /**
     * Deletes the database's {@code files} when {@code dataDir}'s marker asks for them, as the data folder's first use
     * does, before the database opens (ruling V25), and takes the database's mark back; the profile's stays for
     * Chromium. A file that cannot be deleted keeps the mark for the next start, logged, and the database may not use
     * the files meanwhile (ruling V27). Any thread but the UI thread.
     *
     * @param dataDir the plugin's data directory
     * @param files the database's file, its write-ahead log and its shared memory
     * @return whether the database may use the files: false when they are marked and could not all be deleted
     */
    public static boolean wipeMarkedDatabase(Path dataDir, List<Path> files) {
        Content now = read(dataDir);
        if (!now.database()) return true;
        try {
            for (Path file : files) Files.deleteIfExists(file);
        } catch (IOException | RuntimeException kept) {
            LOG.log(System.Logger.Level.WARNING, "The browser's database could not be deleted as Clear Website Data asked; the next start tries "
                + "again", kept);
            return false;
        }
        try {
            write(dataDir, new Content(now.profile(), false));
        } catch (IOException | RuntimeException kept) {
            LOG.log(System.Logger.Level.WARNING, "The browser's clearing marker could not be written: " + dataDir.resolve(MARKER), kept);
        }
        return true;
    }

    /**
     * Wipes the profile when {@code dataDir} is marked {@code all}, as a start does before Chromium opens it: it is moved
     * aside and deleted, and Chromium's log emptied. First, every profile an earlier wipe moved aside and could not
     * delete is deleted. A {@code cookies} mark waits for Chromium, and a database's mark for the database.
     *
     * @param dataDir the plugin's data directory, its owner's alone
     * @return what is still asked of the profile: {@link Mark#NONE} once wiped, {@link Mark#ALL} when it could not be
     *     moved aside and is whole, {@link Mark#COOKIES} for cookies that wait for Chromium
     */
    static Mark runIfMarked(Path dataDir) {
        return runIfMarked(dataDir, Disk.REAL);
    }

    /** As {@link #runIfMarked(Path)}, moving and deleting through {@code disk}. */
    static Mark runIfMarked(Path dataDir, Disk disk) {
        if (!Files.isDirectory(dataDir)) return Mark.NONE; // no data directory: no profile and no marker
        for (Path left : movedAside(dataDir)) delete(disk, left);
        Content now = read(dataDir);
        if (now.profile() != Mark.ALL) return now.profile();
        Path profile = dataDir.resolve(PROFILE), aside = null;
        if (Files.exists(profile)) {
            aside = aside(dataDir);
            try {
                disk.move(profile, aside);
            } catch (IOException | RuntimeException refused) {
                LOG.log(System.Logger.Level.WARNING, "Chromium's profile could not be moved aside to be cleared; it is kept whole, and the "
                    + "next start tries again: " + profile, refused);
                return Mark.ALL;
            }
        }
        // Review round 1's item 10: Chromium's log may name the sites.
        Path log = dataDir.resolve(LOG_FILE);
        try {
            if (Files.exists(log)) Files.write(log, new byte[0]);
        } catch (IOException | RuntimeException kept) {
            LOG.log(System.Logger.Level.WARNING, "Chromium's log could not be emptied: " + log, kept);
        }
        // The profile is no longer Chromium's: its mark goes now, so the profile Chromium makes from now on is kept.
        try {
            write(dataDir, new Content(Mark.NONE, now.database()));
        } catch (IOException | RuntimeException kept) {
            LOG.log(System.Logger.Level.WARNING, "The browser's clearing marker could not be written: " + dataDir.resolve(MARKER), kept);
        }
        if (aside != null) delete(disk, aside);
        return Mark.NONE;
    }

    /**
     * Chromium has deleted its cookies: a {@code cookies} mark goes, and an {@code all} one stays, and so does a
     * database's.
     *
     * @param dataDir the plugin's data directory
     * @throws IOException if the marker cannot be written
     */
    static void cookiesCleared(Path dataDir) throws IOException {
        Content now = read(dataDir);
        if (now.profile() == Mark.COOKIES) write(dataDir, new Content(Mark.NONE, now.database()));
    }

    /** What {@code dataDir}'s marker holds: nothing without one, and {@code all} for one that cannot be read or understood. */
    private static Content read(Path dataDir) {
        String text;
        try {
            text = Files.readString(dataDir.resolve(MARKER), StandardCharsets.US_ASCII).strip();
        } catch (NoSuchFileException none) {
            return Content.NOTHING;
        } catch (IOException | RuntimeException unread) {
            LOG.log(System.Logger.Level.WARNING, "The browser's clearing marker could not be read; it is taken for a wipe", unread);
            return new Content(Mark.ALL, false);
        }
        Mark profile = Mark.NONE;
        boolean database = false;
        for (String word : text.split("\\s+")) {
            switch (word) {
                case "all" -> profile = Mark.ALL;
                case "cookies" -> { if (profile == Mark.NONE) profile = Mark.COOKIES; }
                case DATABASE -> database = true;
                default -> profile = Mark.ALL; // this Jasper does not know the word: the profile's wipe, never the database's
            }
        }
        return new Content(profile, database);
    }

    /** Writes {@code content} as the marker, or deletes the marker when it asks for nothing. */
    private static void write(Path dataDir, Content content) throws IOException {
        Path marker = dataDir.resolve(MARKER);
        if (content.equals(Content.NOTHING)) {
            Files.deleteIfExists(marker);
            return;
        }
        var words = new ArrayList<String>();
        if (content.profile() != Mark.NONE) words.add(content.profile().name().toLowerCase(Locale.ROOT));
        if (content.database()) words.add(DATABASE);
        Path temporary = Files.createTempFile(dataDir, MARKER, ".tmp");
        try {
            Files.writeString(temporary, String.join(" ", words), StandardCharsets.US_ASCII);
            Files.move(temporary, marker, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** A name of its own to move the profile aside to (review round 1's item 6). */
    private static Path aside(Path dataDir) {
        Path aside;
        do aside = dataDir.resolve(CLEARED + "-" + System.nanoTime());
        while (Files.exists(aside));
        return aside;
    }

    /** The profiles moved aside in {@code dataDir}, each wipe's left there. */
    private static List<Path> movedAside(Path dataDir) {
        try (var entries = Files.list(dataDir)) {
            return entries.filter(entry -> entry.getFileName().toString().startsWith(CLEARED) && Files.isDirectory(entry)).toList();
        } catch (IOException | RuntimeException unread) {
            LOG.log(System.Logger.Level.WARNING, "The browser's data folder could not be read for cleared profiles: " + dataDir, unread);
            return List.of();
        }
    }

    /** Deletes a profile moved aside; what cannot be deleted is left for a later start, as it is no longer Chromium's. */
    private static void delete(Disk disk, Path cleared) {
        try {
            disk.deleteTree(cleared);
        } catch (IOException | RuntimeException left) {
            LOG.log(System.Logger.Level.WARNING, "Chromium's cleared profile could not be deleted; the next start tries again: " + cleared, left);
        }
    }
}
