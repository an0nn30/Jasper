package dev.jasper.app.config;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigServiceMigrationTest {
    @TempDir Path directory;

    @Test void migrationBacksUpThenRewritesAndASecondRunDoesNothing() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, ConfigMigrationTest.VERSION_ONE);
        try (var service = new ConfigService(file, true)) {
            assertThat(service.initialState().migratable()).isTrue();
            var first = service.migrate(id -> id.equals("dev.jasper.history")).get(5, TimeUnit.SECONDS);
            assertThat(first.changes()).isEqualTo(6);
            assertThat(first.backup()).hasValue(directory.resolve("config.toml.v1.bak").toRealPath());
            assertThat(Files.readString(directory.resolve("config.toml.v1.bak"))).isEqualTo(ConfigMigrationTest.VERSION_ONE);
            assertThat(Files.readString(file)).isEqualTo(ConfigMigrationTest.VERSION_TWO);
            assertThat(first.state().legacyKeys()).isEmpty();
            var second = service.migrate(id -> id.equals("dev.jasper.history")).get(5, TimeUnit.SECONDS);
            assertThat(second.changes()).isZero();
            assertThat(second.backup()).isEmpty();
            try (var files = Files.list(directory)) {
                assertThat(files.map(p -> p.getFileName().toString()).toList()).containsExactlyInAnyOrder("config.toml", "config.toml.v1.bak");
            }
        }
    }

    @Test void anExistingBackupIsNeverOverwritten() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, "[font]\nsize = 14\n");
        Files.writeString(directory.resolve("config.toml.v1.bak"), "older");
        try (var service = new ConfigService(file, true)) {
            var result = service.migrate(id -> false).get(5, TimeUnit.SECONDS);
            assertThat(result.backup().orElseThrow().getFileName().toString()).isEqualTo("config.toml.v1.2.bak");
            assertThat(Files.readString(directory.resolve("config.toml.v1.bak"))).isEqualTo("older");
            assertThat(Files.readString(directory.resolve("config.toml.v1.2.bak"))).isEqualTo("[font]\nsize = 14\n");
        }
    }

    @Test void aSymlinkedConfigIsMigratedAndBackedUpBesideItsTarget() throws Exception {
        Path real = Files.createDirectories(directory.resolve("dotfiles")).resolve("jasper.toml");
        Path link = directory.resolve("config.toml");
        Files.writeString(real, "[font]\nsize = 14\n");
        try { Files.createSymbolicLink(link, real); }
        catch (UnsupportedOperationException | java.nio.file.FileSystemException unavailable) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable: " + unavailable.getMessage());
        }
        try (var service = new ConfigService(link, true)) {
            service.migrate(id -> false).get(5, TimeUnit.SECONDS);
            assertThat(Files.isSymbolicLink(link)).isTrue();
            assertThat(Files.readString(real)).isEqualTo("version = 2\n[terminal.font]\nsize = 14\n");
            assertThat(real.resolveSibling("jasper.toml.v1.bak")).hasContent("[font]\nsize = 14\n");
        }
    }

    @Test void aMissingOrBrokenFileIsNotMigrated() throws Exception {
        Path missing = directory.resolve("absent/config.toml");
        try (var service = new ConfigService(missing, true)) {
            assertThat(service.migrate(id -> true).get(5, TimeUnit.SECONDS).changes()).isZero();
            assertThat(missing).doesNotExist();
        }
        Path broken = directory.resolve("config.toml");
        Files.writeString(broken, "[font]\nsize = 'x'\n");
        try (var service = new ConfigService(broken, true)) {
            assertThatThrownBy(() -> service.migrate(id -> true).get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(IllegalArgumentException.class);
            assertThat(Files.readString(broken)).isEqualTo("[font]\nsize = 'x'\n");
        }
    }
}
