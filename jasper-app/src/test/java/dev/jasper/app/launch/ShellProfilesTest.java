package dev.jasper.app.launch;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.TerminalConfig;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ShellProfilesTest {
    private static final TerminalConfig.Shell DEFAULT = new TerminalConfig.Shell("", List.of());
    private static final Map<String, String> WINDOWS_ENV = Map.of("ComSpec", "C:\\Windows\\system32\\cmd.exe",
        "Path", "C:\\Windows;\"C:\\Program Files\\PowerShell\\7\"", "ProgramFiles", "C:\\Program Files");

    @Test void windowsOffersPowerShellCommandPromptEachDistributionAndCustom() {
        var distros = List.of(new WslDistro("Debian", 2, false), new WslDistro("Ubuntu", 1, true));
        var profiles = ShellProfiles.available("Windows 11", WINDOWS_ENV, distros, Optional.of("pwsh.exe"), DEFAULT);
        assertThat(profiles).extracting(ShellProfile::id).containsExactly("powershell", "pwsh", "cmd", "wsl:Debian", "wsl:Ubuntu", "custom");
        assertThat(profiles).extracting(ShellProfile::label).containsExactly("PowerShell", "PowerShell 7", "Command Prompt",
            "Debian (WSL 2)", "Ubuntu (WSL 1 \u00B7 default)", "Custom");
        assertThat(profiles.get(0).command()).containsExactly("powershell.exe", "-NoLogo");
        assertThat(profiles.get(1).command()).containsExactly("pwsh.exe", "-NoLogo");
        assertThat(profiles.get(2).command()).containsExactly("C:\\Windows\\system32\\cmd.exe");
        assertThat(profiles.get(4).command()).containsExactly("wsl.exe", "-d", "Ubuntu");
        assertThat(profiles.get(4).kind()).isEqualTo(ShellProfile.Kind.WSL);
        assertThat(profiles.get(4).distro()).contains("Ubuntu");
        assertThat(profiles.get(4).detail()).contains("WSL 1 \u00B7 default");
        assertThat(profiles.get(0).distro()).isEmpty();
        assertThat(profiles.get(5).command()).as("Custom with no program is the default shell").containsExactly("powershell.exe", "-NoLogo");
        var bare = ShellProfiles.available("Windows 11", Map.of(), List.of(), Optional.empty(), DEFAULT);
        assertThat(bare).extracting(ShellProfile::id).containsExactly("powershell", "cmd", "custom");
        assertThat(bare.get(1).command()).as("no ComSpec").containsExactly("cmd.exe");
    }

    @Test void macOsAndLinuxOfferTheLoginShellAndCustom() {
        var profiles = ShellProfiles.available("Mac OS X", Map.of("SHELL", "/opt/homebrew/bin/fish"),
            List.of(new WslDistro("Ubuntu", 2, false)), Optional.of("pwsh"), new TerminalConfig.Shell("/bin/zsh", List.of("-i")));
        assertThat(profiles).extracting(ShellProfile::id).containsExactly("login", "custom");
        assertThat(profiles.get(0).command()).containsExactly("/opt/homebrew/bin/fish", "-l");
        assertThat(profiles.get(0).label()).isEqualTo("Login shell (fish)");
        assertThat(profiles.get(0).kind()).isEqualTo(ShellProfile.Kind.LOGIN_SHELL);
        assertThat(profiles.get(1).command()).containsExactly("/bin/zsh", "-i");
        assertThat(profiles.get(1).label()).isEqualTo("Custom (zsh)");
        assertThat(ShellProfiles.available("Linux", Map.of(), List.of(), Optional.empty(), new TerminalConfig.Shell("", List.of("-i")))
            .get(1).command()).as("args alone append to the default shell").containsExactly("/bin/bash", "-l", "-i");
    }

    @Test void powerShell7IsFoundOnThePathOrUnderProgramFiles() {
        Set<String> files = new HashSet<>();
        assertThat(ShellProfiles.findPwsh("Windows 11", WINDOWS_ENV, files::contains)).isEmpty();
        files.add("C:\\Program Files\\PowerShell\\7\\pwsh.exe");
        assertThat(ShellProfiles.findPwsh("Windows 11", Map.of("ProgramFiles", "C:\\Program Files"), files::contains))
            .contains("C:\\Program Files\\PowerShell\\7\\pwsh.exe");
        assertThat(ShellProfiles.findPwsh("Windows 11", WINDOWS_ENV, files::contains)).as("a PATH entry wins; its quotes are stripped")
            .contains("pwsh.exe");
        assertThat(ShellProfiles.findPwsh("Linux", WINDOWS_ENV, name -> true)).isEmpty();
        String alias = "C:\\Users\\me\\AppData\\Local\\Microsoft\\WindowsApps\\pwsh.exe";
        assertThat(ShellProfiles.findPwsh("Windows 11", Map.of("LOCALAPPDATA", "C:\\Users\\me\\AppData\\Local"), Set.of(alias)::contains))
            .as("the Store package's execution alias, when WindowsApps is not on PATH").contains(alias);
    }

    @Test void anExecutionAliasCountsAsExistingThoughItCannotBeFollowed(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws java.io.IOException {
        // A Store app's execution alias is a reparse point that Files.exists cannot follow; a dangling link stands in.
        java.nio.file.Path link = dir.resolve("pwsh.exe");
        try {
            java.nio.file.Files.createSymbolicLink(link, dir.resolve("missing-target.exe"));
        } catch (java.io.IOException | UnsupportedOperationException noLinks) {
            org.junit.jupiter.api.Assumptions.abort("symbolic links are unavailable here: " + noLinks);
        }
        assertThat(ShellProfiles.exists(link.toString())).isTrue();
        assertThat(ShellProfiles.exists(dir.resolve("absent.exe").toString())).isFalse();
    }

    @Test void theConfiguredIdComesFromTheProfileOrTheOldProgramSettings() {
        assertThat(ShellProfiles.configuredId("Windows 11", DEFAULT)).isEqualTo("powershell");
        assertThat(ShellProfiles.configuredId("Linux", DEFAULT)).isEqualTo("login");
        assertThat(ShellProfiles.configuredId("Linux", new TerminalConfig.Shell("/bin/fish", List.of()))).isEqualTo("custom");
        assertThat(ShellProfiles.configuredId("Linux", new TerminalConfig.Shell("", List.of("-i")))).isEqualTo("custom");
        assertThat(ShellProfiles.configuredId("Windows 11", new TerminalConfig.Shell("/bin/fish", List.of(), "wsl:Ubuntu"))).isEqualTo("wsl:Ubuntu");
    }

    @Test void resolutionFallsBackToThePlatformDefaultWithAWarning() {
        var available = ShellProfiles.available("Windows 11", WINDOWS_ENV, List.of(new WslDistro("Ubuntu", 2, true)), Optional.empty(), DEFAULT);
        var chosen = ShellProfiles.resolve(Optional.of("wsl:Ubuntu"), available, true, "Windows 11", WINDOWS_ENV, DEFAULT);
        assertThat(chosen.profile().id()).isEqualTo("wsl:Ubuntu");
        assertThat(chosen.warning()).isEmpty();
        var otherCase = ShellProfiles.resolve(Optional.of("wsl:UBUNTU"), available, true, "Windows 11", WINDOWS_ENV, DEFAULT);
        assertThat(otherCase.profile()).as("WSL names ignore case; the detected spelling wins").isEqualTo(chosen.profile());
        assertThat(otherCase.warning()).isEmpty();
        assertThat(ShellProfiles.resolve(Optional.of("CMD"), available, true, "Windows 11", WINDOWS_ENV, DEFAULT).warning())
            .as("the other ids are exact").isPresent();
        var byDefault = ShellProfiles.resolve(Optional.empty(), available, true, "Windows 11", WINDOWS_ENV,
            new TerminalConfig.Shell("", List.of(), "wsl:Ubuntu"));
        assertThat(byDefault.profile().id()).isEqualTo("wsl:Ubuntu");
        var gone = ShellProfiles.resolve(Optional.of("wsl:Gone"), available, true, "Windows 11", WINDOWS_ENV, DEFAULT);
        assertThat(gone.profile().id()).isEqualTo("powershell");
        assertThat(gone.warning()).contains("The shell profile \"wsl:Gone\" is not available; new panes start PowerShell instead.");
        var pending = ShellProfiles.resolve(Optional.of("wsl:Later"), available, false, "Windows 11", WINDOWS_ENV, DEFAULT);
        assertThat(pending.profile().command()).as("trusted while detection runs").containsExactly("wsl.exe", "-d", "Later");
        assertThat(pending.warning()).isEmpty();
        assertThat(ShellProfiles.resolve(Optional.of("pwsh"), available, false, "Windows 11", WINDOWS_ENV, DEFAULT).profile().command())
            .containsExactly("pwsh.exe", "-NoLogo");
        assertThat(ShellProfiles.resolve(Optional.of("pwsh"), available, true, "Windows 11", WINDOWS_ENV, DEFAULT).warning()).isPresent();
        var custom = ShellProfiles.resolve(Optional.of("custom"), available, true, "Windows 11", WINDOWS_ENV,
            new TerminalConfig.Shell("C:\\msys64\\usr\\bin\\bash.exe", List.of("-l")));
        assertThat(custom.profile().command()).as("Custom always follows the current settings").containsExactly("C:\\msys64\\usr\\bin\\bash.exe", "-l");
        var login = ShellProfiles.resolve(Optional.of("login"), available, true, "Windows 11", WINDOWS_ENV, DEFAULT);
        assertThat(login.profile().id()).isEqualTo("powershell");
        assertThat(login.warning()).isPresent();
        var linux = ShellProfiles.resolve(Optional.of("cmd"), ShellProfiles.available("Linux", Map.of(), List.of(), Optional.empty(), DEFAULT),
            true, "Linux", Map.of(), DEFAULT);
        assertThat(linux.profile().id()).isEqualTo("login");
        assertThat(ShellProfiles.resolve(Optional.of("wsl:Later"), List.of(), false, "Linux", Map.of(), DEFAULT).profile().id())
            .as("no WSL outside Windows").isEqualTo("login");
    }

    @Test void aProfileNeedsAnIdANameAndACommand() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ShellProfile(" ", "x", ShellProfile.Kind.LOCAL_SHELL, Optional.empty(), List.of("x")));
        assertThatIllegalArgumentException().isThrownBy(() -> new ShellProfile("x", "x", ShellProfile.Kind.LOCAL_SHELL, Optional.empty(), List.of()))
            .withMessage("Shell command must not be empty.");
    }
}
