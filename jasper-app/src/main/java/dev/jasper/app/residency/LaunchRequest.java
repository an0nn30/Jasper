package dev.jasper.app.residency;

import dev.jasper.app.platform.Browser;
import java.net.URI;
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
 * Protocol 3 carries the launch's intent, its working directory, its paths (absolute: resolved against that directory),
 * the start-up flags it could not apply and its web addresses (browser plan 4c). Protocol 2 is the same without the web
 * addresses: a request that has none is sent as protocol 2, so a running Jasper of the build before still takes it
 * (ruling Q11), and both decode.
 *
 * @param token the token beside the owner's socket; empty until the request is sent
 * @param codeLocation the launch's jar or classes directory; empty when unknown
 * @param codeLocationModified its modification time
 * @param intent what the launch asks
 * @param workingDirectory the directory the launch ran in, absolute
 * @param paths the paths to open, absolute
 * @param urls the web addresses to open, absolute http or https ones ({@link Browser#isWebAddress}), at most
 *     {@link #MAX_URLS}; paths or web addresses exactly when the intent is OPEN_PATHS
 * @param ignoredFlags the start-up flags the launch could not apply, from {@link #STARTUP_FLAGS}
 */
public record LaunchRequest(String token, Path codeLocation, long codeLocationModified, Intent intent, Path workingDirectory,
                            List<Path> paths, List<URI> urls, List<String> ignoredFlags) {
    /** The longest request line, newline included; a longer one is not one of ours. */
    public static final int MAX_LINE_BYTES = 1 << 20;
    /** The start-up flags a second launch can report as ignored (spec 5.2). */
    public static final Set<String> STARTUP_FLAGS = Set.of("--safe-mode", "--config", "--plugin-dir");
    /** The most web addresses a request carries: as many as wait for Jasper's browser (browser plan 4c). */
    public static final int MAX_URLS = 32;
    private static final String MAGIC = "jasper";
    private static final int PROTOCOL = 3;
    private static final int FIELDS = 10;
    /** The protocol before web addresses, which a request without them still uses; its line has no last field. */
    private static final int PROTOCOL_WITHOUT_URLS = 2;

    /**
     * Rejects nulls, a relative path, a web address that is not one, more than {@link #MAX_URLS} of them, an intent its
     * paths and web addresses contradict and a flag that is not a start-up flag.
     */
    public LaunchRequest {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(codeLocation, "codeLocation");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        paths = List.copyOf(paths);
        urls = List.copyOf(urls);
        ignoredFlags = List.copyOf(ignoredFlags);
        if (!workingDirectory.isAbsolute()) throw new IllegalArgumentException("The working directory is not absolute: " + workingDirectory);
        for (Path path : paths) if (!path.isAbsolute()) throw new IllegalArgumentException("A path is not absolute: " + path);
        for (URI url : urls) if (!Browser.isWebAddress(url)) throw new IllegalArgumentException("Not a web address: " + url);
        if (urls.size() > MAX_URLS) throw new IllegalArgumentException(urls.size() + " web addresses");
        if ((intent == Intent.OPEN_PATHS) == (paths.isEmpty() && urls.isEmpty()))
            throw new IllegalArgumentException(intent + " with " + paths.size() + " paths and " + urls.size() + " web addresses");
        for (String flag : ignoredFlags)
            if (!STARTUP_FLAGS.contains(flag)) throw new IllegalArgumentException("Not a start-up flag: " + flag);
    }

    /**
     * A launch's request: OPEN_PATHS when it names paths or web addresses, RAISE otherwise. The token is filled in when
     * it is sent.
     *
     * @param codeLocation the launch's jar or classes directory, or null when unknown
     * @param codeLocationModified its modification time
     * @param workingDirectory the directory the launch ran in, absolute
     * @param paths the paths to open, absolute
     * @param urls the web addresses to open, at most {@link #MAX_URLS}
     * @param ignoredFlags the start-up flags the launch could not apply
     * @return the request
     */
    public static LaunchRequest of(Path codeLocation, long codeLocationModified, Path workingDirectory, List<Path> paths,
                                   List<URI> urls, List<String> ignoredFlags) {
        return new LaunchRequest("", codeLocation == null ? Path.of("") : codeLocation, codeLocationModified,
            paths.isEmpty() && urls.isEmpty() ? Intent.RAISE : Intent.OPEN_PATHS, workingDirectory, paths, urls, ignoredFlags);
    }

    /** What the launch asks. */
    public enum Intent {
        /** Raise or restore Jasper's window. */
        RAISE,
        /** Open the paths, each file in the editor and each directory as a terminal tab, and the web addresses. */
        OPEN_PATHS
    }

    /** This request signed with {@code value}. */
    LaunchRequest withToken(String value) {
        return new LaunchRequest(value, codeLocation, codeLocationModified, intent, workingDirectory, paths, urls, ignoredFlags);
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
     * One newline-terminated line of tab-separated fields: protocol 3's ten, the web addresses last, or protocol 2's
     * nine when there are none. Text is base64, so a tab or newline in a path survives; each list is its items' base64,
     * comma-separated.
     */
    String encode() {
        String line = String.join("\t", MAGIC, Integer.toString(urls.isEmpty() ? PROTOCOL_WITHOUT_URLS : PROTOCOL), token,
            text(codeLocation.toString()), Long.toString(codeLocationModified), intent.name().toLowerCase(Locale.ROOT),
            text(workingDirectory.toString()), list(paths.stream().map(Path::toString)), list(ignoredFlags.stream()));
        return (urls.isEmpty() ? line : line + "\t" + list(urls.stream().map(URI::toString))) + "\n";
    }

    /** Null for anything this build cannot act on, including another protocol than 3 and 2. Never throws. */
    static LaunchRequest decode(String line) {
        String body = line.endsWith("\n") ? line.substring(0, line.length() - 1) : line;
        String[] parts = body.split("\t", -1);
        if (parts.length < 2 || !MAGIC.equals(parts[0])) return null;
        try {
            int protocol = Integer.parseInt(parts[1]);
            boolean withUrls = protocol == PROTOCOL;
            if (!withUrls && protocol != PROTOCOL_WITHOUT_URLS) return null;
            if (parts.length != (withUrls ? FIELDS : FIELDS - 1)) return null;
            Intent intent = switch (parts[5]) {
                case "raise" -> Intent.RAISE;
                case "open_paths" -> Intent.OPEN_PATHS;
                default -> null;
            };
            if (intent == null) return null;
            return new LaunchRequest(parts[2], Path.of(untext(parts[3])), Long.parseLong(parts[4]), intent,
                Path.of(untext(parts[6])), items(parts[7]).map(Path::of).toList(),
                withUrls ? items(parts[9]).map(URI::create).toList() : List.of(), items(parts[8]).toList());
        } catch (IllegalArgumentException malformed) {
            // A bad number, bad base64, an unusable or relative path, a bad or non-web address, too many addresses, a
            // contradicted intent, an unknown flag.
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
