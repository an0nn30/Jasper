package dev.jasper.browser.adblock;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.jasper.browser.json.StrictJson;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ad blocking's filter list (spec section 12, plan 6 Task 10): downloaded from a local HTTP server, compiled, cached
 * in the data folder and loaded from there without the network, kept when an update fails, updated once 24 hours
 * have passed or the address changed, and stale from the seventh day. Every list here is written by hand (F36), and
 * no test reaches beyond this computer (F9): the server listens on the loopback address alone, and a list that must
 * not be downloaded gets a client that fails the test if it is asked for.
 */
class FilterListTest {
    /** Three rules Jasper keeps and one it skips, written by hand. */
    private static final String LIST = """
        [{"trigger": {"url-filter": "ads.example/"}, "action": {"type": "block"}},
         {"trigger": {"url-filter": "tracker.example/", "load-type": ["third-party"]}, "action": {"type": "block"}},
         {"trigger": {"url-filter": ".*", "if-domain": ["news.example"]}, "action": {"type": "css-display-none", "selector": ".banner"}},
         {"trigger": {"url-filter": "upgrade"}, "action": {"type": "make-https"}}]
        """;
    /** Another list, of two rules, at another address. */
    private static final String OTHER = """
        [{"trigger": {"url-filter": "pixel.example/"}, "action": {"type": "block"}},
         {"trigger": {"url-filter": "beacon.example/"}, "action": {"type": "block"}}]
        """;
    private static final Instant START = Instant.parse("2026-11-09T10:00:00Z");
    private static final Duration DEADLINE = Duration.ofMillis(700);
    @TempDir Path data;
    private final CountDownLatch release = new CountDownLatch(1), late = new CountDownLatch(1);
    private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
    private final List<Boolean> restricted = new CopyOnWriteArrayList<>();
    private final MutableClock clock = new MutableClock(START);
    private final AtomicReference<URI> address = new AtomicReference<>();
    private final List<HttpClient> made = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private FilterList filters;

