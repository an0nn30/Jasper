package dev.jasper.vault.crypto;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class VaultCipherTest {
    static final byte[] KEY = new byte[32], OTHER = new byte[32], SALT = new byte[16];
    static { Arrays.fill(KEY, (byte) 1); Arrays.fill(OTHER, (byte) 2); }

    @Test void sealsAndOpensWithAFreshNoncePerCall() {
        byte[] first = VaultCipher.seal(KEY, SALT, true, "hello".getBytes());
        byte[] second = VaultCipher.seal(KEY, SALT, true, "hello".getBytes());
        assertThat(first).isNotEqualTo(second);
        VaultFileFormat.Parsed parsed = VaultFileFormat.parse(first);
        assertThat(parsed.header().bound()).isTrue();
        assertThat(parsed.header().salt()).isEqualTo(SALT);
        assertThat(VaultCipher.open(KEY, parsed)).isEqualTo("hello".getBytes());
        assertThat(VaultCipher.open(KEY, VaultFileFormat.parse(second))).isEqualTo("hello".getBytes());
    }

    @Test void aWrongKeyOrATouchedHeaderFailsTheTag() {
        byte[] file = VaultCipher.seal(KEY, SALT, true, "hello".getBytes());
        assertThatThrownBy(() -> VaultCipher.open(OTHER, VaultFileFormat.parse(file))).isInstanceOf(WrongPasswordException.class);
        byte[] unbound = file.clone(); unbound[11] = 0;
        assertThatThrownBy(() -> VaultCipher.open(KEY, VaultFileFormat.parse(unbound))).as("flags are authenticated").isInstanceOf(WrongPasswordException.class);
        byte[] body = file.clone(); body[body.length - 1] ^= 1;
        assertThatThrownBy(() -> VaultCipher.open(KEY, VaultFileFormat.parse(body))).isInstanceOf(WrongPasswordException.class);
    }

    @Test void randomSaltsDiffer() {
        assertThat(VaultCipher.randomSalt()).hasSize(16).isNotEqualTo(VaultCipher.randomSalt());
    }
}
