package dev.jasper.remote.sftp;

import java.util.Objects;

/** Remote paths stay strings; only LocalEndpoint interprets a path on the local filesystem. */
public record FileLocation(String endpointId, String path) {
    public FileLocation { Objects.requireNonNull(endpointId); Objects.requireNonNull(path); }
}
