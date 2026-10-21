package dev.jasper.vault.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What {@link VaultApi#importSshKeys} stored and allowed, without secrets. The importing plugin holds a saved grant for
 * every credential named here.
 *
 * @param keys each source's {@link SshKeySource#requestId() request id} to the stored key now holding it, imported or
 *     reused because Vault already stored a key with the same fingerprint
 * @param selectedCredentials the existing credentials the caller named, in its order without repeats
 */
public record SshKeyImportResult(Map<UUID, CredentialDescriptor> keys, List<CredentialDescriptor> selectedCredentials) {
    /** Copies both collections. */
    public SshKeyImportResult { keys = Map.copyOf(keys); selectedCredentials = List.copyOf(selectedCredentials); }
}
