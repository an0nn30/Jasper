package dev.jasper.indexer.index;

import java.util.Objects;
import java.util.UUID;

/**
 * A folder on one of Remote's saved hosts, in the indexer's own terms (no Remote types outside
 * {@code dev.jasper.indexer.remote}).
 *
 * @param host the saved host's id
 * @param hostName its name
 * @param folder an absolute folder
 */
public record RemotePlace(UUID host, String hostName, String folder) {
    /** Rejects nulls and a relative folder. */
    public RemotePlace {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(hostName, "hostName");
        if (folder == null || !folder.startsWith("/")) throw new IllegalArgumentException("A remote folder is absolute: " + folder);
    }
}
