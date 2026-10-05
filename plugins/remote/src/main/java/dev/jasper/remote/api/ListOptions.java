package dev.jasper.remote.api;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * How {@link RemoteProjects#list} lists a project.
 *
 * @param excludes folder names never listed
 * @param maxFiles the most files to return
 * @param timeout how long all strategies together may take
 * @param cancelled polled while listing, from a worker thread
 */
public record ListOptions(List<String> excludes, int maxFiles, Duration timeout, BooleanSupplier cancelled) {
    /** Copies the excludes and rejects a non-positive limit. */
    public ListOptions {
        excludes = List.copyOf(excludes);
        if (maxFiles < 1) throw new IllegalArgumentException("maxFiles must be positive");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(cancelled, "cancelled");
    }
}
