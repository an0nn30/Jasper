package dev.jasper.app.application;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.launch.LaunchSettings;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.launch.ShellProfile;
import dev.jasper.app.launch.ShellProfiles;
import java.nio.file.Path;
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
            }, warnings::add);
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
}
