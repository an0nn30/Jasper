package dev.jasper.vault.service;

import dev.jasper.vault.model.ManagedSshKey;
import java.time.Instant;
import java.util.UUID;

/** Validated material owned by one import request until it commits or is discarded. */
public final class PreparedKey implements AutoCloseable {
    private final ManagedSshKey material;
    public PreparedKey(String name, String algorithm, String fingerprint, String publicKey, byte[] bytes, char[] phrase) {
        material = new ManagedSshKey(UUID.randomUUID(), name, algorithm, fingerprint, publicKey, bytes, phrase, Instant.now());
    }
    public String name() { return material.name(); }
    public String algorithm() { return material.algorithm(); }
    public String fingerprint() { return material.fingerprint(); }
    public String publicKey() { return material.publicKey(); }
    public ManagedSshKey toManaged(UUID id, Instant created) {
        return new ManagedSshKey(id, name(), algorithm(), fingerprint(), publicKey(), material.privateKey().clone(),
            material.passphrase() == null ? null : material.passphrase().clone(), created);
    }
    @Override public void close() { material.close(); }
}
