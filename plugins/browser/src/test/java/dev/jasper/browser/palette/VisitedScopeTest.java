package dev.jasper.browser.palette;

import dev.jasper.browser.store.HistoryStore;
import dev.jasper.browser.store.Reached;
import dev.jasper.sdk.palette.ScopeSpec;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.browser.palette.PaletteHarness.onUiThread;
import static org.assertj.core.api.Assertions.assertThat;

/** The Browser History scope, searched and run as the host's palette does, over visits in memory. */
class VisitedScopeTest {
    private PaletteHarness harness;

    @AfterEach void close() throws Exception {
        if (harness != null) harness.close();
    }

    private void start() throws Exception {
        onUiThread(() -> harness = new PaletteHarness(false));
    }

    /** Records {@code visits} loads of {@code address}, the last at {@code when}, and titles the entry. */
    private void visit(String address, String title, long when, int visits) throws Exception {
        HistoryStore history = harness.stores.history();
        HistoryStore.Recorded recorded = null;
        for (int i = 0; i < visits; i++) recorded = history.record(address, Reached.LINK, when - visits + 1 + i).get().orElseThrow();
        if (!title.isEmpty()) history.retitle(recorded.entry(), title).get();
    }

    private List<String> search(String query) throws Exception {
        var rows = new AtomicReference<List<String>>();
        onUiThread(() -> rows.set(harness.host.searchScope(VisitedScope.ID, query, harness.window, null)));
        return rows.get();
    }

    private static List<String> titles(List<String> rows) {
        return rows.stream().map(row -> row.split("\\|")[1]).toList();
    }

    @Test void theSpecNamesTheScopeAndItsFlags() throws Exception {
        start();
        ScopeSpec spec = new VisitedScope(harness.stores.visitIndex(), (address, window) -> { }).spec();
        assertThat(spec.id()).isEqualTo("dev.jasper.browser.scope.history");
        assertThat(spec.label()).isEqualTo("Browser History");
        assertThat(spec.placeholder()).isEqualTo("Search visited sites");
        assertThat(spec.description()).isEqualTo("Open Visited Site in Browser");
        assertThat(spec.verbs()).singleElement().satisfies(verb -> {
            assertThat(verb.id()).isEqualTo("open");
            assertThat(verb.label()).isEqualTo("Open");
        });
        assertThat(spec.icon()).contains("history");
        assertThat(spec.itemNoun()).isEqualTo("site");
        assertThat(spec.inAll()).isTrue();
        assertThat(spec.inRecents()).as("as shell history, never offered before the user types").isFalse();
    }

    @Test void anEmptyQueryListsTheFiftyMostRecentSites() throws Exception {
        start();
        for (int i = 1; i <= 60; i++) visit("https://site" + i + ".example/", "Site " + i, i * 10L, 1);
        harness.stores.visitIndex().load();
        harness.settle();
        var rows = new AtomicReference<dev.jasper.sdk.palette.PaletteResults>();
        onUiThread(() -> rows.set(new VisitedScope(harness.stores.visitIndex(), (address, window) -> { })
            .search("", new dev.jasper.sdk.palette.PaletteQuery(harness.handle(), java.util.Optional.empty(), 200, true,
                java.util.Optional.empty()))));
        assertThat(rows.get().rows()).hasSize(50);
        assertThat(rows.get().rows().getFirst().title()).isEqualTo("Site 60");
        assertThat(rows.get().rows().getLast().title()).isEqualTo("Site 11");
        assertThat(rows.get().sectionLabel()).contains("Recent");
    }

    @Test void everyWordMustBeInTheTitleOrTheAddressAndTheMostVisitedComeFirst() throws Exception {
        start();
        visit("https://docs.example/rust", "Rust docs", 100, 2);
        visit("https://news.example/rust-news", "Rust news", 200, 1);
        visit("https://rust.example/", "The Rust site", 50, 5);
        visit("https://cats.example/", "Cats", 300, 9);
        harness.stores.visitIndex().load();
        harness.settle();
        assertThat(titles(search("rust"))).containsExactly("The Rust site", "Rust docs", "Rust news");
        assertThat(titles(search("RUST docs"))).containsExactly("Rust docs");
        assertThat(titles(search("news.example"))).as("the address").containsExactly("Rust news");
        assertThat(search("zebra")).isEmpty();
    }

    @Test void anHttpsAddressWithAHostComesFirstAsARowThatOpensIt() throws Exception {
        start();
        visit("https://example.com/a", "Example A", 100, 1);
        harness.stores.visitIndex().load();
        harness.settle();
        var rows = search("  https://example.com/a ");
        assertThat(rows).containsExactly("open-url|Open URL in Browser|true", rows.get(1));
        assertThat(rows.get(1)).contains("Example A");
        onUiThread(() -> harness.host.executeInScope(VisitedScope.ID, "open-url", "open", harness.window, null));
        assertThat(harness.opened).containsExactly("https://example.com/a");
    }

    @Test void noUrlRowForOtherTextThanAnHttpsAddressWithAHost() throws Exception {
        start();
        harness.stores.visitIndex().load();
        harness.settle();
        for (String text : List.of("http://example.com", "https://", "example.com", "https:///path", "https://exa mple.com"))
            assertThat(search(text)).as(text).isEmpty();
    }
}
