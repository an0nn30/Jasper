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
        // JASPER_* goes too: the scripts export JASPER_INTEGRATION_LOADED and return early when they
        // see it, so a Jasper launched from an integrated pane would get no marks in any pane.
        environment.keySet().removeIf(name -> name.equals("TERM_PROGRAM") || name.equals("TERM_PROGRAM_VERSION")
            || name.equals("TERM_SESSION_ID") || name.equals("TMUX") || name.equals("TMUX_PANE")
            || name.startsWith("ITERM_") || name.startsWith("JASPER_"));
        environment.put("TERM_PROGRAM", "Jasper");
        // tmux overwrites TERM_PROGRAM with "tmux" in every pane, so the scripts need a marker it
        // leaves alone. Measured: arbitrary variables do reach a pane of a server Jasper started.
        environment.put("JASPER_TERMINAL", "1");
        // Before the overlay, so a [terminal.env] override wins here as it does for TERM_PROGRAM.
        if (integrationDir != null && terminal.shellIntegration() != ShellIntegrationMode.OFF) {
            environment.put("JASPER_SHELL_INTEGRATION", integrationDir.toString());
        }
        environment.putAll(terminal.env());
        if (osName.toLowerCase(Locale.ROOT).startsWith("mac")
                && environment.getOrDefault("LANG", "").isBlank()) {
            environment.put("LANG", "en_US.UTF-8");
        }
        environment.put("TERM", "xterm-256color");
        environment.put("COLORTERM", "truecolor");
        if (integrationDir != null && terminal.shellIntegration() == ShellIntegrationMode.AUTO) {
            String effective = environment.get("JASPER_SHELL_INTEGRATION");
            try {
                if (effective != null && !effective.isBlank()) inject(command, environment, Path.of(effective));
            } catch (InvalidPathException notAPath) {
                // A [terminal.env] value that is not a path: the variables only, as for any other program.
            }
        }
        return new LaunchSettings(command, environment, windowColumns, windowLines, terminal.scrollback());
    }

    /**
     * Auto mode for one shell: zsh through ZDOTDIR wrappers, bash through --rcfile, fish through
     * XDG_DATA_DIRS. Both {@code command} and {@code environment} are edited in place, so both must
     * be mutable; anything other than those three shells is left exactly as it was.
     */
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
                // bash honours only the last --rcfile and ignores it entirely under --norc or -c. In
                // those cases Jasper's wrapper would never run, so stripping -l would leave a login
                // shell with nothing to emulate it: keep the user's command exactly as written.
                int options = command.size();
                boolean login = false;
                boolean noProfile = false;
                for (int i = 1; i < command.size(); i++) {
                    String argument = command.get(i);
                    boolean cluster = argument.length() > 1 && (argument.charAt(0) == '-' || argument.charAt(0) == '+')
                        && argument.charAt(1) != '-';
                    if (argument.equals("--") || argument.equals("-") || argument.equals("+")
                        || !(argument.startsWith("-") || argument.startsWith("+"))) { options = i; break; }
                    if (argument.equals("--norc") || argument.equals("--rcfile") || argument.equals("--init-file")
                        || argument.equals("-c") || (cluster && argument.indexOf('c') > 0)) return;
                    noProfile |= argument.equals("--noprofile");
                    login |= argument.equals("--login") || (cluster && argument.indexOf('l') > 0);
                    // -o and -O take a following word. It is an option argument, not an operand, so
                    // skipping it keeps the scan going instead of ending it on "vi" or "extglob".
                    if (cluster && (argument.endsWith("o") || argument.endsWith("O"))) i++;
                }
                for (int i = options - 1; i >= 1; i--) {
                    String argument = command.get(i);
                    if (argument.equals("-l") || argument.equals("--login")) {
                        command.remove(i);
                    } else if (argument.length() > 1 && argument.charAt(0) == '-' && argument.charAt(1) != '-'
                        && argument.indexOf('l') > 0) {
                        String stripped = argument.replace("l", "");
                        if (stripped.equals("-")) command.remove(i); else command.set(i, stripped);
                    }
                }
                command.add(1, "--rcfile");
                command.add(2, dir.resolve("bash/rc.bash").toString());
                // rc.bash reads the profile files whenever this is set, which --noprofile forbids.
                if (login && !noProfile) environment.put("JASPER_LOGIN_SHELL", "1");
            }
            case "tmux" -> {
                // tmux runs the user's $SHELL per pane and passes its own environment down, so the
                // injection that reaches that shell is the environment kind. Recursing keeps the zsh
                // and fish arms as the single definition of each mechanism. bash's is --rcfile, an
                // argument tmux never sees, so bash inside tmux is left to the manual source line.
                String inner = environment.get("SHELL");
                if (inner == null || inner.isBlank()) return;
                String innerShell;
                try {
                    Path name = Path.of(inner).getFileName();
                    innerShell = name == null ? "" : name.toString();
                } catch (InvalidPathException notAPath) {
                    return;
                }
                if (innerShell.equals("zsh") || innerShell.equals("fish")) {
                    inject(new ArrayList<>(List.of(inner)), environment, dir);
                }
            }
            case "fish" -> {
                String jasper = dir.resolve("fish").toString();
                String existing = environment.get("XDG_DATA_DIRS");
                String rest = existing == null || existing.isBlank() ? "/usr/local/share:/usr/share" : existing;
                environment.put("XDG_DATA_DIRS",
                    rest.equals(jasper) || rest.startsWith(jasper + ":") ? rest : jasper + ":" + rest);
            }
            default -> {
                // Only the exported variables reach other programs. A Windows basename ("bash.exe")
                // never matches an arm above, which is intended: those shells are out of scope.
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
