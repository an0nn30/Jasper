package dev.jasper.browser.pages;

import dev.jasper.browser.engine.AboutScheme;
import dev.jasper.browser.json.StrictJson;
import dev.jasper.browser.store.HistoryStore;
import dev.jasper.browser.store.MemoryStores;
import dev.jasper.browser.store.Reached;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code jasper-about:history} (spec section 10): what its script asks, answered from history in memory as JSON the
 * page parses, 50 entries at a time, newest first, and the deletion of one entry or all of them; anything else is
 * refused with one reason. The page itself, rendered from the plugin's template, holds iTerm2's copy.
 */
class HistoryPageTest {
    private static final String NOT_UNDERSTOOD = "Jasper does not understand this request.";
    private final MemoryStores memory = new MemoryStores();
    private final HistoryStore history = memory.make().history();

    @AfterEach void closeStores() throws Exception {
        memory.close();
    }

    @Test void itsAddressIsTheHistoryPage() {
        assertThat(HistoryPage.NAME).isEqualTo("history");
        assertThat(HistoryPage.ADDRESS).isEqualTo(URI.create("jasper-about:history"));
        assertThat(AboutScheme.page(HistoryPage.ADDRESS.toString())).contains(HistoryPage.NAME);
    }

    @Test void entriesListNewestFirstWithTheirIdAddressTitleAndTime() throws Exception {
        long first = record("https://a.example/", 1_000), second = record("https://b.example/two", 2_000);
        long third = record("file:///Users/ada/notes.html", 3_000);
        done(history.retitle(second, "Two"));
        Map<String, Object> answer = list("{\"op\":\"list\",\"query\":\"\"}");
        assertThat(answer).containsOnlyKeys("entries", "next").containsEntry("next", StrictJson.NULL);
        assertThat(entries(answer)).containsExactly(
            Map.of("id", String.valueOf(third), "url", "file:///Users/ada/notes.html", "title", "", "time", 3_000L),
            Map.of("id", String.valueOf(second), "url", "https://b.example/two", "title", "Two", "time", 2_000L),
            Map.of("id", String.valueOf(first), "url", "https://a.example/", "title", "", "time", 1_000L));
        assertThat(entries(list("{\"op\":\"list\",\"query\":\"two\"}"))).as("a search").extracting(entry -> entry.get("id"))
            .containsExactly(String.valueOf(second));
        assertThat(entries(list("{\"op\":\"list\"}"))).as("no query lists every entry").hasSize(3);
        assertThat(entries(list("{\"op\":\"list\",\"query\":\"\",\"before\":null,\"extra\":true}"))).as("what it does not read is ignored")
            .hasSize(3);
    }

    /** Fifty at a time: the next page starts after the last entry shown, and continues the same search. */
    @Test void sixtyEntriesListAsFiftyThenTenAndTheLastPageHasNoNext() throws Exception {
        for (int at = 1; at <= 60; at++) record("https://example.com/" + at, at * 1_000L);
        record("https://other.example/", 30_500);
        Map<String, Object> page = list("{\"op\":\"list\",\"query\":\"example.com\"}");
        assertThat(entries(page)).hasSize(50).first().satisfies(entry -> assertThat(entry).containsEntry("url", "https://example.com/60"));
        assertThat(entries(page).getLast()).containsEntry("url", "https://example.com/11");
        assertThat(page.get("next")).isInstanceOf(String.class).asString().matches("11000:[0-9]+");
        Map<String, Object> rest = list("{\"op\":\"list\",\"query\":\"example.com\",\"before\":" + StrictJson.quote((String) page.get("next")) + "}");
        assertThat(entries(rest)).extracting(entry -> entry.get("url"))
            .containsExactly("https://example.com/10", "https://example.com/9", "https://example.com/8", "https://example.com/7",
                "https://example.com/6", "https://example.com/5", "https://example.com/4", "https://example.com/3",
                "https://example.com/2", "https://example.com/1");
        assertThat(rest).containsEntry("next", StrictJson.NULL);
    }

    @Test void deleteTakesOneEntryAndClearTakesThemAll() throws Exception {
        record("https://a.example/", 1_000);
        long deleted = record("https://b.example/", 2_000);
        record("https://c.example/", 3_000);
        assertThat(answer("{\"op\":\"delete\",\"id\":\"" + deleted + "\"}")).isEqualTo("{}");
        assertThat(entries(list("{\"op\":\"list\",\"query\":\"\"}"))).extracting(entry -> entry.get("url"))
            .containsExactly("https://c.example/", "https://a.example/");
        assertThat(answer("{\"op\":\"delete\",\"id\":\"" + deleted + "\"}")).as("an entry already gone").isEqualTo("{}");
        assertThat(entries(list("{\"op\":\"list\",\"query\":\"\"}"))).hasSize(2);
        assertThat(answer("{\"op\":\"clear\"}")).isEqualTo("{}");
        assertThat(entries(list("{\"op\":\"list\",\"query\":\"\"}"))).isEmpty();
        assertThat(done(history.visits(10))).as("their rows go too").isEmpty();
    }

