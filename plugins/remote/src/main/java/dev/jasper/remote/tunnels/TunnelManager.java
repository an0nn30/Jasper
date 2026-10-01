package dev.jasper.remote.tunnels;

import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.client.Connections;
import dev.jasper.remote.client.Failures;
import dev.jasper.remote.client.SessionLease;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.terminal.WindowHandle;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import org.apache.sshd.client.channel.ChannelDirectTcpip;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.future.CloseFuture;
import org.apache.sshd.common.future.SshFutureListener;
import org.apache.sshd.common.util.net.SshdSocketAddress;

/**
 * Runs tunnels on the UI thread. A start binds first, then leases the host's shared session from {@link Connections}
 * and forwards every accepted connection over its own channel; a remote tunnel asks the server for its port, pointed at
 * a loopback relay. Each running tunnel holds its own lease, so closing terminals never stops it and stopping it never
 * touches a terminal. Connection threads read only a run's session and write its counters and tracked ends. In plan
 * 7b-2 a lost session fails the tunnel.
 */
public final class TunnelManager implements AutoCloseable {
    /** The loopback address a remote tunnel's relay listens on. */
    static final String RELAY = "127.0.0.1";

    /** What a SOCKS client hears once its channel opened or failed; nothing for a local tunnel. */
    private interface Answer { void opened(boolean ok) throws IOException; }

    private final Connections connections;
    private final Supplier<Duration> timeout;
    private final Executor background, ui;
    private final Map<UUID, Run> runs = new HashMap<>();
    private final Map<UUID, TunnelState> states = new HashMap<>();
    private final Map<UUID, Tunnel> quick = new LinkedHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private boolean closed;

    public TunnelManager(Connections connections, Supplier<Duration> timeout, Executor background, Executor ui) {
        this.connections = connections; this.timeout = timeout; this.background = background; this.ui = ui;
    }

    public TunnelState state(UUID id) { return states.getOrDefault(id, TunnelState.STOPPED); }
    /** The definition a tunnel runs with while it is Starting or Up. */
    public Optional<Tunnel> running(UUID id) { return Optional.ofNullable(runs.get(id)).map(run -> run.tunnel); }
    public List<Tunnel> runningTunnels() { return runs.values().stream().map(run -> run.tunnel).toList(); }
    /** Unsaved forwards, oldest first, until they are stopped or saved. */
    public List<Tunnel> quickForwards() { return List.copyOf(quick.values()); }
    /** Tunnels Starting or Up. */
    public int active() { return runs.size(); }
    public int failed() { return (int) states.values().stream().filter(TunnelState.Failed.class::isInstance).count(); }
    public Subscription onChanged(Runnable listener) { listeners.add(listener); return () -> listeners.remove(listener); }

    /** Starts a tunnel; prompts (a Vault unlock, an unknown host key) go to {@code owner}, which may be null. A running one is left alone. */
    public void start(Tunnel tunnel, WindowHandle owner) {
        if (closed || runs.containsKey(tunnel.id())) return;
        UUID id = tunnel.id();
        var run = new Run(tunnel);
        runs.put(id, run);
        set(id, new TunnelState.Starting("Binding…"));
        ConnectionIdentity identity;
        try { identity = connections.identity(tunnel.hostId()); }
        catch (RuntimeException missing) { fail(run, "Host not found".equals(missing.getMessage()) ? "Host missing" : message(missing)); return; }
        try {
            run.listener = switch (tunnel.kind()) {
                case LOCAL -> Listener.open(tunnel.bindAddress(), tunnel.bindPort(),
                    socket -> carry(run, socket, new SshdSocketAddress(tunnel.targetHost(), tunnel.targetPort()), ok -> { }));
                case DYNAMIC -> Listener.open(tunnel.bindAddress(), tunnel.bindPort(), socket -> socks(run, socket));
                case REMOTE -> Listener.open(RELAY, 0, socket -> relay(run, socket));
            };
        } catch (IOException | RuntimeException failure) { fail(run, message(failure)); return; }
        set(id, new TunnelState.Starting("Connecting…"));
        run.leasing = connections.lease(identity, owner, stage -> { if (runs.get(id) == run) set(id, new TunnelState.Starting(stage)); });
        run.leasing.whenComplete((lease, failure) -> ui.execute(() -> leased(run, lease, failure)));
    }

    /** Lists a quick forward and starts it. */
    public void forward(Tunnel quickForward, WindowHandle owner) {
        if (closed) return;
        quick.put(quickForward.id(), quickForward);
        changed();
        start(quickForward, owner);
    }

