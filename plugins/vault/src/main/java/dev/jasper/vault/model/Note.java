package dev.jasper.vault.model;

import dev.jasper.vault.crypto.SecureBytes;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A secure note; never handed to other plugins. */
public record Note(UUID id, String name, char[] text, Instant updated) {
    public Note {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(text, "text"); Objects.requireNonNull(updated, "updated");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A note needs a name");
    }
    public void zero() { SecureBytes.zero(text); }
}
