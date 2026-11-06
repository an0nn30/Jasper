package dev.jasper.browser.pages;

import dev.jasper.browser.engine.AboutScheme;
import dev.jasper.browser.json.StrictJson;
import dev.jasper.browser.store.BookmarkStore;
import dev.jasper.browser.store.MemoryStores;
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
 * {@code jasper-about:bookmarks} (spec section 10): what its script asks, answered from bookmarks in memory as JSON the
 * page parses, 50 at a time, in the order and with the tags asked for, and the deletion of one bookmark or all of them;
 * anything else is refused with one reason. The page itself, rendered from the plugin's template, holds its copy.
 */
class BookmarksPageTest {
    private static final String NOT_UNDERSTOOD = "Jasper does not understand this request.";
    private final MemoryStores memory = new MemoryStores();
    private final BookmarkStore bookmarks = memory.make().bookmarks();

    @AfterEach void closeStores() throws Exception {
        memory.close();
    }

    @Test void itsAddressIsTheBookmarksPage() {
        assertThat(BookmarksPage.NAME).isEqualTo("bookmarks");
        assertThat(BookmarksPage.ADDRESS).isEqualTo(URI.create("jasper-about:bookmarks"));
        assertThat(AboutScheme.page(BookmarksPage.ADDRESS.toString())).contains(BookmarksPage.NAME);
    }

    @Test void bookmarksListNewestFirstWithTheirFieldsAndEveryTagListed() throws Exception {
        long a = add("https://a.example/", "Zed", 1_000, "work", "Read");
        long b = add("https://b.example/", "", 2_000);
        long c = add("https://c.example/", "alpha", 3_000, "home");
        Map<String, Object> answer = list("{\"op\":\"list\"}");
        assertThat(answer).containsOnlyKeys("bookmarks", "more", "tags").containsEntry("more", false);
        assertThat(answer.get("tags")).isEqualTo(List.of("home", "Read", "work"));
        assertThat(found(answer)).containsExactly(
            Map.of("id", String.valueOf(c), "url", "https://c.example/", "title", "alpha", "added", 3_000L, "tags", List.of("home")),
            Map.of("id", String.valueOf(b), "url", "https://b.example/", "title", "", "added", 2_000L, "tags", List.of()),
            Map.of("id", String.valueOf(a), "url", "https://a.example/", "title", "Zed", "added", 1_000L, "tags", List.of("work", "Read")));
        assertThat(urls(list("{\"op\":\"list\",\"query\":\"ALPHA\",\"extra\":true}"))).as("a search; the rest ignored")
            .containsExactly("https://c.example/");
    }

    @Test void eachSortOrdersTheBookmarks() throws Exception {
        add("https://c.example/", "banana", 1_000);
        add("https://b.example/", "Cherry", 2_000);
        add("https://a.example/", "", 3_000);
        add("https://d.example/", "apple", 4_000);
        assertThat(urls(list("{\"op\":\"list\",\"sort\":\"date\"}"))).containsExactly("https://d.example/", "https://a.example/",
            "https://b.example/", "https://c.example/");
        assertThat(urls(list("{\"op\":\"list\",\"sort\":\"title\"}"))).as("an untitled bookmark by its address")
            .containsExactly("https://d.example/", "https://c.example/", "https://b.example/", "https://a.example/");
        assertThat(urls(list("{\"op\":\"list\",\"sort\":\"url\"}"))).containsExactly("https://a.example/", "https://b.example/",
            "https://c.example/", "https://d.example/");
    }

    @Test void tagsChosenFilterToBookmarksWithEveryOne() throws Exception {
        add("https://a.example/", "A", 1_000, "work", "read");
        add("https://b.example/", "B", 2_000, "work");
        add("https://c.example/", "C", 3_000, "read", "work", "home");
        assertThat(urls(list("{\"op\":\"list\",\"tags\":[\"work\"]}"))).containsExactly("https://c.example/", "https://b.example/", "https://a.example/");
        assertThat(urls(list("{\"op\":\"list\",\"tags\":[\"work\",\"READ\"]}"))).containsExactly("https://c.example/", "https://a.example/");
        assertThat(urls(list("{\"op\":\"list\",\"tags\":[\"work\",\"read\",\"home\"]}"))).containsExactly("https://c.example/");
        assertThat(urls(list("{\"op\":\"list\",\"tags\":[]}"))).hasSize(3);
        assertThat(list("{\"op\":\"list\",\"tags\":[\"home\"]}").get("tags")).as("every tag, whatever was chosen")
            .isEqualTo(List.of("home", "read", "work"));
    }

