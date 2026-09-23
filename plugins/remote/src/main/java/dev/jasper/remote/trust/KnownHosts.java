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
    private final java.util.function.Supplier<Optional<Path>> user;

    public KnownHosts(Path own, Optional<Path> user) { this(own, () -> user); }
    public KnownHosts(Path own, java.util.function.Supplier<Optional<Path>> user) { this.own = own; this.user = user; }

    public static String fingerprint(PublicKey key) { return KeyUtils.getFingerPrint(key); }
    public static String hostPattern(String host, int port) { return KnownHostHashValue.createHostPattern(host, port); }

    public Verdict verify(String host, int port, PublicKey key) {
        Verdict ownVerdict = verdict(entries(own, true, host, port), key);
        Verdict userVerdict = user.get().map(file -> verdict(entries(file, false, host, port), key)).orElseGet(Verdict.Unknown::new);
        if (ownVerdict instanceof Verdict.Mismatch) return ownVerdict;
        if (userVerdict instanceof Verdict.Mismatch) return userVerdict;
        return ownVerdict instanceof Verdict.Match || userVerdict instanceof Verdict.Match ? new Verdict.Match() : new Verdict.Unknown();
    }

    private static Verdict verdict(List<Known> candidates, PublicKey key) {
        boolean matched = false;
        String mismatch = null;
        for (Known known : candidates) {
            boolean same = KeyUtils.findMatchingKey(key, List.of(known.key())) != null;
            if (known.revoked() && same) return new Verdict.Mismatch(fingerprint(known.key()));
            if (known.revoked()) continue;
            if (same) matched = true;
            else if (mismatch == null) mismatch = fingerprint(known.key());
        }
        if (matched) return new Verdict.Match();
        return mismatch == null ? new Verdict.Unknown() : new Verdict.Mismatch(mismatch);
    }

    /** Appends {@code pattern key} to the own file unless it is already there; refuses when the file holds a different key. */
    public synchronized void trust(String host, int port, PublicKey key) throws IOException {
        Verdict current = verify(host, port, key);
        if (current instanceof Verdict.Mismatch mismatch) throw new IOException("known_hosts holds a different key or revoked key for " + hostPattern(host, port) + " (" + mismatch.knownFingerprint() + ")");
        if (current instanceof Verdict.Match) return;
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
        if (Files.notExists(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return List.of();
        if (!Files.isRegularFile(file)) {
            if (strict) throw new CorruptTrustFileException("known_hosts is not a readable regular file");
            return List.of();
        }
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
