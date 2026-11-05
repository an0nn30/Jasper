package dev.jasper.browser.preview;

import dev.jasper.browser.api.MarkdownPreview;
import dev.jasper.browser.api.PreviewEvent;
import dev.jasper.browser.engine.AboutScheme;
import dev.jasper.browser.engine.Chromium;
import dev.jasper.browser.engine.PreviewScheme;
import java.awt.EventQueue;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The service's wiring, end to end through {@link PreviewService#create}: what a preview's page tells (its browser
 * exists, its requests, its failure) reaches that preview. The page here is recorded, as no Chromium can start; the
 * service's own {@code PreviewPage} is the production page.
 */
class PreviewServiceTest {
    @TempDir Path data;
    private final PreviewScheme scheme = new PreviewScheme();
    private final List<PreviewService.Wiring> pages = new ArrayList<>();
    private final List<String> ran = new ArrayList<>();
    private final List<Duration> delays = new ArrayList<>();
    private final List<Runnable> waits = new ArrayList<>();

    @Test void eachPreviewHearsItsOwnPagesBrowserRequestsAndFailure() throws Exception {
        var chromium = chromium();
        var service = new PreviewService(scheme, chromium, Runnable::run, wiring -> {
            pages.add(wiring);
            return new PagePreview.Hooks(new JComponent() { }, ran::add, color -> { }, () -> { }, () -> { }, () -> { }, () -> { });
        }, (delay, task) -> {
            delays.add(delay);
            waits.add(task);
            return () -> waits.remove(task);
        });
        List<PreviewEvent> first = new ArrayList<>(), second = new ArrayList<>();
        var made = new ArrayList<MarkdownPreview>();
        EventQueue.invokeAndWait(() -> {
            made.add(service.create(first::add));
            made.add(service.create(second::add));
        });
        assertThat(pages).hasSize(2);
        assertThat(scheme.answer("GET", pages.get(0).shell().toString()).status()).as("each page's shell is its open origin's").isEqualTo(200);
        assertThat(pages.get(0).shell()).isNotEqualTo(pages.get(1).shell());
        EventQueue.invokeAndWait(() -> {
            pages.get(0).created();
            pages.get(0).received("{\"op\":\"ready\"}");
            made.get(0).show(1, "<p>One</p>");
        });
        EventQueue.invokeAndWait(() -> pages.get(0).received("{\"op\":\"shown\",\"rev\":\"1\"}"));
        assertThat(delays).as("created arms the ready watch").containsExactly(PagePreview.READY_WAIT);
        assertThat(waits).as("which ready stopped").isEmpty();
        assertThat(ran).containsExactly("jasperPreview.show(\"1\");");
        assertThat(first).containsExactly(new PreviewEvent.Shown(1));
        EventQueue.invokeAndWait(() -> {
            pages.get(1).created();
            pages.get(1).failed(new IllegalStateException("Chromium's page for the Markdown preview stopped."));
            pages.get(1).failed(new IllegalStateException("again"));
            pages.get(0).failed(new RuntimeException());
        });
        assertThat(second).containsExactly(new PreviewEvent.Unavailable("Chromium's page for the Markdown preview stopped."));
        assertThat(waits).as("Unavailable stops the watch").isEmpty();
        assertThat(first).as("a failure with no message is told by its name").containsExactly(new PreviewEvent.Shown(1),
            new PreviewEvent.Unavailable("java.lang.RuntimeException"));
        EventQueue.invokeAndWait(() -> made.forEach(MarkdownPreview::close));
        chromium.close();
    }

    @Test void aClosedPreviewIsForgottenAndHearsNothing() throws Exception {
        var chromium = chromium();
        var service = new PreviewService(scheme, chromium, Runnable::run);
        List<PreviewEvent> open = new ArrayList<>(), closed = new ArrayList<>();
        EventQueue.invokeAndWait(() -> {
            assertThat(service.available()).isTrue();
            service.create(open::add);
            service.create(closed::add).close();
            service.unavailable(new IllegalStateException("The worker stopped"));
        });
        assertThat(open).containsExactly(new PreviewEvent.Unavailable("The worker stopped"));
        assertThat(closed).isEmpty();
        chromium.close();
        assertThat(service.available()).as("once Chromium is closed").isFalse();
    }

    /** A Chromium that never starts here: its worker refuses, and nothing asks it for a client. */
    private Chromium chromium() {
        return new Chromium(data, task -> { throw new AssertionError("Chromium started"); }, new AboutScheme(), scheme, cause -> { });
    }
}
