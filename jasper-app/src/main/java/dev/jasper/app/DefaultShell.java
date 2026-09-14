package dev.jasper.app;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The command for the user's login shell on this OS. Plan 4 lets the config override it. */
final class DefaultShell {
    private DefaultShell() {
    }

    static List<String> command(String osName, Map<String, String> environment) {
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) {
            return List.of("powershell.exe", "-NoLogo");
        }
        String shell = environment.get("SHELL");
        if (shell == null || shell.isBlank()) {
            shell = os.startsWith("mac") ? "/bin/zsh" : "/bin/bash";
        }
        return List.of(shell, "-l");
    }
}