    /** A quick forward was saved: it leaves the quick list and keeps running as the saved tunnel with the same id. */
    public void saved(UUID id) { if (quick.remove(id) != null) changed(); }

    /** Stops a tunnel: a running one closes its listener and connections and releases its lease; a failed one returns to Stopped; a quick forward disappears. */
    public void stop(UUID id) {
        Run run = runs.remove(id);
        if (run != null) teardown(run);
        boolean known = states.remove(id) != null | quick.remove(id) != null;
        if (run != null || known) changed();
    }

    public void stopAll() {
        var ids = new ArrayList<UUID>(states.keySet());
        ids.addAll(quick.keySet());
        for (UUID id : ids) stop(id);
    }

    /** Plugin stop: closes everything without telling listeners. */
    @Override public void close() {
        closed = true;
        for (Run run : List.copyOf(runs.values())) teardown(run);
        runs.clear(); states.clear(); quick.clear(); listeners.clear();
    }

    /** What the server is asked to listen on: {@code *} goes as 0.0.0.0, which MINA sends for an empty host anyway. */
    static String serverAddress(String bindAddress) { return bindAddress.equals(Tunnel.ALL) ? "0.0.0.0" : bindAddress; }

    private void leased(Run run, SessionLease lease, Throwable failure) {
        UUID id = run.tunnel.id();
        if (runs.get(id) != run) { if (lease != null) lease.close(); return; }
        if (failure != null) {
            if (cause(failure) instanceof CancellationException) stop(id); else fail(run, message(failure));
            return;
        }
        run.lease = lease;
        ClientSession session = lease.session();
        run.leased = session;
        run.watch = done -> ui.execute(() -> { if (runs.get(id) == run && run.leased == session) fail(run, "Connection lost"); });
        session.addCloseFutureListener(run.watch);
        if (!session.isOpen()) { fail(run, "Connection lost"); return; }
        if (run.tunnel.kind() != TunnelKind.REMOTE) { up(run, session, run.listener.port()); return; }
        set(id, new TunnelState.Starting("Requesting port on the server…"));
        var requested = new SshdSocketAddress(serverAddress(run.tunnel.bindAddress()), run.tunnel.bindPort());
        var relay = new SshdSocketAddress(RELAY, run.listener.port());
        background(() -> {
            SshdSocketAddress bound = null;
            try { bound = session.startRemotePortForwarding(requested, relay); }
            catch (IOException | RuntimeException refused) { /* reported below */ }
            SshdSocketAddress answer = bound;
            ui.execute(() -> {
                if (runs.get(id) != run) { if (answer != null) background(() -> stopRemote(session, answer)); return; }
                if (answer == null) {
                    String port = run.tunnel.bindPort() == 0 ? "a port" : "port " + run.tunnel.bindPort();
                    fail(run, "The server refused " + port + ": already in use or not allowed");
                    return;
                }
                run.remoteBound = answer;
                up(run, session, answer.getPort());
            });
        });
    }

    private void up(Run run, ClientSession session, int port) {
        run.session = session;
        set(run.tunnel.id(), new TunnelState.Up(Instant.now(), port, run.counters));
    }

    private void fail(Run run, String reason) {
        runs.remove(run.tunnel.id(), run);
        teardown(run);
        set(run.tunnel.id(), new TunnelState.Failed(reason));
    }

    private void teardown(Run run) {
        run.stop();
        if (run.leasing != null && !run.leasing.isDone()) run.leasing.cancel(true);
        ClientSession session = run.leased;
        if (session != null && run.watch != null) session.removeCloseFutureListener(run.watch);
        SessionLease lease = run.lease;
        SshdSocketAddress bound = run.remoteBound;
        if (lease == null) return;
        if (bound == null) { lease.close(); return; }
        background(() -> { stopRemote(session, bound); lease.close(); });
    }

    private static void stopRemote(ClientSession session, SshdSocketAddress bound) {
        try { session.stopRemotePortForwarding(bound); } catch (IOException | RuntimeException gone) { /* the session is closing */ }
    }

    private void set(UUID id, TunnelState state) { states.put(id, state); changed(); }
    private void changed() { listeners.forEach(Runnable::run); }

    private void background(Runnable work) {
        try { background.execute(work); } catch (RejectedExecutionException stopping) { work.run(); }
    }

    // ---- connection threads

