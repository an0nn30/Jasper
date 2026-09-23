package dev.jasper.vault.model;

import dev.jasper.vault.crypto.SecureBytes;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A private key owned by the encrypted Vault. Constructor consumes the secret arrays. */
public final class ManagedSshKey implements AutoCloseable {
    public static final int MAX_BYTES = 1_048_576;
    private final UUID id;
    private final String name, algorithm, fingerprint, publicKey;
    private final byte[] privateKey;
    private final char[] passphrase;
    private final Instant created;
    private boolean closed;
    public ManagedSshKey(UUID id, String name, String algorithm, String fingerprint, String publicKey,
                         byte[] privateKey, char[] passphrase, Instant created) {
        try {
            Objects.requireNonNull(id); Objects.requireNonNull(created); Objects.requireNonNull(privateKey);
            Objects.requireNonNull(algorithm); Objects.requireNonNull(fingerprint); Objects.requireNonNull(publicKey);
            if (name == null || name.isBlank()) throw new IllegalArgumentException("A key needs a name");
            if (privateKey.length == 0 || privateKey.length > MAX_BYTES) throw new IllegalArgumentException("Unsupported private-key size");
        } catch (RuntimeException failure) { SecureBytes.zero(privateKey); SecureBytes.zero(passphrase); throw failure; }
        this.id = id; this.name = name; this.algorithm = algorithm; this.fingerprint = fingerprint;
        this.publicKey = publicKey; this.privateKey = privateKey; this.passphrase = passphrase; this.created = created;
    }
    public UUID id() { return id; }
    public String name() { return name; }
    public String algorithm() { return algorithm; }
    public String fingerprint() { return fingerprint; }
    public String publicKey() { return publicKey; }
    public Instant created() { return created; }
    public byte[] privateKey() { open(); return privateKey; }
    public char[] passphrase() { open(); return passphrase; }
    public ManagedSshKey copy() { return renamed(name); }
    public ManagedSshKey renamed(String name) {
        open(); return new ManagedSshKey(id, name, algorithm, fingerprint, publicKey, privateKey.clone(),
            passphrase == null ? null : passphrase.clone(), created);
    }
    private void open() { if (closed) throw new IllegalStateException("The managed key is closed"); }
    @Override public void close() { if (closed) return; closed = true; SecureBytes.zero(privateKey); SecureBytes.zero(passphrase); }
    @Override public String toString() { return "ManagedSshKey[" + name + (closed ? ", closed" : "") + "]"; }
}
