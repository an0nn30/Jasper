package dev.jasper.indexer.index;

import java.util.List;
import java.util.Objects;

/**
 * What a lister found.
 *
 * @param files paths relative to the root, {@code /}-separated
 * @param strategy how they were found
 * @param truncated whether the listing stopped at the file limit
 */
public record Listing(List<String> files, Strategy strategy, boolean truncated) {
    /** Copies the files. */
    public Listing {
        files = List.copyOf(files);
        Objects.requireNonNull(strategy, "strategy");
    }
}
