package dev.jasper.browser.page;

import dev.jasper.browser.engine.AboutScheme;
import dev.jasper.browser.engine.Chromium;
import dev.jasper.browser.engine.PreviewScheme;
import java.awt.Color;
import java.awt.EventQueue;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A preview's page before its canvas shows: what the canvas tells screen readers and paints, and that its commands do
 * nothing while it has no browser. Headless, the canvas never shows, so no Chromium starts.
 */
class PreviewPageTest {
    private static final URI SHELL = URI.create("jasper-preview://0123456789abcdef0123456789abcdef/");
    @TempDir Path data;
    private final List<String> heard = new ArrayList<>();
    /** Each request read, and the thread it was read on. */
    private final List<String> read = java.util.Collections.synchronizedList(new ArrayList<>());

    @Test void theCanvasTellsScreenReadersToUseTheBasicPreview() throws Exception {
        EventQueue.invokeAndWait(() -> {
            PreviewPage page = page();
            AccessibleContext context = page.view().getAccessibleContext();
            assertThat(context.getAccessibleName()).isEqualTo("Markdown preview");
            assertThat(context.getAccessibleDescription())
                .isEqualTo("Screen readers cannot read this preview. Set Markdown preview to Basic in the Editor's settings to read it.");
            assertThat(context.getAccessibleRole()).isEqualTo(AccessibleRole.CANVAS);
            assertThat(page.view().getAccessibleContext()).as("the same context each time").isSameAs(context);
            page.close();
        });
    }

    @Test void itPaintsTheThemesBackgroundAndDoesNothingWithoutABrowser() throws Exception {
        EventQueue.invokeAndWait(() -> {
            PreviewPage page = page();
            page.background(new Color(0x101820));
            assertThat(page.view().getBackground()).isEqualTo(new Color(0x101820));
            page.run("jasperPreview.show(\"1\");");
            page.copy();
            page.selectAll();
            page.close();
            page.close();
            page.run("jasperPreview.show(\"2\");");
            page.received("{\"op\":\"ready\"}");
        });
        EventQueue.invokeAndWait(() -> { });
        assertThat(heard).as("nothing after close").isEmpty();
        assertThat(read).as("not even read").isEmpty();
        assertThat(PreviewPage.NAME).isEqualTo("Markdown preview");
    }

    @Test void requestsAndFailuresReachTheOwnerOnTheUiThread() throws Exception {
        EventQueue.invokeAndWait(() -> {
            PreviewPage page = page();
            assertThat(page.token()).isEqualTo("0123456789abcdef0123456789abcdef");
            assertThat(page.shell()).isEqualTo(SHELL.toString());
            new Thread(() -> {
                page.received("{\"op\":\"ready\"}");
                page.received("nothing");
                page.linked(7);
                page.ended("The renderer stopped");
            }).start();
        });
        for (int tries = 0; tries < 100 && heard.size() < 3; tries++) {
            Thread.sleep(20);
            EventQueue.invokeAndWait(() -> { });
        }
        assertThat(read).as("each request is read where it arrives").containsExactly("off {\"op\":\"ready\"}", "off nothing",
            "off {\"op\":\"link\",\"id\":\"7\"}");
        assertThat(heard).as("and what it tells runs on the UI thread").containsExactly("ui {\"op\":\"ready\"}",
            "ui {\"op\":\"link\",\"id\":\"7\"}", "ui failed The renderer stopped");
    }

    @Test void findRunsFindJsAnsweringThroughThePreviewsRouter() {
        String call = PreviewPage.find("{\"action\":\"clear\"}");
        assertThat(call).isEqualTo(PageFind.SOURCE + "\n({\"action\":\"clear\"}, \"jasperPreviewQuery\");");
    }

    private PreviewPage page() {
        var chromium = new Chromium(data, () -> { }, task -> { throw new AssertionError("Chromium started"); }, new AboutScheme(),
            new PreviewScheme(Runnable::run), cause -> heard.add("startFailed"));
        return new PreviewPage(chromium, SHELL, () -> heard.add("created"), request -> {
            read.add((EventQueue.isDispatchThread() ? "ui " : "off ") + request);
            if (request.equals("nothing")) return Optional.empty();
            return Optional.of(() -> heard.add((EventQueue.isDispatchThread() ? "ui " : "off ") + request));
        }, failure -> heard.add((EventQueue.isDispatchThread() ? "ui " : "off ") + "failed " + failure.getMessage()));
    }
}
