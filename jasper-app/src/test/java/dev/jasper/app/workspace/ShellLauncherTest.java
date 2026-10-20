package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.application.ApplicationTestSupport;
import dev.jasper.app.testsupport.ConfigTestSupport;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.launch.LaunchSettings;
import dev.jasper.app.launch.ShellProfile;
import dev.jasper.app.application.JasperApplication;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.workspace.TerminalPane;
import dev.jasper.terminal.session.TerminalSession;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static dev.jasper.app.testsupport.ConfigTestSupport.snapshot;
import static org.assertj.core.api.Assertions.*;

class ShellLauncherTest {
    private static final ShellProfile POWERSHELL =
        new ShellProfile("powershell", "PowerShell", ShellProfile.Kind.LOCAL_SHELL, Optional.empty(), List.of("powershell.exe"));
    private static final ShellProfile UBUNTU =
        new ShellProfile("wsl:Ubuntu", "Ubuntu", ShellProfile.Kind.WSL, Optional.empty(), List.of("wsl.exe", "-d", "Ubuntu"));
    @TempDir Path directory;

    /** Two cases build a TerminalPane, whose labels need an installed theme whatever ran before. */
    @BeforeAll static void theme() throws Exception { edt(() -> ThemeTestSupport.install(true)); }

    @Test @DisabledOnOs(OS.WINDOWS)
    void queuedRequestsCaptureSettingsAndPaneLabelsBeforeWorkerRuns() throws Exception {
        var current = new AtomicReference<>(snapshot("/first shell", List.of("old arg"), Map.of("VALUE", "old"), 12, 150, 45));
        Queue<Runnable> pending = new ArrayDeque<>(); var received = new ArrayList<LaunchSettings>();
        ShellLauncher launcher = new ShellLauncher(pending::add, () -> {
            assertThat(SwingUtilities.isEventDispatchThread()).isTrue();
            return LaunchSettings.resolve(current.get(), "Linux", Map.of(), 150, 45);
        }, (path, settings) -> {
            assertThat(SwingUtilities.isEventDispatchThread()).isFalse();
            assertThat(path).isEqualTo(directory); received.add(settings); return shell(path);
        });
        TerminalPane[] panes = new TerminalPane[2];
        try {
            edt(() -> {
                panes[0] = new TerminalPane(directory, launcher); panes[0].start();
                current.set(snapshot("/next shell", List.of("new", "args"), Map.of("VALUE", "new"), 34, 90, 20));
                panes[1] = new TerminalPane(directory, launcher); panes[1].start();
                assertThat(panes[0].shellLabel()).isEqualTo("first shell");
                assertThat(panes[1].shellLabel()).isEqualTo("next shell");
                for (var pane : panes) pane.onReady = view -> assertThat(SwingUtilities.isEventDispatchThread()).isTrue();
            });
            while (!pending.isEmpty()) pending.remove().run();
            edt(() -> {
                assertThat(panes[0].session()).isNotNull(); assertThat(panes[1].session()).isNotNull();
                assertThat(panes[0].shellLabel()).isEqualTo("first shell");
            });
            assertThat(received.get(0).command()).containsExactly("/first shell", "old arg");
            assertThat(received.get(0).environment()).containsEntry("VALUE", "old");
            assertThat(received.get(0).scrollback()).isEqualTo(12);
            assertThat(received.get(1).command()).containsExactly("/next shell", "new", "args");
            assertThat(received.get(1).environment()).containsEntry("VALUE", "new");
            assertThat(received.get(1).scrollback()).isEqualTo(34);
            assertThat(received).allSatisfy(s -> { assertThat(s.columns()).isEqualTo(150); assertThat(s.lines()).isEqualTo(45); });
        } finally { edt(() -> { for (var pane : panes) if (pane != null) pane.close(); }); }
    }

