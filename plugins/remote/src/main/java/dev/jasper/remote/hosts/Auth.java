package dev.jasper.remote.hosts;

import java.util.Objects;
import java.util.UUID;

/** How a host authenticates: a Vault credential by id, or whatever the SSH agent holds. */
public sealed interface Auth {
    Agent AGENT = new Agent();

    record Vault(UUID credentialId) implements Auth {
        public Vault { Objects.requireNonNull(credentialId, "credentialId"); }
    }

    record VaultKeys(java.util.List<UUID> credentialIds) implements Auth {
        public VaultKeys {
            credentialIds = java.util.List.copyOf(new java.util.LinkedHashSet<>(credentialIds));
            if (credentialIds.isEmpty()) throw new IllegalArgumentException("Choose at least one Vault key");
        }
    }

    record Agent() implements Auth { }
}
