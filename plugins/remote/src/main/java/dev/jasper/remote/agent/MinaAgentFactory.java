package dev.jasper.remote.agent;

import java.util.List;
import org.apache.sshd.agent.SshAgent;
import org.apache.sshd.agent.SshAgentFactory;
import org.apache.sshd.agent.SshAgentServer;
import org.apache.sshd.common.FactoryManager;
import org.apache.sshd.common.channel.ChannelFactory;
import org.apache.sshd.common.session.ConnectionService;
import org.apache.sshd.common.session.Session;

/** Hands MINA a {@link MinaAgent} per session; no agent forwarding, no agent server. */
public final class MinaAgentFactory implements SshAgentFactory {
    private final AgentClient client;

    public MinaAgentFactory(AgentClient client) { this.client = client; }

    @Override public List<ChannelFactory> getChannelForwardingFactories(FactoryManager manager) { return List.of(); }
    @Override public SshAgent createClient(Session session, FactoryManager manager) { return new MinaAgent(client.withTimeout(session == null ? java.time.Duration.ofSeconds(30) : org.apache.sshd.core.CoreModuleProperties.AUTH_TIMEOUT.getRequired(session))); }
    @Override public SshAgentServer createServer(ConnectionService service) { throw new UnsupportedOperationException("Jasper does not forward the agent"); }
}
