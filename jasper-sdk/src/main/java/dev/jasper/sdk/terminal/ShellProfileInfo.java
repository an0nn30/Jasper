package dev.jasper.sdk.terminal;

import java.util.Objects;
import java.util.Optional;

/**
 * A shell Jasper detected and can start in a local pane, as {@link Terminals#profiles()} lists it. Open one with
 * {@link OpenRequest#localProfile(String)}.
 *
 * @param id the stable id to open it by, such as {@code pwsh} or {@code wsl:Ubuntu}
 * @param name the display name, such as {@code Ubuntu}
 * @param kind what sort of shell it is
 * @param detail a short qualifier to show beside the name; for a WSL distribution {@code WSL 1} or {@code WSL 2}
 * @since 0.9.1
 */
public record ShellProfileInfo(String id, String name, Kind kind, Optional<String> detail) {
    /** What sort of shell a profile starts. */
    public enum Kind {
        /** The platform's login shell, which Jasper starts when nothing else is configured (macOS and Linux, id {@code login}). */
        LOGIN_SHELL,
        /** A shell installed on this machine, such as PowerShell, PowerShell 7, Command Prompt or Cygwin Bash. */
        LOCAL_SHELL,
        /** A Windows Subsystem for Linux distribution. */
        WSL,
        /** The program and arguments the user configured in Jasper's settings. */
        CUSTOM
    }

    /** Rejects nulls and a blank id or name. */
    public ShellProfileInfo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(detail, "detail");
        if (id.isBlank()) throw new IllegalArgumentException("A shell profile needs an id");
        if (name.isBlank()) throw new IllegalArgumentException("A shell profile needs a name");
    }
}
