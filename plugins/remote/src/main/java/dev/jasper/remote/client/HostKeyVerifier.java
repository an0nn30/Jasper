package dev.jasper.remote.client;

import dev.jasper.remote.trust.CorruptTrustFileException;
import dev.jasper.remote.trust.KnownHosts;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.PublicKey;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.AttributeRepository;
import org.apache.sshd.common.config.keys.KeyUtils;

/**
 * One connection's host-key verifier. Answers MINA's question from the trust files, asking the user (on the UI thread, while MINA's
 * I/O thread waits) only for an unknown host. The real host and port travel in the {@link #TARGET}
 * attribute, because a ProxyJump hop connects to a loopback forward.
 */
public final class HostKeyVerifier implements ServerKeyVerifier {
    public record Target(String host, int port) { }
    public record Question(String host, int port, String keyType, String fingerprint) { }
    public enum Decision { CANCEL, ONCE, TRUST }

    public static final AttributeRepository.AttributeKey<Target> TARGET = new AttributeRepository.AttributeKey<>();
    private volatile String rejection;

    /** The connection's rejection, retained after MINA clears the closed session's attributes. */
    String rejection() { return rejection; }

    private final KnownHosts trust;
    private final Function<Question, CompletableFuture<Decision>> prompt;
    private final Supplier<Duration> timeout;

    public HostKeyVerifier(KnownHosts trust, Function<Question, CompletableFuture<Decision>> prompt, Supplier<Duration> timeout) {
        this.trust = trust; this.prompt = prompt; this.timeout = timeout;
    }

    @Override public boolean verifyServerKey(ClientSession session, SocketAddress remote, PublicKey key) {
        Target target = session.getConnectionContext() == null ? null : session.getConnectionContext().getAttribute(TARGET);
        if (target == null && remote instanceof InetSocketAddress inet) target = new Target(inet.getHostString(), inet.getPort());
        if (target == null) { rejection = "Host key rejected: unknown target"; return false; }
        try {
            KnownHosts.Verdict verdict = trust.verify(target.host(), target.port(), key);
            return switch (verdict) {
                case KnownHosts.Verdict.Match match -> true;
                case KnownHosts.Verdict.Mismatch mismatch -> {
                    rejection = "Host key rejected: fingerprint changed (known " + mismatch.knownFingerprint() + ", offered " + KnownHosts.fingerprint(key) + ")";
                    yield false;
                }
                case KnownHosts.Verdict.Unknown unknown -> ask(target, key);
            };
        } catch (CorruptTrustFileException corrupt) {
            rejection = corrupt.getMessage();
            return false;
        }
    }

    private boolean ask(Target target, PublicKey key) {
        Decision decision;
        try {
            decision = prompt.apply(new Question(target.host(), target.port(), KeyUtils.getKeyType(key), KnownHosts.fingerprint(key))).get(timeout.get().toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception cancelled) {
            rejection = "Host key not trusted";
            return false;
        }
        switch (decision) {
            case CANCEL -> { rejection = "Host key not trusted"; return false; }
            case ONCE -> { return true; }
            case TRUST -> {
                try { trust.trust(target.host(), target.port(), key); return true; }
                catch (IOException failure) { rejection = "Could not save the host key: " + failure.getMessage(); return false; }
            }
        }
        return false;
    }
}
