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
        assertThat(Browser.withAsciiHost(URI.create("https://bücher.de/"))).hasToString("https://xn--bcher-kva.de/");
        assertThat(Browser.withAsciiHost(URI.create("https://b%C3%BCcher.de/a%20b?q=ü#top")))
            .as("percent-encoded; the rest stays raw").hasToString("https://xn--bcher-kva.de/a%20b?q=ü#top");
        URI full = Browser.withAsciiHost(URI.create("HTTPS://us%65r:pw@BÜCHER.de:8443/p"));
        assertThat(full).as("with user info and a port, in any case").hasToString("HTTPS://us%65r:pw@xn--bcher-kva.de:8443/p");
        assertThat(full.getHost()).isEqualTo("xn--bcher-kva.de");
        assertThat(full.getPort()).isEqualTo(8443);
        for (String text : List.of("http://my_service:8080/", "http://ü_x.de/", "http://-a.com/", "http://b%FFcher.de/",
                "http://a..b/", "https://example.com/a", "HTTP://Example.com", "https://xn--bcher-kva.de/", "http://[::1]:8080/",
                "file:///etc/hosts", "mailto:someone@example.com", "ftp://für.de/"))
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
