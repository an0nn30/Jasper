package dev.jasper.vault.api;

import java.util.Objects;
import java.util.UUID;

/** A credential without its secret: the username for an account, the fingerprint for a key, as {@code subtitle}. */
public record CredentialDescriptor(UUID id, String name, String subtitle, Kind kind) {
    public CredentialDescriptor {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(name, "name"); Objects.requireNonNull(kind, "kind");
        subtitle = subtitle == null ? "" : subtitle;
    }
}
