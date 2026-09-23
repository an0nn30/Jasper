package dev.jasper.remote.hosts;

import java.util.Objects;
import java.util.UUID;

/** How a host authenticates: a Vault credential by id, or whatever the SSH agent holds. */
public sealed interface Auth {
    Agent AGENT = new Agent();

    record Vault(UUID credentialId) implements Auth {
        public Vault { Objects.requireNonNull(credentialId, "credentialId"); }
    }

    record Agent() implements Auth { }
}