    @Test void applicationWindowLauncherFreezesGridButReadsSessionDefaultsForEachRequest() throws Exception {
        var current = new AtomicReference<>(snapshot("/initial", List.of(), Map.of(), 10, 150, 45));
        Queue<Runnable> pending = new ArrayDeque<>(); var received = new ArrayList<LaunchSettings>();
        var errors = new ArrayList<Throwable>();
        edt(() -> {
            var launcher = ApplicationTestSupport.windowLauncher(pending::add, () -> {
                assertThat(SwingUtilities.isEventDispatchThread()).isTrue(); return current.get();
            }, (path, settings) -> {
                assertThat(SwingUtilities.isEventDispatchThread()).isFalse(); received.add(settings);
                throw new IllegalStateException("controlled launch boundary");
            });
            current.set(snapshot("/before-request", List.of("a"), Map.of("VALUE", "first"), 20, 90, 25));
            launcher.launch(directory, (session, failure) -> errors.add(failure));
            current.set(snapshot("/after-request", List.of("b"), Map.of("VALUE", "second"), 30, 80, 20));
            launcher.launch(directory, (session, failure) -> errors.add(failure));
            ApplicationTestSupport.windowLauncher(pending::add, current::get, (path, settings) -> {
                received.add(settings); throw new IllegalStateException("controlled launch boundary");
            }).launch(directory, (session, failure) -> errors.add(failure));
        });
        while (!pending.isEmpty()) pending.remove().run(); edt(() -> {});
        assertThat(received).hasSize(3);
        assertThat(received.get(0).command()).containsExactly("/before-request", "a");
        assertThat(received.get(0).environment()).containsEntry("VALUE", "first");
        assertThat(received.get(0).scrollback()).isEqualTo(20);
        assertThat(received.get(1).command()).containsExactly("/after-request", "b");
        assertThat(received.get(1).environment()).containsEntry("VALUE", "second");
        assertThat(received.get(1).scrollback()).isEqualTo(30);
        assertThat(received.subList(0, 2)).allSatisfy(s -> {
            assertThat(s.columns()).isEqualTo(150); assertThat(s.lines()).isEqualTo(45);
        });
        assertThat(received.get(2).columns()).isEqualTo(80); assertThat(received.get(2).lines()).isEqualTo(20);
        assertThat(errors).hasSize(3).allSatisfy(e -> assertThat(e).hasMessage("controlled launch boundary"));
    }

    @Test void captureSchedulingDirectoryAndStartFailuresReachEdt() throws Exception {
        var failure = new IllegalArgumentException("capture failed"); var errors = new ArrayList<Throwable>();
        BiConsumer<TerminalSession, Throwable> completion = (session, error) -> {
            assertThat(SwingUtilities.isEventDispatchThread()).isTrue(); assertThat(session).isNull(); errors.add(error);
        };
        Queue<Runnable> pending = new ArrayDeque<>();
        var settings = LaunchSettings.resolve(ConfigSnapshot.defaults(), "Linux", Map.of(), 150, 45);
        edt(() -> {
            var capture = new ShellLauncher(pending::add, () -> { throw failure; }, (p, s) -> { throw new AssertionError(); });
            assertThat(capture.launch(directory, completion)).isNotBlank(); assertThat(pending).isEmpty();
            new ShellLauncher(task -> { throw new RejectedExecutionException("closed"); }, () -> settings,
                (p, s) -> { throw new AssertionError(); }).launch(directory, completion);
            new ShellLauncher(pending::add, () -> settings, (p, s) -> { throw new AssertionError(); })
                .launch(directory.resolve("missing"), completion);
            new ShellLauncher(pending::add, () -> settings, (p, s) -> { throw new IllegalStateException("start failed"); })
                .launch(directory, completion);
        });
        while (!pending.isEmpty()) pending.remove().run(); edt(() -> {});
        assertThat(errors).hasSize(4); assertThat(errors.get(0)).isSameAs(failure);
        assertThat(errors.get(1)).isInstanceOf(RejectedExecutionException.class);
        assertThat(errors.get(2)).hasMessageContaining("Directory is unavailable");
        assertThat(errors.get(3)).hasMessage("start failed");
    }

