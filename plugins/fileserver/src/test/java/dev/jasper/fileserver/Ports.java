package dev.jasper.fileserver;

import dev.jasper.fileserver.definition.PortRange;
import dev.jasper.fileserver.engine.EngineFailure;
import dev.jasper.fileserver.engine.ServerEngine;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.BindException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

/** Test ports. */
public final class Ports {
    private static final int ATTEMPTS = 5;
    private static final int LOWEST = 20000, EPHEMERAL = 32768;

    private Ports() { }

    /** Builds an engine that listens on the given port. */
    @FunctionalInterface public interface EngineOnPort<E extends ServerEngine> { E build(int port) throws IOException; }

    /**
     * A port that was free on 127.0.0.1 for both TCP and UDP a moment ago. The TCP and UDP port spaces are separate, so a
     * port the system hands out for TCP can already be bound for UDP.
     */
    public static int free() {
        var loopback = InetAddress.getLoopbackAddress();
        try {
            for (int attempt = 1; ; attempt++) {
                try (var tcp = new ServerSocket(0, 0, loopback); var udp = new DatagramSocket(null)) {
                    udp.bind(new InetSocketAddress(loopback, tcp.getLocalPort()));
                    return tcp.getLocalPort();
                } catch (BindException taken) {
                    if (attempt == ATTEMPTS) throw taken;
                }
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /**
     * {@code count} consecutive ports that were free on 127.0.0.1 for TCP a moment ago, for FTP's passive data connections.
     * They lie below every system's ephemeral range (Linux's starts lowest, at 32768), so no socket bound to port 0 and no
     * outgoing connection takes one while a test runs. Apache FtpServer's own check binds the wildcard address with
     * SO_REUSEADDR, which macOS allows beside a socket on 127.0.0.1, so a passive port held there fails the transfer.
     */
    public static PortRange freeRange(int count) {
        var random = ThreadLocalRandom.current();
        for (int attempt = 1; ; attempt++) {
            int first = random.nextInt(LOWEST, EPHEMERAL - count + 1);
            if (IntStream.range(first, first + count).allMatch(Ports::tcpFree)) return new PortRange(first, first + count - 1);
            if (attempt == ATTEMPTS) throw new IllegalStateException("No " + count + " consecutive ports were free below " + EPHEMERAL);
        }
    }

    private static boolean tcpFree(int port) {
        try (var socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
            return true;
        } catch (IOException taken) {
            return false;
        }
    }

    /**
     * Starts the engine {@code engine} builds on a {@link #free} port. Another socket can take the port before the engine
     * binds it, so a start that finds the port in use builds the engine again on another port.
     */
    public static <E extends ServerEngine> E start(EngineOnPort<E> engine) throws IOException, EngineFailure {
        for (int attempt = 1; ; attempt++) {
            E built = engine.build(free());
            try {
                built.start();
                return built;
            } catch (EngineFailure failure) {
                if (attempt == ATTEMPTS || !(failure.getCause() instanceof BindException)) throw failure;
            }
        }
    }
}
