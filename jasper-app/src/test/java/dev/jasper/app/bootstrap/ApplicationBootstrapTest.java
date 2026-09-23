package dev.jasper.app.bootstrap;

import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.residency.HandoffSocket;
import dev.jasper.app.residency.LaunchRequest;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class ApplicationBootstrapTest {
    @TempDir Path directory;
    @Test void desktopMenuPlacementUsesTheStartupSkin() throws Exception {
        String menuKey = "apple.laf.useScreenMenuBar", appearanceKey = "apple.awt.application.appearance";
        String previousMenu = System.getProperty(menuKey), previousAppearance = System.getProperty(appearanceKey);
        try {
            for (String style : new String[]{"retro", "modern"}) {
                Path file = directory.resolve(style + ".toml");
                Files.writeString(file, "[ui.theme]\nstyle='" + style + "'\n");
                try (var service = new ConfigService(file, true)) {
                    // Override an inherited JVM setting in either direction before desktop startup.
                    System.setProperty(menuKey, style.equals("retro") ? "true" : "false");
                    ApplicationBootstrap.configureDesktopProperties(service.initialState().snapshot().style());
                    assertThat(System.getProperty(menuKey)).isEqualTo(style.equals("modern") ? "true" : "false");
                    assertThat(System.getProperty(appearanceKey)).isEqualTo("system");
                }
            }
        } finally {
            if (previousMenu == null) System.clearProperty(menuKey); else System.setProperty(menuKey, previousMenu);
            if (previousAppearance == null) System.clearProperty(appearanceKey); else System.setProperty(appearanceKey, previousAppearance);
        }
    }

    @Test void helpAndErrorsExitBeforeConfigurationOrDesktopStartup() {
        var out = new ByteArrayOutputStream(); var error = new ByteArrayOutputStream();
        var stdout = new PrintStream(out); var stderr = new PrintStream(error);
        assertThat(ApplicationBootstrap.start(new String[]{"--help"}, stdout, stderr,
            (service, options) -> { throw new AssertionError("Unexpected startup"); })).isZero();
        assertThat(out.toString()).contains("--config", "--help");
        assertThat(ApplicationBootstrap.start(new String[]{"--config"}, stdout, stderr,
            (service, options) -> { throw new AssertionError("Unexpected startup"); })).isEqualTo(2);
        assertThat(error.toString()).contains("requires a file path");
    }
    @Test void explicitConfigurationIsReadBeforeDispatchToSwing() throws Exception {
        Path config = directory.resolve("config.toml");
        Files.writeString(config, "[window]\ntab_height=47\n");
        var received = new AtomicReference<ConfigService>();
        try {
            int result = ApplicationBootstrap.start(new String[]{"--config", config.toString()}, System.out, System.err, (service, options) -> {
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
            assertThat(ApplicationBootstrap.start(new String[]{"--config", config.toString()}, System.out, System.err,
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
            assertThat(ApplicationBootstrap.handsOff(new AppArguments(null, false, false), dirs)).isTrue();
            // A different configuration file is a different Jasper; the resident one holds another.
            assertThat(ApplicationBootstrap.handsOff(new AppArguments(dir.resolve("other.toml"), false, false), dirs)).isFalse();
            // And a resident process never hands off to itself.
            assertThat(ApplicationBootstrap.handsOff(new AppArguments(null, false, true), dirs)).isFalse();
            // Recovery and development launches must never be swallowed by the process they are
            // meant to get away from.
            assertThat(ApplicationBootstrap.handsOff(new AppArguments(null, false, false, true, null, false), dirs)).isFalse();
            assertThat(ApplicationBootstrap.handsOff(new AppArguments(null, false, false, false, dir.resolve("p"), false), dirs)).isFalse();
            assertThat(ApplicationBootstrap.handsOff(new AppArguments(null, false, false, false, null, true), dirs)).isFalse();
        }
    }

    @Test void withNothingListeningThereIsNothingToHandOffTo(@TempDir Path dir) {
        AppDirs dirs = new AppDirs(dir, dir.resolve("config.toml"), dir.resolve("logs"));
        assertThat(ApplicationBootstrap.handsOff(new AppArguments(null, false, false), dirs)).isFalse();
    }

    @Test void residentRoleIsOnlyTrueWithTheSettingOnAndNoConfigOverride() {
        assertThat(ApplicationBootstrap.residentRole(new AppArguments(null, false, false), true)).isTrue();
        assertThat(ApplicationBootstrap.residentRole(new AppArguments(null, false, false), false)).isFalse();
        // A --config launch is standalone: it never claims the shared endpoint, setting or not.
        assertThat(ApplicationBootstrap.residentRole(new AppArguments(Path.of("other.toml"), false, false), true)).isFalse();
        assertThat(ApplicationBootstrap.residentRole(new AppArguments(Path.of("other.toml"), false, false), false)).isFalse();
        assertThat(ApplicationBootstrap.residentRole(new AppArguments(null, false, false, true, null, false), true)).isFalse();
        assertThat(ApplicationBootstrap.residentRole(new AppArguments(null, false, false, false, Path.of("p"), false), true)).isFalse();
        assertThat(ApplicationBootstrap.residentRole(new AppArguments(null, false, false, false, null, true), true)).isFalse();
    }

    @Test void staleFlagsADifferentPathOrModificationTimeButNotAMatchingUnresolvedSource() {
        Path source = Path.of("/app.jar");
        assertThat(ApplicationBootstrap.stale(new LaunchRequest("t", source, 100L), source, 100L)).isFalse();
        assertThat(ApplicationBootstrap.stale(new LaunchRequest("t", Path.of("/other.jar"), 100L), source, 100L)).isTrue();
        assertThat(ApplicationBootstrap.stale(new LaunchRequest("t", source, 999L), source, 100L)).isTrue();
        // An unresolvable code source normalises to the same empty path on both sides.
        assertThat(ApplicationBootstrap.stale(new LaunchRequest("t", Path.of(""), 0L), null, 0L)).isFalse();
    }

    @Test void loginItemReconcilerDedupesAndRunsOffTheCallingThread() throws Exception {
        var applied = new java.util.concurrent.LinkedBlockingQueue<Boolean>();
        var threadNames = new java.util.concurrent.LinkedBlockingQueue<String>();
        var reconciler = ApplicationBootstrap.loginItemReconciler(enabled -> {
            threadNames.add(Thread.currentThread().getName());
            applied.add(enabled);
        });

        reconciler.accept(true);
        assertThat(applied.poll(2, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(true);
        assertThat(threadNames.poll(2, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("jasper-login-item");

        // Repeating the same value is exactly what an unrelated config save replays: no call.
        reconciler.accept(true);
        reconciler.accept(true);
        assertThat(applied.poll(300, java.util.concurrent.TimeUnit.MILLISECONDS))
            .as("an unchanged value must be deduped, not reconciled again").isNull();

        // A value that actually changed still runs.
        reconciler.accept(false);
        assertThat(applied.poll(2, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(false);
    }

    @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.MAC)
    @Test void reconcilingTheLoginItemWritesAndRemovesIt(@TempDir Path fakeHome) {
        Path plist = fakeHome.resolve("Library/LaunchAgents/dev.jasper.background.plist");
        ApplicationBootstrap.reconcileLoginItem(true, "/Applications/Jasper.app/Contents/MacOS/Jasper", fakeHome);
        assertThat(plist).exists();
        ApplicationBootstrap.reconcileLoginItem(false, "/Applications/Jasper.app/Contents/MacOS/Jasper", fakeHome);
        assertThat(plist).doesNotExist();
        // A development run has no installed path, so nothing is ever written.
        ApplicationBootstrap.reconcileLoginItem(true, null, fakeHome);
        assertThat(plist).doesNotExist();
    }
    @Test void failedDesktopDispatchClosesConfigurationAndPreservesError() {
        var received = new AtomicReference<ConfigService>();
        var original = new AssertionError("desktop dispatch");
        try {
            assertThatThrownBy(() -> ApplicationBootstrap.start(new String[]{"--config", directory.resolve("config.toml").toString()},
                System.out, System.err, (service, options) -> { received.set(service); throw original; })).isSameAs(original);
            assertThat(received.get().reload()).isCompletedExceptionally();
        } finally { if (received.get() != null) received.get().close(); }
    }

    @Test void onlyAPlainReplacementCanHandOffAndOnlyAPureStandaloneLaunchExplainsItself() {
        Path file = Path.of("/tmp/other.toml"), plugin = Path.of("/tmp/plugin");
        var safe = new AppArguments(null, false, false, true, null, false);
        assertThat(ApplicationBootstrap.replacementHandsOff(safe)).as("--safe-mode restarts into a plain launch").isTrue();
        assertThat(ApplicationBootstrap.replacementHandsOff(new AppArguments(file, false, false, true, null, false))).isFalse();
        assertThat(ApplicationBootstrap.replacementHandsOff(new AppArguments(null, false, false, true, plugin, false))).isFalse();
        assertThat(ApplicationBootstrap.replacementHandsOff(new AppArguments(null, false, false, true, null, true))).isFalse();
        assertThat(ApplicationBootstrap.standaloneNotice(new AppArguments(null, false, false, false, null, true))).isTrue();
        assertThat(ApplicationBootstrap.standaloneNotice(safe)).isFalse();
        assertThat(ApplicationBootstrap.standaloneNotice(new AppArguments(file, false, false, false, null, true))).isFalse();
        assertThat(ApplicationBootstrap.standaloneNotice(new AppArguments(null, false, false, false, plugin, true))).isFalse();
    }
}
