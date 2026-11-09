package dev.jasper.browser.adblock;

import dev.jasper.browser.http.StandIn;
import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
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
    /** V53: only {@code /hold}, whose body trickles, has a short deadline; every other download a generous one. */
    private static final Duration HOLD = Duration.ofMillis(700), GENEROUS = Duration.ofSeconds(30);
    private static final BooleanSupplier CURRENT = () -> true;
    private ListServer server;
    private HttpClient client;

    @BeforeEach void serve() throws Exception {
        server = new ListServer();
        client = ListDownload.client(Optional.empty());
    }

    @AfterEach void stop() {
        server.close();
        client.shutdownNow();
    }

    @Test void aFinal200IsReadWhole() throws Exception {
        byte[] body = get(client, server.at("/list.json")).get(10, TimeUnit.SECONDS);
        assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo(ListServer.LIST);
    }

    /**
     * Plan 6 Task 11 and rulings F3 and V43: the list's client goes through the HTTP proxy it is given, which a loopback
     * stand-in plays here: an https list through its tunnel, named and never looked up. A list on this computer goes
     * direct, as Chromium sends loopback, so its bytes never leave the computer (F10), and the proxy is never asked.
     */
    @Test void theClientGoesThroughTheProxyItIsGivenButALoopbackListGoesDirect() throws Exception {
        try (var proxy = new StandIn()) {
            HttpClient proxied = ListDownload.client(Optional.of(proxy.address()));
            try {
                Throwable failed = catchThrowable(() -> get(proxied, URI.create("https://list.invalid/list.json")).get(10, TimeUnit.SECONDS));
                assertThat(proxy.next()).isEqualTo("CONNECT list.invalid:443 HTTP/1.1");
                assertThat(failed).as("no TLS from a stand-in").isInstanceOf(ExecutionException.class);
                byte[] body = get(proxied, server.at("/list.json")).get(10, TimeUnit.SECONDS);
                assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo(ListServer.LIST);
                assertThat(server.hits("/list.json")).as("direct to this computer").isEqualTo(1);
                assertThat(proxy.asked()).as("the proxy, never").isFalse();
            } finally {
                proxied.shutdownNow();
            }
        }
    }

    /**
     * Final review B (V51): with the proxy off, the list's client still has a selector of its own: this computer goes
     * direct however its address is written, and everything else as the JDK's default selector routes it, which a
     * recording stand-in plays here, as a JVM run with {@code -Dhttp.proxyHost} has one.
     */
    @Test void withTheProxyOffThisComputerGoesDirectAndAnythingElseAsTheJdksDefaultRoutesIt() throws Exception {
        ProxySelector before = ProxySelector.getDefault();
        try (var proxy = new StandIn()) {
            var asked = new CopyOnWriteArrayList<URI>();
            List<Proxy> through = List.of(new Proxy(Proxy.Type.HTTP, proxy.address()));
            ProxySelector.setDefault(new ProxySelector() {
                @Override public List<Proxy> select(URI uri) {
                    asked.add(uri);
                    return through;
                }

                @Override public void connectFailed(URI uri, SocketAddress address, IOException failure) { }
            });
            HttpClient off = ListDownload.client(Optional.empty());
            try {
                ProxySelector selector = off.proxy().orElseThrow();
                for (String local : List.of("http://127.0.0.1:%d/list.json", "http://localhost:%d/list.json", "http://[::1]:%d/list.json",
                        "http://[0:0:0:0:0:0:0:1]:%d/list.json", "http://[::ffff:127.0.0.1]:%d/list.json", "http://127.1.2.3:%d/list.json"))
                    assertThat(selector.select(URI.create(local.formatted(server.port())))).as(local).containsExactly(Proxy.NO_PROXY);
                assertThat(asked).as("the default selector is never asked for this computer").isEmpty();
                assertThat(selector.select(URI.create("https://lists.example/list.json"))).isEqualTo(through);
                assertThat(asked).containsExactly(URI.create("https://lists.example/list.json"));
                asked.clear();
                byte[] body = get(off, URI.create("http://[::ffff:127.0.0.1]:" + server.port() + "/list.json")).get(10, TimeUnit.SECONDS);
                assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo(ListServer.LIST);
                assertThat(server.hits("/list.json")).as("direct to this computer").isEqualTo(1);
                assertThat(proxy.asked()).as("the JVM's proxy, never").isFalse();
                assertThat(asked).isEmpty();
            } finally {
                off.shutdownNow();
            }
        } finally {
            ProxySelector.setDefault(before);
        }
    }

    @Test void aRedirectIsFollowedToTheList() throws Exception {
        byte[] body = get(client, server.at("/moved")).get(10, TimeUnit.SECONDS);
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
        byte[] body = get(client, server.at("/chain/5")).get(10, TimeUnit.SECONDS);
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
        byte[] body = get(client, server.at("/nested/move")).get(10, TimeUnit.SECONDS);
        assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo(ListServer.OTHER);
        server.handle("/nowhere", exchange -> ListServer.answer(exchange, 302, ""));
        assertThat(reason("/nowhere")).as("a redirect without a Location").isEqualTo("the server answered HTTP 302");
        server.handle("/garbled", exchange -> ListServer.redirect(exchange, "http://a b/list.json"));
        assertThat(reason("/garbled")).as("V53: a Location that cannot be read")
            .isEqualTo("the filter list was redirected to an address that cannot be read");
    }

    /**
     * Final review B (V51): a client retired while its download is under way, as the proxy changed, is asked for no
     * further redirect: the download fails with its reason, and the redirect's target is never asked.
     */
    @Test void aRetiredClientIsAskedForNoFurtherRedirect() {
        var current = new AtomicBoolean(true);
        server.handle("/retiring", exchange -> {
            current.set(false);
            ListServer.redirect(exchange, "/list.json");
        });
        URI address = server.at("/retiring");
        CompletableFuture<byte[]> download = ListDownload.get(client, address, GENEROUS, current::get);
        Throwable failure = catchThrowable(() -> download.get(10, TimeUnit.SECONDS));
        assertThat(failure).isInstanceOf(ExecutionException.class);
        assertThat(ListDownload.reason(failure, address, GENEROUS)).isEqualTo("the HTTP proxy changed during the download");
        assertThat(server.hits("/list.json")).isZero();
    }

    /** Each way a download fails, with the reason Settings shows after "Could not update the filter list: ". */
    @Test void eachFailureHasItsReason() throws Exception {
        assertThat(reason("/error")).isEqualTo("the server answered HTTP 500");
        assertThat(reason("/huge")).as("streamed past 32 MiB").isEqualTo("the list is larger than 32 MiB");
        assertThat(reason("/announced")).as("announced as more than 32 MiB").isEqualTo("the list is larger than 32 MiB");
        long started = System.nanoTime();
        assertThat(reason(server.at("/hold"), HOLD)).as("a body that trickles: one deadline on the whole download")
            .isEqualTo("the download took longer than 0.7 seconds");
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
        return reason(address, GENEROUS);
    }

    private String reason(URI address, Duration deadline) {
        CompletableFuture<byte[]> download = ListDownload.get(client, address, deadline, CURRENT);
        Throwable failure = catchThrowable(() -> download.get(40, TimeUnit.SECONDS));
        assertThat(failure).as(String.valueOf(address)).isInstanceOf(ExecutionException.class);
        return ListDownload.reason(failure, address, deadline);
    }

    /** A download with a generous deadline, by a client that is never retired. */
    private static CompletableFuture<byte[]> get(HttpClient client, URI address) {
        return ListDownload.get(client, address, GENEROUS, CURRENT);
    }
}
