package dev.jasper.app.platform;

import java.awt.GraphicsEnvironment;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class BrowserTest {
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
