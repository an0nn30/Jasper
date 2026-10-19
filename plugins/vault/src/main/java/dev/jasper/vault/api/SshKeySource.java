package dev.jasper.vault.api;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/**
 * One private key file to import with {@link VaultApi#importSshKeys}.
 *
 * @param requestId the caller's id for this source, unique in the batch; the result's
 *     {@link SshKeyImportResult#keys() keys} answer by it
 * @param name the name the key is stored under; a key Vault already stores with the same fingerprint keeps its own,
 *     and a key file Vault refers to with the same fingerprint becomes a stored key under this name, keeping its id
 * @param path the private key file; made absolute and normalized
 */
public record SshKeySource(UUID requestId, String name, Path path) {
    /** Rejects a null request id or path and a blank name. */
    public SshKeySource {
        Objects.requireNonNull(requestId); Objects.requireNonNull(path);
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A key needs a name");
        path = path.toAbsolutePath().normalize();
    }
}
