package dev.jasper.app.launch;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A shell new panes can start.
 *
 * @param id what {@code terminal.shell.profile} saves: {@code login}, {@code powershell}, {@code pwsh}, {@code cmd},
 *           {@code wsl:NAME} or {@code custom}
 * @param name what tabs and the Settings dropdown call it; a WSL distribution's own name
 * @param kind what sort of shell it is
 * @param detail more about it: "WSL 1" or "WSL 2" (with " &middot; default" for the default distribution), or the
 *               program name of the login or Custom shell
 * @param command the program and its arguments; a WSL launch appends {@code --cd} and a directory
 */
public record ShellProfile(String id, String name, Kind kind, Optional<String> detail, List<String> command) {
    /** What sort of shell a profile is. */
    public enum Kind {
        /** The user's login shell on macOS and Linux. */
        LOGIN_SHELL,
        /** A Windows shell: PowerShell, PowerShell 7 or Command Prompt. */
        LOCAL_SHELL,
        /** A WSL distribution's default shell. */
        WSL,
        /** The configured program and arguments. */
        CUSTOM
    }

    public ShellProfile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(detail, "detail");
        if (id.isBlank() || name.isBlank()) throw new IllegalArgumentException("A shell profile needs an id and a name.");
        command = List.copyOf(command);
        if (command.isEmpty()) throw new IllegalArgumentException("Shell command must not be empty.");
    }

    /** The distribution a WSL profile starts; empty for every other kind. */
    public Optional<String> distro() {
        return kind == Kind.WSL && id.startsWith(ShellProfiles.WSL_PREFIX)
            ? Optional.of(id.substring(ShellProfiles.WSL_PREFIX.length())) : Optional.empty();
    }

    /** The dropdown text: the name, followed by the detail in parentheses when there is one. */
    public String label() { return detail.map(text -> name + " (" + text + ")").orElse(name); }
}
