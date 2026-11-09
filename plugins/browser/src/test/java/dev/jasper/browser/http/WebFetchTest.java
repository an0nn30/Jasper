package dev.jasper.browser.http;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plan 6 Task 11 and rulings F3 and V43: the plugin's own fetches go through the HTTP proxy they are given, but for the
 * hosts Chromium never sends through one, the JDK's default proxy selector otherwise, and their client is made again
 * when the proxy changes. Loopback stand-ins play the proxies and the web server: a name under {@code .invalid} reaches
 * a stand-in proxy without being looked up, and nothing leaves this computer.
 */
class WebFetchTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Test void fetchesGoThroughTheProxyTheyAreGivenButLoopbackGoesDirect() throws Exception {
        try (var origin = new StandIn(); var proxy = new StandIn(); var web = new WebFetch(Runnable::run, TIMEOUT, 1024)) {
            URI local = URI.create("http://127.0.0.1:" + origin.port() + "/icon.png");
            URI icon = URI.create("http://icon.invalid/icon.png");
            assertThat(body(web.get(local, "image/*"))).isEqualTo(StandIn.BODY);
            assertThat(origin.next()).as("the JDK's default: direct to this computer").isEqualTo("GET /icon.png HTTP/1.1");
            web.useProxy(Optional.of(proxy.address()));
            assertThat(body(web.get(icon, "image/*"))).isEqualTo(StandIn.BODY);
            assertThat(proxy.next()).as("a new client, through the proxy, which names the whole address").isEqualTo("GET " + icon + " HTTP/1.1");
            assertThat(body(web.get(URI.create("https://icon.invalid/icon.png"), "image/*"))).as("no TLS from a stand-in").isNull();
            assertThat(proxy.next()).as("https through the proxy's tunnel").isEqualTo("CONNECT icon.invalid:443 HTTP/1.1");
            assertThat(body(web.get(local, "image/*"))).isEqualTo(StandIn.BODY);
            assertThat(origin.next()).as("V43: loopback goes direct, as Chromium sends it").isEqualTo("GET /icon.png HTTP/1.1");
            web.useProxy(Optional.empty());
            assertThat(body(web.get(local, "image/*"))).isEqualTo(StandIn.BODY);
            assertThat(origin.next()).isEqualTo("GET /icon.png HTTP/1.1");
            assertThat(origin.asked() || proxy.asked()).as("each fetch asked once").isFalse();
        }
    }

    /** A fetch under way as the proxy changes ends at once and gives nothing: nothing more goes the old way. */
    @Test void aFetchUnderWayAsTheProxyChangesEndsAndGivesNothing() throws Exception {
        try (var first = new StandIn(); var second = new StandIn(); var web = new WebFetch(Runnable::run, TIMEOUT, 1024)) {
            first.holds();
            URI page = URI.create("http://page.invalid/page");
            web.useProxy(Optional.of(first.address()));
            CompletableFuture<Optional<HttpResponse<byte[]>>> held = web.get(page, "text/html");
            assertThat(first.next()).isEqualTo("GET " + page + " HTTP/1.1");
            long began = System.nanoTime();
            web.useProxy(Optional.of(second.address()));
            assertThat(held.get(10, TimeUnit.SECONDS)).as("ended, never answered").isEmpty();
            assertThat(Duration.ofNanos(System.nanoTime() - began)).as("at once, not at the timeout").isLessThan(Duration.ofSeconds(4));
            assertThat(body(web.get(page, "text/html"))).isEqualTo(StandIn.BODY);
            assertThat(second.next()).isEqualTo("GET " + page + " HTTP/1.1");
        }
    }

    /**
     * The review's M4: a client made while the proxy changes is made again, with the new proxy, and the one made with
     * the old is shut down, so no fetch goes the old way.
     */
    @Test void aClientMadeWhileTheProxyChangesIsMadeAgainWithTheNewOne() throws Exception {
        try (var old = new StandIn(); var now = new StandIn()) {
            var made = new CopyOnWriteArrayList<HttpClient>();
            var asked = new CopyOnWriteArrayList<Optional<InetSocketAddress>>();
            var web = new AtomicReference<WebFetch>();
            web.set(new WebFetch(Runnable::run, TIMEOUT, 1024, through -> {
                asked.add(through);
                HttpClient client = WebFetch.newClient(TIMEOUT, through);
                made.add(client);
                if (made.size() == 1) web.get().useProxy(Optional.of(now.address()));
                return client;
            }));
            try (WebFetch fetch = web.get()) {
                fetch.useProxy(Optional.of(old.address()));
                URI icon = URI.create("http://icon.invalid/icon.png");
                assertThat(body(fetch.get(icon, "image/*"))).isEqualTo(StandIn.BODY);
                assertThat(now.next()).isEqualTo("GET " + icon + " HTTP/1.1");
                assertThat(asked).containsExactly(Optional.of(old.address()), Optional.of(now.address()));
                assertThat(made.getFirst().awaitTermination(Duration.ofSeconds(10))).as("the old proxy's client, shut down").isTrue();
                assertThat(old.asked()).isFalse();
                fetch.useProxy(Optional.empty());
                assertThat(body(fetch.get(URI.create("http://127.0.0.1:" + now.port() + "/local"), "*/*"))).isEqualTo(StandIn.BODY);
                assertThat(asked).as("made again for the JDK's default").endsWith(Optional.empty());
                assertThat(now.next()).isEqualTo("GET /local HTTP/1.1");
            } finally {
                made.forEach(HttpClient::shutdownNow);
            }
        }
    }

    /** What a fetch's answer says, read as UTF-8; null for no answer. */
    private static String body(CompletableFuture<Optional<HttpResponse<byte[]>>> fetch) throws Exception {
        return fetch.get(10, TimeUnit.SECONDS).map(response -> new String(response.body(), StandardCharsets.UTF_8)).orElse(null);
    }
}
