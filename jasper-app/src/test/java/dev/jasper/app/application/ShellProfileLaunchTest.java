package dev.jasper.app.application;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.TerminalConfig;
import dev.jasper.app.launch.LaunchSettings;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.launch.ShellProfile;
import dev.jasper.app.launch.ShellProfileCatalog;
import dev.jasper.app.launch.ShellProfiles;
import dev.jasper.app.launch.WslDistro;
import dev.jasper.app.workspace.DesktopTestSupport;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShellProfileLaunchTest {
    @TempDir Path directory;

    @Test void theWindowLauncherResolvesProfilesOnTheEdtReportsReplacementsAndStartsWslInHome() throws Exception {
        var snapshot = ConfigLoader.parse(directory.resolve("config.toml"), "[terminal.shell]\nprofile = 'wsl:Ubuntu'\n", false).snapshot();
        var wsl = new ShellProfile("wsl:Ubuntu", "Ubuntu", ShellProfile.Kind.WSL, Optional.of("WSL 1"), List.of("wsl.exe", "-d", "Ubuntu"));
        var fallback = new ShellProfile("powershell", "PowerShell", ShellProfile.Kind.LOCAL_SHELL, Optional.empty(), List.of("powershell.exe", "-NoLogo"));
        Queue<Runnable> pending = new ArrayDeque<>();
        var requested = new ArrayList<Optional<String>>();
        var warnings = new ArrayList<String>();
        var started = new ArrayList<Map.Entry<Path, LaunchSettings>>();
        var launches = new ArrayList<ShellLauncher.Launch>();
        var errors = new ArrayList<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            var launcher = JasperApplication.windowLauncher(pending::add, () -> snapshot, (path, settings) -> {
                started.add(Map.entry(path, settings));
                throw new IllegalStateException("controlled launch boundary");
            }, null, (profile, shell) -> {
                assertThat(SwingUtilities.isEventDispatchThread()).isTrue();
                assertThat(shell.profile()).isEqualTo("wsl:Ubuntu");
                requested.add(profile);
                return profile.equals(Optional.of("gone"))
                    ? new ShellProfiles.Resolution(fallback, Optional.of("The shell profile \"gone\" is not available."))
                    : new ShellProfiles.Resolution(wsl, Optional.empty());
            }, () -> false, warnings::add);
            launches.add(launcher.launch(directory, Optional.empty(), (session, failure) -> errors.add(failure)));
            launches.add(launcher.launch(directory, Optional.of("gone"), (session, failure) -> errors.add(failure)));
        });
        while (!pending.isEmpty()) pending.remove().run();
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(requested).containsExactly(Optional.empty(), Optional.of("gone"));
        assertThat(warnings).containsExactly("The shell profile \"gone\" is not available.");
        assertThat(launches).containsExactly(new ShellLauncher.Launch("Ubuntu", Optional.of("wsl:Ubuntu")),
            new ShellLauncher.Launch("PowerShell", Optional.of("powershell")));
        assertThat(started.get(0).getKey()).as("the PTY starts in home").isEqualTo(Path.of(System.getProperty("user.home")));
        assertThat(started.get(0).getValue().command()).containsExactly("wsl.exe", "-d", "Ubuntu", "--cd", directory.toString());
        assertThat(started.get(0).getValue().environment().entrySet().stream()
            .filter(entry -> entry.getKey().equalsIgnoreCase("WSLENV")).map(Map.Entry::getValue).findFirst())
            .hasValueSatisfying(value -> assertThat(value).contains("TERM_PROGRAM", "JASPER_TERMINAL"));
        assertThat(started.get(1).getKey()).isEqualTo(directory);
        assertThat(started.get(1).getValue().command()).containsExactly("powershell.exe", "-NoLogo");
        assertThat(errors).hasSize(2).allSatisfy(error -> assertThat(error).hasMessage("controlled launch boundary"));
    }

    private static ShellProfileCatalog windowsCatalog(Queue<Runnable> detection, TerminalConfig.Shell shell) {
        return new ShellProfileCatalog("Windows 11", Map.of(), () -> shell, () -> List.of(new WslDistro("Ubuntu", 2, true)),
            Optional::empty, detection::add);
    }

    /**
     * The first window launches before detection completes: a trusted WSL or PowerShell 7 id waits off the EDT for
     * detection and resolves again, so a missing one starts PowerShell with a warning instead of failing the pane.
     */
    @Test void aProfileTrustedBeforeDetectionIsCheckedOnceDetectionCompletes() throws Exception {
        var snapshot = ConfigLoader.parse(directory.resolve("config.toml"), "[terminal.shell]\nprofile = 'wsl:Gone'\n", false).snapshot();
        Queue<Runnable> detection = new ConcurrentLinkedQueue<>();
        Queue<Runnable> pending = new ArrayDeque<>();
        var catalog = new AtomicReference<ShellProfileCatalog>();
        var warnings = new ArrayList<String>();
        var started = new ArrayList<LaunchSettings>();
        var launches = new ArrayList<ShellLauncher.Launch>();
        var replaced = new ArrayList<ShellLauncher.Launch>();
        var errors = new ArrayList<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            catalog.set(windowsCatalog(detection, snapshot.terminal().shell()));
            var launcher = JasperApplication.windowLauncher(pending::add, () -> snapshot, (path, settings) -> {
                started.add(settings);
                throw new IllegalStateException("controlled launch boundary");
            }, null, catalog.get()::resolve, () -> {
                assertThat(SwingUtilities.isEventDispatchThread()).as("launches wait off the EDT").isFalse();
                return catalog.get().awaitDetection(Duration.ofSeconds(20));
            }, warnings::add);
            launches.add(launcher.launch(Optional.of(directory), Optional.empty(), replaced::add, (session, failure) -> errors.add(failure)));
            launches.add(launcher.launch(Optional.of(directory), Optional.of("wsl:ubuntu"), replaced::add, (session, failure) -> errors.add(failure)));
            launches.add(launcher.launch(Optional.of(directory), Optional.of("cmd"), replaced::add, (session, failure) -> errors.add(failure)));
            catalog.get().refresh();
        });
        assertThat(warnings).as("nothing is known to be missing yet").isEmpty();
        assertThat(launches).extracting(ShellLauncher.Launch::profile)
            .containsExactly(Optional.of("wsl:Gone"), Optional.of("wsl:ubuntu"), Optional.of("cmd"));
        while (!detection.isEmpty()) detection.remove().run();
        while (!pending.isEmpty()) pending.remove().run();
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(warnings).containsExactly("The shell profile \"wsl:Gone\" is not available; new panes start PowerShell instead.");
        assertThat(replaced).as("the pane learns what started instead, and the detected spelling; cmd was never trusted")
            .containsExactly(new ShellLauncher.Launch("PowerShell", Optional.of("powershell")),
                new ShellLauncher.Launch("Ubuntu", Optional.of("wsl:Ubuntu")));
        assertThat(started).extracting(LaunchSettings::command).containsExactly(
            List.of("powershell.exe", "-NoLogo"), List.of("wsl.exe", "-d", "Ubuntu", "--cd", directory.toString()),
            List.of("cmd.exe"));
        assertThat(errors).hasSize(3).allSatisfy(error -> assertThat(error).hasMessage("controlled launch boundary"));
        SwingUtilities.invokeAndWait(() -> catalog.get().close());
    }

    @Test void whenDetectionCannotBeAwaitedTheTrustedProfileStarts() throws Exception {
        var snapshot = ConfigLoader.parse(directory.resolve("config.toml"), "[terminal.shell]\nprofile = 'wsl:Gone'\n", false).snapshot();
        Queue<Runnable> pending = new ArrayDeque<>();
        var catalog = new AtomicReference<ShellProfileCatalog>();
        var started = new ArrayList<LaunchSettings>();
        var warnings = new ArrayList<String>();
        SwingUtilities.invokeAndWait(() -> {
            catalog.set(windowsCatalog(new ArrayDeque<>(), snapshot.terminal().shell()));
            JasperApplication.windowLauncher(pending::add, () -> snapshot, (path, settings) -> {
                started.add(settings);
                throw new IllegalStateException("controlled launch boundary");
            }, null, catalog.get()::resolve, () -> catalog.get().awaitDetection(Duration.ofSeconds(20)), warnings::add)
                .launch(Optional.empty(), Optional.empty(), replaced -> { throw new AssertionError(replaced); }, (session, failure) -> { });
        });
        long before = System.nanoTime();
        while (!pending.isEmpty()) pending.remove().run();
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(Duration.ofNanos(System.nanoTime() - before)).as("a catalog nobody refreshed is not waited for")
            .isLessThan(Duration.ofSeconds(10));
        assertThat(started).extracting(LaunchSettings::command).containsExactly(List.of("wsl.exe", "-d", "Gone", "--cd", "~"));
        assertThat(warnings).isEmpty();
        SwingUtilities.invokeAndWait(() -> catalog.get().close());
    }

    /** A window that started before detection completed still shows why its shell is not the configured one. */
    @Test void detectionThatFindsTheConfiguredProfileMissingReportsIt() throws Exception {
        Queue<Runnable> detection = new ArrayDeque<>();
        var warnings = new ArrayList<String>();
        var catalogs = new ArrayList<ShellProfileCatalog>();
        SwingUtilities.invokeAndWait(() -> {
            for (String profile : List.of("wsl:Gone", "wsl:Ubuntu")) {
                var shell = new TerminalConfig.Shell("", List.of(), profile);
                var catalog = windowsCatalog(detection, shell);
                JasperApplication.warnWhenDetected(catalog, () -> shell, warnings::add);
                catalog.refresh();
                catalogs.add(catalog);
            }
        });
        assertThat(warnings).isEmpty();
        while (!detection.isEmpty()) detection.remove().run();
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(warnings).containsExactly("The shell profile \"wsl:Gone\" is not available; new panes start PowerShell instead.");
        SwingUtilities.invokeAndWait(() -> catalogs.forEach(ShellProfileCatalog::close));
    }

    /** The benchmark and tests supply their own launcher: no reg.exe or wsl.exe, and nothing waits for detection. */
    @Test void anApplicationWithASuppliedLauncherDetectsNothing() throws Exception {
        var terminated = new CountDownLatch(1);
        var application = new AtomicReference<JasperApplication>();
        SwingUtilities.invokeAndWait(() -> application.set(new JasperApplication(null,
            DesktopTestSupport.launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown)));
        try {
            long before = System.nanoTime();
            assertThat(application.get().shellProfiles().awaitDetection(Duration.ofSeconds(20))).isFalse();
            assertThat(Duration.ofNanos(System.nanoTime() - before)).isLessThan(Duration.ofSeconds(10));
        } finally {
            SwingUtilities.invokeAndWait(application.get()::quit);
            assertThat(terminated.await(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    /** A provided session (SSH) asks only for the grid and scrollback: it must not report the local profile's problems. */
    @Test void sessionDefaultsReportNoProfileProblem() throws Exception {
        var snapshot = ConfigLoader.parse(directory.resolve("config.toml"), "[terminal.shell]\nprofile = 'wsl:Gone'\n", false).snapshot();
        var fallback = new ShellProfile("powershell", "PowerShell", ShellProfile.Kind.LOCAL_SHELL, Optional.empty(), List.of("powershell.exe", "-NoLogo"));
        var warnings = new ArrayList<String>();
        var defaults = new ArrayList<ShellLauncher.SessionDefaults>();
        SwingUtilities.invokeAndWait(() -> {
            var launcher = JasperApplication.windowLauncher(task -> { throw new AssertionError("nothing launches"); }, () -> snapshot,
                (path, settings) -> { throw new AssertionError("nothing starts"); }, null,
                (profile, shell) -> new ShellProfiles.Resolution(fallback, Optional.of("The shell profile \"wsl:Gone\" is not available.")),
                () -> false, warnings::add);
            defaults.add(launcher.sessionDefaults());
            defaults.add(launcher.sessionDefaults());
        });
        assertThat(warnings).isEmpty();
        assertThat(defaults).containsOnly(new ShellLauncher.SessionDefaults(snapshot.columns(), snapshot.lines(), snapshot.terminal().scrollback()));
    }
}
