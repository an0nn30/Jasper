package dev.jasper.remote.api;

import java.util.Objects;
import java.util.UUID;

/**
 * A folder or file on one of Remote's saved hosts.
 *
 * @param hostId the saved host's id
 * @param hostName the saved host's name, for display
 * @param path an absolute {@code /}-separated path
 */
public record RemoteFolder(UUID hostId, String hostName, String path) {
    /** Rejects nulls and a relative path. */
    public RemoteFolder {
        Objects.requireNonNull(hostId, "hostId");
        Objects.requireNonNull(hostName, "hostName");
        if (path == null || !path.startsWith("/")) throw new IllegalArgumentException("A remote path is absolute: " + path);
    }
}
