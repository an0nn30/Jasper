package dev.jasper.browser.item;

import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageTestAccess;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.TabPlacement;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec section 10's View Source on the testkit's fake host (P42): the item captures the HTML its page shows, keeps it
 * under a new random token and loads {@code jasper-about:source} with the page and that token, in the same item; it
 * saves the source page without the token, and keeps its captures within a bound until it closes. Chromium's side, the
 * captured HTML, is played by the test through the page's seam. No Chromium.
 */
class BrowserItemSourceTest {
    private static final String ID = "dev.jasper.browser";
    private static final String EXAMPLE = "https://example.com/";
    private FakePluginHost host;
    private BrowserItems items;
    private BrowserItem item;
    private Page page;
    private final List<CompletableFuture<Optional<String>>> reads = new ArrayList<>();

    @AfterEach void closeHost() throws Exception {
        SwingUtilities.invokeAndWait(() -> { if (host != null) host.close(); });
    }

    @Test void viewSourceCapturesThePageAndLoadsItsSourcePageInTheSameItem() throws Exception {
        onUiThread(() -> {
            open(URI.create("https://ada:secret@example.com/"));
            item.viewSource();
            reads.getFirst().complete(Optional.of("<p>captured</p>"));
            assertThat(page.address()).as("not before the capture reaches the UI thread").isEqualTo("https://ada:secret@example.com/");
        });
        onUiThread(() -> {
            assertThat(SourcePage.page(page.address())).as("without user info").contains(EXAMPLE);
            String token = SourcePage.token(page.address()).orElseThrow();
            assertThat(token).matches("[0-9a-f]{32}");
            assertThat(items.items()).as("in the same item").containsExactly(item);
            assertThat(items.capturedSource(token, EXAMPLE)).contains("<p>captured</p>");
            assertThat(items.capturedSource(token, "https://example.org/")).as("for that page only").isEmpty();
            assertThat(items.capturedSource("f".repeat(32), EXAMPLE)).isEmpty();
            PageTestAccess.addressChanged(page, page.address().replace("jasper-about:source?", "jasper-about://source/?"));
            assertThat(host.restoreState(item.tab()).orElseThrow().values()).as("saved without its token")
                .containsEntry("url", "jasper-about:source?url=https%3A%2F%2Fexample.com%2F");
            assertThat(host.failures()).isEmpty();
        });
    }

    /** Each View Source makes a new token: a source page's token is never another's. */
    @Test void eachViewSourceHasATokenOfItsOwn() throws Exception {
        onUiThread(() -> open(URI.create(EXAMPLE)));
        String first = viewSource(Optional.of("<p>one</p>"));
        String second = viewSource(Optional.of("<p>two</p>"));
        assertThat(second).isNotEqualTo(first);
        onUiThread(() -> {
            assertThat(items.capturedSource(first, EXAMPLE)).contains("<p>one</p>");
            assertThat(items.capturedSource(second, EXAMPLE)).contains("<p>two</p>");
        });
    }

    /** A page that went elsewhere while its HTML was read shows no source page; one Chromium gave none still goes there. */
    @Test void aPageThatMovedOnLoadsNoSourceAndOneWithoutACaptureLoadsItsSourcePageWithoutAToken() throws Exception {
        onUiThread(() -> {
            open(URI.create(EXAMPLE));
            item.viewSource();
            PageTestAccess.addressChanged(page, "https://example.org/");
            reads.getFirst().complete(Optional.of("<p>old</p>"));
        });
        onUiThread(() -> {
            assertThat(page.address()).isEqualTo("https://example.org/");
            item.viewSource();
            reads.getLast().complete(Optional.empty());
        });
        onUiThread(() -> {
            assertThat(page.address()).isEqualTo("jasper-about:source?url=https%3A%2F%2Fexample.org%2F");
            PageTestAccess.addressChanged(page, EXAMPLE);
            item.viewSource();
            reads.getLast().completeExceptionally(new IllegalStateException("no browser"));
        });
        onUiThread(() -> {
            assertThat(page.address()).as("a read that failed is none").isEqualTo("jasper-about:source?url=https%3A%2F%2Fexample.com%2F");
            assertThat(host.failures()).isEmpty();
        });
    }

    /** The bound: at most {@value SourcePages#MAX} captures and {@value SourcePages#BUDGET} characters, the oldest dropped first. */
    @Test void anItemKeepsItsCapturesWithinABoundAndDropsThemWhenItCloses() throws Exception {
        onUiThread(() -> open(URI.create(EXAMPLE)));
        var tokens = new ArrayList<String>();
        for (int capture = 0; capture <= SourcePages.MAX; capture++) tokens.add(viewSource(Optional.of("<p>" + capture + "</p>")));
        onUiThread(() -> {
            assertThat(items.capturedSource(tokens.getFirst(), EXAMPLE)).as("the oldest, over the count").isEmpty();
            assertThat(items.capturedSource(tokens.get(1), EXAMPLE)).contains("<p>1</p>");
        });
        String longest = "a".repeat(SourcePage.LIMIT + 100);
        tokens.add(viewSource(Optional.of(longest)));
        tokens.add(viewSource(Optional.of(longest)));
        onUiThread(() -> {
            assertThat(items.capturedSource(tokens.getLast(), EXAMPLE)).as("kept one past the limit, so its page says it was cut")
                .hasValueSatisfying(source -> assertThat(source).hasSize(SourcePage.LIMIT + 1));
            assertThat(items.capturedSource(tokens.get(tokens.size() - 2), EXAMPLE)).as("two of the longest fit the budget").isPresent();
            assertThat(items.capturedSource(tokens.get(SourcePages.MAX), EXAMPLE)).as("older ones, over the budget").isEmpty();
        });
        String last = viewSource(Optional.of("<p>last</p>"));
        onUiThread(() -> {
            assertThat(items.capturedSource(tokens.get(tokens.size() - 2), EXAMPLE)).as("the older of the two, over the budget").isEmpty();
            assertThat(items.capturedSource(tokens.getLast(), EXAMPLE)).isPresent();
            assertThat(items.capturedSource(last, EXAMPLE)).isPresent();
            item.close();
            assertThat(items.capturedSource(last, EXAMPLE)).as("gone with its item").isEmpty();
        });
    }

    /** View Source on the item, back at {@link #EXAMPLE}, with the page's HTML {@code html}: the source page's token. */
    private String viewSource(Optional<String> html) throws Exception {
        onUiThread(() -> {
            PageTestAccess.addressChanged(page, EXAMPLE);
            item.viewSource();
            reads.getLast().complete(html);
        });
        var token = new AtomicReference<String>();
        onUiThread(() -> token.set(SourcePage.token(page.address()).orElseThrow()));
        return token.get();
    }

    /** Opens the plugin on a fake host with one browser item at {@code url}, whose page's HTML the test gives. UI thread. */
    private void open(URI url) {
        host = new FakePluginHost();
        var context = new AtomicReference<PluginContext>();
        var settings = new AtomicReference<BrowserSettings.Declared>();
        host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(), plugin -> {
            context.set(plugin);
            settings.set(BrowserSettings.declare(plugin.settings()));
        });
        items = new BrowserItems(context.get(), settings.get()::read, started -> { });
        item = items.open(url, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
        page = item.page();
        PageTestAccess.useSourceReader(page, read -> {
            var source = new CompletableFuture<Optional<String>>();
            reads.add(source);
            return source;
        });
    }

    private static void onUiThread(Runnable test) throws Exception {
        var failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                test.run();
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        if (failure.get() instanceof Error error) throw error;
        if (failure.get() instanceof Exception exception) throw exception;
    }
}
