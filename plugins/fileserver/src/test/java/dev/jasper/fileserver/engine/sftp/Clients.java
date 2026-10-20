package dev.jasper.fileserver.engine.sftp;

import java.io.IOException;
import java.security.KeyPair;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.config.hosts.HostConfigEntryResolver;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.keyprovider.KeyIdentityProvider;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;

/** SSH and SFTP clients for an engine on 127.0.0.1; they accept any host key and offer only what a test gives them. */
final class Clients implements AutoCloseable {
    final SshClient client = SshClient.setUpDefaultClient();

    Clients() {
        SshSecurity.ensure();
        client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
        client.setKeyIdentityProvider(KeyIdentityProvider.EMPTY_KEYS_PROVIDER);
        client.setHostConfigEntryResolver(HostConfigEntryResolver.EMPTY);
        CoreModuleProperties.REQUEST_EXEC_REPLY.set(client, true);   // so a refused exec or shell shows as a closed channel
        CoreModuleProperties.REQUEST_SHELL_REPLY.set(client, true);
        client.start();
    }

    static KeyPair newKey() throws Exception { return SecurityUtils.getKeyPairGenerator("Ed25519").generateKeyPair(); }

    ClientSession password(int port, String user, String password) throws IOException {
        ClientSession session = client.connect(user, "127.0.0.1", port).verify(Duration.ofSeconds(10)).getSession();
        session.addPasswordIdentity(password);
        session.auth().verify(Duration.ofSeconds(10));
        return session;
    }

    ClientSession key(int port, String user, KeyPair key) throws IOException {
        ClientSession session = client.connect(user, "127.0.0.1", port).verify(Duration.ofSeconds(10)).getSession();
        session.addPublicKeyIdentity(key);
        session.auth().verify(Duration.ofSeconds(10));
        return session;
    }

    static SftpClient sftp(ClientSession session) throws IOException { return SftpClientFactory.instance().createSftpClient(session); }

    /** A folder's names without "." and "..", sorted. */
    static List<String> names(SftpClient sftp, String path) throws IOException {
        var names = new ArrayList<String>();
        for (SftpClient.DirEntry entry : sftp.readDir(path))
            if (!entry.getFilename().equals(".") && !entry.getFilename().equals("..")) names.add(entry.getFilename());
        names.sort(null);
        return names;
    }

    @Override public void close() { client.stop(); }
}
