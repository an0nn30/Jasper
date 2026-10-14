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
     * <p>On Windows a path whose text names a UNC host, two separators ({@code \} or {@code /}, in any mix) and then
     * the host, names nothing: asking the file system about it would make Jasper authenticate to that host over SMB, and
     * a click must never do that for a host that program output names. The text is checked before any {@link Path} is
     * made, so it holds on every OS. The WSL shares ({@code wsl.localhost} and {@code wsl$}, in any case) are this
     * machine's own and resolve. A relative path under a pane's directory that is itself a share is unaffected: that
     * host came from the pane.
     *
     * @param path the path as the terminal reported it
     * @param directory the pane's local directory, if it has one
     * @param home the user's home directory
     * @param windows whether the paths are Windows paths: {@code ~\} means the home directory too, and a path that names
     *        a UNC host other than a WSL share names nothing
     * @return the file, or empty for a relative path without a directory, a path this file system cannot hold and, on
     *         Windows, a path naming a UNC host
     */
    public static Optional<Path> resolve(String path, Optional<Path> directory, Path home, boolean windows) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(home, "home");
        if (windows && namesForeignHost(path)) return Optional.empty();
        try {
            if (path.startsWith("~/") || windows && path.startsWith("~\\")) return Optional.of(home.resolve(path.substring(2)).normalize());
            Path written = Path.of(path);
            if (written.isAbsolute()) return Optional.of(written.normalize());
            return directory.map(base -> base.resolve(written).normalize());
        } catch (InvalidPathException invalid) {
            return Optional.empty();
        }
    }

    /** Whether {@code path} starts with two separators and then a host that is not one of this machine's WSL shares. */
    private static boolean namesForeignHost(String path) {
        if (path.length() < 2 || !separator(path.charAt(0)) || !separator(path.charAt(1))) return false;
        int end = 2;
        while (end < path.length() && !separator(path.charAt(end))) end++;
        String host = path.substring(2, end);
        return !host.equalsIgnoreCase("wsl.localhost") && !host.equalsIgnoreCase("wsl$");
    }

    private static boolean separator(char c) { return c == '\\' || c == '/'; }
}
