package dev.jasper.browser.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A server on the loopback address alone, for the tests of the plugin's own fetches and of its HTTP proxy (plan 6's
 * F3): it records the request line of each connection, then answers {@code 200} with a two-byte body, or, while told
 * to hold, keeps the connection open without answering. As a proxy, it shows that a client went through it, since a
 * request through a proxy names the whole address it asks for; it forwards nothing, so nothing leaves this computer.
 * Closed by its test.
 */
public final class StandIn implements AutoCloseable {
    /** The body every answer carries. */
    public static final String BODY = "ok";
    private final ServerSocket socket;
    private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
    private volatile CountDownLatch hold = new CountDownLatch(0);

    /**
     * A stand-in, listening.
     *
     * @throws IOException when no port is free
     */
    public StandIn() throws IOException {
        socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread.ofVirtual().start(this::serve);
    }

    /**
     * Its port on the loopback address.
     *
     * @return the port
     */
    public int port() {
        return socket.getLocalPort();
    }

    /**
     * Its address, unresolved, as the proxy setting gives one.
     *
     * @return the address
     */
    public InetSocketAddress address() {
        return InetSocketAddress.createUnresolved("127.0.0.1", port());
    }

    /**
     * The request line of the next connection, waiting up to 10 seconds for it.
     *
     * @return the line, or null when none came
     * @throws InterruptedException when interrupted
     */
    public String next() throws InterruptedException {
        return lines.poll(10, TimeUnit.SECONDS);
    }

    /**
     * Whether a request line has come that {@link #next} has not taken yet.
     *
     * @return whether one has
     */
    public boolean asked() {
        return !lines.isEmpty();
    }

    /** From now on, each connection is held open, unanswered, until {@link #close()}. */
    public void holds() {
        hold = new CountDownLatch(1);
    }

    @Override public void close() throws IOException {
        hold.countDown();
        socket.close();
    }

    private void serve() {
        while (!socket.isClosed()) {
            Socket accepted;
            try {
                accepted = socket.accept();
            } catch (IOException closed) {
                return;
            }
            Thread.ofVirtual().start(() -> answer(accepted));
        }
    }

    private void answer(Socket connection) {
        try (connection) {
            var in = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.ISO_8859_1));
            String line = in.readLine();
            if (line == null) return;
            lines.add(line);
            for (String header = in.readLine(); header != null && !header.isEmpty(); header = in.readLine()) {
                // The headers are read and dropped.
            }
            hold.await(30, TimeUnit.SECONDS);
            OutputStream out = connection.getOutputStream();
            out.write(("HTTP/1.1 200 OK\r\nContent-Length: " + BODY.length() + "\r\nConnection: close\r\n\r\n" + BODY)
                .getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
        } catch (IOException | InterruptedException ended) {
            // The client or the test ended the connection.
        }
    }
}
