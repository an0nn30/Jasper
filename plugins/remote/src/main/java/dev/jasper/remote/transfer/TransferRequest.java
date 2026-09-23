package dev.jasper.remote.transfer;

import java.util.List;
import java.util.Objects;

/** Immutable user selection; normalized and inspected off the UI thread before mutation. */
public record TransferRequest(EndpointRef source, List<String> paths, EndpointRef destination, String directory) {
    public TransferRequest {
        Objects.requireNonNull(source); Objects.requireNonNull(destination); Objects.requireNonNull(directory);
        paths = List.copyOf(paths);
        if (paths.isEmpty()) throw new IllegalArgumentException("Select files or a folder");
        if (paths.stream().anyMatch(p -> p.isEmpty() || p.indexOf(0) >= 0) || directory.isEmpty() || directory.indexOf(0) >= 0)
            throw new IllegalArgumentException("Invalid path");
    }
}
