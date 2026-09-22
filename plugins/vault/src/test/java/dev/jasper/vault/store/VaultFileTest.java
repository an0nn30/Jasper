package dev.jasper.vault.store;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class VaultFileTest {
    @Test void writesAtomicallyAndReadsBack(@TempDir Path dir) throws Exception {
        var file = new VaultFile(dir.resolve("vault.jv"));
        assertThat(file.exists()).isFalse();
        file.write(new byte[] {1, 2, 3});
        assertThat(file.exists()).isTrue();
        assertThat(file.read()).containsExactly(1, 2, 3);
        file.write(new byte[] {4});
        assertThat(file.read()).containsExactly(4);
        try (var listing = Files.list(dir)) { assertThat(listing).as("no temp file left").containsExactly(dir.resolve("vault.jv")); }
        if (Files.getFileStore(dir).supportsFileAttributeView("posix"))
            assertThat(Files.getPosixFilePermissions(file.path())).containsExactlyInAnyOrder(java.nio.file.attribute.PosixFilePermission.OWNER_READ, java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
    }

    @Test void createsMissingParents(@TempDir Path dir) throws Exception {
        var file = new VaultFile(dir.resolve("deep/er/vault.jv"));
        file.write(new byte[] {9});
        assertThat(file.read()).containsExactly(9);
    }
}
