package dev.jasper.vault.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Durable imported/reused keys and explicitly authorized existing credentials, without secrets. */
public record SshKeyImportResult(Map<UUID, CredentialDescriptor> keys, List<CredentialDescriptor> selectedCredentials) {
    public SshKeyImportResult { keys = Map.copyOf(keys); selectedCredentials = List.copyOf(selectedCredentials); }
}
