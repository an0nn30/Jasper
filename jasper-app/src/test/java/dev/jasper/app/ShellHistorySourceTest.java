package dev.jasper.app;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ShellHistorySourceTest {
    @Test void discoveryListsEveryShellInOrderAndHonoursHistfile() {
        Path home = Path.of("/home/jasper");
        var sources = ShellHistorySource.discover(home, Map.of(), "Mac OS X");
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
}
