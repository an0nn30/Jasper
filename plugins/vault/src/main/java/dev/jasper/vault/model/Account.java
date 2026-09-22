package dev.jasper.vault.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A login: a username with a password, a key, or both. */
public record Account(UUID id, String name, String username, Auth auth, Instant created, Instant updated) {
    public Account {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(auth, "auth");
        Objects.requireNonNull(created, "created"); Objects.requireNonNull(updated, "updated");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("An account needs a name");
        username = username == null ? "" : username;
    }
}
