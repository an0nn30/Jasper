package dev.jasper.vault.service;

import dev.jasper.vault.keygen.KeyGenerator;
import dev.jasper.vault.keygen.KeyAlgorithm;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.*;

class KeyInspectorTest {
    @ParameterizedTest @EnumSource(KeyAlgorithm.class)
    void derivesPublicKeyWithoutCompanion(KeyAlgorithm algorithm, @TempDir Path dir) throws Exception {
        boolean ec = algorithm == KeyAlgorithm.ECDSA_P256 || algorithm == KeyAlgorithm.ECDSA_P384;
        var key = new KeyGenerator(dir).generate(algorithm, "key", "test", ec ? "secret".toCharArray() : null);
        String pub = Files.readString(key.publicPath()); Files.delete(key.publicPath());
        try (var inspected = KeyInspector.read("key", key.privatePath(), ec ? "secret".toCharArray() : null)) {
            assertThat(inspected.fingerprint()).isEqualTo(key.fingerprint());
            assertThat(inspected.publicKey().split(" ")[1]).isEqualTo(pub.split(" ")[1]);
        }
    }
    @Test void encryptedKeyRequiresCorrectPassphraseAndConsumesIt(@TempDir Path dir) throws Exception {
        var key = new KeyGenerator(dir).generate(KeyAlgorithm.ED25519, "key", "test", "secret".toCharArray());
        assertThatThrownBy(() -> KeyInspector.read("key", key.privatePath(), null)).isInstanceOf(KeyInspector.PassphraseRequired.class);
        char[] wrong = "wrong".toCharArray();
        assertThatThrownBy(() -> KeyInspector.read("key", key.privatePath(), wrong)).isInstanceOf(KeyInspector.InvalidPassphrase.class);
        assertThat(wrong).containsOnly((char) 0);
        char[] right = "secret".toCharArray();
        try (var inspected = KeyInspector.read("key", key.privatePath(), right)) { assertThat(inspected.fingerprint()).isEqualTo(key.fingerprint()); }
        assertThat(right).containsOnly((char) 0);
    }
    @Test void refusesMismatchedCompanionAndOversizedSource(@TempDir Path dir) throws Exception {
        var generator = new KeyGenerator(dir);
        var a = generator.generate(KeyAlgorithm.ED25519, "a", "test");
        var b = generator.generate(KeyAlgorithm.ED25519, "b", "test");
        Files.writeString(a.publicPath(), Files.readString(b.publicPath()));
        assertThatThrownBy(() -> KeyInspector.read("a", a.privatePath(), null)).hasMessageContaining("does not match");
        Files.write(a.privatePath(), new byte[1_048_577]);
        assertThatThrownBy(() -> KeyInspector.read("a", a.privatePath(), null)).hasMessageContaining("size");
    }
}
