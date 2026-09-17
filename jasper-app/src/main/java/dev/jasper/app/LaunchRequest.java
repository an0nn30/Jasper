package dev.jasper.app;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Objects;

/**
 * One line of the handoff protocol: what a launching process asks a resident one to do.
 *
 * <p>There is deliberately no working directory. A resident process opens a window exactly where a
 * cold launch would, so that {@code jasper} from a shell does not behave differently depending on
 * whether a daemon happened to be running.
 */
record LaunchRequest(String token, Path codeSource, long codeSourceModified) {
    private static final String MAGIC = "jasper";
    private static final int PROTOCOL = 1;
    private static final int FIELDS = 5;

    LaunchRequest {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(codeSource, "codeSource");
    }

    /** One newline-terminated line. The path is base64 so a tab or newline in it survives. */
    String encode() {
        return String.join("\t", MAGIC, Integer.toString(PROTOCOL), token,
            encode(codeSource), Long.toString(codeSourceModified)) + "\n";
    }

    /** Null for anything this build cannot act on, including a future protocol. Never throws. */
    static LaunchRequest decode(String line) {
        String[] parts = line.strip().split("\t", -1);
        if (parts.length != FIELDS || !MAGIC.equals(parts[0])) return null;
        try {
            if (Integer.parseInt(parts[1]) != PROTOCOL) return null;
            return new LaunchRequest(parts[2], decodePath(parts[3]), Long.parseLong(parts[4]));
        } catch (IllegalArgumentException malformed) {
            // Covers NumberFormatException, a bad base64 body and an unusable path alike.
            return null;
        }
    }

    private static String encode(Path path) {
        return Base64.getEncoder().encodeToString(path.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static Path decodePath(String encoded) {
        return Path.of(new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8));
    }

    /** What a resident process says back. Anything unrecognised is a protocol error, never an accept. */
    enum Response {
        OK, TOKEN, STALE, PROTOCOL;

        String line() {
            return this == OK ? "ok\n" : "refused\t" + name().toLowerCase(java.util.Locale.ROOT) + "\n";
        }

        static Response of(String line) {
            String text = line.strip();
            if (text.equals("ok")) return OK;
            if (text.equals("refused\tstale")) return STALE;
            if (text.equals("refused\ttoken")) return TOKEN;
            return PROTOCOL;
        }
    }
}
