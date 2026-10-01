package dev.jasper.fileserver.engine.ftp;

import dev.jasper.fileserver.auth.ResolvedLogin;
import dev.jasper.fileserver.definition.PortRange;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.EngineFailure;
import dev.jasper.fileserver.engine.LoginThrottle;
import dev.jasper.fileserver.engine.ServerAddresses;
import dev.jasper.fileserver.engine.ServerEngine;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.engine.tls.TlsMaterial;
import dev.jasper.fileserver.share.ShareRoot;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.ftpserver.ConnectionConfigFactory;
import org.apache.ftpserver.DataConnectionConfigurationFactory;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.Ftplet;
import org.apache.ftpserver.listener.Listener;
import org.apache.ftpserver.listener.ListenerFactory;

/**
 * An FTP server for one share on Apache FtpServer, with explicit FTPS ({@code AUTH TLS}) when the definition uses TLS.
 * Passive data connections use the definition's range, or the default, on the address the control connection
 * arrived on. Active mode is allowed, but only back to the client's own address.
 */
public final class FtpEngine implements ServerEngine {
    static final int IDLE_SECONDS = 300;
    /** FtpServer logs every login and transfer at INFO; the activity log already shows them. Held so the level sticks. */
    private static final Logger FTP_SERVER_LOG = Logger.getLogger("org.apache.ftpserver");
    static { FTP_SERVER_LOG.setLevel(Level.WARNING); }

    private final ServerDefinition definition;
    private final ShareRoot share;
    private final ResolvedLogin login;
    private final ServerEvents events;
    private final LoginThrottle throttle;
    private final Supplier<List<ServerAddresses.Nic>> nics;
    private final PortRange passiveDefault;
    private final TlsMaterial tls;
    private FtpServer server;
    private int port;

    /** {@code tls} may be null when the definition does not use TLS. */
    public FtpEngine(ServerDefinition definition, ShareRoot share, ResolvedLogin login, ServerEvents events, LoginThrottle throttle,
                     Supplier<List<ServerAddresses.Nic>> nics, PortRange passiveDefault, TlsMaterial tls) {
        this.definition = definition; this.share = share; this.login = login; this.events = events; this.throttle = throttle;
        this.nics = nics; this.passiveDefault = passiveDefault; this.tls = tls;
    }

    @Override public synchronized void start() throws EngineFailure {
        if (server != null) return;
        if (definition.tls() && tls == null) throw new IllegalStateException("FTPS needs the TLS material");
        InetAddress address = ServerAddresses.bindAddress(definition.bind(), nics.get());
        TlsConfiguration ssl = definition.tls() ? new TlsConfiguration(tls.context()) : null;

        var data = new DataConnectionConfigurationFactory();
        data.setPassivePorts(definition.passiveRange().orElse(passiveDefault).token());
        data.setActiveIpCheck(true);
        data.setPassiveIpCheck(true);
        if (ssl != null) data.setSslConfiguration(ssl);

        var listeners = new ListenerFactory();
        if (!address.isAnyLocalAddress()) listeners.setServerAddress(address.getHostAddress());
        listeners.setPort(definition.port());
        listeners.setIdleTimeout(IDLE_SECONDS);
        listeners.setDataConnectionConfiguration(data.createDataConnectionConfiguration());
        if (ssl != null) {
            listeners.setSslConfiguration(ssl);
            listeners.setImplicitSsl(false);
        }
        Listener listener = listeners.createListener();

        var connections = new ConnectionConfigFactory();
        connections.setAnonymousLoginEnabled(login.isAnonymous());
        Map<String, Ftplet> ftplets = new LinkedHashMap<>();
        ftplets.put("jasper", new EventFtplet(events, definition.requireTls()));
        var factory = new FtpServerFactory();
        factory.addListener("default", listener);
        factory.setConnectionConfig(connections.createConnectionConfig());
        factory.setUserManager(new ShareUserManager(login, definition.writable(), throttle, events));
        factory.setFileSystem(new ShareFileSystem(share, events));
        factory.setFtplets(ftplets);
        FtpServer starting = factory.createServer();
        try {
            starting.start();
        } catch (FtpException | RuntimeException failure) {
            starting.stop();
            IOException cause = innermostIo(failure);
            if (cause != null)
                throw EngineFailure.bind(cause, definition.port(), ServerAddresses.describe(address), definition.protocol());
            throw new EngineFailure("Could not start the FTP server: " + failure.getMessage(), failure);
        }
        server = starting;
        port = listener.getPort();
    }

    /**
     * The deepest {@link IOException} in the chain: MINA wraps the {@code BindException} ("Address already in use") in an
     * {@code IOException} ("Error while binding"), and FtpServer wraps that in its own exception.
     */
    private static IOException innermostIo(Throwable failure) {
        IOException found = null;
        for (Throwable next = failure.getCause(); next != null && next != failure; next = next.getCause())
            if (next instanceof IOException io) found = io;
        return found;
    }

    @Override public synchronized void stop() {
        if (server == null) return;
        server.stop();
        server = null;
    }

    @Override public synchronized List<URI> addresses() {
        if (server == null) return List.of();
        return ServerAddresses.urls(definition.protocol(), definition.tls(), ServerAddresses.reachable(definition.bind(), nics.get()), port, login.user());
    }

    /** The bound port; 0 before the first start. */
    public synchronized int port() { return port; }
}
