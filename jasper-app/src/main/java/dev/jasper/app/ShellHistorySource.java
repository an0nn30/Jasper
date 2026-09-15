package dev.jasper.app;

import java.nio.file.Path;
import java.util.ArrayList;
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
        String xdg = env.get("XDG_CONFIG_HOME");
        Path configHome = xdg == null || xdg.isBlank() ? home.resolve(".config") : Path.of(xdg);
        boolean macOs = osName != null && osName.toLowerCase(Locale.ROOT).startsWith("mac");
        var sources = new ArrayList<ShellHistorySource>();
        sources.add(new ShellHistorySource(HistoryShell.ZSH, zsh));
        sources.add(new ShellHistorySource(HistoryShell.BASH, home.resolve(".bash_history")));
        sources.add(new ShellHistorySource(HistoryShell.FISH, home.resolve(".local/share/fish/fish_history")));
        sources.add(new ShellHistorySource(HistoryShell.NUSHELL, configHome.resolve("nushell/history.txt")));
        // nushell's default config directory on macOS is Application Support unless XDG_CONFIG_HOME is set.
        if (macOs) sources.add(new ShellHistorySource(HistoryShell.NUSHELL,
            home.resolve("Library/Application Support/nushell/history.txt")));
        sources.add(new ShellHistorySource(HistoryShell.POWERSHELL, powershell));
        return List.copyOf(sources);
    }
}
