package dev.jasper.remote.hosts;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;

/** OpenSSH-style SHA256 fingerprints of public-key blobs; the same form the Vault shows for its keys. */
public final class Fingerprints {
    private Fingerprints() { }

    public static String sha256(byte[] blob) {
        try { return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(blob)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    /** The fingerprint of a {@code type base64 [comment]} line; empty when the line is not one. */
    public static Optional<String> ofPublicKeyLine(String line) {
        String[] parts = line.strip().split("\\s+");
        if (parts.length < 2 || !parts[0].startsWith("ssh-") && !parts[0].startsWith("ecdsa-") && !parts[0].startsWith("sk-")) return Optional.empty();
        try { return Optional.of(sha256(Base64.getDecoder().decode(parts[1]))); }
        catch (IllegalArgumentException bad) { return Optional.empty(); }
    }
}
