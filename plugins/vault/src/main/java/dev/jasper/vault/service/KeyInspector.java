package dev.jasper.vault.service;

import dev.jasper.vault.crypto.SecureBytes;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.session.SessionContext;
import org.apache.sshd.common.config.keys.*;
import org.apache.sshd.common.util.security.SecurityUtils;

/** Bounded private-key inspection; no source bytes escape on failure. */
public final class KeyInspector {
    private KeyInspector() {}
    public static final class PassphraseRequired extends IOException { public PassphraseRequired() { super("Enter the key passphrase"); } }
    public static final class InvalidPassphrase extends IOException { public InvalidPassphrase() { super("Could not unlock the key; check its passphrase"); } }
public static PreparedKey read(String name, Path source, char[] passphrase)
        throws IOException, GeneralSecurityException {
    byte[] encoded = null;
    char[] ownedPhrase = passphrase == null ? null : passphrase.clone();
    List<KeyPair> pairs = new ArrayList<>();
    boolean[] requested = {false}, rejected = {false};
    try {
        Path real = source.toRealPath();
        if (!Files.isRegularFile(real)) throw new IOException("Select a regular private-key file");
        encoded = readBounded(real, 1_048_576);
        char[] supplied = ownedPhrase;
        FilePasswordProvider provider = new FilePasswordProvider() {
            public String getPassword(SessionContext session, NamedResource resource, int retry)
                    throws IOException {
                requested[0] = true;
                if (supplied == null) throw new PassphraseRequired();
                return new String(supplied);
            }
            public ResourceDecodeResult handleDecodeAttemptResult(SessionContext session,
                    NamedResource resource, int retry, String password, Exception error) {
                if (error != null) rejected[0] = true;
                return ResourceDecodeResult.TERMINATE;
            }
        };
        try (InputStream in = new ByteArrayInputStream(encoded)) {
            Iterable<KeyPair> loaded = SecurityUtils.loadKeyPairIdentities(null,
                NamedResource.ofName("SSH import"), in, provider);
            if (loaded != null) for (KeyPair pair : loaded) pairs.add(pair);
        } catch (IOException | GeneralSecurityException | RuntimeException failure) {
            if (requested[0] && supplied == null) throw new PassphraseRequired();
            if (rejected[0]) throw new InvalidPassphrase();
            throw new IOException("Unsupported or malformed private key");
        }
        if (pairs.size() != 1) throw new IOException("Expected one supported private key");
        PublicKey pub = pairs.getFirst().getPublic();
        checkCompanion(source.resolveSibling(source.getFileName() + ".pub"), pub);
        PreparedKey prepared = new PreparedKey(name, KeyUtils.getKeyType(pub),
            KeyUtils.getFingerPrint(pub), PublicKeyEntry.toString(pub), encoded, ownedPhrase);
        encoded = null; ownedPhrase = null;
        return prepared;
    } finally {
        SecureBytes.zero(encoded); SecureBytes.zero(ownedPhrase); SecureBytes.zero(passphrase);
        for (KeyPair pair : pairs) {
            try { pair.getPrivate().destroy(); }
            catch (javax.security.auth.DestroyFailedException ignored) { }
        }
    }
}
private static byte[] readBounded(Path path, int limit) throws IOException {
    byte[] buffer = new byte[limit + 1];
    try (InputStream in = Files.newInputStream(path)) {
        int count = in.readNBytes(buffer, 0, buffer.length);
        if (count == 0) throw new IOException("Key file is empty");
        if (count > limit) throw new IOException("Key file exceeds the supported size");
        return Arrays.copyOf(buffer, count);
    } finally { SecureBytes.zero(buffer); }
}
private static void checkCompanion(Path companion, PublicKey key)
        throws IOException, GeneralSecurityException {
    if (Files.notExists(companion)) return;
    byte[] bytes = readBounded(companion, 65_536);
    try {
        PublicKeyEntry entry = PublicKeyEntry.parsePublicKeyEntry(
            new String(bytes, StandardCharsets.UTF_8).strip());
        if (entry == null || !KeyUtils.compareKeys(key, entry.resolvePublicKey(null, null, null)))
            throw new IOException("Public key file does not match the private key");
    } finally { SecureBytes.zero(bytes); }
}
}
