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
