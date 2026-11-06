package dev.jasper.browser.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The plugin's data folder, which holds the history and bookmarks ({@code browser.sqlite}) and Chromium's profile, its
 * cookies among them: open to its owner alone where the file system has POSIX permissions (plan 5's H37), so that no
 * other user of the computer can read them. The first {@link #restrict()} takes every permission of the group and of
 * others from the folder, keeping the owner's, which leaves {@code rwx------} for the folder the host made; then from
 * {@code browser.sqlite} and its write-ahead log and shared memory, those that exist, as SQLite gives the files it makes
 * beside the database the database's permissions. It runs on whichever thread needs the folder first: the database's,
 * before the file opens, or Chromium's worker, before Chromium writes there. Permissions that cannot be set are logged
 * once, as a warning, and the folder is used all the same. On Windows it does nothing. Making one does no I/O.
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
    private boolean restricted; // guarded by this

    /**
     * The data folder {@code folder}.
     *
     * @param folder the folder, which need not exist yet
     */
    public DataFolder(Path folder) {
        this(folder, DataFolder::ownerOnly);
    }

    /** As {@link #DataFolder(Path)}, each path made its owner's alone by {@code restriction}. */
    DataFolder(Path folder, Restriction restriction) {
        this.folder = Objects.requireNonNull(folder, "folder").toAbsolutePath();
        this.restriction = Objects.requireNonNull(restriction, "restriction");
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
     * Makes the folder, if it is missing, and it and the database's files in it its owner's alone, the first time it is
     * called; later calls do nothing. It does file I/O, so never on the UI thread. Any other thread; a second caller
     * waits until the first is done.
     */
    public synchronized void restrict() {
        if (restricted) return;
        restricted = true;
        try {
            Files.createDirectories(folder);
            restriction.apply(folder);
            Path file = database();
            for (Path made : List.of(file, file.resolveSibling(DATABASE + "-wal"), file.resolveSibling(DATABASE + "-shm")))
                if (Files.exists(made)) restriction.apply(made);
        } catch (IOException | RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "The browser's data folder could not be made its owner's alone: " + folder, failure);
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
