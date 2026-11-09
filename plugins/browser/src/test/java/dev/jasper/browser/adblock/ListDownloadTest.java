package dev.jasper.browser.adblock;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * A filter list's download (ruling F10), against a server on the loopback address alone (F9): only https, or http on
 * this computer, is fetched; only a final 200 is read, whole and at most 32 MiB of it, counted before decoding; one
 * deadline covers the whole download, its body included; and each failure has the reason Settings shows.
 */
class ListDownloadTest {
    private static final Duration DEADLINE = Duration.ofMillis(700);
    private ListServer server;
    private HttpClient client;

    @BeforeEach void serve() throws Exception {
        server = new ListServer();
        client = ListDownload.client();
    }

    @AfterEach void stop() {
        server.close();
        client.shutdownNow();
    }

    @Test void aFinal200IsReadWhole() throws Exception {
        byte[] body = ListDownload.get(client, server.at("/list.json"), DEADLINE).get(10, TimeUnit.SECONDS);
        assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo(ListServer.LIST);
    }

    @Test void aRedirectIsFollowedToTheList() throws Exception {
        byte[] body = ListDownload.get(client, server.at("/moved"), DEADLINE).get(10, TimeUnit.SECONDS);
        assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo(ListServer.LIST);
        assertThat(server.hits("/moved")).isEqualTo(1);
        assertThat(server.hits("/list.json")).isEqualTo(1);
    }

    /**
     * Ruling V38: a redirect's target is checked before it is requested, so an address the list may not come from is
     * never asked: here {@code localhost.}, which {@link ListDownload#fetches} refuses, on this same server.
     */
    @Test void aRedirectToAnAddressTheListMayNotComeFromIsNeverRequested() throws Exception {
        server.handle("/to-dot", exchange -> ListServer.redirect(exchange, "http://localhost.:" + server.port() + "/list.json"));
        assertThat(ListDownload.fetches(URI.create("http://localhost.:" + server.port() + "/list.json"))).isFalse();
        assertThat(reason("/to-dot")).isEqualTo("the filter list was redirected to an address that is not https");
        assertThat(server.hits("/to-dot")).isEqualTo(1);
        assertThat(server.hits("/list.json")).as("the refused target is never asked").isZero();
    }

    /** Ruling V38: at most 5 redirects are followed; the sixth's target is never asked. */
    @Test void atMostFiveRedirectsAreFollowed() throws Exception {
        for (int hop = 1; hop <= 6; hop++) {
            String next = hop == 1 ? "/list.json" : "/chain/" + (hop - 1);
            server.handle("/chain/" + hop, exchange -> ListServer.redirect(exchange, next));
        }
        byte[] body = ListDownload.get(client, server.at("/chain/5"), DEADLINE).get(10, TimeUnit.SECONDS);
        assertThat(new String(body, StandardCharsets.UTF_8)).as("five redirects").isEqualTo(ListServer.LIST);
        assertThat(server.hits("/list.json")).isEqualTo(1);
        assertThat(reason("/chain/6")).isEqualTo("the filter list was redirected more than 5 times");
        assertThat(server.hits("/list.json")).as("the sixth redirect's target is never asked").isEqualTo(1);
        assertThat(server.hits("/chain/1")).as("each hop once for each download").isEqualTo(2);
    }

    /** Ruling V38: a relative {@code Location} is resolved against the address that answered it. */
    @Test void aRelativeLocationIsFollowed() throws Exception {
        server.handle("/nested/move", exchange -> ListServer.redirect(exchange, "next.json"));
        server.handle("/nested/next.json", exchange -> ListServer.answer(exchange, 200, ListServer.OTHER));
        byte[] body = ListDownload.get(client, server.at("/nested/move"), DEADLINE).get(10, TimeUnit.SECONDS);
        assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo(ListServer.OTHER);
        server.handle("/nowhere", exchange -> ListServer.answer(exchange, 302, ""));
        assertThat(reason("/nowhere")).as("a redirect without a Location").isEqualTo("the server answered HTTP 302");
    }

    /** Each way a download fails, with the reason Settings shows after "Could not update the filter list: ". */
    @Test void eachFailureHasItsReason() throws Exception {
        assertThat(reason("/error")).isEqualTo("the server answered HTTP 500");
        assertThat(reason("/huge")).as("streamed past 32 MiB").isEqualTo("the list is larger than 32 MiB");
        assertThat(reason("/announced")).as("announced as more than 32 MiB").isEqualTo("the list is larger than 32 MiB");
        long started = System.nanoTime();
        assertThat(reason("/hold")).as("a body that trickles: one deadline on the whole download").isEqualTo("the download took longer than 0.7 seconds");
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(10));
        int closed;
        try (var socket = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            closed = socket.getLocalPort();
        }
        URI nobody = URI.create("http://127.0.0.1:" + closed + "/list.json");
        assertThat(reason(nobody)).isEqualTo("could not connect to 127.0.0.1");
        assertThat(reason(URI.create("http://lists.example/list.json")))
            .isEqualTo("the filter list's address is not an https address, or an http one on this computer");
    }

    /** The list comes over https, or over http only from this computer. */
    @Test void onlyHttpsOrThisComputersHttpIsFetched() {
        for (String fetched : List.of("https://lists.example/list.json", "HTTPS://lists.example/", "http://127.0.0.1:8080/list.json",
                "http://localhost/list.json", "http://[::1]:8080/list.json", "http://127.1.2.3/"))
            assertThat(ListDownload.fetches(URI.create(fetched))).as(fetched).isTrue();
        for (String other : List.of("http://lists.example/", "http://app.localhost/", "http://localhost.example/", "http://localhost./",
                "https:///list.json", "https://user:secret@lists.example/", "file:///tmp/list.json", "ws://lists.example/", "jasper-about:welcome"))
            assertThat(ListDownload.fetches(URI.create(other))).as(other).isFalse();
        assertThat(ListDownload.fetches(null)).isFalse();
    }

    private String reason(String path) {
        return reason(server.at(path));
    }

    private String reason(URI address) {
        CompletableFuture<byte[]> download = ListDownload.get(client, address, DEADLINE);
        Throwable failure = catchThrowable(() -> download.get(20, TimeUnit.SECONDS));
        assertThat(failure).as(String.valueOf(address)).isInstanceOf(ExecutionException.class);
        return ListDownload.reason(failure, address, DEADLINE);
    }
}
