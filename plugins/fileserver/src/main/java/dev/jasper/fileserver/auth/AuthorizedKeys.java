package dev.jasper.fileserver.auth;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

/**
 * OpenSSH public key lines ({@code ssh-ed25519 AAAA… comment}) as {@code authorized_keys} holds them. They are
 * checked without an SSH library: a known key type, base64 key data whose own type matches, and an optional comment.
 * Lines with options ({@code command=}, {@code from=}, {@code restrict}, …) are refused, because File Server could
 * not honour them. The SFTP engine turns stored lines into keys.
 */
public final class AuthorizedKeys {
    public static final Set<String> TYPES = Set.of("ssh-ed25519", "ssh-rsa", "ecdsa-sha2-nistp256", "ecdsa-sha2-nistp384",
        "ecdsa-sha2-nistp521", "sk-ssh-ed25519@openssh.com", "sk-ecdsa-sha2-nistp256@openssh.com");

    /** One key: its type, its base64 data and its comment (empty when none). */
    public record Key(String type, String data, String comment) {
        /** The line to store. */
        public String line() { return comment.isEmpty() ? type + " " + data : type + " " + data + " " + comment; }

        /** The OpenSSH SHA-256 fingerprint, as {@code ssh-keygen -l} prints it. */
        public String fingerprint() {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(data));
                return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest);
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException(impossible);
            }
        }

        /** The type and fingerprint, then the comment, for lists. */
        public String summary() { return type + " " + fingerprint() + (comment.isEmpty() ? "" : "  " + comment); }
    }

    private AuthorizedKeys() { }

    public static Key parse(String line) {
        String text = line == null ? "" : line.strip();
        if (text.chars().anyMatch(c -> c != '\t' && Character.isISOControl(c))) throw new IllegalArgumentException("The line contains control characters");
        String[] parts = text.split("\\s+", 3);
        if (!TYPES.contains(parts[0])) {
            for (String part : text.split("\\s+"))
                if (TYPES.contains(part)) throw new IllegalArgumentException("Key options (such as command= or from=) are not supported: remove them from the line");
            throw new IllegalArgumentException("Not an OpenSSH public key (for example ssh-ed25519 AAAA…)");
        }
        if (parts.length < 2) throw new IllegalArgumentException("The key data is missing");
        byte[] blob;
        try { blob = Base64.getDecoder().decode(parts[1]); }
        catch (IllegalArgumentException bad) { throw new IllegalArgumentException("The key data is not valid base64"); }
        if (!parts[0].equals(embeddedType(blob))) throw new IllegalArgumentException("The key data does not match its type " + parts[0]);
        return new Key(parts[0], parts[1], parts.length == 3 ? parts[2].strip() : "");
    }

    /** Every key in {@code authorized_keys} text, from a file or the clipboard; blank and {@code #} lines are skipped. */
    public static List<Key> parseAll(String text) {
        var keys = new ArrayList<Key>();
        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            try { keys.add(parse(line)); }
            catch (IllegalArgumentException bad) { throw new IllegalArgumentException("Line " + (i + 1) + ": " + bad.getMessage()); }
        }
        if (keys.isEmpty()) throw new IllegalArgumentException("No public keys found");
        return List.copyOf(keys);
    }

    /** The type string an SSH key blob starts with: a 4-byte length, then the name. */
    private static String embeddedType(byte[] blob) {
        if (blob.length < 4) return "";
        int length = ByteBuffer.wrap(blob, 0, 4).getInt();
        if (length < 0 || length > blob.length - 4) return "";
        return new String(blob, 4, length, StandardCharsets.US_ASCII);
    }
}
