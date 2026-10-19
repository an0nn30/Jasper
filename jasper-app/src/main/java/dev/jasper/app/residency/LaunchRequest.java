package dev.jasper.app.residency;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * One line of the handoff protocol (workspace-tabs spec 5.3): what a second launch asks the home's owner to do.
 * Protocol 2 carries the launch's intent, its working directory, its paths (absolute: resolved against that directory)
 * and the start-up flags it could not apply.
 *
 * @param token the token beside the owner's socket; empty until the request is sent
 * @param codeLocation the launch's jar or classes directory; empty when unknown
 * @param codeLocationModified its modification time
 * @param intent what the launch asks
 * @param workingDirectory the directory the launch ran in, absolute
 * @param paths the paths to open, absolute; some exactly when the intent is OPEN_PATHS
 * @param ignoredFlags the start-up flags the launch could not apply, from {@link #STARTUP_FLAGS}
 */
public record LaunchRequest(String token, Path codeLocation, long codeLocationModified, Intent intent, Path workingDirectory,
                            List<Path> paths, List<String> ignoredFlags) {
    /** The longest request line, newline included; a longer one is not one of ours. */
    public static final int MAX_LINE_BYTES = 1 << 20;
    /** The start-up flags a second launch can report as ignored (spec 5.2). */
    public static final Set<String> STARTUP_FLAGS = Set.of("--safe-mode", "--config", "--plugin-dir");
    private static final String MAGIC = "jasper";
    private static final int PROTOCOL = 2;
    private static final int FIELDS = 9;

    /** Rejects nulls, a relative path, an intent its paths contradict and a flag that is not a start-up flag. */
    public LaunchRequest {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(codeLocation, "codeLocation");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        paths = List.copyOf(paths);
        ignoredFlags = List.copyOf(ignoredFlags);
        if (!workingDirectory.isAbsolute()) throw new IllegalArgumentException("The working directory is not absolute: " + workingDirectory);
        for (Path path : paths) if (!path.isAbsolute()) throw new IllegalArgumentException("A path is not absolute: " + path);
        if ((intent == Intent.OPEN_PATHS) == paths.isEmpty())
            throw new IllegalArgumentException(intent + " with " + paths.size() + " paths");
        for (String flag : ignoredFlags)
            if (!STARTUP_FLAGS.contains(flag)) throw new IllegalArgumentException("Not a start-up flag: " + flag);
    }

    /**
     * A launch's request: OPEN_PATHS when it names paths, RAISE otherwise. The token is filled in when it is sent.
     *
     * @param codeLocation the launch's jar or classes directory, or null when unknown
     * @param codeLocationModified its modification time
     * @param workingDirectory the directory the launch ran in, absolute
     * @param paths the paths to open, absolute
     * @param ignoredFlags the start-up flags the launch could not apply
     * @return the request
     */
    public static LaunchRequest of(Path codeLocation, long codeLocationModified, Path workingDirectory, List<Path> paths,
                                   List<String> ignoredFlags) {
        return new LaunchRequest("", codeLocation == null ? Path.of("") : codeLocation, codeLocationModified,
            paths.isEmpty() ? Intent.RAISE : Intent.OPEN_PATHS, workingDirectory, paths, ignoredFlags);
    }

    /** What the launch asks. */
    public enum Intent {
        /** Raise or restore Jasper's window. */
        RAISE,
        /** Open the paths: each file in the editor, each directory as a terminal tab. */
        OPEN_PATHS
    }

    /** This request signed with {@code value}. */
    LaunchRequest withToken(String value) {
        return new LaunchRequest(value, codeLocation, codeLocationModified, intent, workingDirectory, paths, ignoredFlags);
    }

    /**
     * Whether this request, signed with a token, stays within {@link #MAX_LINE_BYTES}.
     *
     * @return true when it can be sent
     */
    public boolean fits() {
        return withToken("t".repeat(64)).encode().getBytes(StandardCharsets.UTF_8).length <= MAX_LINE_BYTES;
    }

    /**
     * Whether the launch ran another build than {@code source} at {@code modified}: its code location or its time
     * differs. An unknown location compares equal to itself and to nothing else.
     *
     * @param source the owner's code location, or null when unknown
     * @param modified its modification time
     * @return true for another build
     */
    public boolean anotherBuild(Path source, long modified) {
        return !codeLocation.equals(source == null ? Path.of("") : source) || codeLocationModified != modified;
    }

    /**
     * One newline-terminated line of nine tab-separated fields. Text is base64, so a tab or newline in a path survives;
     * each list is its items' base64, comma-separated.
     */
    String encode() {
        return String.join("\t", MAGIC, Integer.toString(PROTOCOL), token, text(codeLocation.toString()),
            Long.toString(codeLocationModified), intent.name().toLowerCase(Locale.ROOT), text(workingDirectory.toString()),
            list(paths.stream().map(Path::toString)), list(ignoredFlags.stream())) + "\n";
    }

    /** Null for anything this build cannot act on, including another protocol. Never throws. */
    static LaunchRequest decode(String line) {
        String body = line.endsWith("\n") ? line.substring(0, line.length() - 1) : line;
        String[] parts = body.split("\t", -1);
        if (parts.length != FIELDS || !MAGIC.equals(parts[0])) return null;
        try {
            if (Integer.parseInt(parts[1]) != PROTOCOL) return null;
            Intent intent = switch (parts[5]) {
                case "raise" -> Intent.RAISE;
                case "open_paths" -> Intent.OPEN_PATHS;
                default -> null;
            };
            if (intent == null) return null;
            return new LaunchRequest(parts[2], Path.of(untext(parts[3])), Long.parseLong(parts[4]), intent,
                Path.of(untext(parts[6])), items(parts[7]).map(Path::of).toList(), items(parts[8]).toList());
        } catch (IllegalArgumentException malformed) {
            // A bad number, bad base64, an unusable or relative path, a contradicted intent, an unknown flag.
            return null;
        }
    }

    private static String text(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String untext(String field) {
        return new String(Base64.getDecoder().decode(field), StandardCharsets.UTF_8);
    }

    private static String list(Stream<String> items) {
        return items.map(LaunchRequest::text).collect(Collectors.joining(","));
    }

    private static Stream<String> items(String field) {
        return field.isEmpty() ? Stream.empty() : Arrays.stream(field.split(",", -1)).map(LaunchRequest::untext);
    }

    /** What the owner says back. Anything unrecognised is a protocol error, never an accept. */
    public enum Response {
        /** Taken: the owner carries it out. */
        OK,
        /** Refused: the token was not the owner's. */
        TOKEN,
        /** Taken from another build: the owner carries it out, or restarts into the new build (spec 5.3). */
        UPDATED,
        /** Not understood, not answered, or nothing to send it to. */
        PROTOCOL;

        String line() {
            return switch (this) {
                case OK -> "ok\n";
                case UPDATED -> "updated\n";
                case TOKEN -> "refused\ttoken\n";
                case PROTOCOL -> "refused\tprotocol\n";
            };
        }

        static Response of(String line) {
            return switch (line.strip()) {
                case "ok" -> OK;
                case "updated" -> UPDATED;
                case "refused\ttoken" -> TOKEN;
                default -> PROTOCOL;
            };
        }
    }
}
