package dev.jasper.app;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class MainConfigurationTest {
    @TempDir Path directory;
    @Test void helpAndErrorsExitBeforeConfigurationOrDesktopStartup() {
        var out = new ByteArrayOutputStream(); var error = new ByteArrayOutputStream();
        var stdout = new PrintStream(out); var stderr = new PrintStream(error);
        assertThat(Main.start(new String[]{"--help"}, stdout, stderr,
            service -> { throw new AssertionError("Unexpected startup"); })).isZero();
        assertThat(out.toString()).contains("--config", "--help");
        assertThat(Main.start(new String[]{"--config"}, stdout, stderr,
            service -> { throw new AssertionError("Unexpected startup"); })).isEqualTo(2);
        assertThat(error.toString()).contains("requires a file path");
    }
    @Test void explicitConfigurationIsReadBeforeDispatchToSwing() throws Exception {
        Path config = directory.resolve("config.toml");
        Files.writeString(config, "[window]\ntab_height=47\n");
        var received = new AtomicReference<ConfigService>();
        try {
            int result = Main.start(new String[]{"--config", config.toString()}, System.out, System.err, service -> {
                assertThat(SwingUtilities.isEventDispatchThread()).isFalse(); received.set(service);
            });
            assertThat(result).isZero();
            assertThat(received.get()).isNotNull();
            assertThat(received.get().initialState().snapshot().tabHeight()).isEqualTo(47);
        } finally { if (received.get() != null) received.get().close(); }
    }

    @Test void explicitConfigurationSuppliesTheSavedThemeVariant() throws Exception {
        Path config = directory.resolve("elsewhere/config.toml");
        Files.createDirectories(config.getParent());
        Files.writeString(config, "ui.theme.variant='light'");
        var received = new AtomicReference<ConfigService>();
        try {
            assertThat(Main.start(new String[]{"--config", config.toString()}, System.out, System.err, received::set)).isZero();
            assertThat(received.get().initialState().snapshot().variant()).isEqualTo(Appearance.LIGHT);
        } finally {
            if (received.get() != null) received.get().close();
        }
    }
}