    /** Workspace tabs plan 6a: a restored tab's saved directory that is gone starts the shell where a new shell starts. */
    @Test void aMissingDirectoryStartsWhereANewShellStartsOnlyWhenAnyDirectoryWillDo() throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        var settings = LaunchSettings.resolve(ConfigSnapshot.defaults(), "Linux", Map.of(), 150, 45);
        Queue<Path> started = new ArrayDeque<>();
        Queue<String> errors = new ArrayDeque<>();
        Path missing = directory.resolve("missing");
        BiConsumer<TerminalSession, Throwable> completion = (session, error) -> errors.add(error.getMessage());
        edt(() -> {
            new ShellLauncher(pending::add, () -> settings, (path, launch) -> { started.add(path); throw new IllegalStateException("started"); })
                .launch(Optional.of(missing), true, Optional.empty(), Optional.empty(), Optional.empty(), replaced -> { }, completion);
            new ShellLauncher(pending::add, path -> { started.add(path); throw new IllegalStateException("started"); }, "sh")
                .launch(Optional.of(missing), true, Optional.empty(), Optional.empty(), Optional.empty(), replaced -> { }, completion);
            new ShellLauncher(pending::add, () -> settings, (path, launch) -> { throw new AssertionError(); })
                .launch(Optional.of(missing), false, Optional.empty(), Optional.empty(), Optional.empty(), replaced -> { }, completion);
        });
        while (!pending.isEmpty()) pending.remove().run(); edt(() -> {});
        assertThat(started).containsExactly(HOME, HOME);
        assertThat(errors).hasSize(3);
        assertThat(errors.remove()).isEqualTo("started");
        assertThat(errors.remove()).isEqualTo("started");
        assertThat(errors.remove()).as("any other launch still needs its directory").contains("Directory is unavailable");
    }

    /** Workspace tabs plan 6a: a restored tab's saved directory that is still there is where its shell starts. */
    @Test void anyDirectoryKeepsADirectoryThatIsThere() throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        var settings = LaunchSettings.resolve(ConfigSnapshot.defaults(), "Linux", Map.of(), 150, 45);
        Queue<Path> started = new ArrayDeque<>();
        BiConsumer<TerminalSession, Throwable> completion = (session, error) -> { };
        edt(() -> {
            new ShellLauncher(pending::add, () -> settings, (path, launch) -> { started.add(path); throw new IllegalStateException("started"); })
                .launch(Optional.of(directory), true, Optional.empty(), Optional.empty(), Optional.empty(), replaced -> { }, completion);
            new ShellLauncher(pending::add, path -> { started.add(path); throw new IllegalStateException("started"); }, "sh")
                .launch(Optional.of(directory), true, Optional.empty(), Optional.empty(), Optional.empty(), replaced -> { }, completion);
        });
        while (!pending.isEmpty()) pending.remove().run(); edt(() -> {});
        assertThat(started).containsExactly(directory, directory);
    }

    @Test @DisabledOnOs(OS.WINDOWS)
    void aPanesShellNamesThePaneLastOverTheConfiguredEnvironment() throws Exception {
        var configured = snapshot("/bin/sh", List.of(), Map.of("JASPER_PANE", "configured"), 12, 150, 45);
        Queue<Runnable> pending = new ArrayDeque<>();
        var received = new ArrayList<LaunchSettings>();
        ShellLauncher launcher = new ShellLauncher(pending::add, requested -> LaunchSettings.resolve(configured, "Linux", Map.of(), 150, 45),
            (requested, captured) -> captured, null, (path, settings) -> { received.add(settings); return shell(path); },
            pane -> Map.of("JASPER_PANE", pane.toString(), "JASPER_COMMAND_SOCKET", "/tmp/commands"));
        TerminalPane[] pane = new TerminalPane[1];
        try {
            edt(() -> { pane[0] = new TerminalPane(directory, launcher); pane[0].start(); });
            while (!pending.isEmpty()) pending.remove().run();
            edt(() -> { });
            assertThat(received).singleElement().satisfies(settings -> assertThat(settings.environment())
                .containsEntry("JASPER_PANE", pane[0].id().toString()).containsEntry("JASPER_COMMAND_SOCKET", "/tmp/commands"));
        } finally {
            edt(() -> { if (pane[0] != null) pane[0].close(); });
        }
    }

    @Test void aProgramRunsInsteadOfTheShellWithTheShellsEnvironmentAndThePanesVariables() throws Exception {
        var configured = snapshot("/bin/zsh", List.of("-l"), Map.of("VALUE", "configured"), 12, 150, 45);
        var expected = LaunchSettings.resolve(configured, "Mac OS X", Map.of("PATH", "/bin"), 150, 45);
        Queue<Runnable> pending = new ArrayDeque<>();
        var received = new ArrayList<Map.Entry<Path, LaunchSettings>>();
        var errors = new ArrayList<Throwable>();
        UUID pane = UUID.randomUUID();
        var launched = new AtomicReference<ShellLauncher.Launch>();
        ShellLauncher launcher = new ShellLauncher(pending::add, requested -> LaunchSettings.resolve(configured, "Mac OS X", Map.of("PATH", "/bin"), 150, 45),
            (requested, captured) -> captured, null, (path, settings) -> {
                received.add(Map.entry(path, settings));
                throw new IllegalStateException("controlled launch boundary");
            }, id -> Map.of("JASPER_PANE", id.toString()));
        edt(() -> launched.set(launcher.launch(Optional.of(directory), Optional.empty(), Optional.of(List.of("/opt/tools/nvim", "+3", "notes.txt")),
            Optional.of(pane), replaced -> { }, (session, failure) -> errors.add(failure))));
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> { });
        assertThat(launched.get().label()).as("the tab is named after the program").isEqualTo("nvim");
        assertThat(launched.get().profile()).as("the default profile, which a split of the pane starts").contains(expected.profile().id());
        assertThat(received).singleElement().satisfies(entry -> {
            assertThat(entry.getKey()).isEqualTo(directory);
            assertThat(entry.getValue().command()).containsExactly("/opt/tools/nvim", "+3", "notes.txt");
            assertThat(entry.getValue().environment()).containsEntry("VALUE", "configured").containsEntry("PATH", "/bin")
                .containsEntry("TERM", "xterm-256color").containsEntry("JASPER_PANE", pane.toString());
        });
        assertThat(errors).singleElement().satisfies(error -> assertThat(error).hasMessage("controlled launch boundary"));
    }

    @Test void aProgramStartsInItsDirectoryWithoutAWslProfilesCd() throws Exception {
        var wsl = new dev.jasper.app.launch.ShellProfile("wsl:Ubuntu", "Ubuntu", dev.jasper.app.launch.ShellProfile.Kind.WSL,
            Optional.of("WSL 2"), List.of("wsl.exe", "-d", "Ubuntu"));
        Queue<Runnable> pending = new ArrayDeque<>();
        var received = new ArrayList<Map.Entry<Path, LaunchSettings>>();
        ShellLauncher launcher = new ShellLauncher(pending::add,
            requested -> LaunchSettings.resolve(ConfigSnapshot.defaults(), wsl, "Windows 11", Map.of(), 150, 45, null),
            (requested, captured) -> captured, null, (path, settings) -> {
                received.add(Map.entry(path, settings));
                throw new IllegalStateException("controlled launch boundary");
            });
        Path home = Path.of(System.getProperty("user.home"));
        edt(() -> {
            launcher.launch(Optional.of(directory), Optional.empty(), Optional.of(List.of("notepad.exe", "notes.txt")), Optional.empty(),
                replaced -> { }, (session, failure) -> { });
            launcher.launch(Optional.empty(), Optional.empty(), Optional.of(List.of("notepad.exe")), Optional.empty(),
                replaced -> { }, (session, failure) -> { });
        });
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> { });
        assertThat(received).extracting(Map.Entry::getKey).as("the requested directory, else home").containsExactly(directory, home);
        assertThat(received).extracting(entry -> entry.getValue().command())
            .containsExactly(List.of("notepad.exe", "notes.txt"), List.of("notepad.exe"));
    }

    @Test void aLauncherWithoutSettingsCannotRunAProgramAndAMissingDirectoryFails() throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        var errors = new ArrayList<Throwable>();
        var bare = new ShellLauncher(pending::add, path -> { throw new AssertionError("nothing starts"); }, "sh");
        edt(() -> assertThat(bare.launch(Optional.of(directory), Optional.empty(), Optional.of(List.of("nvim")), Optional.empty(),
            replaced -> { }, (session, failure) -> errors.add(failure)).label()).isEqualTo("sh"));
        edt(() -> { });
        assertThat(pending).as("nothing was scheduled").isEmpty();
        assertThat(errors).singleElement().satisfies(error -> assertThat(error).isInstanceOf(IllegalStateException.class)
            .hasMessage("This window cannot run a program"));
        var settings = LaunchSettings.resolve(ConfigSnapshot.defaults(), "Linux", Map.of(), 150, 45);
        edt(() -> new ShellLauncher(pending::add, () -> settings, (p, s) -> { throw new AssertionError("nothing starts"); })
            .launch(Optional.of(directory.resolve("missing")), Optional.empty(), Optional.of(List.of("nvim")), Optional.empty(),
                replaced -> { }, (session, failure) -> errors.add(failure)));
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> { });
        assertThat(errors.getLast()).hasMessageContaining("Directory is unavailable");
    }

    /**
     * On Windows a launch asked to start on another host starts where a new shell starts: any program can report such a
     * directory (OSC 7) for a split or new tab to inherit, and starting there, or only checking that it exists, makes Windows
     * authenticate to that host over SMB. Windows only, like the rule ({@code LaunchSettingsTest} and {@code UncHostsTest}
     * test its text everywhere). The host ends in {@code .invalid}, which never resolves, should a regression ask for it.
     */
    @Test @EnabledOnOs(OS.WINDOWS) void onWindowsALaunchAskedToStartOnAnotherHostStartsWhereANewShellStarts() throws Exception {
        Path share = Path.of("\\\\attacker.invalid\\share\\project");
        Path home = Path.of(System.getProperty("user.home"));
        assertThat(startOf(POWERSHELL, share, Optional.empty())).isEqualTo(Map.entry(home, List.of("powershell.exe")));
        assertThat(startOf(UBUNTU, share, Optional.empty())).as("the WSL profile's own start")
            .isEqualTo(Map.entry(home, List.of("wsl.exe", "-d", "Ubuntu", "--cd", "~")));
        assertThat(startOf(POWERSHELL, share, Optional.of(List.of("vim")))).isEqualTo(Map.entry(home, List.of("vim")));
        assertThat(startOf(UBUNTU, share, Optional.of(List.of("vim")))).isEqualTo(Map.entry(home, List.of("vim")));

        Queue<Runnable> pending = new ArrayDeque<>();
        var started = new ArrayList<Path>();
        var bare = new ShellLauncher(pending::add, path -> { started.add(path); throw new IllegalStateException("controlled launch boundary"); }, "sh");
        edt(() -> bare.launch(Optional.of(share), Optional.empty(), replaced -> { }, (session, failure) -> { }));
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> { });
        assertThat(started).as("a launcher without settings").containsExactly(home);
    }

    /** Only a WSL profile's launch has the WSL shares: elsewhere {@code wsl$} may be a name the network answers. */
    @Test @EnabledOnOs(OS.WINDOWS) void onWindowsOnlyAWslProfileStartsOnAWslShare() throws Exception {
        Path home = Path.of(System.getProperty("user.home"));
        assertThat(startOf(UBUNTU, Path.of("\\\\wsl.localhost\\Ubuntu\\home\\me"), Optional.empty()))
            .isEqualTo(Map.entry(home, List.of("wsl.exe", "-d", "Ubuntu", "--cd", "/home/me")));
        assertThat(startOf(POWERSHELL, Path.of("\\\\wsl$\\Ubuntu\\home\\me"), Optional.of(List.of("vim"))))
            .isEqualTo(Map.entry(home, List.of("vim")));
    }

    /** Where {@code profile}'s launch asked for {@code requested} starts, and what it runs; null when it starts nothing. */
    private static Map.Entry<Path, List<String>> startOf(ShellProfile profile, Path requested, Optional<List<String>> program) throws Exception {
        var settings = new LaunchSettings(profile.command(), Map.of(), 80, 24, 0, profile);
        Queue<Runnable> pending = new ArrayDeque<>();
        var started = new ArrayList<Map.Entry<Path, List<String>>>();
        ShellLauncher launcher = new ShellLauncher(pending::add, () -> settings, (path, captured) -> {
            started.add(Map.entry(path, captured.command()));
            throw new IllegalStateException("controlled launch boundary");
        });
        edt(() -> launcher.launch(Optional.of(requested), Optional.empty(), program, Optional.empty(), replaced -> { }, (session, failure) -> { }));
        while (!pending.isEmpty()) pending.remove().run();
        edt(() -> { });
        return started.isEmpty() ? null : started.getFirst();
    }
}
