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
            (service, options) -> { throw new AssertionError("Unexpected startup"); })).isZero();
        assertThat(out.toString()).contains("--config", "--help");
        assertThat(Main.start(new String[]{"--config"}, stdout, stderr,
            (service, options) -> { throw new AssertionError("Unexpected startup"); })).isEqualTo(2);
        assertThat(error.toString()).contains("requires a file path");
    }
    @Test void explicitConfigurationIsReadBeforeDispatchToSwing() throws Exception {
        Path config = directory.resolve("config.toml");
        Files.writeString(config, "[window]\ntab_height=47\n");
        var received = new AtomicReference<ConfigService>();
        try {
            int result = Main.start(new String[]{"--config", config.toString()}, System.out, System.err, (service, options) -> {
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
            assertThat(Main.start(new String[]{"--config", config.toString()}, System.out, System.err,
                (service, options) -> received.set(service))).isZero();
            assertThat(received.get().initialState().snapshot().variant()).isEqualTo(Appearance.LIGHT);
        } finally {
            if (received.get() != null) received.get().close();
        }
    }

    @Test void neitherAnotherConfigurationNorAResidentProcessHandsOff(@TempDir Path dir) throws Exception {
        AppDirs dirs = new AppDirs(dir, dir.resolve("config.toml"), dir.resolve("logs"));
        try (HandoffSocket endpoint = HandoffSocket.bind(dirs.daemonSocket(), dirs.daemonToken(),
                dirs.daemonLock(), request -> LaunchRequest.Response.OK)) {
            assertThat(endpoint).isNotNull();
            // The control: with this endpoint listening, an ordinary launch does hand off. That is
            // what makes the two refusals below attributable to the guard rather than to silence.
            assertThat(Main.handsOff(new AppArguments(null, false, false), dirs)).isTrue();
            // A different configuration file is a different Jasper; the resident one holds another.
            assertThat(Main.handsOff(new AppArguments(dir.resolve("other.toml"), false, false), dirs)).isFalse();
            // And a resident process never hands off to itself.
            assertThat(Main.handsOff(new AppArguments(null, false, true), dirs)).isFalse();
        }
    }

    @Test void withNothingListeningThereIsNothingToHandOffTo(@TempDir Path dir) {
        AppDirs dirs = new AppDirs(dir, dir.resolve("config.toml"), dir.resolve("logs"));
        assertThat(Main.handsOff(new AppArguments(null, false, false), dirs)).isFalse();
    }

    @Test void residentRoleIsOnlyTrueWithTheSettingOnAndNoConfigOverride() {
        assertThat(Main.residentRole(new AppArguments(null, false, false), true)).isTrue();
        assertThat(Main.residentRole(new AppArguments(null, false, false), false)).isFalse();
        // A --config launch is standalone: it never claims the shared endpoint, setting or not.
        assertThat(Main.residentRole(new AppArguments(Path.of("other.toml"), false, false), true)).isFalse();
        assertThat(Main.residentRole(new AppArguments(Path.of("other.toml"), false, false), false)).isFalse();
    }

    @Test void staleFlagsADifferentPathOrModificationTimeButNotAMatchingUnresolvedSource() {
        Path source = Path.of("/app.jar");
        assertThat(Main.stale(new LaunchRequest("t", source, 100L), source, 100L)).isFalse();
        assertThat(Main.stale(new LaunchRequest("t", Path.of("/other.jar"), 100L), source, 100L)).isTrue();
        assertThat(Main.stale(new LaunchRequest("t", source, 999L), source, 100L)).isTrue();
        // An unresolvable code source normalises to the same empty path on both sides.
        assertThat(Main.stale(new LaunchRequest("t", Path.of(""), 0L), null, 0L)).isFalse();
    }

    @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.MAC)
    @Test void reconcilingTheLoginItemWritesAndRemovesIt(@TempDir Path fakeHome) {
        Path plist = fakeHome.resolve("Library/LaunchAgents/dev.jasper.background.plist");
        Main.reconcileLoginItem(true, "/Applications/Jasper.app/Contents/MacOS/Jasper", fakeHome);
        assertThat(plist).exists();
        Main.reconcileLoginItem(false, "/Applications/Jasper.app/Contents/MacOS/Jasper", fakeHome);
        assertThat(plist).doesNotExist();
        // A development run has no installed path, so nothing is ever written.
        Main.reconcileLoginItem(true, null, fakeHome);
        assertThat(plist).doesNotExist();
    }
}
