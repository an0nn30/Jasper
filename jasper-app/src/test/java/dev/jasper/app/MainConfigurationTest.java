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

    @Test void explicitConfigurationStillUsesDefaultAppThemeDirectory() throws Exception {
        String oldHome = System.getProperty("user.home");
        String oldOs = System.getProperty("os.name");
        Path home = Files.createDirectory(directory.resolve("home"));
        Path themes = Files.createDirectories(home.resolve(".config/jasper/themes"));
        Files.writeString(themes.resolve("night.toml"), "[colors.primary]\nbackground='#101820'");
        Path config = directory.resolve("elsewhere/config.toml");
        Files.createDirectories(config.getParent());
        Files.writeString(config, "colors.theme='night'");
        var received = new AtomicReference<ConfigService>();
        try {
            System.setProperty("user.home", home.toString());
            System.setProperty("os.name", "Mac OS X");
            assertThat(Main.start(new String[]{"--config", config.toString()}, System.out, System.err, received::set)).isZero();
            assertThat(received.get().initialState().palette().background().getRGB() & 0xffffff).isEqualTo(0x101820);
        } finally {
            if (received.get() != null) received.get().close();
            System.setProperty("user.home", oldHome);
            System.setProperty("os.name", oldOs);
        }
    }
}
