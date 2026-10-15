package dev.jasper.remote.tunnels;

import dev.jasper.remote.client.Failures;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * A tunnel's listening sockets on this computer: bound at start, closed at stop. {@code localhost} binds both loopback
 * addresses, like OpenSSH (::1 only where the machine has it); {@code *} binds the wildcard. One virtual thread per
 * socket accepts, and each connection gets its own virtual thread.
 */
final class Listener implements AutoCloseable {
    /** The longest {@link #close} waits for an accepting thread to let go of its socket. */
    private static final Duration LET_GO = Duration.ofSeconds(1);

    private final List<ServerSocket> sockets;
    private final List<Thread> accepting = new ArrayList<>();

    private Listener(List<ServerSocket> sockets) { this.sockets = List.copyOf(sockets); }

    /**
     * Binds and starts accepting; {@code accepted} runs on a new virtual thread per connection and owns its socket.
     *
     * @throws Failures.Failure when binding fails, with the reason in the user's words
     */
    static Listener open(String bindAddress, int port, Consumer<Socket> accepted) throws IOException {
        List<InetAddress> addresses = addresses(bindAddress);
        var sockets = new ArrayList<ServerSocket>();
        try {
            sockets.add(bind(addresses.getFirst(), port, bindAddress));
            int bound = sockets.getFirst().getLocalPort();
            for (InetAddress extra : addresses.subList(1, addresses.size())) {
                try { sockets.add(bind(extra, bound, bindAddress)); }
                catch (IOException | RuntimeException unavailable) { /* best effort: no IPv6 loopback here */ }
            }
        } catch (IOException | RuntimeException failure) {
            sockets.forEach(Listener::quietly);
            throw failure;
        }
        var listener = new Listener(sockets);
        for (ServerSocket socket : listener.sockets)
            listener.accepting.add(Thread.ofVirtual().name("jasper-tunnel-accept").start(() -> accept(socket, accepted)));
        return listener;
    }

    /** The port every socket listens on. */
    int port() { return sockets.getFirst().getLocalPort(); }

    /**
     * Closes the sockets and waits, up to a second each, for the threads accepting on them: the JDK keeps a socket that
     * a virtual thread is accepting on open until that thread runs again, and until then the port still takes
     * connections and cannot be bound again.
     */
    @Override public void close() {
        sockets.forEach(Listener::quietly);
        for (Thread thread : accepting) {
            try { thread.join(LET_GO); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
        }
    }

    /** The addresses a bind address stands for; never asks DNS. */
    static List<InetAddress> addresses(String bindAddress) {
        return switch (bindAddress) {
            case Tunnel.LOCALHOST -> List.of(InetAddress.ofLiteral("127.0.0.1"), InetAddress.ofLiteral("::1"));
            case Tunnel.ALL -> List.of(new InetSocketAddress(0).getAddress());
            default -> List.of(InetAddress.ofLiteral(bindAddress));
        };
    }

    static String bindMessage(BindException failure, String address, int port) {
        String text = String.valueOf(failure.getMessage()).toLowerCase(Locale.ROOT);
        if (text.contains("in use")) return "Port " + port + " is already in use on this computer";
        if (text.contains("permission") || text.contains("access")) return "Port " + port + " needs administrator rights";
        if (text.contains("assign")) return "Cannot listen on " + address + ": not an address of this computer";
        return "Cannot listen on " + address + ":" + port + ": " + failure.getMessage();
    }

    private static ServerSocket bind(InetAddress address, int port, String shown) throws IOException {
        var socket = new ServerSocket();
        try {
            socket.bind(new InetSocketAddress(address, port), 50);
            return socket;
        } catch (BindException refused) {
            quietly(socket);
            throw new Failures.Failure(bindMessage(refused, shown, port), refused);
        } catch (IOException | RuntimeException other) {
            quietly(socket);
            throw other;
        }
    }

    private static void accept(ServerSocket socket, Consumer<Socket> accepted) {
        while (!socket.isClosed()) {
            Socket client;
            try { client = socket.accept(); }
            catch (IOException closedOrBroken) { return; }
            Thread.ofVirtual().name("jasper-tunnel-connection").start(() -> accepted.accept(client));
        }
    }

    private static void quietly(ServerSocket socket) {
        try { socket.close(); } catch (IOException ignored) { /* closing */ }
    }
}
