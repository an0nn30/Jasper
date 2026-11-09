package dev.jasper.browser.adblock;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A filter list server on the loopback address alone (F9), for the list's tests: each path answers as a list's server
 * might, well or badly, and counts the requests it was sent. Every list it serves is written by hand (F36). Closed by
 * its test.
 */
final class ListServer implements AutoCloseable {
    /** Three rules Jasper keeps and one it skips. */
    static final String LIST = """
        [{"trigger": {"url-filter": "ads.example/"}, "action": {"type": "block"}},
         {"trigger": {"url-filter": "tracker.example/", "load-type": ["third-party"]}, "action": {"type": "block"}},
         {"trigger": {"url-filter": ".*", "if-domain": ["news.example"]}, "action": {"type": "css-display-none", "selector": ".banner"}},
         {"trigger": {"url-filter": "upgrade"}, "action": {"type": "make-https"}}]
        """;
    /** Another list, of two rules, at another address. */
    static final String OTHER = """
        [{"trigger": {"url-filter": "pixel.example/"}, "action": {"type": "block"}},
         {"trigger": {"url-filter": "beacon.example/"}, "action": {"type": "block"}}]
        """;
    /** Lets {@code /hold} and {@code /announced} end, and {@code /late} answer. */
    final CountDownLatch release = new CountDownLatch(1), late = new CountDownLatch(1);
    /** Hold the answers of {@code /list.json} and {@code /other.json} while shut; both start open. */
    final Gate list, other;
    private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
    private final List<Gate> gates = new CopyOnWriteArrayList<>();
    private final HttpServer server;

    /**
     * The server, started, with its paths: {@code /list.json} and {@code /other.json}, the lists, each through its
     * gate; {@code /error}, a 500; {@code /text}, a 200 that is no JSON; {@code /moved}, a 302 to {@code /list.json};
     * {@code /huge}, a body of more than 32 MiB of no announced length; {@code /announced}, one announced as more than
     * 32 MiB; {@code /hold}, headers and then a body that trickles until {@link #release}; and {@code /late}, the list
     * once {@link #late} lets it come.
     */
    ListServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        list = gated("/list.json", LIST);
        other = gated("/other.json", OTHER);
        handle("/error", exchange -> answer(exchange, 500, "broken"));
        handle("/text", exchange -> answer(exchange, 200, "this is not json"));
        handle("/moved", exchange -> redirect(exchange, "/list.json"));
        handle("/huge", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream body = exchange.getResponseBody()) {
                byte[] block = new byte[64 * 1024];
                Arrays.fill(block, (byte) ' ');
                for (long sent = 0; sent <= 33L * 1024 * 1024; sent += block.length) body.write(block);
            } catch (IOException stopped) {
                // The client stopped reading.
            }
        });
        handle("/announced", exchange -> {
            exchange.sendResponseHeaders(200, 33L * 1024 * 1024);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(new byte[1024]);
                body.flush();
                release.await(30, TimeUnit.SECONDS);
            } catch (IOException | InterruptedException stopped) {
                // The client stopped reading.
            }
        });
        handle("/late", exchange -> {
            try {
                late.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            answer(exchange, 200, LIST);
        });
        handle("/hold", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write('[');
                body.flush();
                release.await(30, TimeUnit.SECONDS);
            } catch (IOException | InterruptedException stopped) {
                // The client gave up.
            }
        });
        server.start();
    }

    /** The address of {@code path} on this server. */
    URI at(String path) {
        return URI.create("http://127.0.0.1:" + port() + path);
    }

    /** The server's port. */
    int port() {
        return server.getAddress().getPort();
    }

    /** How many requests {@code path} was sent. */
    int hits(String path) {
        return hits.getOrDefault(path, new AtomicInteger()).get();
    }

    /** Answers {@code path} with {@code handler}, counting each request. */
    void handle(String path, HttpHandler handler) {
        server.createContext(path, exchange -> {
            hits.computeIfAbsent(path, any -> new AtomicInteger()).incrementAndGet();
            handler.handle(exchange);
        });
    }

    /** Answers {@code path} with a 200 of {@code body}, once its gate, which starts open, lets it. */
    Gate gated(String path, String body) {
        var gate = new Gate();
        gates.add(gate);
        handle(path, exchange -> {
            try {
                gate.pass();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            answer(exchange, 200, body);
        });
        return gate;
    }

    /** Answers with a 302 to {@code location}, as written. */
    static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        answer(exchange, 302, "");
    }

    /** Answers with {@code status} and {@code body}. */
    static void answer(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override public void close() {
        release.countDown();
        late.countDown();
        for (Gate gate : gates) gate.open();
        server.stop(0);
        ((ExecutorService) server.getExecutor()).shutdownNow();
    }

    /**
     * Holds a path's answers while shut, so a test sees a download under way for as long as it needs (ruling V53),
     * whatever the machine's load.
     */
    static final class Gate {
        private volatile CountDownLatch open = new CountDownLatch(0);

        /** From now on, the path's answers wait until {@link #open}. */
        void shut() {
            open = new CountDownLatch(1);
        }

        /** Lets the answers waiting go, and those after. */
        void open() {
            open.countDown();
        }

        private void pass() throws InterruptedException {
            open.await(30, TimeUnit.SECONDS);
        }
    }
}
