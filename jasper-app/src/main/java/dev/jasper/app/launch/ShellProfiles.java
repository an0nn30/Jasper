package dev.jasper.app.launch;

import dev.jasper.app.config.TerminalConfig;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The shell profiles an OS offers and how a profile id becomes one. Pure apart from the four-argument
 * {@code available}, which looks for PowerShell 7 on disk.
 */
public final class ShellProfiles {
    public static final String LOGIN = "login";
    public static final String POWERSHELL = "powershell";
    public static final String PWSH = "pwsh";
    public static final String CMD = "cmd";
    public static final String CUSTOM = "custom";
    public static final String WSL_PREFIX = "wsl:";
    private static final Pattern WSL_ID = Pattern.compile("wsl:([A-Za-z0-9._-]+)");

    private ShellProfiles() { }

    /**
     * A profile id resolved.
     *
     * @param profile the profile to start
     * @param warning why it is not the one asked for, when it is not
     */
    public record Resolution(ShellProfile profile, Optional<String> warning) {
        public Resolution {
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(warning, "warning");
        }
    }

    /** As the five-argument form, looking for PowerShell 7 on disk. Touches the file system: not on the EDT. */
    public static List<ShellProfile> available(String osName, Map<String, String> environment, List<WslDistro> distros,
                                               TerminalConfig.Shell shell) {
        return available(osName, environment, distros, findPwsh(osName, environment, ShellProfiles::exists), shell);
    }

    /**
     * The profiles for {@code osName}, platform default first. Windows: PowerShell, PowerShell 7 when {@code pwsh}
     * names it, Command Prompt and each distribution; macOS and Linux: the login shell. Custom is always last.
     */
    public static List<ShellProfile> available(String osName, Map<String, String> environment, List<WslDistro> distros,
                                               Optional<String> pwsh, TerminalConfig.Shell shell) {
        var profiles = new ArrayList<ShellProfile>();
        if (windows(osName)) {
            profiles.add(powershell());
            pwsh.ifPresent(program -> profiles.add(pwsh(program)));
            profiles.add(cmd(environment));
            for (WslDistro distro : distros) profiles.add(wsl(distro));
        } else {
            profiles.add(login(osName, environment));
        }
        profiles.add(custom(osName, environment, shell));
        return List.copyOf(profiles);
    }

    /** {@code powershell} on Windows, {@code login} elsewhere. */
    public static String platformDefault(String osName) { return windows(osName) ? POWERSHELL : LOGIN; }

    /** The id the settings choose: the profile, else Custom for the old program and args settings, else the platform default. */
    public static String configuredId(String osName, TerminalConfig.Shell shell) {
        if (!shell.profile().isEmpty()) return shell.profile();
        return shell.custom() ? CUSTOM : platformDefault(osName);
    }

    /** The configured program and args; an empty program means the platform's default shell, as it always has. */
    public static ShellProfile custom(String osName, Map<String, String> environment, TerminalConfig.Shell shell) {
        var command = new ArrayList<>(shell.program().isEmpty() ? DefaultShell.command(osName, environment) : List.of(shell.program()));
        command.addAll(shell.args());
        Optional<String> detail = shell.program().isEmpty() ? Optional.empty() : basename(shell.program());
        return new ShellProfile(CUSTOM, "Custom", ShellProfile.Kind.CUSTOM, detail, command);
    }

    /**
     * Whether two profile ids name the same profile: equal, or both {@code wsl:NAME} with names that differ only in
     * case, because WSL distribution names are case-insensitive.
     */
    public static boolean sameId(String a, String b) {
        return a.equals(b) || a.startsWith(WSL_PREFIX) && b.startsWith(WSL_PREFIX) && a.equalsIgnoreCase(b);
    }

    /**
     * The profile to start for {@code requested} (empty: the configured id). Custom always follows {@code shell}. An
     * {@code available} profile matches by {@link #sameId}, so a {@code wsl:NAME} in another case resolves to the
     * detected spelling. While detection has not completed, a well-formed {@code wsl:NAME} (as written; wsl.exe
     * ignores case) or {@code pwsh} is trusted; otherwise an id that is not {@code available} starts the platform
     * default with a warning.
     */
    public static Resolution resolve(Optional<String> requested, List<ShellProfile> available, boolean detectionComplete,
                                     String osName, Map<String, String> environment, TerminalConfig.Shell shell) {
        String id = requested.filter(value -> !value.isBlank()).orElseGet(() -> configuredId(osName, shell));
        if (id.equals(CUSTOM)) return new Resolution(custom(osName, environment, shell), Optional.empty());
        for (ShellProfile profile : available) if (sameId(profile.id(), id)) return new Resolution(profile, Optional.empty());
        if (!detectionComplete && windows(osName)) {
            Matcher wsl = WSL_ID.matcher(id);
            if (wsl.matches()) return new Resolution(wsl(wsl.group(1), Optional.empty()), Optional.empty());
            if (id.equals(PWSH)) return new Resolution(pwsh("pwsh.exe"), Optional.empty());
        }
        String fallbackId = platformDefault(osName);
        ShellProfile fallback = available.stream().filter(profile -> profile.id().equals(fallbackId)).findFirst()
            .orElseGet(() -> windows(osName) ? powershell() : login(osName, environment));
        return new Resolution(fallback, Optional.of("The shell profile \"" + id + "\" is not available; new panes start "
            + fallback.name() + " instead."));
    }

