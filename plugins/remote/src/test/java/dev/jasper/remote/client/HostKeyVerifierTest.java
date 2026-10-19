package dev.jasper.remote.client;

import dev.jasper.remote.trust.KnownHosts;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.sshd.client.session.ClientSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class HostKeyVerifierTest {
    @Test void changedKeyReasonSurvivesSessionCleanupAndIsConnectionScoped(@TempDir Path dir) throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        var known = generator.generateKeyPair().getPublic();
        var offered = generator.generateKeyPair().getPublic();
        var trust = new KnownHosts(dir.resolve("known_hosts"), Optional.empty());
        trust.trust("127.0.0.1", 2222, known);
        var verifier = new HostKeyVerifier(trust, question -> { throw new AssertionError("A changed key must not prompt"); },
            () -> Duration.ofSeconds(1));
        var session = session();
        assertThat(verifier.verifyServerKey(session, new InetSocketAddress("127.0.0.1", 2222), offered)).isFalse();
        // MINA's AbstractSession closes the transport and clears its attributes before our waiting worker may resume.
        session.clearAttributes();
        assertThat(verifier.rejection()).isEqualTo("Host key rejected: fingerprint changed (known "
            + KnownHosts.fingerprint(known) + ", offered " + KnownHosts.fingerprint(offered) + ")");

        var other = new HostKeyVerifier(trust, question -> CompletableFuture.completedFuture(HostKeyVerifier.Decision.ONCE),
            () -> Duration.ofSeconds(1));
        assertThat(other.verifyServerKey(session(), new InetSocketAddress("127.0.0.1", 2222), known)).isTrue();
        assertThat(other.rejection()).as("another connection does not inherit the rejection").isNull();
    }

    @Test void cancelledTrustReasonSurvivesSessionCleanup(@TempDir Path dir) throws Exception {
        var verifier = new HostKeyVerifier(new KnownHosts(dir.resolve("known_hosts"), Optional.empty()),
            question -> CompletableFuture.completedFuture(HostKeyVerifier.Decision.CANCEL), () -> Duration.ofSeconds(1));
        var session = session();
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        assertThat(verifier.verifyServerKey(session, new InetSocketAddress("127.0.0.1", 2222), generator.generateKeyPair().getPublic())).isFalse();
        session.clearAttributes();
        assertThat(verifier.rejection()).isEqualTo("Host key not trusted");
    }

    private static ClientSession session() {
        Map<Object, Object> attributes = new HashMap<>();
        return (ClientSession) Proxy.newProxyInstance(ClientSession.class.getClassLoader(), new Class<?>[] {ClientSession.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getConnectionContext" -> null;
                case "getAttribute" -> attributes.get(args[0]);
                case "setAttribute" -> attributes.put(args[0], args[1]);
                case "clearAttributes" -> { attributes.clear(); yield null; }
                default -> throw new AssertionError("Unexpected session call: " + method.getName());
            });
    }
}
