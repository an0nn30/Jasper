package dev.jasper.app;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Where a shell keeps its history. Discovery lists every candidate; the index checks existence at each refresh. */
record ShellHistorySource(HistoryShell shell, Path file) {
    static List<ShellHistorySource> discover(Path home, Map<String, String> env, String osName) {
        String histfile = env.get("HISTFILE");
        Path zsh = histfile == null || histfile.isBlank() ? home.resolve(".zsh_history") : Path.of(histfile);
        boolean windows = osName != null && osName.toLowerCase(Locale.ROOT).contains("win");
        Path powershell = windows
            ? Path.of(env.getOrDefault("APPDATA", home.resolve("AppData/Roaming").toString()))
                .resolve("Microsoft/Windows/PowerShell/PSReadLine/ConsoleHost_history.txt")
            : home.resolve(".local/share/powershell/PSReadLine/ConsoleHost_history.txt");
        return List.of(
            new ShellHistorySource(HistoryShell.ZSH, zsh),
            new ShellHistorySource(HistoryShell.BASH, home.resolve(".bash_history")),
            new ShellHistorySource(HistoryShell.FISH, home.resolve(".local/share/fish/fish_history")),
            new ShellHistorySource(HistoryShell.NUSHELL, home.resolve(".config/nushell/history.txt")),
            new ShellHistorySource(HistoryShell.POWERSHELL, powershell));
    }
}
