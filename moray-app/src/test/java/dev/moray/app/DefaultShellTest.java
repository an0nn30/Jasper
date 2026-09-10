package dev.moray.app;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultShellTest {

    @Test
    void usesTheUsersShellAsALoginShell() {
        assertThat(DefaultShell.command("Mac OS X", Map.of("SHELL", "/opt/homebrew/bin/fish")))
            .containsExactly("/opt/homebrew/bin/fish", "-l");
    }

    @Test
    void fallsBackToZshOnMacAndBashOnLinux() {
        assertThat(DefaultShell.command("Mac OS X", Map.of())).containsExactly("/bin/zsh", "-l");
        assertThat(DefaultShell.command("Linux", Map.of("SHELL", " "))).containsExactly("/bin/bash", "-l");
    }

    @Test
    void usesPowerShellOnWindows() {
        assertThat(DefaultShell.command("Windows 11", Map.of("SHELL", "/bin/bash")))
            .containsExactly("powershell.exe", "-NoLogo");
    }
}
