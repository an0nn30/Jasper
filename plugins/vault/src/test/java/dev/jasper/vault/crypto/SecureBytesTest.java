package dev.jasper.vault.crypto;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SecureBytesTest {
    @Test void utf8RoundTripsAndZeroes() {
        char[] text = "pässword é".toCharArray();
        byte[] bytes = SecureBytes.utf8(text);
        assertThat(bytes).isEqualTo("pässword é".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        char[] back = SecureBytes.chars(bytes);
        assertThat(back).isEqualTo(text);
        SecureBytes.zero(bytes);
        SecureBytes.zero(back);
        assertThat(bytes).containsOnly((byte) 0);
        assertThat(back).containsOnly((char) 0);
    }

    @Test void concatCopiesBothOperands() {
        byte[] a = {1, 2}, b = {3};
        assertThat(SecureBytes.concat(a, b)).containsExactly(1, 2, 3);
        assertThat(SecureBytes.concat(a, null)).containsExactly(1, 2);
    }

    @Test void zeroToleratesNull() {
        SecureBytes.zero((byte[]) null);
        SecureBytes.zero((char[]) null);
    }
}
