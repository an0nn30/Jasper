package dev.jasper.indexer.detect;

import java.util.Objects;

/**
 * A project root and the marker that made it one.
 *
 * @param root the root folder: a {@code Path} locally, a {@code String} on a remote host
 * @param marker the marker found there
 * @param <F> how folders are named
 */
public record Detection<F>(F root, Markers.Match marker) {
    /** Rejects nulls. */
    public Detection {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(marker, "marker");
    }
}
