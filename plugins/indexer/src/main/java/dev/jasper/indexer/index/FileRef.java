package dev.jasper.indexer.index;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One file of a project.
 *
 * @param key the project
 * @param path the file's path relative to the root, {@code /}-separated
 */
public record FileRef(ProjectKey key, String path) {
    /** Rejects nulls and an empty path. */
    public FileRef {
        Objects.requireNonNull(key, "key");
        if (path == null || path.isEmpty()) throw new IllegalArgumentException("A file needs a path");
    }

    /** @return the file's own name */
    public String name() { return path.substring(path.lastIndexOf('/') + 1); }

    /** @return the local file; throws for a remote project */
    public Path localPath() { return key.path().resolve(path); }

    /** @return the absolute remote path */
    public String remotePath() { return (key.root().endsWith("/") ? key.root() : key.root() + "/") + path; }
}
