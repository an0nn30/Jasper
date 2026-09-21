package dev.jasper.sdk.terminal;

import java.util.Objects;

/**
 * A working directory the shell reported under a host that is not this machine. It is unauthenticated
 * text from whatever runs in the pane: a hint, never a fact.
 *
 * @param host the reported host name
 * @param path the reported path, as text
 */
public record RemoteDirectory(String host, String path) {
    /** Rejects nulls and a blank host. */
    public RemoteDirectory {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(path, "path");
        if (host.isBlank()) throw new IllegalArgumentException("A remote directory needs a host");
    }
}