    /** Fifty at a time: the next page starts after the offset, and more says whether one follows. */
    @Test void sixtyBookmarksListAsFiftyThenTen() throws Exception {
        for (int at = 1; at <= 60; at++) add("https://example.com/" + at, "t" + at, at * 1_000L);
        Map<String, Object> page = list("{\"op\":\"list\",\"offset\":\"0\"}");
        assertThat(found(page)).hasSize(50).first().satisfies(first -> assertThat(first).containsEntry("url", "https://example.com/60"));
        assertThat(page).containsEntry("more", true);
        Map<String, Object> rest = list("{\"op\":\"list\",\"offset\":\"50\"}");
        assertThat(urls(rest)).hasSize(10).first().isEqualTo("https://example.com/10");
        assertThat(rest).containsEntry("more", false);
        assertThat(found(list("{\"op\":\"list\",\"offset\":\"60\"}"))).isEmpty();
    }

    @Test void deleteTakesOneBookmarkAndClearTakesThemAll() throws Exception {
        add("https://a.example/", "A", 1_000);
        long deleted = add("https://b.example/", "B", 2_000, "t");
        add("https://c.example/", "C", 3_000);
        assertThat(answer("{\"op\":\"delete\",\"id\":\"" + deleted + "\"}")).isEqualTo("{}");
        assertThat(urls(list("{\"op\":\"list\"}"))).containsExactly("https://c.example/", "https://a.example/");
        assertThat(answer("{\"op\":\"delete\",\"id\":\"" + deleted + "\"}")).as("a bookmark already gone").isEqualTo("{}");
        assertThat(list("{\"op\":\"list\"}").get("tags")).as("its tags go too").isEqualTo(List.of());
        assertThat(answer("{\"op\":\"clear\"}")).isEqualTo("{}");
        assertThat(found(list("{\"op\":\"list\"}"))).isEmpty();
    }

    /** Anything but the page's three requests fails with one reason, and the answer never throws. */
    @Test void anythingElseFailsTheAnswerWithOneReason() throws Exception {
        add("https://a.example/", "A", 1_000);
        for (String request : new String[] {null, "", "not json", "[]", "{\"op\":\"list\"", "{\"op\":\"list\"} x", "{}",
            "{\"op\":\"open\"}", "{\"op\":\"LIST\"}", "{\"op\":7}", "{\"op\":null}", "{\"op\":\"list\",\"op\":\"clear\"}",
            "{\"op\":\"list\",\"query\":7}", "{\"op\":\"list\",\"query\":{}}",
            "{\"op\":\"list\",\"sort\":\"newest\"}", "{\"op\":\"list\",\"sort\":\"DATE\"}", "{\"op\":\"list\",\"sort\":1}",
            "{\"op\":\"list\",\"tags\":\"a\\nb\"}", "{\"op\":\"list\",\"tags\":[1]}", "{\"op\":\"list\",\"tags\":[[\"a\"]]}",
            "{\"op\":\"list\",\"tags\":{}}", "{\"op\":\"list\",\"tags\":null}",
            "{\"op\":\"list\",\"offset\":5}", "{\"op\":\"list\",\"offset\":\"-1\"}", "{\"op\":\"list\",\"offset\":\"x\"}",
            "{\"op\":\"list\",\"offset\":\"\"}", "{\"op\":\"list\",\"offset\":\"99999999999\"}",
            "{\"op\":\"delete\"}", "{\"op\":\"delete\",\"id\":\"x\"}", "{\"op\":\"delete\",\"id\":\"\"}", "{\"op\":\"delete\",\"id\":\"-1\"}",
            "{\"op\":\"delete\",\"id\":\"1.5\"}", "{\"op\":\"delete\",\"id\":1}", "{\"op\":\"delete\",\"id\":\"99999999999999999999\"}",
            "{\"op\":\"delete\",\"id\":null}"}) {
            CompletableFuture<String> answer = BookmarksPage.answer(bookmarks, request);
            assertThatThrownBy(() -> answer.get(10, TimeUnit.SECONDS)).as(String.valueOf(request)).isInstanceOf(ExecutionException.class)
                .cause().isInstanceOf(IllegalArgumentException.class).hasMessage(NOT_UNDERSTOOD);
        }
        assertThat(found(list("{\"op\":\"list\"}"))).as("nothing was deleted").hasSize(1);
    }

