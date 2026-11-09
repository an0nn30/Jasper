package dev.jasper.browser.http;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ruling V43: the plugin's own fetches route as Chromium routes its pages' requests while the HTTP proxy is on, direct
 * for the hosts of Chromium's implicit bypass rules and through the proxy for every other web address, each host read
 * as the address writes it, so no name is looked up. Loopback, the part of them that is this computer, is what the
 * filter list's http rule shares (ruling F10).
 */
class ProxyRouteTest {
    private static final InetSocketAddress PROXY = InetSocketAddress.createUnresolved("proxy.invalid", 8118);

    @Test void chromiumsImplicitBypassHostsAreBypassedAndNoOtherIs() {
        for (String host : List.of("localhost", "LOCALHOST", "localhost.", "a.localhost", "a.b.LocalHost", "a.localhost.",
                "127.0.0.1", "127.255.255.254", "127.000.000.001", "[::1]", "[0:0:0:0:0:0:0:1]", "[::ffff:127.0.0.1]", "::1",
                "169.254.0.1", "169.254.255.255", "[fe80::1]", "[FEBF::1]", "[::ffff:169.254.0.1]"))
            assertThat(ProxyRoute.bypassed(host)).as(host).isTrue();
        for (String host : List.of("example.com", "localhost.example", "xlocalhost", "local", "localhost..",
                "128.0.0.1", "126.255.255.255", "169.253.1.1", "169.255.0.1", "10.0.0.1", "192.168.1.1", "0177.0.0.1", "[::2]",
                "[::]", "[fec0::1]", "[fe80::1%25en0]", "[::1", "[]", "", "1.2.3.4.5", "999.1.1.1"))
            assertThat(ProxyRoute.bypassed(host)).as(host).isFalse();
    }

    /** F10's loopback: this computer only, so neither {@code *.localhost}, which a name server answers, nor link-local. */
    @Test void loopbackIsThisComputerAlone() {
        for (String host : List.of("localhost", "LocalHost", "127.0.0.1", "127.1.2.3", "[::1]", "[0:0:0:0:0:0:0:1]", "[::ffff:127.0.0.1]"))
            assertThat(ProxyRoute.loopback(host)).as(host).isTrue();
        for (String host : List.of("localhost.", "a.localhost", "169.254.0.1", "[fe80::1]", "128.0.0.1", "example.com", "[::]", ""))
            assertThat(ProxyRoute.loopback(host)).as(host).isFalse();
    }

    @Test void aBypassedHostGoesDirectAndAnyOtherWebAddressThroughTheProxy() {
        ProxySelector route = ProxyRoute.through(PROXY);
        List<Proxy> direct = List.of(Proxy.NO_PROXY), proxied = List.of(new Proxy(Proxy.Type.HTTP, PROXY));
        assertThat(route.select(URI.create("http://example.com/page"))).isEqualTo(proxied);
        assertThat(route.select(URI.create("https://example.com:8443/page"))).isEqualTo(proxied);
        assertThat(route.select(URI.create("HTTPS://Example.COM/"))).isEqualTo(proxied);
        assertThat(route.select(URI.create("http://localhost:8080/list.json"))).isEqualTo(direct);
        assertThat(route.select(URI.create("https://dev.localhost/"))).isEqualTo(direct);
        assertThat(route.select(URI.create("http://127.0.0.1:5000/icon.png"))).isEqualTo(direct);
        assertThat(route.select(URI.create("http://[::1]:8000/"))).isEqualTo(direct);
        assertThat(route.select(URI.create("http://169.254.169.254/latest"))).isEqualTo(direct);
        assertThat(route.select(URI.create("http://[fe80::2]/"))).isEqualTo(direct);
        assertThat(route.select(URI.create("ftp://example.com/file"))).as("not a web address").isEqualTo(direct);
        assertThat(route.select(URI.create("mailto:someone@example.com"))).as("no host").isEqualTo(direct);
        assertThatThrownBy(() -> route.select(null)).isInstanceOf(IllegalArgumentException.class);
        route.connectFailed(URI.create("http://example.com/"), PROXY, new java.io.IOException("refused"));
    }
}
