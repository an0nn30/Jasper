package dev.jasper.app.vault;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class VaultBrandTest {
    @TempDir Path temp;

    @Test void newVaultFilesCarryTheJasperMagicAndSidecarName() throws Exception {
        Path file = temp.resolve("vault.enc");
        try (var service = new VaultService(file, new VaultServiceTest.MemoryStore(), Clock.systemUTC())) {
            service.create("synthetic master".toCharArray(), null);
        }
        byte[] header = Arrays.copyOf(Files.readAllBytes(file), 8);
        assertThat(new String(header, StandardCharsets.US_ASCII)).isEqualTo("JASPRVLT");
        assertThat(Files.list(temp).map(p -> p.getFileName().toString()))
            .containsExactlyInAnyOrder("vault.enc", "vault.enc.lck");
        try (var service = new VaultService(file, new VaultServiceTest.MemoryStore(), Clock.systemUTC())) {
            service.unlock("synthetic master".toCharArray(), java.time.Duration.ofDays(7));
        }
        assertThat(Files.exists(temp.resolve("vault.enc.device"))).isTrue();
    }
}
