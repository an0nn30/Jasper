package dev.jasper.vault.crypto;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class VaultFileFormatTest {
    static final byte[] SALT = new byte[16], NONCE = new byte[12];
    static { Arrays.fill(SALT, (byte) 5); Arrays.fill(NONCE, (byte) 9); }

    @Test void roundTripsHeaderAndCiphertext() {
        var header = new VaultFileFormat.Header(true, SALT, NONCE);
        byte[] file = VaultFileFormat.assemble(header, new byte[] {1, 2, 3});
        assertThat(file).startsWith("JASPERVLT".getBytes()).hasSize(9 + 2 + 1 + 16 + 12 + 3);
        assertThat(file[9]).isEqualTo((byte) 1);
        assertThat(file[10]).isEqualTo((byte) 0);
        assertThat(file[11]).as("flags: bound").isEqualTo((byte) 1);
        VaultFileFormat.Parsed parsed = VaultFileFormat.parse(file);
        assertThat(parsed.header()).isEqualTo(header);
        assertThat(parsed.ciphertext()).containsExactly(1, 2, 3);
        assertThat(parsed.header().encode()).isEqualTo(Arrays.copyOf(file, VaultFileFormat.HEADER_LENGTH));
        assertThat(new VaultFileFormat.Header(false, SALT, NONCE).encode()[11]).isEqualTo((byte) 0);
    }

    @Test void rejectsWrongMagicVersionAndLengths() {
        byte[] file = VaultFileFormat.assemble(new VaultFileFormat.Header(false, SALT, NONCE), new byte[16]);
        byte[] magic = file.clone(); magic[0] = 'X';
        assertThatThrownBy(() -> VaultFileFormat.parse(magic)).isInstanceOf(CorruptVaultException.class).hasMessageContaining("not a Jasper vault");
        byte[] version = file.clone(); version[9] = 2;
        assertThatThrownBy(() -> VaultFileFormat.parse(version)).isInstanceOf(CorruptVaultException.class).hasMessageContaining("version 2");
        assertThatThrownBy(() -> VaultFileFormat.parse(Arrays.copyOf(file, 20))).isInstanceOf(CorruptVaultException.class);
        assertThatThrownBy(() -> new VaultFileFormat.Header(false, new byte[3], NONCE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VaultFileFormat.Header(false, SALT, new byte[3])).isInstanceOf(IllegalArgumentException.class);
    }
}
