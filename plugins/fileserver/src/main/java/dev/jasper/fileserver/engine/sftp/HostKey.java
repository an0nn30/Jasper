package dev.jasper.fileserver.engine.sftp;

import dev.jasper.fileserver.engine.EngineFailure;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.List;
import java.util.Optional;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.digest.BuiltinDigests;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;

/**
 * The SFTP servers' host key: one Ed25519 key in {@code data/hostkey}, created on first use by sshd, which writes it
 * owner-only. sshd replaces a key file it cannot read with a new key, so the fingerprint then changes. The
 * fingerprint is in OpenSSH form ({@code SHA256:…}).
 *
 * <p>Thread-safe. Every method does file I/O except {@link #cachedFingerprint()}, so call them off the UI thread.
 */
public final class HostKey {
    private final Path file;
    private SimpleGeneratorHostKeyProvider provider;
    private volatile String fingerprint;

    public HostKey(Path file) { this.file = file; }

    /** The key for a server, created if there is none yet. */
    synchronized KeyPairProvider provider() throws EngineFailure {
        if (provider != null) return provider;
        SshSecurity.ensure();
        try { Files.createDirectories(file.getParent()); }
        catch (IOException failure) { throw new EngineFailure("Could not create the SSH host key in " + file + ": " + failure.getMessage(), failure); }
        var created = new SimpleGeneratorHostKeyProvider(file);
        created.setAlgorithm("Ed25519"); // never "EdDSA": with BouncyCastle alone it fails silently
        List<KeyPair> keys;
        try { keys = created.loadKeys(null); }
        catch (RuntimeException failure) { throw new EngineFailure("The SSH host key in " + file + " can't be read", failure); }
        // sshd falls back to a throwaway key, without an exception, when it cannot write the file.
        if (keys.isEmpty() || !Files.isRegularFile(file)) throw new EngineFailure("Could not create the SSH host key in " + file);
        fingerprint = KeyUtils.getFingerPrint(BuiltinDigests.sha256, keys.getFirst().getPublic());
        provider = created;
        return created;
    }

    /** The existing key's fingerprint; empty when none was created yet. Never creates one. */
    public synchronized Optional<String> fingerprint() throws EngineFailure {
        if (provider == null && !Files.isRegularFile(file)) return Optional.empty();
        provider();
        return Optional.of(fingerprint);
    }

    /** The fingerprint already known in memory, without touching the disk. */
    public Optional<String> cachedFingerprint() { return Optional.ofNullable(fingerprint); }
}