    /** On the connection's own thread: a channel to {@code target} over the run's session, then the pump. */
    private void carry(Run run, Socket client, SshdSocketAddress target, Answer answer) {
        Pump.End near = Pump.of(client);
        ClientSession session = run.session;
        if (session == null || !run.track(near)) { near.close(); return; }
        ChannelDirectTcpip channel = null;
        try {
            channel = session.createDirectTcpipChannel(new SshdSocketAddress(client.getInetAddress().getHostAddress(), client.getPort()), target);
            channel.open().verify(timeout.get());
        } catch (IOException | RuntimeException refused) {
            if (channel != null) channel.close(true);
            run.counters.refused(refusal(refused, target.getHostName()));
            try { answer.opened(false); } catch (IOException gone) { /* the client left */ }
            run.untrack(near);
            near.close();
            return;
        }
        Pump.End far = Pump.of(channel);
        if (!run.track(far)) { run.untrack(near); near.close(); return; }
        try { answer.opened(true); }
        catch (IOException gone) { run.untrack(near); run.untrack(far); near.close(); far.close(); return; }
        pump(run, near, far);
    }

    /** A dynamic tunnel's connection: the SOCKS5 handshake names the target. */
    private void socks(Run run, Socket client) {
        if (run.session == null) { quietly(client); return; }
        Socks5.Target target;
        try {
            client.setSoTimeout(millis(timeout.get()));
            target = Socks5.accept(client.getInputStream(), client.getOutputStream());
            client.setSoTimeout(0);
        } catch (IOException refused) { quietly(client); return; }
        carry(run, client, new SshdSocketAddress(target.host(), target.port()),
            ok -> Socks5.reply(client.getOutputStream(), ok ? Socks5.SUCCEEDED : Socks5.HOST_UNREACHABLE));
    }

    /** A connection the server forwarded to a remote tunnel's relay: on to the target from this computer. */
    private void relay(Run run, Socket fromServer) {
        Pump.End near = Pump.of(fromServer);
        if (!run.track(near)) return;
        var target = new Socket();
        try { target.connect(new InetSocketAddress(run.tunnel.targetHost(), run.tunnel.targetPort()), millis(timeout.get())); }
        catch (IOException | RuntimeException refused) {
            quietly(target);
            run.counters.refused(refusal(refused, run.tunnel.targetHost()));
            run.untrack(near);
            near.close();
            return;
        }
        Pump.End far = Pump.of(target);
        if (!run.track(far)) { run.untrack(near); near.close(); return; }
        pump(run, near, far);
    }

    private static void pump(Run run, Pump.End near, Pump.End far) {
        run.counters.opened();
        try { Pump.run(near, far, run.counters); }
        finally { run.counters.closed(); run.untrack(near); run.untrack(far); }
    }

    private String refusal(Throwable failure, String host) {
        return "Connections are being refused: " + Failures.message(failure, timeout.get(), host);
    }

    private static int millis(Duration duration) { return (int) Math.min(Integer.MAX_VALUE, Math.max(0, duration.toMillis())); }

    private static void quietly(Socket socket) {
        try { socket.close(); } catch (IOException ignored) { /* closing */ }
    }

    private static Throwable cause(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) cause = cause.getCause();
        return cause;
    }

    private static String message(Throwable failure) {
        Throwable cause = cause(failure);
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    /** One running tunnel. Fields without volatile belong to the UI thread. */
    private static final class Run {
        final Tunnel tunnel;
        final Counters counters = new Counters();
        private final Set<Pump.End> open = ConcurrentHashMap.newKeySet();
        private volatile boolean stopped;
        /** The session connections go over; null until Up and after stop. */
        volatile ClientSession session;
        Listener listener;
        CompletableFuture<SessionLease> leasing;
        SessionLease lease;
        ClientSession leased;
        SshFutureListener<CloseFuture> watch;
        SshdSocketAddress remoteBound;

        Run(Tunnel tunnel) { this.tunnel = tunnel; }

        /** Keeps {@code end} to close on stop; false, with {@code end} closed, once the run has stopped. */
        boolean track(Pump.End end) {
            open.add(end);
            if (!stopped) return true;
            open.remove(end);
            end.close();
            return false;
        }

        void untrack(Pump.End end) { open.remove(end); }

        /** Stops accepting and closes every connection; releasing the lease is the manager's. */
        void stop() {
            stopped = true;
            session = null;
            if (listener != null) listener.close();
            for (Pump.End end : List.copyOf(open)) end.close();
            open.clear();
        }
    }
}
