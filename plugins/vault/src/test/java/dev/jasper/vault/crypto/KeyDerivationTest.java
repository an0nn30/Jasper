package dev.jasper.vault.crypto;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class KeyDerivationTest {
    static final byte[] SALT = new byte[16];
    static { Arrays.fill(SALT, (byte) 7); }

    @Test void isDeterministicAndThirtyTwoBytes() {
        byte[] first = KeyDerivation.derive("pw".getBytes(), null, SALT);
        byte[] second = KeyDerivation.derive("pw".getBytes(), null, SALT);
        assertThat(first).hasSize(KeyDerivation.KEY_LENGTH).isEqualTo(second);
    }

    @Test void deviceSecretSaltAndPasswordAllChangeTheKey() {
        byte[] base = KeyDerivation.derive("pw".getBytes(), null, SALT);
        byte[] device = new byte[32]; Arrays.fill(device, (byte) 1);
        assertThat(KeyDerivation.derive("pw".getBytes(), device, SALT)).isNotEqualTo(base);
        assertThat(KeyDerivation.derive("pX".getBytes(), null, SALT)).isNotEqualTo(base);
        byte[] otherSalt = new byte[16]; otherSalt[0] = 1;
        assertThat(KeyDerivation.derive("pw".getBytes(), null, otherSalt)).isNotEqualTo(base);
    }

    @Test void rejectsAWrongSaltLength() {
        assertThatThrownBy(() -> KeyDerivation.derive("pw".getBytes(), null, new byte[8]))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("16");
    }
}
