package dev.jasper.app.config;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigStateTest {
    @TempDir Path directory;

    @Test void stateCarriesLoadedValuesAndKeepsTheLastGoodOnesWhenRejected() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, "[window]\ntab_height = 44\n");
        try (var service = new ConfigService(file, true)) {
            var first = service.initialState();
            assertThat(first.rejected()).isFalse();
            assertThat(first.values().get(CoreSettings.WINDOW_TAB_HEIGHT)).isEqualTo(44);
            assertThat(first.values().present(CoreSettings.WINDOW_TAB_HEIGHT)).isTrue();
            Files.writeString(file, "[window\n");
            var broken = service.reload().get(5, TimeUnit.SECONDS);
            assertThat(broken.rejected()).isTrue();
            assertThat(broken.values().get(CoreSettings.WINDOW_TAB_HEIGHT)).isEqualTo(44);
            assertThat(broken.snapshot().tabHeight()).isEqualTo(44);
            Files.delete(file);
            var missing = service.reload().get(5, TimeUnit.SECONDS);
            assertThat(missing.rejected()).isFalse();
            assertThat(missing.values().present(CoreSettings.WINDOW_TAB_HEIGHT)).isFalse();
        }
    }

    @Test void unreadableFilesAreRejectedToo() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.createDirectory(file);
        try (var service = new ConfigService(file, true)) {
            assertThat(service.initialState().rejected()).isTrue();
        }
    }
}
