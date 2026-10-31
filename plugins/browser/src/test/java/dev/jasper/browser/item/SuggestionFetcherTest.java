package dev.jasper.browser.item;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The engine's search suggestions (spec section 6; plan 4b Task 4), against a local HTTP server: OpenSearch JSON read
 * strictly, a slow answer given up after the timeout, each request superseding the one before, and anything but a good
 * answer giving no suggestions. Nothing here needs Chromium or the network.
 */
class SuggestionFetcherTest {
    private static final Duration TIMEOUT = Duration.ofMillis(400);
    private static final Executor STARTS = task -> Thread.ofVirtual().start(task);
    // Answers that are not OpenSearch suggestions: an object, too short, no list second, text after, bad JSON, empty.
    private static final List<String> ODD = List.of("{\"q\":[\"x\"]}", "[\"x\"]", "[\"x\",\"y\"]", "[\"x\",[\"y\"]] trailing",
        "not json", "[\"x\",[\"y\",]]", "");
    private final CountDownLatch release = new CountDownLatch(1);
    private final List<String> asked = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private SuggestionFetcher fetcher;

    @BeforeEach void serve() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        handle("/google", exchange -> answer(exchange, 200, "text/javascript; charset=UTF-8",
            "[\"jc\",[\"jcef\",\"jcef maven\",\"caf\\u00e9\",42,null,\"  \"],[],{\"google:suggestsubtypes\":[[512,433],[512]]}]",
            StandardCharsets.UTF_8));
        handle("/latin", exchange -> answer(exchange, 200, "application/x-suggestions+json; charset=ISO-8859-1",
            "[\"ca\",[\"café\"]]", StandardCharsets.ISO_8859_1));
        handle("/plain", exchange -> answer(exchange, 200, "", "[\"ca\",[\"café\"]]", StandardCharsets.UTF_8));
        handle("/broken", exchange -> answer(exchange, 500, "application/json", "[\"x\",[\"y\"]]", StandardCharsets.UTF_8));
        handle("/moved", exchange -> {
            exchange.getResponseHeaders().add("Location", "/google");
            answer(exchange, 302, "", "", StandardCharsets.UTF_8);
        });
        handle("/silent", exchange -> {
            hold();
            answer(exchange, 200, "application/json", "[\"s\",[\"slow\"]]", StandardCharsets.UTF_8);
        });
        handle("/huge", exchange -> answer(exchange, 200, "application/json",
            "[\"h\",[\"" + "h".repeat(SuggestionFetcher.LIMIT) + "\"]]", StandardCharsets.UTF_8));
        for (int odd = 0; odd < ODD.size(); odd++) {
            String body = ODD.get(odd);
            handle("/odd" + odd, exchange -> answer(exchange, 200, "application/json", body, StandardCharsets.UTF_8));
        }
        server.start();
    }

    @AfterEach void stop() {
        release.countDown();
        if (fetcher != null) fetcher.close();
        server.stop(0);
        ((ExecutorService) server.getExecutor()).shutdownNow();
    }

    @Test void theSecondMemberOfAnOpenSearchAnswerIsTheSuggestions() throws Exception {
        fetcher = new SuggestionFetcher(STARTS, TIMEOUT);
        assertThat(fetcher.fetch(at("/google?q=jc")).get(5, TimeUnit.SECONDS)).as("strings only, stripped, none blank")
            .containsExactly("jcef", "jcef maven", "café");
        assertThat(asked).containsExactly("/google?q=jc");
        assertThat(fetcher.fetch(at("/moved")).get(5, TimeUnit.SECONDS)).as("after a redirect").containsExactly("jcef", "jcef maven", "café");
    }

    @Test void theAnswersCharsetIsRead() throws Exception {
        fetcher = new SuggestionFetcher(STARTS, TIMEOUT);
        assertThat(fetcher.fetch(at("/latin")).get(5, TimeUnit.SECONDS)).containsExactly("café");
        assertThat(fetcher.fetch(at("/plain")).get(5, TimeUnit.SECONDS)).as("UTF-8 when none is named").containsExactly("café");
    }

    @Test void anErrorAnOddOrAnOverlongAnswerGivesNoSuggestions() throws Exception {
        fetcher = new SuggestionFetcher(STARTS, TIMEOUT);
        assertThat(fetcher.fetch(at("/broken")).get(5, TimeUnit.SECONDS)).as("a 500").isEmpty();
        assertThat(fetcher.fetch(at("/missing")).get(5, TimeUnit.SECONDS)).as("a 404").isEmpty();
        assertThat(fetcher.fetch(at("/huge")).get(5, TimeUnit.SECONDS)).as("over the limit").isEmpty();
        for (int odd = 0; odd < ODD.size(); odd++)
            assertThat(fetcher.fetch(at("/odd" + odd)).get(5, TimeUnit.SECONDS)).as(ODD.get(odd)).isEmpty();
    }

    @Test void aSlowAnswerGivesNoSuggestionsAfterTheTimeout() throws Exception {
        fetcher = new SuggestionFetcher(STARTS, TIMEOUT);
        long start = System.nanoTime();
        assertThat(fetcher.fetch(at("/silent")).get(5, TimeUnit.SECONDS)).isEmpty();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(TIMEOUT).isLessThan(Duration.ofSeconds(3));
    }

    @Test void theDefaultTimeoutIsThreeSeconds() {
        assertThat(SuggestionFetcher.TIMEOUT).isEqualTo(Duration.ofSeconds(3));
    }

    /** Each keystroke's request supersedes the one before: its answer, however late, is dropped. */
    @Test void aNewRequestDropsTheAnswerOfTheOneBefore() throws Exception {
        fetcher = new SuggestionFetcher(STARTS, Duration.ofSeconds(5));
        var stale = fetcher.fetch(at("/silent"));
        await(() -> asked.contains("/silent"), "the first request");
        var fresh = fetcher.fetch(at("/google"));
        assertThat(stale.get(5, TimeUnit.SECONDS)).as("superseded").isEmpty();
        release.countDown();
        assertThat(fresh.get(5, TimeUnit.SECONDS)).containsExactly("jcef", "jcef maven", "café");
        assertThat(stale.get()).as("the late answer is dropped").isEmpty();
    }

    @Test void offlineOrClosedGivesNoSuggestions() throws Exception {
        fetcher = new SuggestionFetcher(STARTS, TIMEOUT);
        int closedPort;
        try (var socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }
        assertThat(fetcher.fetch(URI.create("http://127.0.0.1:" + closedPort + "/q")).get(5, TimeUnit.SECONDS)).as("no server").isEmpty();
        for (String address : List.of("file:///etc/hosts", "jasper-about:welcome", "http://ada:secret@127.0.0.1/q"))
            assertThat(fetcher.fetch(URI.create(address)).get(5, TimeUnit.SECONDS)).as(address).isEmpty();
        var waiting = fetcher.fetch(at("/silent"));
        await(() -> asked.contains("/silent"), "the request");
        fetcher.close();
        assertThat(waiting.get(5, TimeUnit.SECONDS)).as("the fetch under way").isEmpty();
        assertThat(fetcher.fetch(at("/google")).get(5, TimeUnit.SECONDS)).isEmpty();
        assertThat(asked).containsExactly("/silent");
    }

    @Test void readingTakesOnlyAnArrayWhoseSecondMemberListsStrings() {
        assertThat(SuggestionFetcher.read("[\"q\",[\"a\",\" b \"]]")).containsExactly("a", "b");
        assertThat(SuggestionFetcher.read("[\"q\",[]]")).isEmpty();
        assertThat(SuggestionFetcher.read("[\"q\",[[\"nested\"]]]")).isEmpty();
        assertThat(SuggestionFetcher.read("[\"q\"," + "[".repeat(100_000))).as("no stack overflow").isEmpty();
    }

    private URI at(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    private void handle(String path, HttpHandler handler) {
        server.createContext(path, exchange -> {
            asked.add(exchange.getRequestURI().toString());
            try {
                handler.handle(exchange);
            } catch (IOException gone) {
                // The client gave up, as it does on its timeout or when a request is superseded.
            } finally {
                exchange.close();
            }
        });
    }

    private void hold() {
        try {
            release.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void answer(HttpExchange exchange, int status, String type, String body, Charset charset) throws IOException {
        byte[] bytes = body.getBytes(charset);
        if (!type.isEmpty()) exchange.getResponseHeaders().add("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private interface Check {
        boolean holds() throws Exception;
    }

    private static void await(Check check, String what) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!check.holds()) {
            if (System.nanoTime() > deadline) throw new AssertionError("timed out waiting for " + what);
            Thread.sleep(10);
        }
    }
}
