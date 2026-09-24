package dev.jasper.remote.transfer;

import java.util.List;
import java.util.Objects;

/**
 * Immutable user selection; normalized and inspected off the UI thread before mutation. {@code existing} is the
 * choice made before copying for items already at the destination: ASK (none), REPLACE or SKIP; folders merge.
 */
public record TransferRequest(EndpointRef source, List<String> paths, EndpointRef destination, String directory, ConflictDecision existing) {
    public TransferRequest {
        Objects.requireNonNull(source); Objects.requireNonNull(destination); Objects.requireNonNull(directory); Objects.requireNonNull(existing);
        paths = List.copyOf(paths);
        if (paths.isEmpty()) throw new IllegalArgumentException("Select files or a folder");
        if (paths.stream().anyMatch(p -> p.isEmpty() || p.indexOf(0) >= 0) || directory.isEmpty() || directory.indexOf(0) >= 0)
            throw new IllegalArgumentException("Invalid path");
        if (existing != ConflictDecision.ASK && existing != ConflictDecision.REPLACE && existing != ConflictDecision.SKIP)
            throw new IllegalArgumentException("Choose Replace or Skip existing");
    }

    public TransferRequest(EndpointRef source, List<String> paths, EndpointRef destination, String directory) {
        this(source, paths, destination, directory, ConflictDecision.ASK);
    }

    public TransferRequest withExisting(ConflictDecision choice) { return new TransferRequest(source, paths, destination, directory, choice); }
}
