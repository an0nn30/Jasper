package dev.jasper.app.vault;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class VaultStorageTest {
    @TempDir Path temp;

    @Test void authenticatesHeaderAndCiphertextAndRejectsIncorrectPassword() throws Exception {
        var header = VaultCrypto.fresh();
        byte[] key = VaultCrypto.derive("test master".toCharArray(), header.salt());
        try (var data = new VaultData()) {
            data.logins.put(UUID.randomUUID(), new VaultData.Login("demo", "alice", null,
                    "unique synthetic password".toCharArray()));
            byte[] encrypted = VaultCrypto.encrypt(header, key, data);
            assertThat(new String(encrypted, java.nio.charset.StandardCharsets.ISO_8859_1))
                    .doesNotContain("unique synthetic password").doesNotContain("alice");
            try (var decoded = VaultCrypto.decrypt(encrypted, key)) {
                assertThat(decoded.logins.values().iterator().next().username).isEqualTo("alice");
            }
            byte[] wrong = new byte[32];
            assertThatThrownBy(() -> VaultCrypto.decrypt(encrypted, wrong)).isInstanceOf(IOException.class);
            for (int offset : new int[]{12, 40, 56, encrypted.length - 1}) {
                byte[] changed = encrypted.clone(); changed[offset] ^= 1;
                assertThatThrownBy(() -> VaultCrypto.decrypt(changed, key)).isInstanceOf(IOException.class);
            }
            assertThat(VaultCrypto.encrypt(header, key, data)).isNotEqualTo(encrypted);
        } finally { Arrays.fill(key, (byte) 0); }
    }

    @Test void rejectsMalformedLengthsReferencesAndTrailingBytes() throws Exception {
        assertThatThrownBy(() -> VaultCrypto.header(new byte[10])).isInstanceOf(IOException.class);
        var header = VaultCrypto.fresh();
        byte[] key = new byte[32];
        try (var data = new VaultData()) {
            byte[] plain = data.encode();
            assertThatThrownBy(() -> VaultData.decode(Arrays.copyOf(plain, plain.length + 1)))
                    .isInstanceOf(IOException.class);
            plain[12] = 127;
            assertThatThrownBy(() -> VaultData.decode(plain)).isInstanceOf(IOException.class);
            data.logins.put(UUID.randomUUID(), new VaultData.Login("demo", "user", UUID.randomUUID(), new char[0]));
            assertThatThrownBy(() -> VaultCrypto.encrypt(header, key, data)).isInstanceOf(IOException.class);
        }
    }

    @Test void atomicCreateDoesNotOverwriteAndReplaceDetectsConflicts() throws Exception {
        Path file = temp.resolve("private/vault.bin");
        byte[] first = {1, 2, 3};
        VaultFiles.publish(file, first, null, true);
        assertThatThrownBy(() -> VaultFiles.publish(file, new byte[]{4}, null, true))
                .isInstanceOf(IOException.class);
        assertThat(Files.readAllBytes(file)).containsExactly(first);
        assertThatThrownBy(() -> VaultFiles.publish(file, new byte[]{4}, new byte[32], false))
                .isInstanceOf(IOException.class);
        assertThat(Files.readAllBytes(file)).containsExactly(first);
        VaultFiles.publish(file, new byte[]{4}, VaultFiles.hash(first), false);
        assertThat(Files.readAllBytes(file)).containsExactly(4);
        if (Files.getFileStore(file).supportsFileAttributeView("posix")) {
            assertThat(Files.getPosixFilePermissions(file))
                    .isEqualTo(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        }
        try (var listing = Files.list(file.getParent())) {
            assertThat(listing.filter(p -> p.getFileName().toString().endsWith(".tmp")).toList()).isEmpty();
        }
    }

    @Test void boundedReadAndOwnedMaterialClearOnClose() throws Exception {
        Path large = temp.resolve("large");
        try (var out = new java.io.RandomAccessFile(large.toFile(), "rw")) {
            out.setLength(VaultFiles.MAX_FILE + 1L);
        }
        assertThatThrownBy(() -> VaultFiles.read(large)).isInstanceOf(IOException.class);
        char[] password = "demo".toCharArray();
        var material = new CredentialMaterial("alice", password, new byte[]{7}, new char[]{'p'});
        password[0] = 'x';
        char[] owned = material.password();
        assertThat(owned[0]).isEqualTo('d');
        assertThat(material.toString()).doesNotContain("demo");
        material.close();
        assertThat(owned).containsOnly((char) 0);
        assertThatThrownBy(material::password).isInstanceOf(IllegalStateException.class);
    }

    @Test void rejectsMalformedUnicodeInsteadOfChangingCredentialMetadata() throws Exception {
        String malformed = "name" + (char) 0xD800;
        try (var data = new VaultData()) {
            data.logins.put(UUID.randomUUID(), new VaultData.Login(malformed, "alice", null, new char[]{'p'}));
            assertThatThrownBy(data::encode).isInstanceOf(IOException.class);
        }
    }

    @Test void rejectsMalformedMasterPasswordRatherThanReplacingItsCharacters() {
        assertThatThrownBy(() -> VaultCrypto.derive(new char[]{'p', (char) 0xD800}, new byte[16]))
                .isInstanceOf(IOException.class);
    }

    @Test void refusesShortEncryptionKeysRatherThanWritingAnUnsupportedSecurityLevel() throws Exception {
        try (var data = new VaultData()) {
            assertThatThrownBy(() -> VaultCrypto.encrypt(VaultCrypto.fresh(), new byte[16], data))
                    .isInstanceOf(IOException.class);
        }
    }

    @Test void oversizedPublicationPreservesExistingFile() throws Exception {
        Path file = temp.resolve("bounded.bin");
        byte[] original = {1, 2, 3};
        VaultFiles.publish(file, original, null, true);
        byte[] oversized = new byte[VaultFiles.MAX_FILE + 1];
        assertThatThrownBy(() -> VaultFiles.publish(file, oversized, VaultFiles.hash(original), false))
                .isInstanceOf(IOException.class);
        assertThat(Files.readAllBytes(file)).containsExactly(original);
    }

    @Test void committedCopiesRetainStableReferencesAndOwnEverySecretArray() throws Exception {
        UUID keyId = UUID.randomUUID(), loginId = UUID.randomUUID();
        char[] password = {'p'}, phrase = {'s'}; byte[] bytes = {1, 2, 3};
        try (var data = new VaultData()) {
            data.settings = new VaultSettings(0, 365);
            data.keys.put(keyId, new VaultData.Key("key", "RSA", "SHA256:fixture", "ssh-rsa fixture", bytes, phrase));
            data.logins.put(loginId, new VaultData.Login("login", "alice", keyId, password));
            Arrays.fill(bytes, (byte) 0); Arrays.fill(phrase, (char) 0); Arrays.fill(password, (char) 0);
            char[] ownedPassword; byte[] ownedKey; char[] ownedPhrase;
            try (var copy = data.copy()) {
                assertThat(copy.settings).isEqualTo(new VaultSettings(0, 365));
                assertThat(copy.logins.get(loginId).keyId).isEqualTo(keyId);
                assertThat(copy.logins.get(loginId).password).containsExactly('p');
                assertThat(copy.keys.get(keyId).bytes).containsExactly(1, 2, 3);
                assertThat(copy.keys.get(keyId).passphrase).containsExactly('s');
                copy.keys.get(keyId).name = "renamed";
                assertThat(data.keys.get(keyId).name).isEqualTo("key");
                ownedPassword = copy.logins.get(loginId).password;
                ownedKey = copy.keys.get(keyId).bytes;
                ownedPhrase = copy.keys.get(keyId).passphrase;
            }
            assertThat(ownedPassword).containsOnly((char) 0);
            assertThat(ownedKey).containsOnly((byte) 0);
            assertThat(ownedPhrase).containsOnly((char) 0);
        }
    }

    @Test void competingLockNeverReplacesTheCommittedFile() throws Exception {
        Path file = temp.resolve("locked.bin");
        byte[] original = {1, 2, 3}; VaultFiles.publish(file, original, null, true);
        try (var channel = java.nio.channels.FileChannel.open(temp.resolve("locked.bin.lck"), StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            assertThat(lock.isValid()).isTrue();
            assertThatThrownBy(() -> VaultFiles.publish(file, new byte[]{4}, VaultFiles.hash(original), false))
                    .isInstanceOf(IOException.class);
        }
        assertThat(Files.readAllBytes(file)).containsExactly(original);
    }

    @Test void aggregatePayloadBoundRejectsLargeKeyCollections() throws Exception {
        try (var data = new VaultData()) {
            for (int i = 0; i < 16; i++) data.keys.put(UUID.randomUUID(),
                    new VaultData.Key("key" + i, "RSA", "SHA256:" + i, "ssh-rsa fixture", new byte[1_048_576], new char[0]));
            assertThatThrownBy(data::encode).isInstanceOf(IOException.class);
        }
    }

}
