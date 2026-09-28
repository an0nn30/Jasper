package dev.jasper.sdk.terminal;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * A local terminal to open: the user's configured shell, or one of the shell profiles Jasper detected.
 *
 * @param workingDirectory where it starts; empty means where Jasper's own New Tab would start
 * @param profile the id of a profile from {@link Terminals#profiles()} (since 0.8.6); empty means the shell Jasper's own
 *     New Tab or Split would start. An id the host does not list opens nothing.
 */
public record LocalSpec(Optional<Path> workingDirectory, Optional<String> profile) {
    /** Rejects null, a relative directory and a blank profile id. */
    public LocalSpec {
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        Objects.requireNonNull(profile, "profile");
        if (workingDirectory.isPresent() && !workingDirectory.get().isAbsolute())
            throw new IllegalArgumentException("A working directory must be absolute: " + workingDirectory.get());
        if (profile.isPresent() && profile.get().isBlank()) throw new IllegalArgumentException("A profile id cannot be blank");
    }

    /**
     * The user's shell.
     *
     * @param workingDirectory where it starts; empty means where Jasper's own New Tab would start
     */
    public LocalSpec(Optional<Path> workingDirectory) { this(workingDirectory, Optional.empty()); }

    /**
     * A detected shell profile, where Jasper's own New Tab would start it.
     *
     * @param profileId an id from {@link Terminals#profiles()}
     * @return the spec
     * @since 0.8.6
     */
    public static LocalSpec ofProfile(String profileId) { return new LocalSpec(Optional.empty(), Optional.of(profileId)); }
}
