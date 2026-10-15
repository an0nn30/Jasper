package dev.jasper.app.workspace;

import dev.jasper.app.launch.UncHosts;
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
     * <p>On Windows a path whose text names a UNC host, two separators ({@code \} or {@code /}, in any mix) and then
     * the host, names nothing: asking the file system about it would make Jasper authenticate to that host over SMB, and
     * a click must never do that for a host that program output names. That text is checked before any {@link Path} is
     * made, so it holds on every OS: the path itself, and what follows {@code ~/}, which {@link Path#resolve} would
     * return as it is when it is absolute. In a WSL profile's pane the WSL shares are this machine's own and resolve, as
     * {@link UncHosts} describes; elsewhere they are refused like any other host. The same rule applies to {@code directory}
     * for a relative path: any program can print an OSC 7 report that becomes a pane's directory, so the directory is
     * program output too. Finally the resulting path's own text is checked, whichever branch built it, so a {@code home}
     * on a share other than a WSL pane's resolves no {@code ~/} path either.
     *
     * @param path the path as the terminal reported it
     * @param directory the pane's local directory, if it has one; on Windows one that names a UNC host other than a WSL
     *        pane's WSL share resolves no relative path
     * @param home the user's home directory
     * @param windows whether the paths are Windows paths: {@code ~\} means the home directory too, and a path that names
     *        a UNC host other than a WSL pane's WSL share names nothing
     * @param wsl whether the pane runs a WSL profile, so that this machine's WSL shares are real there
     * @return the file, which on Windows does not name a UNC host other than a WSL pane's WSL share; empty for a path or a
     *         {@code ~/} remainder that names one, a relative path without a directory or under a directory that names one,
     *         a result that names one, and a path this file system cannot hold
     */
    public static Optional<Path> resolve(String path, Optional<Path> directory, Path home, boolean windows, boolean wsl) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(home, "home");
        if (windows && UncHosts.namesForeignHost(path, wsl)) return Optional.empty();
        try {
            Optional<Path> file = locate(path, directory, home, windows, wsl);
            return windows ? file.filter(found -> !UncHosts.namesForeignHost(found.toString(), wsl)) : file;
        } catch (InvalidPathException invalid) {
            return Optional.empty();
        }
    }

    private static Optional<Path> locate(String path, Optional<Path> directory, Path home, boolean windows, boolean wsl) {
        if (path.startsWith("~/") || windows && path.startsWith("~\\")) {
            String rest = path.substring(2);
            if (windows && UncHosts.namesForeignHost(rest, wsl)) return Optional.empty();
            return Optional.of(home.resolve(rest).normalize());
        }
        Path written = Path.of(path);
        if (written.isAbsolute()) return Optional.of(written.normalize());
        return directory.filter(base -> !windows || !UncHosts.namesForeignHost(base.toString(), wsl))
            .map(base -> base.resolve(written).normalize());
    }
}