    /**
     * PowerShell 7's program: {@code pwsh.exe} when a {@code PATH} directory holds it, else its full path under
     * {@code %ProgramFiles%\PowerShell\7} or {@code 7-preview}, else the Microsoft Store package's execution alias
     * {@code %LOCALAPPDATA%\Microsoft\WindowsApps\pwsh.exe}; empty off Windows or when it is not installed.
     */
    public static Optional<String> findPwsh(String osName, Map<String, String> environment, Predicate<String> exists) {
        if (!windows(osName)) return Optional.empty();
        String path = variable(environment, "PATH");
        if (path != null) {
            for (String entry : path.split(";")) {
                String directory = entry.strip();
                if (directory.length() >= 2 && directory.startsWith("\"") && directory.endsWith("\""))
                    directory = directory.substring(1, directory.length() - 1);
                while (directory.endsWith("\\")) directory = directory.substring(0, directory.length() - 1);
                if (!directory.isEmpty() && exists.test(directory + "\\pwsh.exe")) return Optional.of("pwsh.exe");
            }
        }
        String programFiles = variable(environment, "ProgramFiles");
        if (programFiles != null && !programFiles.isBlank()) {
            for (String version : List.of("7", "7-preview")) {
                String candidate = programFiles + "\\PowerShell\\" + version + "\\pwsh.exe";
                if (exists.test(candidate)) return Optional.of(candidate);
            }
        }
        String localAppData = variable(environment, "LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            String alias = localAppData + "\\Microsoft\\WindowsApps\\pwsh.exe";
            if (exists.test(alias)) return Optional.of(alias);
        }
        return Optional.empty();
    }

    /**
     * Whether something exists at {@code name}; false for a name that is not a path here. Links are not followed: a
     * Store app's execution alias is a reparse point that {@code Files.exists} cannot follow, yet it starts the app.
     * Touches the file system.
     */
    static boolean exists(String name) {
        try {
            return Files.exists(Path.of(name), LinkOption.NOFOLLOW_LINKS);
        } catch (InvalidPathException | SecurityException notHere) {
            return false;
        }
    }

    static boolean windows(String osName) { return osName.toLowerCase(Locale.ROOT).startsWith("windows"); }

    private static ShellProfile powershell() {
        return new ShellProfile(POWERSHELL, "PowerShell", ShellProfile.Kind.LOCAL_SHELL, Optional.empty(), List.of("powershell.exe", "-NoLogo"));
    }

    private static ShellProfile pwsh(String program) {
        return new ShellProfile(PWSH, "PowerShell 7", ShellProfile.Kind.LOCAL_SHELL, Optional.empty(), List.of(program, "-NoLogo"));
    }

    private static ShellProfile cmd(Map<String, String> environment) {
        String comspec = variable(environment, "ComSpec");
        return new ShellProfile(CMD, "Command Prompt", ShellProfile.Kind.LOCAL_SHELL, Optional.empty(),
            List.of(comspec == null || comspec.isBlank() ? "cmd.exe" : comspec));
    }

    private static ShellProfile login(String osName, Map<String, String> environment) {
        List<String> command = DefaultShell.command(osName, environment);
        return new ShellProfile(LOGIN, "Login shell", ShellProfile.Kind.LOGIN_SHELL, basename(command.getFirst()), command);
    }

    private static ShellProfile wsl(WslDistro distro) {
        return wsl(distro.name(), Optional.of("WSL " + distro.version() + (distro.isDefault() ? " \u00B7 default" : "")));
    }

    private static ShellProfile wsl(String name, Optional<String> detail) {
        return new ShellProfile(WSL_PREFIX + name, name, ShellProfile.Kind.WSL, detail, List.of("wsl.exe", "-d", name));
    }

    /** The last path segment by either separator, so a Windows path reads the same on every OS; empty when unusable. */
    private static Optional<String> basename(String program) {
        if (program.indexOf('\0') >= 0) return Optional.empty();
        String name = program.substring(Math.max(program.lastIndexOf('/'), program.lastIndexOf('\\')) + 1);
        return name.isBlank() ? Optional.empty() : Optional.of(name);
    }

    /** Windows environment names are case-insensitive, and a copied map no longer knows that. */
    private static String variable(Map<String, String> environment, String name) {
        String exact = environment.get(name);
        if (exact != null) return exact;
        for (var entry : environment.entrySet()) if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
        return null;
    }
}
