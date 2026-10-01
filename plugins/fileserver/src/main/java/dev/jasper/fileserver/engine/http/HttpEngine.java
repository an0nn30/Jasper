package dev.jasper.fileserver.engine.http;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpServer;
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
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/** An HTTP server for one share on the JDK's HTTP server, with at most 16 requests handled at once. */
public final class HttpEngine implements ServerEngine {
    static final int WORKERS = 16;
    private final ServerDefinition definition;
    private final ShareRoot share;
    private final ResolvedLogin login;
    private final ServerEvents events;
    private final LoginThrottle throttle;
    private final Supplier<List<ServerAddresses.Nic>> nics;
    private HttpServer server;
    private ExecutorService workers;
    private int port;

    public HttpEngine(ServerDefinition definition, ShareRoot share, ResolvedLogin login, ServerEvents events, LoginThrottle throttle,
                      Supplier<List<ServerAddresses.Nic>> nics) {
        this.definition = definition; this.share = share; this.login = login; this.events = events; this.throttle = throttle; this.nics = nics;
    }

    @Override public synchronized void start() throws EngineFailure {
        if (server != null) return;
        InetAddress address = ServerAddresses.bindAddress(definition.bind(), nics.get());
        HttpServer created;
        try { created = HttpServer.create(new InetSocketAddress(address, definition.port()), 64); }
        catch (IOException failure) { throw EngineFailure.bind(failure, definition.port(), ServerAddresses.describe(address), definition.protocol()); }
        ExecutorService pool = Executors.newFixedThreadPool(WORKERS, Thread.ofPlatform().daemon().name("file-server-http-", 0).factory());
        HttpContext context = created.createContext("/", new FileHandler(share, events, HostCheck.forBind(definition.bind())));
        if (!login.isAnonymous()) context.setAuthenticator(new ThrottledAuthenticator(definition.name(), login, throttle, events));
        created.setExecutor(pool);
        created.start();
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
