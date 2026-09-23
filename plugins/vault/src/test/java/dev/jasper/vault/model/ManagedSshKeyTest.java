package dev.jasper.vault.model;

import dev.jasper.vault.api.Credential;
import dev.jasper.vault.api.Kind;
import dev.jasper.vault.crypto.CorruptVaultException;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ManagedSshKeyTest {
    @Test void readsIndependentLegacyFixture() throws Exception {
        byte[] fixture;
        try (var in = getClass().getResourceAsStream("/vault-v1-all-kinds.bin")) { fixture = in.readAllBytes(); }
        Vault v = VaultCodec.decode(fixture);
        assertThat(v.accounts()).hasSize(3); assertThat(v.keys()).hasSize(1); assertThat(v.notes()).hasSize(1);
        assertThat(v.accounts().get(1).auth()).isInstanceOf(Auth.Key.class);
        assertThat(v.grants()).containsExactly(new Grant("dev.jasper.remote", new UUID(0, 1)));
        assertThat(VaultCodec.encode(v)).isEqualTo(fixture); v.zero();
    }
    @Test void roundTripOwnsSecretsAndRemovalClearsThem() {
        UUID id = UUID.randomUUID();
        byte[] source = {1, 2, 3}; char[] phrase = "fixture".toCharArray();
        Vault vault = new Vault();
        vault.requireManagedFormat();
        vault.managedKeys().add(new ManagedSshKey(id, "key", "ssh-ed25519", "SHA256:test", "public", source, phrase, Instant.EPOCH));
        byte[] encoded = VaultCodec.encode(vault);
        Vault copy = VaultCodec.decode(encoded);
        byte[] copied = copy.managedKey(id).orElseThrow().privateKey();
        assertThat(copied).isEqualTo(source).isNotSameAs(source);
        vault.remove(id);
        assertThat(source).containsOnly((byte) 0); assertThat(phrase).containsOnly((char) 0);
        assertThat(copied).containsExactly(1, 2, 3);
        copy.zero(); assertThat(copied).containsOnly((byte) 0);
        Arrays.fill(encoded, (byte) 0);
    }
    @Test void legacyPayloadStaysLegacyAndManagedPayloadNeverDowngrades() {
        byte[] legacy = {0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        Vault vault = VaultCodec.decode(legacy);
        assertThat(VaultCodec.encode(vault)).isEqualTo(legacy);
        vault.requireManagedFormat();
        assertThat(VaultCodec.decode(VaultCodec.encode(vault)).payloadVersion()).isEqualTo(2);
    }
    @Test void managedCredentialIsOwnedAndCannotHaveTwoSources() {
        byte[] bytes = {1, 2}; char[] phrase = {'p'};
        var credential = new Credential(UUID.randomUUID(), "key", Kind.SSH_KEY, null, null, null, bytes, phrase);
        assertThat(credential.keyBytes()).contains(bytes);
        credential.close();
        assertThat(bytes).containsOnly((byte) 0); assertThat(phrase).containsOnly((char) 0);
        assertThatThrownBy(credential::keyBytes).isInstanceOf(IllegalStateException.class);
    }
    @Test void rejectsEveryTruncatedManagedPayloadAndTrailingData() {
        Vault vault = new Vault(); vault.requireManagedFormat();
        vault.managedKeys().add(new ManagedSshKey(UUID.randomUUID(), "key", "a", "f", "p", new byte[]{1, 2}, new char[]{'s'}, Instant.EPOCH));
        byte[] encoded = VaultCodec.encode(vault);
        for (int end = 0; end < encoded.length; end++) {
            byte[] cut = Arrays.copyOf(encoded, end);
            assertThatThrownBy(() -> VaultCodec.decode(cut)).isInstanceOf(CorruptVaultException.class);
        }
        assertThatThrownBy(() -> VaultCodec.decode(Arrays.copyOf(encoded, encoded.length + 1))).isInstanceOf(CorruptVaultException.class);
        vault.zero();
    }

    @Test void encodingRejectsDuplicateIdsBeforeWriting() {
        var vault = new Vault(); UUID id = UUID.randomUUID();
        vault.managedKeys().add(new ManagedSshKey(id, "managed", "a", "f", "p", new byte[]{1}, null, Instant.EPOCH));
        vault.keys().add(new SshKey(id, "legacy", "a", "f", "", java.nio.file.Path.of("key"), java.nio.file.Path.of("key.pub"), Instant.EPOCH));
        try { assertThatThrownBy(() -> VaultCodec.encode(vault)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate entry ID"); }
        finally { vault.zero(); }
    }
}
