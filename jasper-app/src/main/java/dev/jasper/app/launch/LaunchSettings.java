package dev.jasper.app.launch;

import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.ShellIntegrationMode;
import dev.jasper.app.config.TerminalConfig;
import dev.jasper.terminal.session.SessionLaunchOptions;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/** Immutable process inputs captured before a shell request leaves the EDT, and the shell profile they came from. */
public record LaunchSettings(List<String> command, Map<String, String> environment,
                      int columns, int lines, int scrollback, ShellProfile profile) {
    /** What a WSL shell needs from Windows; WSLENV is the only way variables cross into Linux. */
    private static final List<String> WSL_SHARED = List.of("TERM", "COLORTERM", "TERM_PROGRAM", "JASPER_TERMINAL");

    public LaunchSettings {
        command = List.copyOf(command);
        environment = Map.copyOf(environment);
        Objects.requireNonNull(profile, "profile");
        if (command.isEmpty()) throw new IllegalArgumentException("Shell command must not be empty.");
        if (columns < 5 || columns > 500 || lines < 2 || lines > 200) {
            throw new IllegalArgumentException("Invalid initial terminal grid.");
        }
        if (scrollback < 0 || scrollback > 1_000_000) throw new IllegalArgumentException("Invalid scrollback capacity.");
    }

    /** Settings for a command that is not one of the detected profiles; it counts as the Custom profile. */
    public LaunchSettings(List<String> command, Map<String, String> environment, int columns, int lines, int scrollback) {
        this(command, environment, columns, lines, scrollback,
            new ShellProfile(ShellProfiles.CUSTOM, "Custom", ShellProfile.Kind.CUSTOM, Optional.empty(), command));
    }

    /** Where a launch runs: the PTY's directory, and the settings with a WSL profile's {@code --cd} argument added. */
    public record Start(Path directory, LaunchSettings settings) { }

    public static LaunchSettings resolve(ConfigSnapshot snapshot, String osName, Map<String, String> inherited,
                                  int windowColumns, int windowLines) {
        return resolve(snapshot, osName, inherited, windowColumns, windowLines, null);
    }

    /**
     * The configured profile without detection: WSL distributions and PowerShell 7 count as missing, so they start
     * the platform default. {@code integrationDir} is the extracted script directory, or null.
     */
    public static LaunchSettings resolve(ConfigSnapshot snapshot, String osName, Map<String, String> inherited,
                                  int windowColumns, int windowLines, Path integrationDir) {
        TerminalConfig.Shell shell = snapshot.terminal().shell();
        ShellProfile profile = ShellProfiles.resolve(Optional.empty(), ShellProfiles.available(osName, inherited, List.of(), Optional.empty(), shell),
            true, osName, inherited, shell).profile();
        return resolve(snapshot, profile, osName, inherited, windowColumns, windowLines, integrationDir);
    }

    /** {@code integrationDir} is the extracted script directory, or null when extraction failed or tests want none. */
    public static LaunchSettings resolve(ConfigSnapshot snapshot, ShellProfile profile, String osName, Map<String, String> inherited,
                                  int windowColumns, int windowLines, Path integrationDir) {
        TerminalConfig terminal = snapshot.terminal();
        var command = new ArrayList<>(profile.command());
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
        if (profile.kind() == ShellProfile.Kind.WSL) {
            // /p makes WSL translate the Windows script directory into its /mnt/c/... path.
            var shared = new ArrayList<>(WSL_SHARED);
            if (environment.containsKey("JASPER_SHELL_INTEGRATION")) shared.add("JASPER_SHELL_INTEGRATION/p");
            // Windows names are case-insensitive, so the inherited "WslEnv" and a [terminal.env] "WSLENV" are one
            // variable: merge every spelling (inherited first) into a single entry under the first name.
            List<String> names = environment.keySet().stream().filter(name -> name.equalsIgnoreCase("WSLENV"))
                .sorted(java.util.Comparator.comparing((String name) -> terminal.env().containsKey(name)).thenComparing(name -> name))
                .toList();
            String existing = null;
            for (String name : names) {
                String value = environment.remove(name);
                existing = existing == null ? value : existing + ":" + value;
            }
            environment.put(names.isEmpty() ? "WSLENV" : names.getFirst(), wslEnv(existing, shared));
        }
        if (integrationDir != null && terminal.shellIntegration() == ShellIntegrationMode.AUTO) {
            String effective = environment.get("JASPER_SHELL_INTEGRATION");
            try {
                if (effective != null && !effective.isBlank()) inject(command, environment, Path.of(effective));
            } catch (InvalidPathException notAPath) {
                // A [terminal.env] value that is not a path: the variables only, as for any other program.
            }
        }
        return new LaunchSettings(command, environment, windowColumns, windowLines, terminal.scrollback(), profile);
    }

    /** {@code existing} WSLENV entries, then each of {@code entries} whose variable is not already listed. */
    static String wslEnv(String existing, List<String> entries) {
        var parts = new ArrayList<String>();
        if (existing != null) for (String part : existing.split(":")) if (!part.isEmpty()) parts.add(part);
        for (String entry : entries) {
            String name = variableOf(entry);
            if (parts.stream().noneMatch(part -> variableOf(part).equals(name))) parts.add(entry);
        }
        return String.join(":", parts);
    }

    private static String variableOf(String entry) {
        int flags = entry.indexOf('/');
        return flags < 0 ? entry : entry.substring(0, flags);
    }

    /**
     * The start for {@code requested}; empty means nobody asked for a directory (a new window or tab), so the shell
     * starts where a new shell starts: {@code home}, which a WSL profile reads as its Linux home ({@code --cd ~})
     * rather than {@code /mnt/c/Users/NAME}. A WSL profile starts its PTY in {@code home} and passes a requested
     * directory to {@code wsl.exe --cd}: the Linux path for its own distribution's share, a Windows directory that
     * exists as it is (the Windows home included), and {@code ~} otherwise. Any other profile asked to start on a WSL
     * share starts in {@code home}, because PowerShell and cmd misbehave in UNC directories and touching the share can
     * start the WSL 2 VM; otherwise the directory must exist. {@code isDirectory} touches the file system: call this
     * off the EDT.
     */
    public Start at(Optional<Path> requested, Path home, Predicate<Path> isDirectory) {
        Path directory = requested.orElse(home);
        boolean onShare = WslPaths.distroOf(directory).isPresent();
        Optional<String> distro = profile.distro();
        if (distro.isPresent()) {
            String cd = requested.isEmpty() ? "~" : WslPaths.toLinux(distro.get(), directory)
                .orElseGet(() -> !onShare && isDirectory.test(directory) ? directory.toString() : "~");
            var started = new ArrayList<>(command);
            started.add("--cd");
            started.add(cd);
            return new Start(home, new LaunchSettings(started, environment, columns, lines, scrollback, profile));
        }
        if (onShare) return new Start(home, this);
        if (!isDirectory.test(directory)) throw new IllegalArgumentException("Directory is unavailable: " + directory);
        return new Start(directory, this);
    }

    /** How the session reads a local working-directory report: a WSL profile's Linux paths become Windows paths. */
    public Function<String, Optional<Path>> localPaths() {
        return profile.distro().<Function<String, Optional<Path>>>map(distro -> path -> WslPaths.toWindows(distro, path))
            .orElse(SessionLaunchOptions.DEFAULT_LOCAL_PATHS);
    }

    /**
     * Auto mode for one shell: zsh through ZDOTDIR wrappers, bash through --rcfile, fish through
     * XDG_DATA_DIRS. Both {@code command} and {@code environment} are edited in place, so both must
     * be mutable; anything other than those three shells is left exactly as it was.
     */
    public static void inject(List<String> command, Map<String, String> environment, Path dir) {
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
                // never matches an arm above, which is intended: those shells are out of scope, and so
                // is wsl.exe, which cannot know the distribution's login shell (spec section 3).
            }
        }
    }

    /** The tab's name: a Windows shell's or WSL distribution's profile name; a login or Custom shell's program name. */
    String label() {
        if (profile.kind() == ShellProfile.Kind.WSL || profile.kind() == ShellProfile.Kind.LOCAL_SHELL) return profile.name();
        try {
            Path name = Path.of(command.getFirst()).getFileName();
            return name == null || name.toString().isBlank() ? "shell" : name.toString();
        } catch (InvalidPathException failure) {
            return "shell";
        }
    }
}
