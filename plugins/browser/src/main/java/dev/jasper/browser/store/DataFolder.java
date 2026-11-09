package dev.jasper.browser.store;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The plugin's data folder, which holds the history and bookmarks ({@code browser.sqlite}) and Chromium's profile, its
 * cookies among them: open to its owner alone where the file system has POSIX permissions (plan 5's H37), so that no
 * other user of the computer can read them. The first {@link #restrict()} takes every permission of the group and of
 * others from the folder, keeping the owner's, which leaves {@code rwx------} for the folder the host made; then from
 * {@code browser.sqlite} and its write-ahead log and shared memory, those that exist, as SQLite gives the files it makes
 * beside the database the database's permissions, and the database makes a new {@code browser.sqlite} its owner's
 * alone before SQLite opens it ({@code createOwnerOnly}). First of all, before any permission is set, it hands the
 * database's files to its {@code beforeDatabase}, which deletes them when a Clear Website Data that could not erase them
 * marked them (ruling V25), so the database never opens them; a marked file that cannot be deleted is not to be used
 * for the run ({@link #databaseUsable()}, ruling V27). It runs on whichever thread needs the folder first: the
 * database's, before the file opens, or Chromium's worker, before Chromium writes there. Permissions that cannot be set
 * are logged once, as a warning, and the folder is used all the same. On a file system without POSIX permissions, as
 * on Windows, only the permissions are left as they are: marked database files are deleted there too. Making one does
 * no I/O.
 */
public final class DataFolder {
    /** The history's and bookmarks' file in the folder. */
    static final String DATABASE = "browser.sqlite";
    private static final Set<PosixFilePermission> OWNER = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_EXECUTE);
    private static final System.Logger LOG = System.getLogger(DataFolder.class.getName());

    /** How a path is made its owner's alone; the test seam for permissions that cannot be set. */
    @FunctionalInterface interface Restriction {
        void apply(Path path) throws IOException;
    }

    private final Path folder;
    private final Restriction restriction;
    private final Predicate<List<Path>> beforeDatabase;
    private boolean restricted; // guarded by this
    private boolean databaseUsable = true; // guarded by this

    /**
     * The data folder {@code folder}.
     *
     * @param folder the folder, which need not exist yet
     */
    public DataFolder(Path folder) {
        this(folder, DataFolder::ownerOnly, files -> true);
    }

    /**
     * The data folder {@code folder}, whose first {@link #restrict()} hands the database's files to {@code beforeDatabase}
     * before the database opens them.
     *
     * @param folder the folder, which need not exist yet
     * @param beforeDatabase given the database's file, its write-ahead log and its shared memory, once, on the thread that
     *     uses the folder first; it deletes them when a clearing marked them, and answers whether the database may use
     *     them: false for marked files it could not delete
     */
    public DataFolder(Path folder, Predicate<List<Path>> beforeDatabase) {
        this(folder, DataFolder::ownerOnly, beforeDatabase);
    }

    /** As {@link #DataFolder(Path, Predicate)}, each path made its owner's alone by {@code restriction}. */
    DataFolder(Path folder, Restriction restriction, Predicate<List<Path>> beforeDatabase) {
        this.folder = Objects.requireNonNull(folder, "folder").toAbsolutePath();
        this.restriction = Objects.requireNonNull(restriction, "restriction");
        this.beforeDatabase = Objects.requireNonNull(beforeDatabase, "beforeDatabase");
    }

    /**
     * The folder.
     *
     * @return its absolute path
     */
    public Path path() {
        return folder;
    }

    /** The history's and bookmarks' file in the folder. */
    Path database() {
        return folder.resolve(DATABASE);
    }

    /**
     * Makes the folder, if it is missing, hands the database's files to {@code beforeDatabase}, then makes the folder and
     * those files its owner's alone, the first time it is called; later calls do nothing. Permissions that cannot be set
     * keep no marked database from being deleted (the re-review's item 1). It does file I/O, so never on the UI thread.
     * Any other thread; a second caller waits until the first is done.
     */
    public synchronized void restrict() {
        if (restricted) return;
        restricted = true;
        try {
            Files.createDirectories(folder);
        } catch (IOException | RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "The browser's data folder could not be made its owner's alone: " + folder, failure);
            return;
        }
        Path file = database();
        List<Path> files = List.of(file, file.resolveSibling(DATABASE + "-wal"), file.resolveSibling(DATABASE + "-shm"));
        try {
            databaseUsable = beforeDatabase.test(files);
        } catch (RuntimeException failure) {
            // Whether a clearing marked them is unknown, so they are not used: cleared rows must not come back (V27).
            databaseUsable = false;
            LOG.log(System.Logger.Level.WARNING, "The browser's database files could not be checked for a clearing", failure);
        }
        try {
            restriction.apply(folder);
            for (Path made : files) if (Files.exists(made)) restriction.apply(made);
        } catch (IOException | RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "The browser's data folder could not be made its owner's alone: " + folder, failure);
        }
    }

    /**
     * Whether the database may use its files in the folder: false when a Clear Website Data marked them for deletion and
     * they could not be deleted (ruling V27), as the rows it cleared must never be read again; the database then runs in
     * memory for the run. True before {@link #restrict()}.
     *
     * @return whether it may
     */
    public synchronized boolean databaseUsable() {
        return databaseUsable;
    }

    /**
     * Makes {@code file}, empty and its owner's alone ({@code rw-------}), when it is missing and its folder's file system
     * has POSIX permissions (H41): SQLite gives the write-ahead log and shared memory it makes beside a database the
     * database's permissions, so they are the owner's alone too.
     *
     * @param file the database's file, in a folder that exists
     * @return whether it made the file; false when the file was there, or another program made it meanwhile, and on
     *     a file system without POSIX permissions
     * @throws IOException if the file cannot be made
     */
    static boolean createOwnerOnly(Path file) throws IOException {
        Path folder = file.toAbsolutePath().getParent();
        if (Files.exists(file) || Files.getFileAttributeView(folder, PosixFileAttributeView.class) == null) return false;
        try {
            Files.createFile(file, PosixFilePermissions.asFileAttribute(EnumSet.of(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE)));
            return true;
        } catch (FileAlreadyExistsException meanwhile) {
            return false;
        }
    }

    /**
     * Takes every permission of the group and of others from {@code path}, keeping its owner's, where its file system has
     * POSIX permissions; elsewhere it does nothing.
     */
    static void ownerOnly(Path path) throws IOException {
        PosixFileAttributeView view = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        if (view == null) return;
        Set<PosixFilePermission> permissions = view.readAttributes().permissions();
        Set<PosixFilePermission> kept = EnumSet.noneOf(PosixFilePermission.class);
        for (PosixFilePermission permission : permissions) if (OWNER.contains(permission)) kept.add(permission);
        if (!kept.equals(permissions)) view.setPermissions(kept);
    }
}