    @BeforeEach void serve() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        handle("/list.json", exchange -> answer(exchange, 200, LIST));
        handle("/other.json", exchange -> answer(exchange, 200, OTHER));
        handle("/error", exchange -> answer(exchange, 500, "broken"));
        handle("/text", exchange -> answer(exchange, 200, "this is not json"));
        handle("/moved", exchange -> {
            exchange.getResponseHeaders().add("Location", "/list.json");
            answer(exchange, 302, "");
        });
        handle("/huge", exchange -> {
            // A body of no announced length, more than 32 MiB of it, which the list must stop reading at its bound.
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream body = exchange.getResponseBody()) {
                byte[] block = new byte[64 * 1024];
                java.util.Arrays.fill(block, (byte) ' ');
                for (long sent = 0; sent <= 33L * 1024 * 1024; sent += block.length) body.write(block);
            } catch (IOException stopped) {
                // The client stopped reading.
            }
        });
        handle("/announced", exchange -> {
            // A body announced as more than 32 MiB, which the list must refuse before reading it.
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
            // The list, once the test lets it come.
            try {
                late.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            answer(exchange, 200, LIST);
        });
        handle("/hold", exchange -> {
            // Headers at once, then a body that trickles until the test ends: only a deadline on the whole download ends it.
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
        address.set(at("/list.json"));
    }

    @AfterEach void stop() {
        release.countDown();
        late.countDown();
        if (filters != null) filters.close();
        server.stop(0);
        ((ExecutorService) server.getExecutor()).shutdownNow();
        for (HttpClient client : made) client.shutdownNow();
    }

    @Test void theListIsDownloadedCompiledAndCachedWithItsCount() throws Exception {
        filters = list(this::client);
        filters.enabled(true);
        filters.start();
        FilterList.Status status = filters.updateNow().get(10, TimeUnit.SECONDS);
        assertThat(status).isEqualTo(new FilterList.Status(3, Optional.of(START), false, Optional.empty()));
        assertThat(filters.status()).isEqualTo(status);
        assertThat(hits("/list.json")).as("one download, which Update Now joined").isEqualTo(1);
        RuleSet rules = filters.active().orElseThrow();
        assertThat(rules.size()).isEqualTo(3);
        assertThat(rules.blocks("https://ads.example/banner.js", Set.of(ResourceKind.SCRIPT), "https://news.example/")).isTrue();
        assertThat(rules.stylesheet("https://news.example/")).isEqualTo(".banner{display:none!important}\n");

        Path folder = data.resolve("adblock");
        assertThat(restricted).as("H37: the data folder is its owner's before the list's folder is made (F11)").first().isEqualTo(false);
        Map<String, Object> state = StrictJson.object(Files.readString(folder.resolve("state.json")), 1).orElseThrow();
        assertThat(state).containsExactly(Map.entry("url", at("/list.json").toString()), Map.entry("updated", START.toString()),
            Map.entry("rules", 3L), Map.entry("skipped", 1L));
        var cached = ContentRules.compile(BlockerJson.read(Files.readString(folder.resolve("rules.json"))));
        assertThat(cached.rules()).as("the compiled list, as encode writes it").hasSize(3);
        assertThat(cached.skipped()).isZero();
        try (var files = Files.list(folder)) {
            assertThat(files.map(file -> file.getFileName().toString()).sorted().toList()).as("no temporary file is left")
                .containsExactly("rules.json", "state.json");
        }
        if (Files.getFileAttributeView(folder, PosixFileAttributeView.class) != null)
            for (String name : List.of("rules.json", "state.json"))
                assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(folder.resolve(name)))).as(name).isEqualTo("rw-------");
    }

    @Test void aNewListLoadsTheCacheWithoutTheNetwork() throws Exception {
        filters = list(this::client);
        filters.updateNow().get(10, TimeUnit.SECONDS);
        assertThat(filters.active()).as("turned off, a downloaded list is not applied").isEmpty();
        filters.close();

        var asked = new AtomicInteger();
        clock.advance(Duration.ofHours(23));
        filters = list(() -> {
            asked.incrementAndGet();
            throw new AssertionError("The cache is loaded without the network");
        });
        assertThat(filters.status()).as("nothing is read before it starts").isEqualTo(new FilterList.Status(0, Optional.empty(), false, Optional.empty()));
        filters.enabled(true);
        assertThat(filters.active()).as("turned on before it starts, it reads nothing (F8)").isEmpty();
        filters.start();
        assertThat(filters.active()).isPresent().get().extracting(RuleSet::size).isEqualTo(3);
        assertThat(filters.status()).isEqualTo(new FilterList.Status(3, Optional.of(START), false, Optional.empty()));
        assertThat(asked).hasValue(0);
        assertThat(hits("/list.json")).isEqualTo(1);
    }

    @Test void aFailedUpdateKeepsTheOldListAndRecordsWhy() throws Exception {
        filters = list(this::client, Duration.ofHours(1), DEADLINE);
        filters.enabled(true);
        filters.start();
        filters.updateNow().get(10, TimeUnit.SECONDS);
        RuleSet kept = filters.active().orElseThrow();
        String state = Files.readString(data.resolve("adblock/state.json"));
        Map<String, String> failures = new java.util.LinkedHashMap<>();
        failures.put("/error", "the server answered HTTP 500");
        failures.put("/text", "Not a content blocker list: not a JSON array");
        failures.put("/huge", "the list is larger than 32 MiB");
        failures.put("/announced", "the list is larger than 32 MiB");
        failures.put("/hold", "the download took longer than 0.7 seconds");
        for (var failure : failures.entrySet()) {
            address.set(at(failure.getKey()));
            FilterList.Status status = filters.updateNow().get(20, TimeUnit.SECONDS);
            assertThat(status.error()).as(failure.getKey()).contains(failure.getValue());
            assertThat(status.rules()).as(failure.getKey()).isEqualTo(3);
            assertThat(status.updated()).as(failure.getKey()).contains(START);
            assertThat(status.updating()).isFalse();
            assertThat(filters.active()).as(failure.getKey()).containsSame(kept);
            assertThat(Files.readString(data.resolve("adblock/state.json"))).as("the cache is the old list's").isEqualTo(state);
        }
        address.set(at("/other.json"));
        assertThat(filters.updateNow().get(10, TimeUnit.SECONDS)).as("a success forgets the failure")
            .isEqualTo(new FilterList.Status(2, Optional.of(START), false, Optional.empty()));
        assertThat(filters.active().orElseThrow().size()).isEqualTo(2);
    }

    /** F10: redirects are followed, within http and https, and only the final answer's body is read. */
    @Test void aRedirectIsFollowedToTheList() throws Exception {
        address.set(at("/moved"));
        filters = list(this::client);
        filters.enabled(true);
        assertThat(filters.updateNow().get(10, TimeUnit.SECONDS).rules()).isEqualTo(3);
        assertThat(hits("/moved")).isEqualTo(1);
        assertThat(hits("/list.json")).isEqualTo(1);
    }

    /** F10: the list comes over https, or over http only from this computer; anything else asks for no client. */
    @Test void anAddressThatIsNotHttpsOrThisComputersIsRefusedWithoutAClient() throws Exception {
        var asked = new AtomicInteger();
        Supplier<HttpClient> refused = () -> {
            asked.incrementAndGet();
            throw new AssertionError("No client for a refused address");
        };
        for (String refusedAddress : List.of("http://lists.example/list.json", "ftp://lists.example/list.json", "https://user@lists.example/list.json")) {
            address.set(URI.create(refusedAddress));
            filters = list(refused);
            assertThat(filters.updateNow().get(10, TimeUnit.SECONDS).error()).as(refusedAddress)
                .contains("the filter list's address is not an https address, or an http one on this computer");
            filters.close();
        }
        assertThat(asked).hasValue(0);
        for (String fetched : List.of("https://lists.example/list.json", "HTTPS://lists.example/", "http://127.0.0.1:8080/list.json",
                "http://localhost/list.json", "http://[::1]:8080/list.json", "http://127.1.2.3/"))
            assertThat(FilterList.fetches(URI.create(fetched))).as(fetched).isTrue();
        for (String other : List.of("http://lists.example/", "http://app.localhost/", "http://localhost.example/", "https:///list.json",
                "https://user:secret@lists.example/", "file:///tmp/list.json", "ws://lists.example/", "jasper-about:welcome"))
            assertThat(FilterList.fetches(URI.create(other))).as(other).isFalse();
    }

    @Test void anUpdateIsDueAfter24HoursOrFromAnotherAddress() throws Exception {
        filters = list(this::client);
        filters.enabled(true);
        filters.start();
        filters.updateNow().get(10, TimeUnit.SECONDS);
        assertThat(hits("/list.json")).isEqualTo(1);
        clock.advance(Duration.ofHours(23));
        filters.checkNow();
        assertThat(filters.status().updating()).as("not due at 23 hours").isFalse();
        assertThat(hits("/list.json")).isEqualTo(1);
        clock.advance(Duration.ofHours(1));
        filters.checkNow();
        assertThat(filters.status().updating()).as("due at 24 hours").isTrue();
        assertThat(filters.updateNow().get(10, TimeUnit.SECONDS).updated()).contains(START.plus(Duration.ofHours(24)));
        assertThat(hits("/list.json")).isEqualTo(2);
        address.set(at("/other.json"));
        filters.checkNow();
        assertThat(filters.status().updating()).as("due at once from another address").isTrue();
        assertThat(filters.updateNow().get(10, TimeUnit.SECONDS).rules()).isEqualTo(2);
        assertThat(hits("/other.json")).isEqualTo(1);
    }

    @Test void nothingIsCheckedBeforeItStartsOrWhileTurnedOff() throws Exception {
        filters = list(this::client);
        filters.enabled(true);
        filters.checkNow();
        assertThat(filters.status().updating()).as("before it starts").isFalse();
        filters.enabled(false);
        filters.start();
        filters.checkNow();
        assertThat(filters.status().updating()).as("turned off").isFalse();
        assertThat(hits("/list.json")).isZero();
        assertThat(made).isEmpty();
        filters.enabled(true);
        assertThat(filters.status().updating()).as("turned on once started: nothing is cached, so it downloads").isTrue();
        filters.updateNow().get(10, TimeUnit.SECONDS);
        assertThat(hits("/list.json")).isEqualTo(1);
    }

    @Test void theHourlyCheckRunsByItself() throws Exception {
        filters = list(this::client, Duration.ofMillis(20), DEADLINE);
        filters.enabled(true);
        filters.start();
        filters.updateNow().get(10, TimeUnit.SECONDS);
        clock.advance(Duration.ofHours(25));
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (hits("/list.json") < 2 && System.nanoTime() < until) Thread.sleep(10);
        assertThat(hits("/list.json")).as("a check after 24 hours downloads by itself").isEqualTo(2);
    }

    @Test void staleDaysCountFromTheSeventhDay() throws Exception {
        filters = list(this::client);
        assertThat(filters.staleDays(START.plus(Duration.ofDays(30)))).as("never updated").isEmpty();
        filters.updateNow().get(10, TimeUnit.SECONDS);
        assertThat(filters.staleDays(START.plus(Duration.ofDays(6)))).isEmpty();
        assertThat(filters.staleDays(START.plus(Duration.ofDays(7)).minusSeconds(1))).isEmpty();
        assertThat(filters.staleDays(START.plus(Duration.ofDays(7)))).isEqualTo(OptionalInt.of(7));
        assertThat(filters.staleDays(START.plus(Duration.ofDays(9).plusHours(12)))).isEqualTo(OptionalInt.of(9));
    }

    @Test void turnedOffTheListIsAppliedNoMore() throws Exception {
        filters = list(this::client);
        filters.enabled(true);
        filters.start();
        filters.updateNow().get(10, TimeUnit.SECONDS);
        assertThat(filters.active()).isPresent();
        var told = new AtomicInteger();
        filters.onChanged(told::incrementAndGet);
        filters.enabled(false);
        assertThat(filters.active()).isEmpty();
        assertThat(filters.status().rules()).as("the cached list's count stays").isEqualTo(3);
        assertThat(told.get()).as("the change is told").isPositive();
        filters.enabled(true);
        assertThat(filters.active()).as("loaded again from its cache").isPresent();
        assertThat(hits("/list.json")).isEqualTo(1);

        address.set(at("/late"));
        clock.advance(Duration.ofDays(1));
        filters.checkNow();
        assertThat(filters.status().updating()).isTrue();
        filters.enabled(false);
        late.countDown();
        assertThat(filters.updateNow().get(20, TimeUnit.SECONDS).updated()).as("it is cached").contains(START.plus(Duration.ofDays(1)));
        assertThat(filters.active()).as("a download that ends while turned off applies nothing").isEmpty();
    }

    @Test void closingEndsTheChecksTheDownloadAndTheClient() throws Exception {
        address.set(at("/hold"));
        filters = list(this::client, Duration.ofMillis(20), Duration.ofSeconds(30));
        filters.enabled(true);
        filters.start();
        var download = filters.updateNow();
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (hits("/hold") == 0 && System.nanoTime() < until) Thread.sleep(10);
        filters.close();
        assertThat(download.get(10, TimeUnit.SECONDS).updating()).as("the download ends with the close").isFalse();
        assertThat(filters.active()).isEmpty();
        assertThat(made).singleElement().satisfies(client -> {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!client.isTerminated() && System.nanoTime() < deadline) Thread.sleep(10);
            assertThat(client.isTerminated()).as("the client is shut down").isTrue();
        });
        address.set(at("/list.json"));
        clock.advance(Duration.ofDays(2));
        filters.enabled(true);
        filters.start();
        filters.checkNow();
        assertThat(filters.updateNow().get(10, TimeUnit.SECONDS).updating()).isFalse();
        Thread.sleep(100);
        assertThat(hits("/list.json")).as("a closed list downloads nothing, hourly or asked").isZero();
    }

    /** A cache that cannot be read is no cache: the list downloads again. */
    @Test void aCacheThatCannotBeReadIsDownloadedAgain() throws Exception {
        Files.createDirectories(data.resolve("adblock"));
        Files.writeString(data.resolve("adblock/state.json"), "{\"url\": 1}");
        Files.writeString(data.resolve("adblock/rules.json"), "[");
        filters = list(this::client);
        filters.enabled(true);
        filters.start();
        assertThat(filters.status().updating()).isTrue();
        assertThat(filters.updateNow().get(10, TimeUnit.SECONDS).rules()).isEqualTo(3);
    }

    private FilterList list(Supplier<HttpClient> clients) {
        return list(clients, Duration.ofHours(1), DEADLINE);
    }

    private FilterList list(Supplier<HttpClient> clients, Duration every, Duration deadline) {
        return new FilterList(data, () -> restricted.add(Files.exists(data.resolve("adblock"))), Runnable::run, clients, clock,
            address::get, every, deadline);
    }

    /** The list's client, as the plugin makes it, kept to be shut down. */
    private HttpClient client() {
        HttpClient client = FilterList.client();
        made.add(client);
        return client;
    }

    private int hits(String path) {
        return hits.getOrDefault(path, new AtomicInteger()).get();
    }

    private URI at(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    private void handle(String path, com.sun.net.httpserver.HttpHandler handler) {
        server.createContext(path, exchange -> {
            hits.computeIfAbsent(path, any -> new AtomicInteger()).incrementAndGet();
            handler.handle(exchange);
        });
    }

    private static void answer(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** A clock the test moves. */
    private static final class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant start) {
            now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override public Instant instant() {
            return now;
        }
    }
}
