package dev.jasper.app.vault;

import java.io.*;
import java.security.*;
import java.security.interfaces.*;
import java.util.*;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.util.security.SecurityUtils;

final class VaultKeys {
    static VaultService.KeyPreview inspect(byte[] bytes, char[] passphrase) throws IOException {
        if (bytes.length < 1 || bytes.length > VaultData.MAX_KEY || passphrase.length > VaultData.MAX_SECRET)
            throw new IOException("Invalid private key size");
        List<KeyPair> keys = new ArrayList<>();
        try (var in = new ByteArrayInputStream(bytes)) {
            var parsed = SecurityUtils.loadKeyPairIdentities(null, NamedResource.ofName("vault import"), in,
                    (session, resource, retry) -> retry == 0 ? new String(passphrase) : null);
            if (parsed != null) for (KeyPair pair : parsed) {
                keys.add(pair); if (keys.size() > 1) throw new IOException("Import exactly one private key");
            }
            if (keys.size() != 1 || keys.getFirst().getPrivate() == null) throw new IOException("Private key required");
            PublicKey key = keys.getFirst().getPublic();
            String publicText = PublicKeyEntry.toString(key);
            String[] pieces = publicText.split(" ");
            String type = pieces[0];
            boolean allowed = type.equals("ssh-ed25519")
                    || (type.equals("ssh-rsa") && key instanceof RSAKey rsa && rsa.getModulus().bitLength() >= 2048)
                    || Set.of("ecdsa-sha2-nistp256", "ecdsa-sha2-nistp384", "ecdsa-sha2-nistp521").contains(type);
            if (!allowed || pieces.length != 2) throw new IOException("Supported keys: Ed25519, RSA 2048+, ECDSA P-256/P-384/P-521");
            String fingerprint = "SHA256:" + Base64.getEncoder().withoutPadding()
                    .encodeToString(VaultFiles.hash(Base64.getDecoder().decode(pieces[1])));
            return new VaultService.KeyPreview(type, fingerprint, publicText);
        } catch (GeneralSecurityException | RuntimeException e) {
            throw new IOException("Cannot parse private key; check format and passphrase");
        } catch (IOException e) {
            // Library exceptions may include imported lines or passphrases. Preserve no raw cause/message.
            throw new IOException("Cannot parse one supported private key; check format and passphrase");
        } finally {
            for (KeyPair pair : keys) try { pair.getPrivate().destroy(); }
            catch (javax.security.auth.DestroyFailedException | NullPointerException ignored) { }
        }
    }
}
