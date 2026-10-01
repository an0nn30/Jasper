package dev.jasper.fileserver.engine.http;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import dev.jasper.fileserver.auth.ResolvedLogin;
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
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import javax.net.ssl.SSLContext;

/**
 * An HTTP or HTTPS server for one share on the JDK's HTTP server, with at most 16 requests handled at once. The
 * server is set up unbound and binds last, so a failed setup never holds the port.
 */
public final class HttpEngine implements ServerEngine {
    static final int WORKERS = 16;
    private final ServerDefinition definition;
    private final ShareRoot share;
    private final ResolvedLogin login;
    private final ServerEvents events;
    private final LoginThrottle throttle;
    private final Supplier<List<ServerAddresses.Nic>> nics;
    private final TlsMaterial tls;
    private HttpServer server;
    private ExecutorService workers;
    private int port;

    public HttpEngine(ServerDefinition definition, ShareRoot share, ResolvedLogin login, ServerEvents events, LoginThrottle throttle,
                      Supplier<List<ServerAddresses.Nic>> nics) {
        this(definition, share, login, events, throttle, nics, null);
    }

    /** {@code tls} may be null when the definition does not use TLS. */
    public HttpEngine(ServerDefinition definition, ShareRoot share, ResolvedLogin login, ServerEvents events, LoginThrottle throttle,
                      Supplier<List<ServerAddresses.Nic>> nics, TlsMaterial tls) {
        this.definition = definition; this.share = share; this.login = login; this.events = events; this.throttle = throttle;
        this.nics = nics; this.tls = tls;
    }

    @Override public synchronized void start() throws EngineFailure {
        if (server != null) return;
        if (definition.tls() && tls == null) throw new IllegalStateException("HTTPS needs the TLS material");
        InetAddress address = ServerAddresses.bindAddress(definition.bind(), nics.get());
        SSLContext ssl = definition.tls() ? tls.context() : null;
        HttpServer created;
        try {
            if (definition.tls()) {
                HttpsServer secure = HttpsServer.create();
                secure.setHttpsConfigurator(new HttpsConfigurator(ssl));
                created = secure;
            } else {
                created = HttpServer.create();
            }
        } catch (IOException failure) {
            throw new EngineFailure("Could not create the " + (definition.tls() ? "HTTPS" : "HTTP") + " server: " + failure.getMessage(), failure);
        }
        ExecutorService pool = Executors.newFixedThreadPool(WORKERS, Thread.ofPlatform().daemon().name("file-server-http-", 0).factory());
        try {
            HostCheck hosts = HostCheck.forBind(definition.bind());
            HttpContext context = created.createContext("/", new FileHandler(share, events, hosts));
            if (!login.isAnonymous()) context.setAuthenticator(new ThrottledAuthenticator(definition.name(), login, throttle, events, hosts));
            created.setExecutor(pool);
            try { created.bind(new InetSocketAddress(address, definition.port()), 64); }
            catch (IOException failure) { throw EngineFailure.bind(failure, definition.port(), ServerAddresses.describe(address), definition.protocol()); }
            created.start();
        } catch (EngineFailure | RuntimeException | Error failure) {
            created.stop(0);
            pool.shutdownNow();
            throw failure;
        }
        server = created;
        workers = pool;
        port = created.getAddress().getPort();
    }

    @Override public synchronized void stop() {
        if (server == null) return;
        server.stop(0);
        workers.shutdownNow();
        server = null;
        workers = null;
    }

    @Override public synchronized List<URI> addresses() {
        if (server == null) return List.of();
        return ServerAddresses.urls(definition.protocol(), definition.tls(), ServerAddresses.reachable(definition.bind(), nics.get()), port, login.user());
    }

    /** The bound port; 0 before the first start. */
    public synchronized int port() { return port; }
}