    /** Bookmarks that cannot be read fail the answer with the reason. */
    @Test void aClosedDatabaseFailsTheAnswer() throws Exception {
        memory.close();
        CompletableFuture<String> answer = BookmarksPage.answer(bookmarks, "{\"op\":\"list\"}");
        assertThatThrownBy(() -> answer.get(10, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
            .cause().hasMessage("The browser's database is closed");
    }

    /** A hostile title, address and tag read back exactly, as the page puts them in only as text. */
    @Test void aHostileTitleAddressAndTagReadBackExactly() throws Exception {
        String title = "</script><img src=x onerror=alert(1)>" + (char) 0x2028 + "\"'\\" + (char) 0x2029;
        String address = "https://a.example/?q=%22%3Cb%3E&x=" + "\u00e9";
        String tag = "<b>\"x\"</b>";
        add(address, title, 1_000, tag);
        Map<String, Object> read = list("{\"op\":\"list\",\"tags\":[" + StrictJson.quote(tag) + "]}");
        assertThat(found(read)).singleElement().satisfies(bookmark ->
            assertThat(bookmark).containsEntry("title", title).containsEntry("url", address).containsEntry("tags", List.of(tag)));
        assertThat(read.get("tags")).isEqualTo(List.of(tag));
    }

    /** The page as the plugin serves it: its copy, the page's own script after about.js, and no slot left unfilled. */
    @Test void thePageHoldsItsCopyAndItsScript() throws Exception {
        var about = new AboutScheme();
        String html = render(about);
        assertThat(html).contains("<title>Bookmarks</title>", "<h1>Bookmarks</h1>", "Manage and organize your saved websites",
            "Clear All", "placeholder=\"Search bookmarks...\"", "Sort by:", "Date Added", "Title", "URL", "Filter by tags:",
            "No bookmarks found", "Load More",
            "(" + StrictJson.quote(about.secret(BookmarksPage.NAME).orElseThrow()) + ");\n" + AboutPages.resource("bookmarks.js").strip());
        assertThat(html).doesNotContain("{{");
        assertThat(AboutPages.resource("bookmarks.js")).contains("'Delete this bookmark?'",
            "'This will delete all bookmarks. This action cannot be undone. Continue?'", "'Added '", "debounce(", ", 300)", "aria-pressed");
    }

    /** The bookmarks page rendered, as the plugin registers it, with its queries, on a fake host's appearance. */
    private String render(AboutScheme about) throws Exception {
        about.register(BookmarksPage.NAME, () -> "");
        about.registerQueries(BookmarksPage.NAME, (browser, request) -> BookmarksPage.answer(bookmarks, request));
        var host = new AtomicReference<FakePluginHost>();
        var pages = new AtomicReference<AboutPages>();
        SwingUtilities.invokeAndWait(() -> {
            host.set(new FakePluginHost());
            var started = new AtomicReference<PluginContext>();
            host.get().start(new PluginInfo("dev.jasper.browser", "Browser", "0.1.0", Set.of()), Set.of(), Set.of(), started::set);
            pages.set(new AboutPages(started.get().appearance(), about));
        });
        try {
            return pages.get().render(BookmarksPage.NAME, Map.of());
        } finally {
            SwingUtilities.invokeAndWait(() -> host.get().close());
        }
    }

    private long add(String address, String title, long when, String... tags) throws Exception {
        long id = done(bookmarks.add(address, title, when)).id();
        if (tags.length > 0) done(bookmarks.tags(id, List.of(tags)));
        return id;
    }

    private String answer(String request) throws Exception {
        return BookmarksPage.answer(bookmarks, request).get(10, TimeUnit.SECONDS);
    }

    private Map<String, Object> list(String request) throws Exception {
        return StrictJson.object(answer(request), 4).orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> found(Map<String, Object> answer) {
        return (List<Map<String, Object>>) answer.get("bookmarks");
    }

    private static List<Object> urls(Map<String, Object> answer) {
        return found(answer).stream().map(bookmark -> bookmark.get("url")).toList();
    }

    private static <T> T done(CompletableFuture<T> future) throws Exception {
        return future.get(10, TimeUnit.SECONDS);
    }
}
