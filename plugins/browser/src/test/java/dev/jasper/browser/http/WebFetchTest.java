package dev.jasper.browser.http;

import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plan 6 Task 11 and ruling F3: the plugin's own fetches go through the HTTP proxy they are given, the JDK's default
 * proxy selector otherwise, and their client is made again when the proxy changes. A loopback stand-in plays the proxy
 * and another the web server, so nothing leaves this computer.
 */
class WebFetchTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Test void fetchesGoThroughTheProxyTheyAreGivenAndTheJdksDefaultOtherwise() throws Exception {
        try (var origin = new StandIn(); var proxy = new StandIn(); var web = new WebFetch(Runnable::run, TIMEOUT, 1024)) {
            URI icon = URI.create("http://127.0.0.1:" + origin.port() + "/icon.png");
            assertThat(body(web.get(icon, "image/*"))).isEqualTo(StandIn.BODY);
            assertThat(origin.next()).as("the JDK's default: direct to this computer").isEqualTo("GET /icon.png HTTP/1.1");
            web.useProxy(Optional.of(proxy.address()));
            assertThat(body(web.get(icon, "image/*"))).isEqualTo(StandIn.BODY);
            assertThat(proxy.next()).as("a new client, through the proxy, which names the whole address").isEqualTo("GET " + icon + " HTTP/1.1");
            web.useProxy(Optional.empty());
            assertThat(body(web.get(icon, "image/*"))).isEqualTo(StandIn.BODY);
            assertThat(origin.next()).isEqualTo("GET /icon.png HTTP/1.1");
            assertThat(origin.asked() || proxy.asked()).as("each fetch asked once").isFalse();
        }
    }

    /** A fetch under way as the proxy changes ends at once and gives nothing: nothing more goes the old way. */
    @Test void aFetchUnderWayAsTheProxyChangesEndsAndGivesNothing() throws Exception {
        try (var origin = new StandIn(); var proxy = new StandIn(); var web = new WebFetch(Runnable::run, TIMEOUT, 1024)) {
            origin.holds();
            URI page = URI.create("http://127.0.0.1:" + origin.port() + "/page");
            CompletableFuture<Optional<HttpResponse<byte[]>>> held = web.get(page, "text/html");
            assertThat(origin.next()).isEqualTo("GET /page HTTP/1.1");
            long began = System.nanoTime();
            web.useProxy(Optional.of(proxy.address()));
            assertThat(held.get(10, TimeUnit.SECONDS)).as("ended, never answered").isEmpty();
            assertThat(Duration.ofNanos(System.nanoTime() - began)).as("at once, not at the timeout").isLessThan(Duration.ofSeconds(4));
            assertThat(body(web.get(page, "text/html"))).isEqualTo(StandIn.BODY);
            assertThat(proxy.next()).isEqualTo("GET " + page + " HTTP/1.1");
        }
    }

    /** What a fetch's answer says, read as UTF-8; null for no answer. */
    private static String body(CompletableFuture<Optional<HttpResponse<byte[]>>> fetch) throws Exception {
        return fetch.get(10, TimeUnit.SECONDS).map(response -> new String(response.body(), StandardCharsets.UTF_8)).orElse(null);
    }
}
