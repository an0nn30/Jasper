package dev.jasper.remote.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class AgentClientTest {
    static KeyPair generate(String algorithm, int size) throws Exception {
        KeyPairGenerator generator = algorithm.equals("Ed25519") ? KeyPairGenerator.getInstance(algorithm, new org.bouncycastle.jce.provider.BouncyCastleProvider()) : KeyPairGenerator.getInstance(algorithm);
        if (size > 0) generator.initialize(size);
        return generator.generateKeyPair();
    }

    @Test void listsIdentitiesAndSignsThroughTheAgent() throws Exception {
        KeyPair rsa = generate("RSA", 2048), ed = generate("Ed25519", 0);
        var agent = new FakeAgent(rsa, ed);
        var client = new AgentClient(agent::handle);
        List<AgentClient.Identity> identities = client.identities();
        assertThat(identities).hasSize(2);
        assertThat(identities.get(0).comment()).isEqualTo("fake ssh-rsa");
        assertThat(KeyUtils.findMatchingKey(rsa.getPublic(), List.of(identities.get(0).key()))).isNotNull();
        byte[] data = "sign me".getBytes();
        Map.Entry<String, byte[]> signed = client.sign(identities.get(0), data, AgentClient.FLAG_RSA_SHA2_256);
        assertThat(signed.getKey()).isEqualTo("rsa-sha2-256");
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(rsa.getPublic());
        verifier.update(data);
        assertThat(verifier.verify(signed.getValue())).isTrue();
        assertThat(agent.flagsSeen).containsExactly(2);
        assertThat(client.sign(identities.get(1), data, 0).getKey()).isEqualTo("ssh-ed25519");
    }

    @Test void aFailureReplyIsAnIOException() throws Exception {
        var client = new AgentClient(request -> new byte[] {5});
        assertThatThrownBy(client::identities).isInstanceOf(IOException.class).hasMessageContaining("agent");
    }

    @Test void minaAdapterMapsAlgorithmsToFlags() throws Exception {
        KeyPair rsa = generate("RSA", 2048);
        var agent = new FakeAgent(rsa);
        var mina = new MinaAgent(new AgentClient(agent::handle));
        var identities = new java.util.ArrayList<Map.Entry<java.security.PublicKey, String>>();
        mina.getIdentities().forEach(identities::add);
        assertThat(identities).hasSize(1);
        assertThat(mina.sign(null, rsa.getPublic(), "rsa-sha2-512", "x".getBytes()).getKey()).isEqualTo("rsa-sha2-512");
        assertThat(mina.sign(null, rsa.getPublic(), "ssh-rsa", "x".getBytes()).getKey()).isEqualTo("ssh-rsa");
        assertThat(agent.flagsSeen).containsExactly(4, 0);
        assertThatThrownBy(() -> mina.sign(null, generate("RSA", 2048).getPublic(), "ssh-rsa", "x".getBytes())).isInstanceOf(IOException.class).hasMessageContaining("not in the agent");
        assertThatThrownBy(() -> mina.addIdentity(rsa, "c")).isInstanceOf(UnsupportedOperationException.class);
        var factory = new MinaAgentFactory(new AgentClient(agent::handle));
        assertThat(factory.getChannelForwardingFactories(null)).isEmpty();
        assertThat(factory.createClient(null, null)).isInstanceOf(MinaAgent.class);
    }

    @Test void talksToAUnixSocketAgent(@TempDir Path dir) throws Exception {
        assumeFalse(System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win"));
        Path socket = Path.of("/tmp", "jasper-agent-" + java.util.UUID.randomUUID());
        try (var agent = new FakeAgent(generate("RSA", 2048))) {
            agent.serveUnixSocket(socket);
            Optional<AgentClient> client = AgentClient.forEnvironment(Map.of("SSH_AUTH_SOCK", socket.toString()), "Mac OS X");
            assertThat(client).isPresent();
            assertThat(client.get().identities()).hasSize(1);
            assertThat(client.get().identities()).as("a fresh connection per request").hasSize(1);
        }
        assertThat(AgentClient.forEnvironment(Map.of(), "Linux")).isEmpty();
        assertThat(AgentClient.forEnvironment(Map.of("SSH_AUTH_SOCK", socket.toString()), "Linux")).isPresent();
        assertThat(AgentClient.forEnvironment(Map.of(), "Windows 11")).as("the OpenSSH pipe is always a candidate on Windows").isPresent();
        assertThatThrownBy(() -> AgentClient.forEnvironment(Map.of("SSH_AUTH_SOCK", dir.resolve("gone").toString()), "Linux").get().identities())
            .isInstanceOf(IOException.class).hasMessageContaining("agent");
    }
    @Test void closingMinaAgentUnblocksASilentSocket() throws Exception {
        assumeFalse(System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win"));
        Path socket = Path.of("/tmp", "jasper-silent-" + java.util.UUID.randomUUID());
        try (var server = java.nio.channels.ServerSocketChannel.open(java.net.StandardProtocolFamily.UNIX)) {
            server.bind(java.net.UnixDomainSocketAddress.of(socket));
            var mina = new MinaAgent(AgentClient.forEnvironment(Map.of("SSH_AUTH_SOCK", socket.toString()), "Linux").orElseThrow());
            var request = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try { return mina.getIdentities(); } catch (IOException failure) { throw new java.util.concurrent.CompletionException(failure); }
            });
            try (var accepted = server.accept()) {
                mina.close();
                assertThatThrownBy(() -> request.get(2, java.util.concurrent.TimeUnit.SECONDS)).isInstanceOf(java.util.concurrent.ExecutionException.class);
            }
        } finally { Files.deleteIfExists(socket); }
    }

}
