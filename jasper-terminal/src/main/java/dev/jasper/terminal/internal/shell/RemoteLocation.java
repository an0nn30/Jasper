package dev.jasper.terminal.internal.shell;

import java.util.Objects;

/**
 * A directory a shell reported under a host that is not this machine, or in a session that is not a local
 * process. Unauthenticated text from the program: a hint, never a fact. The host is as reported and may be empty.
 */
public record RemoteLocation(String host, String path) {
    public RemoteLocation {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(path, "path");
    }
}
