package dev.jasper.app.residency;

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
public record LaunchRequest(String token, Path codeSource, long codeSourceModified, Kind kind) {
    private static final String MAGIC = "jasper";
    private static final int PROTOCOL = 1;
    private static final int FIELDS = 5;

    public LaunchRequest {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(codeSource, "codeSource");
        Objects.requireNonNull(kind, "kind");
    }

    /** What is asked of the resident process. */
    public enum Kind {
        /** Reveal a window, as a cold launch would open one. */
        OPEN,
        /** Quit through the normal quit path, so a recovery launch can replace the plugin set. */
        RETIRE
    }

    /** An ordinary launch. */
    public LaunchRequest(String token, Path codeSource, long codeSourceModified) {
        this(token, codeSource, codeSourceModified, Kind.OPEN);
    }

    /**
     * One newline-terminated line. The path is base64 so a tab or newline in it survives. An open request
     * is exactly the five fields it always was: an older resident must still understand it well enough
     * to answer {@code stale} and release the endpoint. Only a retire carries a sixth field.
     */
    String encode() {
        String line = String.join("\t", MAGIC, Integer.toString(PROTOCOL), token,
            encode(codeSource), Long.toString(codeSourceModified));
        return line + (kind == Kind.RETIRE ? "\tretire" : "") + "\n";
    }

    /** Null for anything this build cannot act on, including a future protocol. Never throws. */
    static LaunchRequest decode(String line) {
        String[] parts = line.strip().split("\t", -1);
        if ((parts.length != FIELDS && parts.length != FIELDS + 1) || !MAGIC.equals(parts[0])) return null;
        if (parts.length == FIELDS + 1 && !parts[FIELDS].equals("retire")) return null;
        try {
            if (Integer.parseInt(parts[1]) != PROTOCOL) return null;
            return new LaunchRequest(parts[2], decodePath(parts[3]), Long.parseLong(parts[4]),
                parts.length == FIELDS ? Kind.OPEN : Kind.RETIRE);
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
    public enum Response {
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
