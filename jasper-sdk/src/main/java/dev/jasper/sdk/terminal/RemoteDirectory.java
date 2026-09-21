package dev.jasper.sdk.terminal;

import java.util.Objects;

/**
 * A working directory the shell reported that is not a directory of this machine: another host named it, or the
 * pane's session is not a local process. It is unauthenticated
 * text from whatever runs in the pane: a hint, never a fact.
 *
 * @param host the reported host name; empty when the program named none
 * @param path the reported path, as text
 */
public record RemoteDirectory(String host, String path) {
    /** Rejects nulls and a blank host. */
    public RemoteDirectory {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(path, "path");
        if (!host.isEmpty() && host.isBlank()) throw new IllegalArgumentException("A remote directory's host is a name or empty");
    }
}
