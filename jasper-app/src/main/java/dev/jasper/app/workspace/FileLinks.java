package dev.jasper.app.workspace;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Where a file that a Cmd-click in a terminal named is. Pure path work: whether it exists is the caller's question. The
 * path is whatever a program printed or linked, so the file it names is opened for editing only, never run.
 */
public final class FileLinks {
    private FileLinks() { }

    /**
     * The file {@code path} names. A path starting {@code ~/} is under {@code home}; a relative one is under
     * {@code directory}, the pane's local directory, and names nothing without one; an absolute one is itself. The
     * result is normalized.
     *
     * @param path the path as the terminal reported it
     * @param directory the pane's local directory, if it has one
     * @param home the user's home directory
     * @param windows whether {@code ~\} means the home directory too
     * @return the file, or empty for a relative path without a directory and for a path this file system cannot hold
     */
    public static Optional<Path> resolve(String path, Optional<Path> directory, Path home, boolean windows) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(home, "home");
        try {
            if (path.startsWith("~/") || windows && path.startsWith("~\\")) return Optional.of(home.resolve(path.substring(2)).normalize());
            Path written = Path.of(path);
            if (written.isAbsolute()) return Optional.of(written.normalize());
            return directory.map(base -> base.resolve(written).normalize());
        } catch (InvalidPathException invalid) {
            return Optional.empty();
        }
    }
}
