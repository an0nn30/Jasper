package dev.jasper.browser.page;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a page gives the {@code tools} package (plan 7's Task 5): a script run in its top frame, its main frame's load
 * ends and the reports its page scripts send through the opener bridge, each until the page closes. The page has no
 * browser here, so its script runner is a recording stand-in where a test needs one.
 */
class PageToolHooksTest {
    private static final URI ARTICLE = URI.create("https://news.example/article");

    @Test void aScriptRunsInTheTopFrameUntilThePageCloses() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Page page = page();
            var ran = new ArrayList<String>();
            page.useScriptRunner((script, url) -> ran.add(url + " " + script));
            page.run("1 + 1", "jasper-about:reader.js");
            assertThat(ran).containsExactly("jasper-about:reader.js 1 + 1");
            page.close();
            page.run("2 + 2", "jasper-about:reader.js");
            assertThat(ran).as("nothing after the close").hasSize(1);
        });
    }

    /** Before its browser is ready, the page's own runner runs nothing, and throws nothing. */
    @Test void withoutItsBrowserAScriptRunsNowhere() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Page page = page();
            page.run("1 + 1", "jasper-about:reader.js");
            assertThat(page.hasBrowser()).isFalse();
            page.close();
        });
    }

    @Test void eachLoadEndIsToldUntilThePageCloses() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Page page = page();
            var told = new ArrayList<String>();
            var first = page.onLoadEnd(() -> told.add("first"));
            page.onLoadEnd(() -> told.add("second"));
            page.loadEnded();
            assertThat(told).containsExactly("first", "second");
            assertThat(page.documentLoaded()).isTrue();
            first.close();
            page.loadEnded();
            assertThat(told).containsExactly("first", "second", "second");
            page.close();
            page.loadEnded();
            assertThat(told).as("nothing after the close").hasSize(3);
            assertThat(page.onLoadEnd(() -> told.add("late"))).isNotNull();
            page.loadEnded();
            assertThat(told).hasSize(3);
        });
    }

    /** The bridge hands the page each report of its tools' ops, which the page tells on, word for word. */
    @Test void aToolReportIsToldUntilThePageCloses() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Page page = page();
            List<String> told = new ArrayList<>();
            var registration = page.onToolReport((op, json) -> told.add(op + " " + json));
            page.toolReported("reader", "{\"op\":\"reader\",\"check\":1,\"length\":42}");
            assertThat(told).containsExactly("reader {\"op\":\"reader\",\"check\":1,\"length\":42}");
            registration.close();
            page.toolReported("reader", "{}");
            assertThat(told).hasSize(1);
            page.onToolReport((op, json) -> told.add(op));
            page.close();
            page.toolReported("distractions", "{}");
            assertThat(told).as("nothing after the close").hasSize(1);
        });
    }

    private static Page page() {
        return new Page(ARTICLE, () -> { }, PageTestAccess.noFavicons(), Runnable::run, PageContext.persistent());
    }
}
