package dev.jasper.vault.api;

import java.util.Objects;
import java.util.UUID;

/**
 * A credential without its secret, as {@link VaultApi#credentials()} lists it and the pickers return it.
 *
 * @param id the entry's id, which {@link VaultApi#credential(UUID)} takes
 * @param name the entry's name in Vault
 * @param subtitle the username for an account, the fingerprint for an SSH key; empty when there is none
 * @param kind what the credential carries
 * @param managedKey whether this is an SSH key stored in Vault rather than a key file Vault refers to by its path;
 *     false for accounts
 */
public record CredentialDescriptor(UUID id, String name, String subtitle, Kind kind, boolean managedKey) {
    /**
     * A descriptor whose {@code managedKey} is false.
     *
     * @param id the entry's id
     * @param name the entry's name in Vault
     * @param subtitle the username for an account, the fingerprint for an SSH key
     * @param kind what the credential carries
     */
    public CredentialDescriptor(UUID id, String name, String subtitle, Kind kind) { this(id, name, subtitle, kind, false); }
    /** Rejects a null id, name or kind; a null subtitle becomes empty. */
    public CredentialDescriptor {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(name, "name"); Objects.requireNonNull(kind, "kind");
        subtitle = subtitle == null ? "" : subtitle;
    }
}
