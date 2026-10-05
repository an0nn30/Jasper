package dev.jasper.fileserver.engine.tftp;

import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.EngineFailure;
import dev.jasper.fileserver.engine.ServerAddresses;
import dev.jasper.fileserver.engine.ServerEngine;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.share.ShareRoot;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * A TFTP server for one share, on the JDK: the server's port takes requests, and each transfer runs on its own
 * ephemeral socket. At most 16 run at once; another request gets error 0 "Server busy". A lost packet is resent after
 * one second, up to five times. TFTP has no login: anyone who can reach the port can use it. An error packet is never
 * answered, so a forged one cannot start an endless exchange (CVE-2024-2169).
 */
public final class TftpEngine implements ServerEngine {
    static final Duration TIMEOUT = Duration.ofSeconds(1);
    static final int RETRIES = 5;
    static final int MAX_TRANSFERS = 16;
    /** How long the listener waits after a failed receive, so a persistent error cannot spin a core. */
    static final Duration RECEIVE_PAUSE = Duration.ofMillis(50);
    private final ServerDefinition definition;
    private final ShareRoot share;
    private final ServerEvents events;
    private final Supplier<List<ServerAddresses.Nic>> nics;
    private final Duration timeout;
    private final int retries, maxTransfers;
    private final Set<DatagramSocket> transferSockets = ConcurrentHashMap.newKeySet();
    private DatagramSocket socket;
    private ExecutorService transfers;
    private Thread listener;
    private InetAddress address;
    private volatile boolean running;
    private int port;

    public TftpEngine(ServerDefinition definition, ShareRoot share, ServerEvents events, Supplier<List<ServerAddresses.Nic>> nics) {
        this(definition, share, events, nics, TIMEOUT, RETRIES, MAX_TRANSFERS);
    }

    TftpEngine(ServerDefinition definition, ShareRoot share, ServerEvents events, Supplier<List<ServerAddresses.Nic>> nics,
               Duration timeout, int retries, int maxTransfers) {
        this.definition = definition; this.share = share; this.events = events; this.nics = nics;
        this.timeout = timeout; this.retries = retries; this.maxTransfers = maxTransfers;
    }

    @Override public synchronized void start() throws EngineFailure {
        if (socket != null) return;
        InetAddress bindTo = ServerAddresses.bindAddress(definition.bind(), nics.get());
        DatagramSocket bound;
        try { bound = new DatagramSocket(new InetSocketAddress(bindTo, definition.port())); }
        catch (SocketException failure) { throw EngineFailure.bind(failure, definition.port(), ServerAddresses.describe(bindTo), definition.protocol()); }
        address = bindTo;
        socket = bound;
        port = bound.getLocalPort();
        // Each start has its own slots and pool, so a transfer still unwinding from an earlier start frees its own slot.
        var slots = new Semaphore(maxTransfers);
        var pool = Executors.newFixedThreadPool(maxTransfers, Thread.ofPlatform().daemon().name("file-server-tftp-transfer-", 0).factory());
        transfers = pool;
        running = true;
        DatagramSocket listening = bound;
        listener = Thread.ofPlatform().daemon().name("file-server-tftp-" + port).start(() -> listen(listening, slots, pool));
    }

    /**
     * Takes requests until the socket closes; each gets a slot and its own socket, or an error packet. An error packet
     * gets nothing, even a malformed one.
     */
    private void listen(DatagramSocket server, Semaphore slots, ExecutorService pool) {
        byte[] buffer = new byte[TftpPacket.MAX_PACKET];
        while (running) {
            var received = new DatagramPacket(buffer, buffer.length);
            try { server.receive(received); }
            catch (IOException failed) {
                if (!running || server.isClosed()) return;
                try { Thread.sleep(RECEIVE_PAUSE); }
                catch (InterruptedException interrupted) { return; }
                continue;
            }
            if (TftpPacket.isError(received.getData(), received.getLength())) continue;
            var client = (InetSocketAddress) received.getSocketAddress();
            TftpPacket packet;
            try { packet = TftpPacket.parse(received.getData(), received.getLength()); }
            catch (IllegalArgumentException malformed) { reply(server, client, TftpTransfer.ILLEGAL); continue; }
            if (!(packet instanceof TftpPacket.Request request)) { reply(server, client, TftpTransfer.ILLEGAL); continue; }
            if (!slots.tryAcquire()) { reply(server, client, TftpTransfer.BUSY); continue; }
            try { pool.execute(() -> serve(client, request, slots)); }
            catch (RejectedExecutionException stopping) { slots.release(); }
        }
    }

    private void serve(InetSocketAddress client, TftpPacket.Request request, Semaphore slots) {
        try (DatagramSocket transferSocket = new DatagramSocket(new InetSocketAddress(address, 0))) {
            transferSockets.add(transferSocket);
            try {
                if (running) new TftpTransfer(share, events, client, request, timeout, retries).run(transferSocket);
            } finally {
                transferSockets.remove(transferSocket);
            }
        } catch (SocketException noSocket) {
            if (running) events.error(ServerAddresses.hostText(client.getAddress()), "TFTP transfer failed: " + noSocket.getMessage());
        } finally {
            slots.release();
        }
    }

    private static void reply(DatagramSocket server, InetSocketAddress client, TftpPacket packet) {
        byte[] bytes = packet.encode();
        try { server.send(new DatagramPacket(bytes, bytes.length, client)); }
        catch (IOException ignored) { /* the client will time out */ }
    }

    @Override public synchronized void stop() {
        if (socket == null) return;
        running = false;
        socket.close();
        transferSockets.forEach(DatagramSocket::close);
        transfers.shutdownNow();
        try { listener.join(1000); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        socket = null;
        transfers = null;
    }

    @Override public synchronized List<URI> addresses() {
        if (socket == null) return List.of();
        return ServerAddresses.urls(definition.protocol(), false, ServerAddresses.reachable(definition.bind(), nics.get()), port, Optional.empty());
    }

    /** The bound port; 0 before the first start. */
    public synchronized int port() { return port; }
}
