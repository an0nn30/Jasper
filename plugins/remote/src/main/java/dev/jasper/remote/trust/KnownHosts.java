package dev.jasper.remote.trust;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.sshd.client.config.hosts.KnownHostEntry;
import org.apache.sshd.client.config.hosts.KnownHostHashValue;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;

/**
 * OpenSSH known_hosts files: Jasper's own (strict: a line that fails to parse makes the file corrupt)
 * and optionally the user's (lenient: bad lines are skipped, nothing is ever written there). Hashed
 * entries match; {@code @revoked} entries mismatch; {@code @cert-authority} entries are ignored.
 */
public final class KnownHosts {
    public sealed interface Verdict {
        record Match() implements Verdict { }
        record Mismatch(String knownFingerprint) implements Verdict { }
        record Unknown() implements Verdict { }
    }

    private record Known(String pattern, PublicKey key, boolean revoked) { }

    private final Path own;
    private final Optional<Path> user;

    public KnownHosts(Path own, Optional<Path> user) { this.own = own; this.user = user; }

    public static String fingerprint(PublicKey key) { return KeyUtils.getFingerPrint(key); }
    public static String hostPattern(String host, int port) { return KnownHostHashValue.createHostPattern(host, port); }

    public Verdict verify(String host, int port, PublicKey key) {
        List<Known> candidates = new ArrayList<>(entries(own, true, host, port));
        user.ifPresent(file -> candidates.addAll(entries(file, false, host, port)));
        boolean matched = false;
        String mismatch = null;
        for (Known known : candidates) {
            boolean same = KeyUtils.findMatchingKey(key, List.of(known.key())) != null;
            if (known.revoked() && same) return new Verdict.Mismatch(fingerprint(known.key()));
            if (known.revoked()) continue;
            if (same) matched = true; else if (mismatch == null) mismatch = fingerprint(known.key());
        }
        if (mismatch != null) return new Verdict.Mismatch(mismatch);
        return matched ? new Verdict.Match() : new Verdict.Unknown();
    }

    /** Appends {@code pattern key} to the own file unless it is already there; refuses when the file holds a different key. */
    public synchronized void trust(String host, int port, PublicKey key) throws IOException {
        for (Known known : entries(own, true, host, port)) {
            if (KeyUtils.findMatchingKey(key, List.of(known.key())) != null) { if (!known.revoked()) return; }
            else throw new IOException("known_hosts already holds a different key for " + hostPattern(host, port) + " (" + fingerprint(known.key()) + ")");
        }
        String line = hostPattern(host, port) + " " + PublicKeyEntry.appendPublicKeyEntry(new StringBuilder(), key) + "\n";
        String existing = Files.isRegularFile(own) ? Files.readString(own, StandardCharsets.UTF_8) : "";
        if (!existing.isEmpty() && !existing.endsWith("\n")) existing += "\n";
        Files.createDirectories(own.toAbsolutePath().getParent());
        Path temp = own.resolveSibling(own.getFileName() + ".tmp");
        Files.writeString(temp, existing + line, StandardCharsets.UTF_8);
        try { Files.move(temp, own, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException unsupported) { Files.move(temp, own, StandardCopyOption.REPLACE_EXISTING); }
    }

    private static List<Known> entries(Path file, boolean strict, String host, int port) {
        if (!Files.isRegularFile(file)) return List.of();
        List<String> lines;
        try { lines = Files.readAllLines(file, StandardCharsets.UTF_8); }
        catch (IOException unreadable) { if (strict) throw new CorruptTrustFileException("known_hosts is unreadable: " + unreadable.getMessage()); return List.of(); }
        var out = new ArrayList<Known>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            try {
                KnownHostEntry entry = KnownHostEntry.parseKnownHostEntry(line);
                if (entry == null) continue;
                String marker = entry.getMarker();
                if ("cert-authority".equals(marker)) continue;
                PublicKey key = entry.getKeyEntry().resolvePublicKey(null, PublicKeyEntryResolver.IGNORING);
                if (key == null) { if (strict) throw new IOException("Unsupported or invalid host key"); continue; }
                if (!entry.isHostMatch(host, port)) continue;
                out.add(new Known(line, key, "revoked".equals(marker)));
            } catch (RuntimeException | java.security.GeneralSecurityException | IOException bad) {
                if (strict) throw new CorruptTrustFileException("known_hosts line " + (i + 1) + " is not a known_hosts entry");
            }
        }
        return out;
    }
}
