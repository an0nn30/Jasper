package dev.jasper.fileserver.engine.sftp;

import dev.jasper.fileserver.auth.ResolvedLogin;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.EngineFailure;
import dev.jasper.fileserver.engine.LoginThrottle;
import dev.jasper.fileserver.engine.ServerAddresses;
import dev.jasper.fileserver.engine.ServerEngine;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.share.ShareRoot;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.apache.sshd.common.file.nativefs.NativeFileSystemFactory;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.UserAuthFactory;
import org.apache.sshd.server.auth.password.UserAuthPasswordFactory;
import org.apache.sshd.server.auth.pubkey.RejectAllPublickeyAuthenticator;
import org.apache.sshd.server.auth.pubkey.UserAuthPublicKeyFactory;
import org.apache.sshd.server.forward.RejectAllForwardingFilter;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.apache.sshd.sftp.server.UnsupportedAttributePolicy;

/**
 * An SFTP server for one share on Apache MINA sshd. It serves only the SFTP subsystem, with no shell, no exec and no
 * forwarding. Logins are by password, by authorized key, or both; keyboard-interactive is off. Every file operation
 * goes through the share ({@link ShareAccessor}). An {@code SshServer} cannot be restarted, so each start builds a
 * new one.
 */
public final class SftpEngine implements ServerEngine {
    static final int MAX_AUTH_REQUESTS = 10;
    private final ServerDefinition definition;
    private final ShareRoot share;
    private final ResolvedLogin login;
    private final ServerEvents events;
    private final LoginThrottle throttle;
    private final Supplier<List<ServerAddresses.Nic>> nics;
    private final HostKey hostKey;
    private SshServer server;
    private int port;

    public SftpEngine(ServerDefinition definition, ShareRoot share, ResolvedLogin login, ServerEvents events, LoginThrottle throttle,
                      Supplier<List<ServerAddresses.Nic>> nics, HostKey hostKey) {
        this.definition = definition; this.share = share; this.login = login; this.events = events; this.throttle = throttle;
        this.nics = nics; this.hostKey = hostKey;
    }

    @Override public synchronized void start() throws EngineFailure {
        if (server != null) return;
        InetAddress address = ServerAddresses.bindAddress(definition.bind(), nics.get());
        var keys = new KeyCheck(definition.authorizedKeys());
        if (login.isAnonymous() && keys.isEmpty()) throw new EngineFailure("SFTP needs a password or authorized keys");
        var hostKeys = hostKey.provider();
        var logins = new SftpLogins(login, keys, throttle, events);
        var sftp = new SftpSubsystemFactory.Builder().withFileSystemAccessor(new ShareAccessor(share))
            .withUnsupportedAttributePolicy(UnsupportedAttributePolicy.ThrowException) // a refused stat fails instead of sshd reading it itself
            .build();
        sftp.addSftpEventListener(new SftpActivity());

        SshServer created = SshServer.setUpDefaultServer();
        created.setHost(address.isAnyLocalAddress() ? null : address.getHostAddress());
        created.setPort(definition.port());
        created.setKeyPairProvider(hostKeys);
        var methods = new ArrayList<UserAuthFactory>();
        if (!login.isAnonymous()) methods.add(UserAuthPasswordFactory.INSTANCE);
        if (!keys.isEmpty()) methods.add(UserAuthPublicKeyFactory.INSTANCE);
        created.setUserAuthFactories(methods); // an explicit list: no keyboard-interactive
        created.setPasswordAuthenticator(logins);
        created.setPublickeyAuthenticator(keys.isEmpty() ? RejectAllPublickeyAuthenticator.INSTANCE : logins); // never the OS user's authorized_keys
        created.setKeyboardInteractiveAuthenticator(null);
        created.setShellFactory(null);
        created.setCommandFactory(null);
        created.setForwardingFilter(RejectAllForwardingFilter.INSTANCE);
        created.setFileSystemFactory(NativeFileSystemFactory.INSTANCE); // the accessor confines every path to the share
        created.setSubsystemFactories(List.of(sftp));
        created.addSessionListener(logins);
        CoreModuleProperties.MAX_AUTH_REQUESTS.set(created, MAX_AUTH_REQUESTS);
        try {
            created.start();
        } catch (IOException failure) {
            closeQuietly(created);
            throw EngineFailure.bind(failure, definition.port(), ServerAddresses.describe(address), definition.protocol());
        } catch (RuntimeException failure) {
            closeQuietly(created);
            throw new EngineFailure("Could not start the SFTP server: " + failure.getMessage(), failure);
        }
        server = created;
        port = created.getPort();
    }

    @Override public synchronized void stop() {
        if (server == null) return;
        try { server.stop(true); }
        catch (IOException ignored) { /* the server is going away either way */ }
        server = null;
    }

    @Override public synchronized List<URI> addresses() {
        if (server == null) return List.of();
        return ServerAddresses.urls(definition.protocol(), false, ServerAddresses.reachable(definition.bind(), nics.get()), port, login.user());
    }

    /** The bound port; 0 before the first start. */
    public synchronized int port() { return port; }

    /** A server whose start failed stays open: {@code stop()} would do nothing, so close it. */
    private static void closeQuietly(SshServer server) {
        try { server.close(true).await(Duration.ofSeconds(5)); }
        catch (IOException ignored) { /* nothing more to release */ }
    }
}
