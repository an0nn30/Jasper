package dev.jasper.vault.api;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/** One explicitly selected private-key source; the request ID binds it back to the caller's preview. */
public record SshKeySource(UUID requestId, String name, Path path) {
    public SshKeySource {
        Objects.requireNonNull(requestId); Objects.requireNonNull(path);
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A key needs a name");
        path = path.toAbsolutePath().normalize();
    }
}
