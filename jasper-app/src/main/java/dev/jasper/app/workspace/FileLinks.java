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
     * a click must never do that for a host that program output names. That text is checked before any {@link Path} is
     * made, so it holds on every OS: the path itself, and what follows {@code ~/}, which {@link Path#resolve} would
     * return as it is when it is absolute. The WSL shares ({@code wsl.localhost} and {@code wsl$}, in any case) are this
     * machine's own and resolve, but never as {@code .} or {@code ..}, which Windows resolves to another host, and only
     * when the host is all ASCII: {@link String#equalsIgnoreCase} folds some other characters to ASCII ones that Windows
     * does not, and such a name would go to name resolution on the network. The same rule applies to {@code directory}
     * for a relative path: any program can print an OSC 7 report that becomes a pane's directory, so the directory is
     * program output too. Finally the resulting path's own text is checked, whichever branch built it, so a {@code home}
     * on a share other than WSL's resolves no {@code ~/} path either.
     *
     * @param path the path as the terminal reported it
     * @param directory the pane's local directory, if it has one; on Windows one that names a UNC host other than a WSL
     *        share resolves no relative path
     * @param home the user's home directory
     * @param windows whether the paths are Windows paths: {@code ~\} means the home directory too, and a path that names
     *        a UNC host other than a WSL share names nothing
     * @return the file, which on Windows does not name a UNC host other than a WSL share; empty for a path or a
     *         {@code ~/} remainder that names one, a relative path without a directory or under a directory that names one,
     *         a result that names one, and a path this file system cannot hold
     */
    public static Optional<Path> resolve(String path, Optional<Path> directory, Path home, boolean windows) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(home, "home");
        if (windows && namesForeignHost(path)) return Optional.empty();
        try {
            Optional<Path> file = locate(path, directory, home, windows);
            return windows ? file.filter(found -> !namesForeignHost(found.toString())) : file;
        } catch (InvalidPathException invalid) {
            return Optional.empty();
        }
    }

    private static Optional<Path> locate(String path, Optional<Path> directory, Path home, boolean windows) {
        if (path.startsWith("~/") || windows && path.startsWith("~\\")) {
            String rest = path.substring(2);
            if (windows && namesForeignHost(rest)) return Optional.empty();
            return Optional.of(home.resolve(rest).normalize());
        }
        Path written = Path.of(path);
        if (written.isAbsolute()) return Optional.of(written.normalize());
        return directory.filter(base -> !windows || !namesForeignHost(base.toString()))
            .map(base -> base.resolve(written).normalize());
    }

    /**
     * Whether {@code text} starts with two separators and then a host that is not one of this machine's WSL shares. A WSL
     * host counts too when its share is {@code .} or {@code ..}.
     */
    private static boolean namesForeignHost(String text) {
        if (text.length() < 2 || !separator(text.charAt(0)) || !separator(text.charAt(1))) return false;
        int hostEnd = endOfSegment(text, 2);
        String host = text.substring(2, hostEnd);
        if (!wslHost(host)) return true;
        int share = hostEnd;
        while (share < text.length() && separator(text.charAt(share))) share++;
        String name = text.substring(share, endOfSegment(text, share));
        return name.equals(".") || name.equals("..");
    }

    /** Whether {@code host} is a WSL share's host: ASCII only, as {@link String#equalsIgnoreCase} would fold other letters in. */
    private static boolean wslHost(String host) {
        return host.chars().allMatch(c -> c < 128) && (host.equalsIgnoreCase("wsl.localhost") || host.equalsIgnoreCase("wsl$"));
    }

    private static int endOfSegment(String text, int from) {
        int end = from;
        while (end < text.length() && !separator(text.charAt(end))) end++;
        return end;
    }

    private static boolean separator(char c) { return c == '\\' || c == '/'; }
}
