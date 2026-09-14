package dev.jasper.app.vault;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public record VaultSnapshot(boolean exists, boolean locked, List<LoginInfo> logins,
        List<KeyInfo> keys, VaultSettings settings, Instant rememberedUntil,
        long revision, String deviceWarning) {
    public VaultSnapshot { logins = List.copyOf(logins); keys = List.copyOf(keys); }
    public record LoginInfo(UUID id, String name, String username, UUID keyId, boolean hasPassword) {}
    public record KeyInfo(UUID id, String name, String algorithm, String fingerprint,
                          String publicKey, int loginUses) {}
}
