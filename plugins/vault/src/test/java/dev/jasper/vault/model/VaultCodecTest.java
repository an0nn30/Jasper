package dev.jasper.vault.model;

import dev.jasper.vault.crypto.CorruptVaultException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class VaultCodecTest {
    static Vault sample() {
        var vault = new Vault();
        vault.accounts().add(new Account(UUID.randomUUID(), "prod", "deploy", new Auth.Password("s3cret".toCharArray()), Instant.ofEpochSecond(1), Instant.ofEpochSecond(2)));
        vault.accounts().add(new Account(UUID.randomUUID(), "bastion", "ops", new Auth.Key(Path.of("/keys/id_ed25519"), null), Instant.ofEpochSecond(3), Instant.ofEpochSecond(4)));
        vault.accounts().add(new Account(UUID.randomUUID(), "both", "root", new Auth.KeyAndPassword(Path.of("/keys/rsa"), "pp".toCharArray(), "pw".toCharArray()), Instant.ofEpochSecond(5), Instant.ofEpochSecond(6)));
        vault.keys().add(new SshKey(UUID.randomUUID(), "laptop", "ed25519", "SHA256:abc", "me@laptop", Path.of("/keys/a"), Path.of("/keys/a.pub"), Instant.ofEpochSecond(7)));
        vault.notes().add(new Note(UUID.randomUUID(), "wifi", "hunter2 é".toCharArray(), Instant.ofEpochSecond(8)));
        vault.grants().add(new Grant("dev.jasper.ssh", vault.accounts().getFirst().id()));
        return vault;
    }

    @Test void roundTripsEveryKind() {
        Vault original = sample();
        Vault copy = VaultCodec.decode(VaultCodec.encode(original));
        assertThat(copy.accounts()).hasSize(3);
        assertThat(copy.accounts().get(0).auth()).isInstanceOf(Auth.Password.class);
        assertThat(((Auth.Password) copy.accounts().get(0).auth()).password()).isEqualTo("s3cret".toCharArray());
        assertThat(copy.accounts().get(0)).usingRecursiveComparison().ignoringFields("auth").isEqualTo(original.accounts().get(0));
        Auth.Key key = (Auth.Key) copy.accounts().get(1).auth();
        assertThat(key.keyPath()).isEqualTo(Path.of("/keys/id_ed25519"));
        assertThat(key.passphrase()).isNull();
        Auth.KeyAndPassword both = (Auth.KeyAndPassword) copy.accounts().get(2).auth();
        assertThat(both.passphrase()).isEqualTo("pp".toCharArray());
        assertThat(both.password()).isEqualTo("pw".toCharArray());
        assertThat(copy.keys()).singleElement().isEqualTo(original.keys().getFirst());
        assertThat(copy.notes().getFirst().text()).isEqualTo("hunter2 é".toCharArray());
        assertThat(copy.notes().getFirst().name()).isEqualTo("wifi");
        assertThat(copy.grants()).containsExactlyElementsOf(original.grants());
    }

    @Test void anEmptyVaultRoundTrips() {
        Vault copy = VaultCodec.decode(VaultCodec.encode(new Vault()));
        assertThat(copy.accounts()).isEmpty();
        assertThat(copy.grants()).isEmpty();
    }

    @Test void rejectsAnUnknownVersion() {
        byte[] bytes = VaultCodec.encode(new Vault());
        bytes[1] = 9;
        assertThatThrownBy(() -> VaultCodec.decode(bytes)).isInstanceOf(CorruptVaultException.class).hasMessageContaining("version");
        assertThatThrownBy(() -> VaultCodec.decode(new byte[] {0, 1, 0})).isInstanceOf(CorruptVaultException.class);
    }

    @Test void zeroClearsEverySecretAndRemoveTakesGrantsAlong() {
        Vault vault = sample();
        UUID first = vault.accounts().getFirst().id();
        assertThat(vault.account(first)).isPresent();
        vault.remove(first);
        assertThat(vault.account(first)).isEmpty();
        assertThat(vault.grants()).as("the grant for the removed account is gone").isEmpty();
        char[] password = ((Auth.KeyAndPassword) vault.accounts().get(1).auth()).password();
        char[] note = vault.notes().getFirst().text();
        vault.zero();
        assertThat(password).containsOnly((char) 0);
        assertThat(note).containsOnly((char) 0);
        assertThat(vault.accounts()).isEmpty();
    }
}
