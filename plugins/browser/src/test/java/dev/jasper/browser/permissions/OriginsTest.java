package dev.jasper.browser.permissions;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The origin a site permission is kept for: an http or https address's scheme, host and port, as Chromium writes an
 * origin, and one for every {@code file:} address; none for any other address.
 */
class OriginsTest {
    @Test void anHttpOrHttpsAddressGivesItsSchemeHostAndPortWithoutItsDefaultPort() {
        assertThat(Origins.of("https://Example.com:443/a")).contains("https://example.com");
        assertThat(Origins.of("http://a.test:8080/")).contains("http://a.test:8080");
        assertThat(Origins.of("http://a.test:80/x?y#z")).contains("http://a.test");
        assertThat(Origins.of("https://a.test:80/")).as("http's port is no default of https").contains("https://a.test:80");
        assertThat(Origins.of("HTTPS://A.TEST")).contains("https://a.test");
        assertThat(Origins.of("https://a.test:/")).as("an empty port is the default").contains("https://a.test");
        assertThat(Origins.of("http://my_host.test/path")).contains("http://my_host.test");
    }

    @Test void anIpv6HostKeepsItsBrackets() {
        assertThat(Origins.of("http://[::1]:3000/")).contains("http://[::1]:3000");
        assertThat(Origins.of("https://[::FFFF:7F00:1]/")).contains("https://[::ffff:7f00:1]");
    }

    @Test void userInformationIsLeftOut() {
        assertThat(Origins.of("https://u:p@h.test/")).contains("https://h.test");
        assertThat(Origins.of("https://a@b@h.test:8443/")).contains("https://h.test:8443");
    }

    /**
     * Review item 6: what Chromium takes for the host. A backslash ends the host in an http or https address, as a
     * slash does, and so do a query and a fragment, whatever {@code @} follows them.
     */
    @Test void theHostEndsWhereChromiumEndsIt() {
        assertThat(Origins.of("https://good.test\\@evil.test/")).contains("https://good.test");
        assertThat(Origins.of("https://good.test?@evil.test")).contains("https://good.test");
        assertThat(Origins.of("https://evil.test#@good.test")).contains("https://evil.test");
    }

    /** Chromium writes an international host in punycode, which is kept; a raw Unicode host is no host Chromium writes. */
    @Test void aPunycodeHostIsKeptAndARawUnicodeHostHasNoOrigin() {
        assertThat(Origins.of("https://xn--bcher-kva.example/")).contains("https://xn--bcher-kva.example");
        assertThat(Origins.of("https://XN--BCHER-KVA.example:8443/")).contains("https://xn--bcher-kva.example:8443");
        assertThat(Origins.of("https://b" + (char) 0xFC + "cher.example/")).isEmpty();
    }

    @Test void everyFileAddressHasOneOrigin() {
        assertThat(Origins.of("file:///x")).contains("file://");
        assertThat(Origins.of("FILE:///Users/ada/notes.html")).contains("file://");
    }

    @Test void anyOtherAddressHasNone() {
        for (String address : new String[] {"jasper-about:history", "data:,x", "about:blank", "blob:https://a.test/0b1c", "ftp://a.test/",
            "chrome://version", "https:a.test", "https://", "https://:443/", "https://[]/", "https://[::1", "https://[::1]x/",
            "https://a.test:99999/", "https://a.test:8x/", "https://a test/", "https://a.test\"/", " https://a.test/", "", "no address"})
            assertThat(Origins.of(address)).as(address).isEmpty();
        assertThat(Origins.of(null)).isEqualTo(Optional.empty());
    }
}
