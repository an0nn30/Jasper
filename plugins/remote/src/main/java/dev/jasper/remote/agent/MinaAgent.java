package dev.jasper.remote.agent;

import java.io.IOException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.sshd.agent.SshAgent;
import org.apache.sshd.agent.SshAgentKeyConstraint;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.session.SessionContext;

/** MINA's view of the agent: identities to offer and signing that goes back to the agent. Read-only. */
public final class MinaAgent implements SshAgent {
    private final AgentClient client;
    private volatile boolean open = true;

    public MinaAgent(AgentClient client) { this.client = client; }

    @Override public Iterable<? extends Map.Entry<PublicKey, String>> getIdentities() throws IOException {
        var out = new ArrayList<Map.Entry<PublicKey, String>>();
        for (AgentClient.Identity identity : client.identities()) out.add(Map.entry(identity.key(), identity.comment()));
        return out;
    }

    @Override public Map.Entry<String, byte[]> sign(SessionContext session, PublicKey key, String algorithm, byte[] data) throws IOException {
        for (AgentClient.Identity identity : client.identities()) {
            if (KeyUtils.findMatchingKey(key, List.of(identity.key())) == null) continue;
            int flags = switch (algorithm) { case "rsa-sha2-256" -> AgentClient.FLAG_RSA_SHA2_256; case "rsa-sha2-512" -> AgentClient.FLAG_RSA_SHA2_512; default -> 0; };
            return client.sign(identity, data, flags);
        }
        throw new IOException("The key is not in the agent");
    }

    @Override public void addIdentity(KeyPair key, String comment, SshAgentKeyConstraint... constraints) { throw new UnsupportedOperationException("Jasper does not add keys to the agent"); }
    @Override public void removeIdentity(PublicKey key) { throw new UnsupportedOperationException("Jasper does not remove keys from the agent"); }
    @Override public void removeAllIdentities() { throw new UnsupportedOperationException("Jasper does not remove keys from the agent"); }
    @Override public boolean isOpen() { return open; }
    @Override public void close() { open = false; client.close(); }
}
