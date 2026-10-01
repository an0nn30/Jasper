package dev.jasper.remote.tunnels;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** A TCP echo server on 127.0.0.1 for tunnel tests: each connection gets its own bytes back, then a half-close. */
public final class EchoServer implements AutoCloseable {
    private final ServerSocket socket;

    public EchoServer() throws IOException {
        socket = new ServerSocket(0, 50, InetAddress.ofLiteral("127.0.0.1"));
        Thread.ofVirtual().name("echo-accept").start(this::serve);
    }

    public int port() { return socket.getLocalPort(); }

    private void serve() {
        while (!socket.isClosed()) {
            Socket client;
            try { client = socket.accept(); } catch (IOException closed) { return; }
            Thread.ofVirtual().name("echo").start(() -> {
                try (client) { client.getInputStream().transferTo(client.getOutputStream()); client.shutdownOutput(); }
                catch (IOException ignored) { /* the test hung up */ }
            });
        }
    }

    /** Connects to {@code 127.0.0.1:port}, sends {@code text}, half-closes and returns everything that comes back. */
    public static String roundTrip(int port, String text) throws IOException {
        try (var client = new Socket(InetAddress.ofLiteral("127.0.0.1"), port)) {
            client.setSoTimeout(10_000);
            client.getOutputStream().write(text.getBytes(StandardCharsets.UTF_8));
            client.shutdownOutput();
            return new String(client.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Override public void close() throws IOException { socket.close(); }
}
