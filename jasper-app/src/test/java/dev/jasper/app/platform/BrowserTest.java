package dev.jasper.app.platform;

import java.awt.GraphicsEnvironment;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class BrowserTest {
    /** Browser plan 3, Review Focus 5, and plan 4c: only an absolute http or https address with an authority is a web address. */
    @Test void onlyAnAbsoluteHttpOrHttpsAddressWithAnAuthorityIsAWebAddress() {
        assertThat(Browser.webAddress("https://example.com/a?b=1")).contains(URI.create("https://example.com/a?b=1"));
        assertThat(Browser.webAddress("HTTP://Example.com")).as("the scheme in any case").contains(URI.create("HTTP://Example.com"));
        assertThat(Browser.webAddress("http://under_score:8080/")).as("a host Java reads as a registry name").isPresent();
        for (String text : List.of("C:\\Users\\me\\notes.txt", "c:/Users/me/notes.txt", "\\\\server\\share\\notes.txt",
                "./https://example.com", "https:example.com", "http:/notes", "ftp://files.example.com/x", "mailto:someone@example.com",
                "file:///etc/hosts", "example.com", "https://exa mple.com", ""))
            assertThat(Browser.webAddress(text)).as(text).isEmpty();
        assertThat(Browser.isWebAddress(URI.create("https://example.com"))).isTrue();
        for (String text : List.of("https:example.com", "http:/notes", "/notes", "file:///etc/hosts", "ftp://files.example.com/x"))
            assertThat(Browser.isWebAddress(URI.create(text))).as(text).isFalse();
    }

    /**
     * Browser plan 4c's final review (ruling Q17): an internationalized host, in Unicode or percent-encoded, which
     * {@link URI} reads as no host, gets its ASCII form, so the address has a host; everything else stays as it is.
     */
    @Test void anInternationalizedHostGetsItsAsciiForm() {
        assertThat(Browser.withAsciiHost(URI.create("https://b\u00fccher.de/"))).hasToString("https://xn--bcher-kva.de/");
        assertThat(Browser.withAsciiHost(URI.create("https://b%C3%BCcher.de/a%20b?q=\u00fc#top")))
            .as("percent-encoded; the rest stays raw").hasToString("https://xn--bcher-kva.de/a%20b?q=\u00fc#top");
        URI full = Browser.withAsciiHost(URI.create("HTTPS://us%65r:pw@B\u00dcCHER.de:8443/p"));
        assertThat(full).as("with user info and a port, in any case").hasToString("HTTPS://us%65r:pw@xn--bcher-kva.de:8443/p");
        assertThat(full.getHost()).isEqualTo("xn--bcher-kva.de");
        assertThat(full.getPort()).isEqualTo(8443);
        for (String text : List.of("http://my_service:8080/", "http://\u00fc_x.de/", "http://-a.com/", "http://b%FFcher.de/",
                "http://a..b/", "https://example.com/a", "HTTP://Example.com", "https://xn--bcher-kva.de/", "http://[::1]:8080/",
                "file:///etc/hosts", "mailto:someone@example.com", "ftp://f\u00fcr.de/"))
            assertThat(Browser.withAsciiHost(URI.create(text))).as(text).hasToString(text);
    }

    /**
     * Ruling Q22 (H1): a host whose decoded or mapped form holds a URI delimiter, percent-encoded ({@code %2F},
     * {@code %40}, {@code %23}, {@code %3F}) or full-width ({@code \uff0f}, {@code \uff20}, {@code \uff03}), would move
     * part of itself into the path, query, fragment or user info and open another host, so it stays as it is.
     */
    @Test void aHostThatWouldSpillIntoTheRestOfTheAddressStaysAsItIs() {
        for (String text : List.of("http://evil.com%2F.good.com/x", "http://good.com%40evil.com/x", "http://evil.com%23.good.com/x",
                "http://evil.com%3F.good.com/x", "http://evil.com\uff0f.good.com/x", "http://good.com\uff20evil.com/x",
                "http://evil.com\uff03.good.com/x", "http://b\u00fccher.de%2Fevil/x", "http://b\u00fccher.de\uff0fevil/x",
                "http://b\u00fccher.de%3A99/x", "http://user@b\u00fccher.de%40evil.com/x"))
            assertThat(Browser.withAsciiHost(URI.create(text))).as(text).hasToString(text);
        assertThat(Browser.withAsciiHost(URI.create("http://\uff45\uff58\uff41\uff4d\uff50\uff4c\uff45.com/x")))
            .as("full-width letters map to a host that round-trips").hasToString("http://example.com/x");
    }

    /**
     * Ruling Q22 (H2): {@link java.net.IDN} follows IDNA2003, which maps {@code \u00df} to {@code ss} and {@code \u03c2}
     * to {@code \u03c3} and drops the zero-width joiners, a different domain from the one browsers reach; such a host
     * stays as it is.
     */
    @Test void aHostIdna2003WouldMapToAnotherDomainStaysAsItIs() {
        for (String text : List.of("https://fa\u00df.de/", "https://stra\u00dfe.de/", "https://fa%C3%9F.de/", "https://\u03c2\u03b1.gr/",
                "https://a\u200cb\u00fc.de/", "https://a\u200db\u00fc.de/"))
            assertThat(Browser.withAsciiHost(URI.create(text))).as(text).hasToString(text);
    }

    @Test void neverRunsOnTheEventDispatchThread() throws Exception {
        var failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try { Browser.open(URI.create("https://example.com/")); }
            catch (Throwable thrown) { failure.set(thrown); }
        });
        assertThat(failure.get()).isInstanceOf(IllegalStateException.class);
    }

    @Test void aHeadlessDesktopCannotOpenABrowser() {
        // Guarded so running this test in a desktop session never opens a real browser.
        assumeTrue(GraphicsEnvironment.isHeadless(), "only meaningful headless");
        assertThatThrownBy(() -> Browser.open(URI.create("https://example.com/"))).isInstanceOf(UnsupportedOperationException.class);
    }
}
