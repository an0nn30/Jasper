package dev.jasper.history;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ShellHistorySourceTest {
    @Test void discoveryListsEveryShellInOrderAndHonoursHistfile() {
        Path home = Path.of("/home/jasper");
        var sources = ShellHistorySource.discover(home, Map.of(), "Linux");
        assertThat(sources).extracting(ShellHistorySource::shell).containsExactly(
            HistoryShell.ZSH, HistoryShell.BASH, HistoryShell.FISH, HistoryShell.NUSHELL, HistoryShell.POWERSHELL);
        assertThat(sources).extracting(ShellHistorySource::file).containsExactly(
            home.resolve(".zsh_history"), home.resolve(".bash_history"),
            home.resolve(".local/share/fish/fish_history"), home.resolve(".config/nushell/history.txt"),
            home.resolve(".local/share/powershell/PSReadLine/ConsoleHost_history.txt"));
        var custom = ShellHistorySource.discover(home, Map.of("HISTFILE", "/var/hist/zsh"), "Linux");
        assertThat(custom.getFirst().file()).isEqualTo(Path.of("/var/hist/zsh"));
        var windows = ShellHistorySource.discover(home, Map.of("APPDATA", "C:\\Users\\j\\AppData\\Roaming"), "Windows 11");
        assertThat(windows.getLast().file().toString()).endsWith("PSReadLine" + java.io.File.separator + "ConsoleHost_history.txt");
        assertThat(windows.getLast().file().toString()).startsWith("C:\\Users\\j\\AppData\\Roaming");
    }

    @Test void nushellIsAlsoLookedForUnderApplicationSupportOnMacos() {
        Path home = Path.of("/Users/jasper");
        var mac = ShellHistorySource.discover(home, Map.of(), "Mac OS X");
        assertThat(mac).extracting(ShellHistorySource::shell).containsExactly(
            HistoryShell.ZSH, HistoryShell.BASH, HistoryShell.FISH, HistoryShell.NUSHELL, HistoryShell.NUSHELL,
            HistoryShell.POWERSHELL);
        assertThat(mac.get(3).file()).isEqualTo(home.resolve(".config/nushell/history.txt"));
        assertThat(mac.get(4).file()).isEqualTo(home.resolve("Library/Application Support/nushell/history.txt"));
        var xdg = ShellHistorySource.discover(home, Map.of("XDG_CONFIG_HOME", "/tmp/xdg"), "Mac OS X");
        assertThat(xdg).extracting(ShellHistorySource::shell).containsExactly(
            HistoryShell.ZSH, HistoryShell.BASH, HistoryShell.FISH, HistoryShell.NUSHELL, HistoryShell.NUSHELL,
            HistoryShell.POWERSHELL);
        assertThat(xdg.get(3).file()).isEqualTo(Path.of("/tmp/xdg/nushell/history.txt"));
        var linuxXdg = ShellHistorySource.discover(home, Map.of("XDG_CONFIG_HOME", "/tmp/xdg"), "Linux");
        assertThat(linuxXdg).extracting(ShellHistorySource::shell).doesNotHaveDuplicates();
        assertThat(linuxXdg.get(3).file()).isEqualTo(Path.of("/tmp/xdg/nushell/history.txt"));
    }
}
