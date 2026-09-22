package dev.jasper.vault.model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A standalone SSH key the vault generated or imported; the private key stays on disk at {@code privatePath}. */
public record SshKey(UUID id, String name, String algorithm, String fingerprint, String comment, Path privatePath, Path publicPath, Instant created) {
    public SshKey {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(privatePath, "privatePath");
        Objects.requireNonNull(publicPath, "publicPath"); Objects.requireNonNull(created, "created");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A key needs a name");
        algorithm = algorithm == null ? "" : algorithm; fingerprint = fingerprint == null ? "" : fingerprint; comment = comment == null ? "" : comment;
    }
}
