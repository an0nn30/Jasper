package dev.jasper.app.terminals;

import java.util.Objects;

/**
 * A working directory a pane's program reported that is not a directory of this machine. Unauthenticated
 * text: for display, never a path to open. The host is as reported and may be empty.
 */
public record RemoteLocation(String host, String path) {
    public RemoteLocation {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(path, "path");
    }

    /** {@code host:path}, or the path alone when the program named no host. */
    public String label() { return host.isEmpty() ? path : host + ":" + path; }
}
