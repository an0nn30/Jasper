package dev.jasper.terminal.internal.shell;
import java.util.Optional;
import java.util.OptionalInt;
import java.nio.file.Path;
import java.time.Duration;

/** Shell command tracking value.
 * @param command captured command text
 * @param status reported exit status, empty if absent
 * @param directory directory captured for this command
 * @param duration monotonic elapsed duration since the command-start mark
 */
public record CompletedCommand(String command, OptionalInt status, Optional<Path> directory, Duration duration) { }
