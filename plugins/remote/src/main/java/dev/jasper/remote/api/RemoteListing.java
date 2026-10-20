package dev.jasper.remote.api;

import java.util.List;
import java.util.Objects;

/**
 * What {@link RemoteProjects#list} found.
 *
 * @param files paths relative to the root, {@code /}-separated
 * @param strategy how they were found
 * @param truncated whether the listing stopped at the limit
 */
public record RemoteListing(List<String> files, Strategy strategy, boolean truncated) {
    /** How a listing was made. */
    public enum Strategy {
        /** {@code git ls-files} in a work tree. */
        GIT,
        /** {@code find}. */
        FIND,
        /** An SFTP walk. */
        SFTP
    }

    /** Copies the files. */
    public RemoteListing {
        files = List.copyOf(files);
        Objects.requireNonNull(strategy, "strategy");
    }
}
