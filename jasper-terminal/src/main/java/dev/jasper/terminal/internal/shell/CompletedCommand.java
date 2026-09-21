package dev.jasper.terminal.internal.shell;
import java.util.Optional;
import java.util.OptionalInt;
import java.nio.file.Path;
import java.time.Duration;

/** Shell command tracking value. */
public record CompletedCommand(String command, OptionalInt status, Optional<Path> directory, Duration duration) { }
