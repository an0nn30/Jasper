package dev.jasper.app;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Immutable process inputs captured before a shell request leaves the EDT. */
record LaunchSettings(List<String> command, Map<String, String> environment,
                      int columns, int lines, int scrollback) {
    LaunchSettings {
        command = List.copyOf(command);
        environment = Map.copyOf(environment);
        if (command.isEmpty()) throw new IllegalArgumentException("Shell command must not be empty.");
        if (columns < 5 || columns > 500 || lines < 2 || lines > 200) {
            throw new IllegalArgumentException("Invalid initial terminal grid.");
        }
        if (scrollback < 0 || scrollback > 1_000_000) throw new IllegalArgumentException("Invalid scrollback capacity.");
    }

    static LaunchSettings resolve(ConfigSnapshot snapshot, String osName, Map<String, String> inherited,
                                  int windowColumns, int windowLines) {
        return resolve(snapshot, osName, inherited, windowColumns, windowLines, null);
    }

    /** {@code integrationDir} is the extracted script directory, or null when extraction failed or tests want none. */
    static LaunchSettings resolve(ConfigSnapshot snapshot, String osName, Map<String, String> inherited,
                                  int windowColumns, int windowLines, Path integrationDir) {
        TerminalConfig terminal = snapshot.terminal();
        var command = new ArrayList<>(terminal.shell().program().isEmpty()
            ? DefaultShell.command(osName, inherited) : List.of(terminal.shell().program()));
        command.addAll(terminal.shell().args());
        var environment = new HashMap<>(inherited);
        environment.keySet().removeIf(name -> name.equals("TERM_PROGRAM") || name.equals("TERM_PROGRAM_VERSION")
            || name.equals("TERM_SESSION_ID") || name.equals("TMUX") || name.equals("TMUX_PANE")
            || name.startsWith("ITERM_"));
        environment.put("TERM_PROGRAM", "Jasper");
        environment.putAll(terminal.env());
        if (osName.toLowerCase(Locale.ROOT).startsWith("mac")
                && environment.getOrDefault("LANG", "").isBlank()) {
            environment.put("LANG", "en_US.UTF-8");
        }
        environment.put("TERM", "xterm-256color");
        environment.put("COLORTERM", "truecolor");
        if (integrationDir != null && terminal.shellIntegration() != ShellIntegrationMode.OFF) {
            environment.put("JASPER_SHELL_INTEGRATION", integrationDir.toString());
            if (terminal.shellIntegration() == ShellIntegrationMode.AUTO) inject(command, environment, integrationDir);
        }
        return new LaunchSettings(command, environment, windowColumns, windowLines, terminal.scrollback());
    }

    /** Auto mode for one shell: zsh through ZDOTDIR wrappers, bash through --rcfile, fish through XDG_DATA_DIRS. */
    static void inject(List<String> command, Map<String, String> environment, Path dir) {
        String shell;
        try {
            Path name = Path.of(command.getFirst()).getFileName();
            shell = name == null ? "" : name.toString();
        } catch (InvalidPathException failure) {
            return;
        }
        switch (shell) {
            case "zsh" -> {
                String original = environment.get("ZDOTDIR");
                if (original != null && !original.isBlank()) environment.put("JASPER_ORIGINAL_ZDOTDIR", original);
                else environment.remove("JASPER_ORIGINAL_ZDOTDIR");
                environment.put("ZDOTDIR", dir.resolve("zsh").toString());
            }
            case "bash" -> {
                boolean login = false;
                for (int i = command.size() - 1; i >= 1; i--) {
                    if (command.get(i).equals("-l") || command.get(i).equals("--login")) { command.remove(i); login = true; }
                }
                command.add(1, "--rcfile");
                command.add(2, dir.resolve("bash/rc.bash").toString());
                if (login) environment.put("JASPER_LOGIN_SHELL", "1");
            }
            case "fish" -> {
                String existing = environment.get("XDG_DATA_DIRS");
                String rest = existing == null || existing.isBlank() ? "/usr/local/share:/usr/share" : existing;
                environment.put("XDG_DATA_DIRS", dir.resolve("fish") + ":" + rest);
            }
            default -> {
                // Only the exported variables reach other programs.
            }
        }
    }

    String label() {
        try {
            Path name = Path.of(command.getFirst()).getFileName();
            return name == null || name.toString().isBlank() ? "shell" : name.toString();
        } catch (InvalidPathException failure) {
            return "shell";
        }
    }
}
