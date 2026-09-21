package dev.jasper.terminal.session;

import java.util.Objects;

/**
 * A working directory the program reported that is not a directory of this machine: another host named it,
 * or the session is not a local process. It is unauthenticated text from whatever runs in the terminal, so it
 * is a hint for display and never a path to open.
 *
 * @param host the host as reported; empty when the program named none
 * @param path the reported path, decoded, as text
 */
public record RemoteDirectory(String host, String path) {
    /** Rejects nulls. */
    public RemoteDirectory {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(path, "path");
    }
}
