package dev.jasper.app.themestore;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.*;

import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarketplaceClientTest {
    @TempDir Path home;
    private FakeMarketplace market;
    private MarketplaceClient client;

    @BeforeEach void start() throws Exception { market = new FakeMarketplace(); client = market.client(); }
    @AfterEach void stop() { client.close(); market.close(); }

    @Test void searchReadsListingsDropsPaidAndMalformedOnesAndPagesByTheRawOffset() throws Exception {
        market.json("/api/searchPlugins", FakeMarketplace.search(45,
            FakeMarketplace.plugin(12275, "com.vermouthx.idea", "Dracula Theme", "FREE"),
            FakeMarketplace.plugin(2, "x.paid", "Paid Theme", "PAID"),
            FakeMarketplace.plugin(3, "x.freemium", "Freemium Theme", "FREEMIUM"),
            "{\"id\":\"4\",\"name\":\"No xmlId\"}",
            "{\"id\":\"5\",\"xmlId\":\"x.strings\",\"name\":\"Strings\",\"downloads\":\"3598855\",\"rating\":\"4.5\",\"pricingModel\":\"FREE\"}"));
        var page = client.search("  dracula pro ", 20);
        assertThat(page.listings()).extracting(MarketplaceClient.Listing::name).containsExactly("Dracula Theme", "Freemium Theme", "Strings");
        var dracula = page.listings().getFirst();
        assertThat(dracula).isEqualTo(new MarketplaceClient.Listing(12275, "com.vermouthx.idea", "Dracula Theme", "About Dracula Theme",
            1234567, 4.42, false, "/files/12275/icon/default.svg", "Vendor 12275", "/plugin/12275"));
        assertThat(page.listings().get(1).freemium()).isTrue();
        assertThat(page.listings().get(2).downloads()).isEqualTo(3598855);
        assertThat(page.total()).isEqualTo(45);
        assertThat(page.nextOffset()).isEqualTo(40);
        assertThat(page.more()).isTrue();
        assertThat(market.requests).containsExactly("/api/searchPlugins?tags=Theme&max=20&offset=20&search=dracula+pro");
        client.search("", 0);
        assertThat(market.requests.getLast()).isEqualTo("/api/searchPlugins?tags=Theme&max=20&offset=0&orderBy=downloads");
    }

    @Test void detailsPreferTheVendorsPublicNameAndLatestReadsTheNewestRelease() throws Exception {
        market.json("/api/plugins/12275", FakeMarketplace.details(12275, "com.vermouthx.idea", "Dracula Theme", "<p>Hi</p>"))
            .json("/api/plugins/12275/updates", FakeMarketplace.release(1162585, "2.3.1", "12275/1162585/dracula-theme-2.3.1.zip"))
            .json("/api/plugins/7/updates", "[]");
        assertThat(client.details(12275)).isEqualTo(new MarketplaceClient.Details(12275, "com.vermouthx.idea", "Dracula Theme",
            "<p>Hi</p>", "Vendor 12275", "/plugin/12275"));
        assertThat(client.latest(12275)).contains(new MarketplaceClient.Release(1162585, "2.3.1", "12275/1162585/dracula-theme-2.3.1.zip"));
        assertThat(market.requests).contains("/api/plugins/12275/updates?size=1");
        assertThat(client.latest(7)).isEmpty();
    }

    @Test void downloadsFollowTrustedRedirectsOnlyAndStopAtTheCap() throws Exception {
        byte[] body = "zip bytes".getBytes(UTF_8);
        market.redirect("/files/1/2/theme.zip", "/cdn/theme.zip").bytes("/cdn/theme.zip", 200, body)
            .redirect("/files/1/3/theme.zip", "http://example.invalid/theme.zip")
            .bytes("/files/1/4/big.zip", 200, new byte[2048]);
        Path target = home.resolve("theme.part");
        client.download(new MarketplaceClient.Release(2, "1.0", "1/2/theme.zip"), target);
        assertThat(target).hasBinaryContent(body);
        assertThatThrownBy(() -> client.download(new MarketplaceClient.Release(3, "1.0", "1/3/theme.zip"), home.resolve("x.part")))
            .isInstanceOf(MarketplaceClient.MarketplaceException.class).hasMessageContaining("example.invalid").hasMessageContaining("does not trust");
        try (var small = market.client(1024)) {
            assertThatThrownBy(() -> small.download(new MarketplaceClient.Release(4, "1.0", "1/4/big.zip"), home.resolve("big.part")))
                .isInstanceOf(MarketplaceClient.MarketplaceException.class).hasMessageContaining("larger than");
        }
        assertThatThrownBy(() -> client.download(new MarketplaceClient.Release(5, "1.0", "../../etc/passwd"), home.resolve("y.part")))
            .isInstanceOf(MarketplaceClient.MarketplaceException.class);
        assertThat(market.requests).noneMatch(request -> request.contains("etc"));
    }

    @Test void failuresBecomeMarketplaceExceptionsWithAMessageForPeople() throws Exception {
        market.bytes("/api/plugins/1", 500, new byte[0]).json("/api/plugins/2", "{not json");
        assertThatThrownBy(() -> client.details(1)).isInstanceOf(MarketplaceClient.MarketplaceException.class)
            .hasMessage("The Marketplace answered HTTP 500.");
        assertThatThrownBy(() -> client.details(2)).isInstanceOf(MarketplaceClient.MarketplaceException.class)
            .hasMessageContaining("cannot read");
        int closed;
        try (var socket = new ServerSocket(0)) { closed = socket.getLocalPort(); }
        try (var offline = new MarketplaceClient(URI.create("http://127.0.0.1:" + closed), uri -> true, 1024)) {
            assertThatThrownBy(() -> offline.search("", 0)).isInstanceOf(MarketplaceClient.MarketplaceException.class)
                .hasMessageStartingWith("Could not reach the JetBrains Marketplace");
        }
    }

    @Test void iconsOnOtherHostsAreRefusedWithoutARequest() {
        assertThatThrownBy(() -> client.icon("//127.0.0.2/x")).isInstanceOf(MarketplaceClient.MarketplaceException.class);
        assertThat(market.requests).isEmpty();
    }

    @Test void onlyHttpsJetbrainsHostsAreTrusted() {
        assertThat(MarketplaceClient.jetbrains(URI.create("https://downloads.marketplace.jetbrains.com/files/x.zip"))).isTrue();
        assertThat(MarketplaceClient.jetbrains(URI.create("https://plugins.jetbrains.com/x"))).isTrue();
        assertThat(MarketplaceClient.jetbrains(URI.create("http://plugins.jetbrains.com/x"))).isFalse();
        assertThat(MarketplaceClient.jetbrains(URI.create("https://jetbrains.com.evil.io/x"))).isFalse();
        assertThat(MarketplaceClient.jetbrains(URI.create("https://notjetbrains.com/x"))).isFalse();
    }
}