    /**
     * Anything but the page's three requests fails with one reason, a request that is no flat object among them, and the
     * answer never throws.
     */
    @Test void anythingElseFailsTheAnswerWithOneReason() throws Exception {
        record("https://a.example/", 1_000);
        for (String request : new String[] {null, "", "not json", "[]", "{\"op\":\"list\"", "{\"op\":\"list\"} x", "{}",
            "{\"op\":\"open\"}", "{\"op\":\"LIST\"}", "{\"op\":7}", "{\"op\":null}", "{\"op\":\"list\",\"op\":\"clear\"}",
            "{\"op\":\"list\",\"extra\":[]}", "{\"op\":\"list\",\"query\":7}", "{\"op\":\"list\",\"query\":{}}", "{\"op\":\"list\",\"before\":\"x\"}",
            "{\"op\":\"list\",\"before\":\"1000\"}", "{\"op\":\"list\",\"before\":\"1000:\"}", "{\"op\":\"list\",\"before\":\":4\"}",
            "{\"op\":\"list\",\"before\":\"1000:4:2\"}", "{\"op\":\"list\",\"before\":\"1000:99999999999999999999\"}",
            "{\"op\":\"list\",\"before\":1000}",
            "{\"op\":\"delete\"}", "{\"op\":\"delete\",\"id\":\"x\"}", "{\"op\":\"delete\",\"id\":\"\"}", "{\"op\":\"delete\",\"id\":\"-1\"}",
            "{\"op\":\"delete\",\"id\":\"1.5\"}", "{\"op\":\"delete\",\"id\":\" 1\"}", "{\"op\":\"delete\",\"id\":1}",
            "{\"op\":\"delete\",\"id\":\"99999999999999999999\"}", "{\"op\":\"delete\",\"id\":null}"}) {
            CompletableFuture<String> answer = HistoryPage.answer(history, request);
            assertThatThrownBy(() -> answer.get(10, TimeUnit.SECONDS)).as(String.valueOf(request)).isInstanceOf(ExecutionException.class)
                .cause().isInstanceOf(IllegalArgumentException.class).hasMessage(NOT_UNDERSTOOD);
        }
        assertThat(entries(list("{\"op\":\"list\",\"query\":\"\"}"))).as("nothing was deleted").hasSize(1);
    }

    /** History that cannot be read fails the answer with its reason. */
    @Test void aClosedHistoryFailsTheAnswer() throws Exception {
        memory.close();
        CompletableFuture<String> answer = HistoryPage.answer(history, "{\"op\":\"list\",\"query\":\"\"}");
        assertThatThrownBy(() -> answer.get(10, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
            .cause().hasMessage("The browser's database is closed");
    }

    /** Review Focus 1: a hostile title and address read back exactly, as the page puts them in only as text. */
    @Test void aHostileTitleAndAddressReadBackExactly() throws Exception {
        String title = "</script><img src=x onerror=alert(1)>" + (char) 0x2028 + "\"'\\" + (char) 0x2029;
        String address = "https://a.example/?q=%22%3Cb%3E&x=" + "é";
        long entry = record(address, 1_000);
        done(history.retitle(entry, title));
        String answer = answer("{\"op\":\"list\",\"query\":\"\"}");
        assertThat(entries(StrictJson.object(answer, 3).orElseThrow())).singleElement()
            .satisfies(read -> assertThat(read).containsEntry("title", title).containsEntry("url", address));
    }

    /** The page as the plugin serves it: iTerm2's copy, the page's own script after about.js, and no slot left unfilled. */
    @Test void thePageHoldsItsCopyAndItsScript() throws Exception {
        var about = new AboutScheme();
        String html = render(about);
        assertThat(html).contains("<title>History</title>", "<h1>Browsing History</h1>", "View and manage your browsing history",
            "Clear All History", "placeholder=\"Search your browsing history…\"", "No browsing history", "Load More",
            "(" + StrictJson.quote(about.secret(HistoryPage.NAME).orElseThrow()) + ");\n" + AboutPages.resource("history.js").strip());
        assertThat(html).doesNotContain("{{");
        assertThat(AboutPages.resource("history.js")).contains("'Today'", "'Yesterday'", "'Untitled'", "'Delete'",
            "'Delete this history entry?'", "'This will delete all browsing history. This action cannot be undone. Continue?'",
            "weekday: 'long'", "debounce(", ", 300)");
    }

    /** The history page rendered, as the plugin registers it, with its queries, on a fake host's appearance. */
    private String render(AboutScheme about) throws Exception {
        about.register(HistoryPage.NAME, () -> "");
        about.registerQueries(HistoryPage.NAME, (browser, request) -> HistoryPage.answer(history, request));
        var host = new AtomicReference<FakePluginHost>();
        var pages = new AtomicReference<AboutPages>();
        SwingUtilities.invokeAndWait(() -> {
            host.set(new FakePluginHost());
            var started = new AtomicReference<PluginContext>();
            host.get().start(new PluginInfo("dev.jasper.browser", "Browser", "0.1.0", Set.of()), Set.of(), Set.of(), started::set);
            pages.set(new AboutPages(started.get().appearance(), about));
        });
        try {
            return pages.get().render(HistoryPage.NAME, Map.of());
        } finally {
            SwingUtilities.invokeAndWait(() -> host.get().close());
        }
    }

    private long record(String address, long when) throws Exception {
        return done(history.record(address, Reached.LINK, when)).orElseThrow().entry();
    }

    private String answer(String request) throws Exception {
        return HistoryPage.answer(history, request).get(10, TimeUnit.SECONDS);
    }

    private Map<String, Object> list(String request) throws Exception {
        return StrictJson.object(answer(request), 3).orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> entries(Map<String, Object> answer) {
        return (List<Map<String, Object>>) answer.get("entries");
    }

    private static <T> T done(CompletableFuture<T> future) throws Exception {
        return future.get(10, TimeUnit.SECONDS);
    }
}
