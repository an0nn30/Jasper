package dev.jasper.app.commandline;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The command socket's wire format, shared with the native client in {@code packaging/cli}: one request line from the
 * client, then reply lines from Jasper. Lines are UTF-8 and end in a newline; fields are separated by tabs; free text
 * is standard base64 with padding.
 */
public final class CommandProtocol {
    public static final String MAGIC = "jasper-command";
    public static final int VERSION = 1;
    /** A request longer than this, newline included, is not one of ours. */
    public static final int MAX_REQUEST_BYTES = 1 << 20;
    /** Answers a request from another protocol version. */
    public static final String STALE = "stale\n";
    /** Answers a request with the wrong token. */
    public static final String REFUSED = "refused\n";

    private static final Base64.Encoder ENCODER = Base64.getEncoder();
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    private CommandProtocol() { }

    /**
     * One call as the client sends it.
     *
     * @param token the token beside the socket
     * @param workingDirectory the client's working directory
     * @param pane the Jasper pane the client ran in, from {@code JASPER_PANE}
     * @param arguments everything after {@code jasper}; none opens a window
     */
    public record Request(String token, Path workingDirectory, Optional<UUID> pane, List<String> arguments) {
        public Request {
            Objects.requireNonNull(token, "token");
            Objects.requireNonNull(workingDirectory, "workingDirectory");
            Objects.requireNonNull(pane, "pane");
            arguments = List.copyOf(arguments);
        }

        /** The request line, newline included. */
        public String encode() {
            var line = new StringBuilder(MAGIC).append('\t').append(VERSION).append('\t').append(token)
                .append('\t').append(text(workingDirectory.toString()))
                .append('\t').append(pane.map(UUID::toString).orElse(""));
            for (String argument : arguments) line.append('\t').append(text(argument));
            return line.append('\n').toString();
        }
    }

    /** The protocol version a line claims, or -1 when it is not a request at all. */
    public static int version(String line) {
        String[] parts = line.split("\t", 3);
        if (parts.length < 2 || !MAGIC.equals(parts[0])) return -1;
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException malformed) {
            return -1;
        }
    }

    /**
     * A request of this version, without its newline, or null when the line is malformed. A pane that is not a UUID
     * counts as none, so a stale or garbled {@code JASPER_PANE} still runs the command.
     */
    public static Request decode(String line) {
        String[] parts = line.split("\t", -1);
        if (parts.length < 5 || version(line) != VERSION || parts[2].isEmpty()) return null;
        try {
            Path directory = Path.of(decodeText(parts[3]));
            if (!directory.isAbsolute()) return null;
            List<String> arguments = new ArrayList<>();
            for (int i = 5; i < parts.length; i++) arguments.add(decodeText(parts[i]));
            return new Request(parts[2], directory, pane(parts[4]), arguments);
        } catch (IllegalArgumentException malformed) {
            // Bad base64 and an unusable path (InvalidPathException) alike.
            return null;
        }
    }

    /** Text for the caller's standard output. */
    public static String out(String text) { return "out\t" + text(text) + "\n"; }

    /** Text for the caller's standard error. */
    public static String err(String text) { return "err\t" + text(text) + "\n"; }

    /** The call's exit code; the client exits with it. */
    public static String exit(int code) { return "exit\t" + code + "\n"; }

    static String text(String value) { return ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8)); }

    static String decodeText(String field) { return new String(DECODER.decode(field), StandardCharsets.UTF_8); }

    private static Optional<UUID> pane(String field) {
        if (field.length() != 36) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(field));
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }
}
