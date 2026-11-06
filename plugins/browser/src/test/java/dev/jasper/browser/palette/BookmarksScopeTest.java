package dev.jasper.browser.palette;

import dev.jasper.sdk.palette.ScopeSpec;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.browser.palette.PaletteHarness.onUiThread;
import static org.assertj.core.api.Assertions.assertThat;

/** The Bookmarks scope, searched and run as the host's palette does, over bookmarks in memory. */
class BookmarksScopeTest {
    private PaletteHarness harness;

    @AfterEach void close() throws Exception {
        if (harness != null) harness.close();
    }

    private void start() throws Exception {
        onUiThread(() -> harness = new PaletteHarness(false));
    }

    private void bookmark(String address, String title, long when, String... tags) throws Exception {
        long id = harness.stores.bookmarks().add(address, title, when).get().id();
        if (tags.length > 0) harness.stores.bookmarks().tags(id, List.of(tags)).get();
    }

    private List<String> search(String query) throws Exception {
        var rows = new java.util.concurrent.atomic.AtomicReference<List<String>>();
        onUiThread(() -> rows.set(harness.host.searchScope(BookmarksScope.ID, query, harness.window, null)));
        return rows.get();
    }

    @Test void theSpecNamesTheScopeAndItsFlags() throws Exception {
        start();
        ScopeSpec spec = new BookmarksScope(harness.stores.bookmarkIndex(), (address, window) -> { }).spec();
        assertThat(spec.id()).isEqualTo("dev.jasper.browser.scope.bookmarks");
        assertThat(spec.label()).isEqualTo("Bookmarks");
        assertThat(spec.placeholder()).isEqualTo("Search bookmarks");
        assertThat(spec.description()).isEqualTo("Open Bookmark in Browser");
        assertThat(spec.verbs()).singleElement().satisfies(verb -> {
            assertThat(verb.id()).isEqualTo("open");
            assertThat(verb.label()).isEqualTo("Open");
        });
        assertThat(spec.icon()).contains("bookmark");
        assertThat(spec.itemNoun()).isEqualTo("bookmark");
        assertThat(spec.inAll()).isTrue();
        assertThat(spec.inRecents()).isTrue();
    }

    @Test void anEmptyQueryListsEveryBookmarkNewestFirstAndTheScopeLoadsTheIndexWhenActivated() throws Exception {
        start();
        bookmark("https://a.example/", "Alpha", 1);
        bookmark("https://b.example/", "", 3);
        bookmark("https://c.example/", "Gamma", 2);
        assertThat(search("")).as("nothing loaded yet").isEmpty();
        onUiThread(() -> harness.host.searchScope(BookmarksScope.ID, "", harness.window, null));
        onUiThread(() -> new BookmarksScope(harness.stores.bookmarkIndex(), (address, window) -> { }).activated(null));
        harness.settle();
        List<String> rows = search("");
        assertThat(rows).extracting(row -> row.split("\\|")[1]).containsExactly("https://b.example/", "Gamma", "Alpha");
        assertThat(rows.getFirst()).startsWith("bookmark:");
    }

    @Test void everyWordMustBeInTheTitleTheAddressOrTheTagsAndATitlePrefixComesFirst() throws Exception {
        start();
        bookmark("https://docs.example/guide", "Rust guide", 1, "lang");
        bookmark("https://news.example/rust", "News about the Rust language", 2);
        bookmark("https://rust.example/", "Rust", 3, "systems");
        bookmark("https://other.example/", "Other", 4, "Rust", "tools");
        bookmark("https://cats.example/", "Cats", 5);
        harness.stores.bookmarkIndex().load();
        harness.settle();
        assertThat(titles(search("rust"))).as("the title prefixes first, newest first, then the rest, newest first")
            .containsExactly("Rust", "Rust guide", "Other", "News about the Rust language");
        assertThat(titles(search("RUST lang"))).as("words in any field, in any case").containsExactly("News about the Rust language", "Rust guide");
        assertThat(titles(search("rust tools"))).containsExactly("Other");
        assertThat(titles(search("example/rust"))).as("the address").containsExactly("News about the Rust language");
        assertThat(search("zebra")).isEmpty();
    }

    @Test void aRunRowIsListedWithItsAddressAndTheScopeIsToldOfChanges() throws Exception {
        start();
        var told = new java.util.concurrent.atomic.AtomicInteger();
        onUiThread(() -> harness.stores.bookmarkIndex().onChanged(told::incrementAndGet));
        bookmark("https://a.example/", "Alpha", 1);
        harness.stores.bookmarkIndex().load();
        harness.settle();
        assertThat(told.get()).isPositive();
        var rows = search("");
        assertThat(rows).hasSize(1);
        String id = rows.getFirst().split("\\|")[0];
        onUiThread(() -> harness.host.executeInScope(BookmarksScope.ID, id, "open", harness.window, null));
        assertThat(harness.opened).containsExactly("https://a.example/");
        assertThat(Optional.of(rows.getFirst())).hasValueSatisfying(row -> assertThat(row).endsWith("|true"));
    }

    static List<String> titles(List<String> rows) {
        return rows.stream().map(row -> row.split("\\|")[1]).toList();
    }
}
