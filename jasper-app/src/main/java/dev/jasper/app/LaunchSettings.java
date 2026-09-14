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
        TerminalConfig terminal = snapshot.terminal();
        var command = new ArrayList<>(terminal.shell().program().isEmpty()
            ? DefaultShell.command(osName, inherited) : List.of(terminal.shell().program()));
        command.addAll(terminal.shell().args());
        var environment = new HashMap<>(inherited);
        environment.keySet().removeIf(name -> name.equals("TERM_PROGRAM") || name.equals("TERM_PROGRAM_VERSION")
            || name.equals("TERM_SESSION_ID") || name.equals("TMUX") || name.equals("TMUX_PANE")
            || name.startsWith("ITERM_"));
        environment.putAll(terminal.env());
        if (osName.toLowerCase(Locale.ROOT).startsWith("mac")
                && environment.getOrDefault("LANG", "").isBlank()) {
            environment.put("LANG", "en_US.UTF-8");
        }
        environment.put("TERM", "xterm-256color");
        environment.put("COLORTERM", "truecolor");
        return new LaunchSettings(command, environment, windowColumns, windowLines, terminal.scrollback());
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
