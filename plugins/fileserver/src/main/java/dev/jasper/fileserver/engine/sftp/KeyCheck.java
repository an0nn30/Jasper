package dev.jasper.fileserver.engine.sftp;

import dev.jasper.fileserver.engine.EngineFailure;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;

/** A server's authorized keys as sshd keys. The editor already checked the lines; one sshd cannot use fails the start. */
final class KeyCheck {
    private final List<PublicKey> keys;

    KeyCheck(List<String> lines) throws EngineFailure {
        SshSecurity.ensure();
        var resolved = new ArrayList<PublicKey>();
        for (int i = 0; i < lines.size(); i++) {
            try {
                AuthorizedKeyEntry entry = AuthorizedKeyEntry.parseAuthorizedKeyEntry(lines.get(i));
                if (entry != null) resolved.add(entry.resolvePublicKey(null, PublicKeyEntryResolver.FAILING));
            } catch (IOException | GeneralSecurityException | IllegalArgumentException failure) {
                throw new EngineFailure("Authorized key " + (i + 1) + " can't be used: " + failure.getMessage(), failure);
            }
        }
        keys = List.copyOf(resolved);
    }

    boolean isEmpty() { return keys.isEmpty(); }

    boolean accepts(PublicKey offered) { return keys.stream().anyMatch(key -> KeyUtils.compareKeys(key, offered)); }
}
