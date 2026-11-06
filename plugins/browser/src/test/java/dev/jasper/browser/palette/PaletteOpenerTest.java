package dev.jasper.browser.palette;

import dev.jasper.browser.item.BrowserItem;
import dev.jasper.sdk.ui.TabPlacement;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.browser.palette.PaletteHarness.onUiThread;
import static org.assertj.core.api.Assertions.assertThat;

/** What a palette row does in a window: load where a browser item is focused, open a tab elsewhere, or tell why not. */
class PaletteOpenerTest {
    private PaletteHarness harness;

    @AfterEach void close() throws Exception {
        if (harness != null) harness.close();
    }

    private String row(String scope, String query) throws Exception {
        var found = new java.util.concurrent.atomic.AtomicReference<String>();
        onUiThread(() -> found.set(harness.host.searchScope(scope, query, harness.window, null).getFirst().split("\\|")[0]));
        return found.get();
    }

    @Test void aRowRunWhereABrowserItemIsFocusedLoadsThereAndOpensNoTab() throws Exception {
        onUiThread(() -> harness = new PaletteHarness(true));
        harness.stores.bookmarks().add("https://a.example/", "Alpha", 1).get();
        harness.stores.bookmarkIndex().load();
        harness.settle();
        String id = row(BookmarksScope.ID, "alpha");
        onUiThread(() -> {
            BrowserItem item = harness.items.open(URI.create("https://start.example/"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            assertThat(harness.items.focused(item.tab().window())).containsSame(item);
            harness.host.executeInScope(BookmarksScope.ID, id, "open", harness.window, null);
            assertThat(harness.items.items()).containsExactly(item);
            assertThat(item.url()).isEqualTo(URI.create("https://a.example/"));
        });
    }

    @Test void aRowRunWhereNoBrowserItemIsFocusedOpensANewTab() throws Exception {
        onUiThread(() -> harness = new PaletteHarness(true));
        harness.stores.bookmarks().add("https://a.example/", "Alpha", 1).get();
        harness.stores.bookmarkIndex().load();
        harness.settle();
        String id = row(BookmarksScope.ID, "alpha");
        onUiThread(() -> {
            assertThat(harness.items.items()).isEmpty();
            harness.host.executeInScope(BookmarksScope.ID, id, "open", harness.window, null);
            assertThat(harness.items.items()).singleElement().satisfies(item -> assertThat(item.url()).isEqualTo(URI.create("https://a.example/")));
        });
    }

    @Test void theTypedAddressRowOpensToo() throws Exception {
        onUiThread(() -> harness = new PaletteHarness(true));
        harness.stores.visitIndex().load();
        harness.settle();
        onUiThread(() -> {
            harness.host.searchScope(VisitedScope.ID, "https://example.com/a", harness.window, null);
            harness.host.executeInScope(VisitedScope.ID, VisitedScope.OPEN_URL, "open", harness.window, null);
            assertThat(harness.items.items()).singleElement().satisfies(item -> assertThat(item.url()).isEqualTo(URI.create("https://example.com/a")));
        });
    }

    @Test void anAddressTheAddressFieldWouldNotLoadOpensNothing() throws Exception {
        onUiThread(() -> harness = new PaletteHarness(true));
        onUiThread(() -> {
            var opener = new PaletteOpener(harness.items, harness.unavailable::get, harness.told::add);
            var window = harness.handle();
            opener.open(URI.create("javascript:alert(1)"), window);
            opener.open(URI.create("chrome://settings"), window);
            opener.open(URI.create("data:text/html,hi"), window);
            assertThat(harness.items.items()).as("only the one that gave the window").hasSize(1);
            assertThat(harness.told).isEmpty();
        });
    }

    @Test void aChromiumThatCannotServePagesTellsWhyAndOpensNothing() throws Exception {
        onUiThread(() -> harness = new PaletteHarness(true));
        var why = new IllegalStateException("Chromium did not start");
        onUiThread(() -> {
            var window = harness.handle();
            harness.unavailable.set(Optional.of(why));
            new PaletteOpener(harness.items, harness.unavailable::get, harness.told::add).open(URI.create("https://a.example/"), window);
            assertThat(harness.items.items()).hasSize(1);
            assertThat(harness.told).containsExactly(why);
        });
    }
}
