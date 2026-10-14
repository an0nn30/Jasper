package dev.jasper.sdk.terminal;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A local terminal to open: the user's configured shell, one of the shell profiles Jasper detected, or a program.
 *
 * @param workingDirectory where it starts; empty means where Jasper's own New Tab would start
 * @param profile the id of a profile from {@link Terminals#profiles()} (since 0.9.1); empty means the shell Jasper's own
 *     New Tab or Split would start. An id the host does not list opens nothing.
 * @param command a program run instead of the shell, with the shell's environment: the executable, then its arguments,
 *     each passed as it is, with no shell parsing them (since 0.17.1). Its tab or pane closes when the program exits with
 *     status 0 and stays open otherwise. Empty runs the shell.
 */
public record LocalSpec(Optional<Path> workingDirectory, Optional<String> profile, Optional<List<String>> command) {
    /**
     * Rejects null (a null word of the command too), a relative directory, a blank profile id, an empty command or one
     * whose program is blank, and a profile together with a command. The command is copied.
     */
    public LocalSpec {
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(command, "command");
        if (workingDirectory.isPresent() && !workingDirectory.get().isAbsolute())
            throw new IllegalArgumentException("A working directory must be absolute: " + workingDirectory.get());
        if (profile.isPresent() && profile.get().isBlank()) throw new IllegalArgumentException("A profile id cannot be blank");
        if (command.isPresent()) {
            List<String> words = List.copyOf(command.get());
            if (words.isEmpty()) throw new IllegalArgumentException("A command needs a program");
            if (words.getFirst().isBlank()) throw new IllegalArgumentException("A command's program cannot be blank");
            if (profile.isPresent()) throw new IllegalArgumentException("A local terminal runs a profile's shell or a command, not both");
            command = Optional.of(words);
        }
    }

    /**
     * The user's shell, or a detected shell profile.
     *
     * @param workingDirectory where it starts; empty means where Jasper's own New Tab would start
     * @param profile the id of a profile from {@link Terminals#profiles()}; empty means the shell Jasper's own New Tab or
     *     Split would start
     * @since 0.9.1
     */
    public LocalSpec(Optional<Path> workingDirectory, Optional<String> profile) { this(workingDirectory, profile, Optional.empty()); }

    /**
     * The user's shell.
     *
     * @param workingDirectory where it starts; empty means where Jasper's own New Tab would start
     */
    public LocalSpec(Optional<Path> workingDirectory) { this(workingDirectory, Optional.empty(), Optional.empty()); }

    /**
     * A detected shell profile, where Jasper's own New Tab would start it.
     *
     * @param profileId an id from {@link Terminals#profiles()}
     * @return the spec
     * @since 0.9.1
     */
    public static LocalSpec ofProfile(String profileId) { return new LocalSpec(Optional.empty(), Optional.of(profileId)); }

    /**
     * A program run in a tab or split instead of the shell, with the shell's environment. Its tab or pane closes when it
     * exits with status 0 and stays open otherwise.
     *
     * @param command the executable, then its arguments
     * @param workingDirectory where it starts; empty means where Jasper's own New Tab would start
     * @return the spec
     * @since 0.17.1
     */
    public static LocalSpec running(List<String> command, Optional<Path> workingDirectory) {
        return new LocalSpec(workingDirectory, Optional.empty(), Optional.of(command));
    }
}
