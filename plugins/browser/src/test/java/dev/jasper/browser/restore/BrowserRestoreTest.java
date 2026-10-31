package dev.jasper.browser.restore;

import dev.jasper.browser.item.AddressInput;
import dev.jasper.browser.item.BrowserItem;
import dev.jasper.browser.item.BrowserItems;
import dev.jasper.browser.item.ItemTestAccess;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageTestAccess;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.RestoreState;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.TabPlacement;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * A browser item's restore state (spec section 16) and its restore, which opens the item at once and loads its page only
 * the first time it shows (spec section 19). The items' pages are made by a recording stand-in, as a page cannot be made
 * without Chromium. Headless, a lightweight parent given a peer shows, so adding a page's canvas to it shows the item.
 */
class BrowserRestoreTest {
    private static final String ID = "dev.jasper.browser";
    private static final URI EXAMPLE = URI.create("https://example.com/docs"), START = URI.create("jasper-about:welcome");

    @Test void aUrlOverTheBudgetIsSavedWithoutItsQueryAndFragment() {
        URI url = URI.create("https://example.com/search?q=" + "a".repeat(5_000) + "#results");
        assertThat(BrowserRestore.state(url, Optional.empty())).get().satisfies(state -> {
            assertThat(state.kind()).isEqualTo(BrowserRestore.KIND).isEqualTo("dev.jasper.browser.page");
            assertThat(state.values()).containsExactly(entry("url", "https://example.com/search"));
        });
        URI fragment = URI.create("https://example.com/page#" + "f".repeat(5_000));
        assertThat(BrowserRestore.state(fragment, Optional.of("docs")).orElseThrow().values())
            .containsOnly(entry("url", "https://example.com/page"), entry("target", "docs"));
    }

    @Test void aUrlStillOverTheBudgetWithoutItsQuerySavesNoUrl() {
        URI url = URI.create("https://example.com/" + "p".repeat(5_000) + "?q=1");
        assertThat(BrowserRestore.state(url, Optional.empty())).get().satisfies(state -> {
            assertThat(state.kind()).isEqualTo(BrowserRestore.KIND);
            assertThat(state.values()).as("no url: the item comes back at the start page").isEmpty();
        });
        assertThat(BrowserRestore.state(url, Optional.of("docs")).orElseThrow().values()).containsExactly(entry("target", "docs"));
    }

    /** The budget counts the keys as well as the values, and the target's entry with the url's. */
    @Test void theBudgetCountsEveryKeyAndValue() {
        String prefix = "https://example.com/";
        URI fits = URI.create(prefix + "p".repeat(RestoreState.MAX_CHARACTERS - "url".length() - prefix.length()) + "?q");
        URI whole = URI.create(fits.toString().substring(0, fits.toString().length() - 2));
        assertThat(whole.toString().length() + "url".length()).isEqualTo(RestoreState.MAX_CHARACTERS);
        assertThat(BrowserRestore.state(whole, Optional.empty()).orElseThrow().values()).containsExactly(entry("url", whole.toString()));
        assertThat(BrowserRestore.state(fits, Optional.empty()).orElseThrow().values()).as("one character over, with its query")
            .containsExactly(entry("url", whole.toString()));
        assertThat(BrowserRestore.state(whole, Optional.of("t")).orElseThrow().values()).as("the target's entry takes 7")
            .containsExactly(entry("target", "t"));
        String shorter = whole.toString().substring(0, whole.toString().length() - "target".length() - 1);
        assertThat(BrowserRestore.state(URI.create(shorter), Optional.of("t")).orElseThrow().values())
            .containsOnly(entry("url", shorter), entry("target", "t"));
    }

    @Test void dataAndBlobPagesSaveNoUrl() {
        for (String address : List.of("data:text/html,Hello%20there", "DATA:text/plain;base64,SGk=",
                "blob:https://example.com/0c0e9c4a-4b5e-4f4c-9a39-0d2f0b6d2b8e")) {
            assertThat(BrowserRestore.state(URI.create(address), Optional.empty()).orElseThrow().values()).as(address).isEmpty();
            assertThat(BrowserRestore.state(URI.create(address), Optional.of("docs")).orElseThrow().values()).as(address)
                .containsExactly(entry("target", "docs"));
        }
    }

    @Test void jasperAboutPagesSaveTheirUrl() {
        assertThat(BrowserRestore.state(START, Optional.empty()).orElseThrow().values()).containsExactly(entry("url", "jasper-about:welcome"));
        assertThat(BrowserRestore.url(BrowserRestore.state(START, Optional.empty()).orElseThrow())).contains(START);
    }

    @Test void aUrlAndATargetRoundTrip() {
        URI url = URI.create("https://example.com/a%20b?q=%E2%9C%93&x=1#part");
        RestoreState state = BrowserRestore.state(url, Optional.of("docs")).orElseThrow();
        assertThat(state.values()).containsOnly(entry("url", url.toString()), entry("target", "docs"));
        assertThat(BrowserRestore.url(state)).contains(url);
        assertThat(BrowserRestore.target(state)).contains("docs");
        RestoreState untargeted = BrowserRestore.state(url, Optional.empty()).orElseThrow();
        assertThat(untargeted.values()).containsOnlyKeys("url");
        assertThat(BrowserRestore.target(untargeted)).isEmpty();
    }

    /** Spec section 16: an item never saves credentials, so a URL is saved without its user name and password. */
    @Test void aUrlIsSavedWithoutItsUserNameAndPassword() {
        URI url = URI.create("https://ada:s%40cret@example.com:8443/a%2Fb%20c?q=%E2%9C%93&x=1#part");
        RestoreState state = BrowserRestore.state(url, Optional.of("docs")).orElseThrow();
        assertThat(state.values()).containsOnly(entry("url", "https://example.com:8443/a%2Fb%20c?q=%E2%9C%93&x=1#part"), entry("target", "docs"));
        assertThat(BrowserRestore.url(state)).contains(URI.create("https://example.com:8443/a%2Fb%20c?q=%E2%9C%93&x=1#part"));
        assertThat(BrowserRestore.state(URI.create("http://ada@example.com/"), Optional.empty()).orElseThrow().values())
            .as("a user name alone").containsExactly(entry("url", "http://example.com/"));
        assertThat(BrowserRestore.state(URI.create("ftp://ada:pw@example.com"), Optional.empty()).orElseThrow().values())
            .as("no path").containsExactly(entry("url", "ftp://example.com"));
        assertThat(BrowserRestore.state(URI.create("http://ada:pw@[::1]:8080/x"), Optional.empty()).orElseThrow().values())
            .as("an IPv6 host").containsExactly(entry("url", "http://[::1]:8080/x"));
    }

    @Test void anAddressWithoutAUserNameIsKeptAsItIs() {
        for (URI url : List.of(EXAMPLE, START, URI.create("file:///Users/me/index.html"), URI.create("about:blank"),
                URI.create("https://example.com/@ada?to=ada@example.com#@"), URI.create("mailto:ada@example.com")))
            assertThat(BrowserRestore.withoutUserInfo(url)).as(url.toString()).isSameAs(url);
        assertThat(BrowserRestore.withoutUserInfo(URI.create("HTTPS://Ada:pw@Example.COM/Path"))).as("the rest as written")
            .hasToString("HTTPS://Example.COM/Path");
    }

    /**
     * {@code workspace.toml} is plain text anyone can edit, so a saved url comes back only as an address the address field
     * would load, with one of its schemes, and a saved target only as WebLink allows one.
     */
    @Test void aSavedUrlComesBackOnlyWithASchemeTheAddressFieldLoadsAndATargetOnlyAsWebLinkAllowsIt() {
        var offered = Set.of("http", "https", "file", "about", "jasper-about", "HTTPS", "Jasper-About");
        for (String scheme : List.of("http", "https", "file", "about", "jasper-about", "HTTPS", "Jasper-About", "javascript", "chrome",
                "data", "blob", "view-source", "mailto", "ftp", "devtools", "chrome-extension")) {
            String saved = scheme + "://example.com/x";
            boolean field = AddressInput.address(saved).map(URI::getScheme).filter(scheme::equalsIgnoreCase).isPresent();
            assertThat(field).as("the address field loads " + scheme + " as typed").isEqualTo(offered.contains(scheme));
            assertThat(BrowserRestore.url(saved(Map.of("url", saved)))).as(scheme).isEqualTo(field ? Optional.of(URI.create(saved)) : Optional.empty());
        }
        for (String saved : List.of("", "not a url", "example.com", "https://exa mple.com/", "javascript:alert(1)", "chrome://version"))
            assertThat(BrowserRestore.url(saved(Map.of("url", saved)))).as(saved).isEmpty();
        assertThat(BrowserRestore.url(saved(Map.of()))).isEmpty();
        assertThat(BrowserRestore.url(saved(Map.of("url", "about:blank")))).contains(URI.create("about:blank"));
        assertThat(BrowserRestore.url(saved(Map.of("url", "file:///Users/me/index.html")))).contains(URI.create("file:///Users/me/index.html"));

        assertThat(BrowserRestore.target(saved(Map.of("target", "t".repeat(256))))).contains("t".repeat(256));
        for (String target : List.of("", "t".repeat(257), "a\u0007b", "line\nbreak", "tab\there", "del\u007f"))
            assertThat(BrowserRestore.target(saved(Map.of("target", target)))).as(target).isEmpty();
        assertThat(BrowserRestore.target(saved(Map.of()))).isEmpty();
        assertThat(BrowserRestore.state(EXAMPLE, Optional.of("bad\u0000target")).orElseThrow().values()).as("an invalid target is not saved")
            .containsExactly(entry("url", EXAMPLE.toString()));
    }

    @Test void aRestoredItemOpensAtOnceAndLoadsOnlyWhenItFirstShows() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var asked = new java.util.ArrayList<Page>();
            var items = restoring(host, asked::add);
            UUID window = host.addTerminalWindow();
            RestoreState saved = BrowserRestore.state(EXAMPLE, Optional.of("docs")).orElseThrow();
            Optional<UUID> tab = host.restoreTab(ID, window, "Docs", saved);
            assertThat(tab).as("the restorer opened its tab").isPresent();
            BrowserItem item = items.items().getLast();
            assertThat(item.tab().window().id()).isEqualTo(window);
            assertThat(item.url()).isEqualTo(EXAMPLE);
            assertThat(item.target()).contains("docs");
            assertThat(items.find("docs")).containsSame(item);
            assertThat(host.contentPresentation(item.tab()).location()).isEqualTo(EXAMPLE.toString());
            assertThat(host.restoreState(tab.get())).as("never shown, it saves the same state again").contains(saved);
            assertThat(ItemTestAccess.pageAsked(item)).as("restored, not shown").isFalse();
            assertThat(ItemTestAccess.waiting(item)).isFalse();
            assertThat(asked).isEmpty();
            var parent = new JPanel();
            parent.addNotify();
            try {
                parent.add(ItemTestAccess.pageView(item));
                assertThat(ItemTestAccess.pageAsked(item)).as("shown").isTrue();
                assertThat(asked).containsExactly(ItemTestAccess.page(item));
            } finally {
                parent.removeNotify();
            }
            assertThat(host.declinedRestores()).isEmpty();
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * After a run that ended while Chromium ran, every item the restorer opens waits: it comes back as saved, and
     * showing it asks for no page until the user loads it.
     */
    @Test void restoredItemsWaitAfterARunThatEndedWhileChromiumRan() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var asked = new java.util.ArrayList<Page>();
            var items = restoring(host, asked::add, true);
            UUID window = host.addTerminalWindow();
            RestoreState saved = BrowserRestore.state(EXAMPLE, Optional.of("docs")).orElseThrow();
            Optional<UUID> tab = host.restoreTab(ID, window, "Docs", saved);
            assertThat(tab).isPresent();
            assertThat(host.restoreTab(ID, window, "Saved", saved(Map.of()))).isPresent();
            assertThat(items.items()).hasSize(2).allSatisfy(item -> assertThat(ItemTestAccess.waiting(item)).isTrue());
            BrowserItem item = items.items().getFirst();
            assertThat(item.url()).isEqualTo(EXAMPLE);
            assertThat(item.target()).contains("docs");
            assertThat(host.restoreState(tab.get())).as("it saves what it was saved as").contains(saved);
            var parent = new JPanel();
            parent.addNotify();
            try {
                parent.add(ItemTestAccess.pageView(item));
                assertThat(ItemTestAccess.pageAsked(item)).as("shown, waiting").isFalse();
                assertThat(asked).isEmpty();
                item.reload();
                assertThat(asked).as("loaded").containsExactly(ItemTestAccess.page(item));
            } finally {
                parent.removeNotify();
            }
            assertThat(ItemTestAccess.waiting(items.items().getLast())).as("the other still waits").isTrue();
            assertThat(host.declinedRestores()).isEmpty();
            assertThat(host.failures()).isEmpty();
        } });
    }

    /** Review focus 4: a window with many browser items restores them all, and none asks for its page until it shows. */
    @Test void aWindowWithManyBrowserItemsRestoresThemAllWithoutLoadingAny() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var asked = new java.util.ArrayList<Page>();
            var items = restoring(host, asked::add);
            UUID window = host.addTerminalWindow();
            for (int i = 0; i < 40; i++)
                assertThat(host.restoreTab(ID, window, "Page " + i,
                    BrowserRestore.state(URI.create("https://example.com/" + i), Optional.empty()).orElseThrow())).isPresent();
            assertThat(items.items()).hasSize(40).allSatisfy(item -> assertThat(ItemTestAccess.pageAsked(item)).isFalse());
            assertThat(host.windowTabs(window)).hasSize(40);
            assertThat(asked).isEmpty();
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * A saved state with no url, or a url or target the address field or WebLink would refuse, still comes back: at the
     * start page, without that target, asking the user nothing.
     */
    @Test void anItemWithNoUsableSavedUrlComesBackAtTheStartPage() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var items = restoring(host, page -> { });
            UUID window = host.addTerminalWindow();
            for (var values : List.<Map<String, String>>of(Map.of(), Map.of("url", "javascript:alert(1)"), Map.of("url", "not a url"),
                    Map.of("url", "data:text/html,Hi", "target", "line\nbreak"))) {
                Optional<UUID> tab = host.restoreTab(ID, window, "Saved", saved(values));
                assertThat(tab).as(values.toString()).isPresent();
                BrowserItem item = items.items().getLast();
                assertThat(item.url()).as(values.toString()).isEqualTo(START);
                assertThat(item.target()).isEmpty();
                assertThat(host.restoreState(tab.get())).contains(BrowserRestore.state(START, Optional.empty()).orElseThrow());
            }
            assertThat(host.notifications().lines()).as("the restorer asks nothing").isEmpty();
            assertThat(host.declinedRestores()).isEmpty();
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * An item's restore state is set when it opens and replaced each time its page's main frame commits a navigation (its
     * address changes), not when a load only starts. Before the item first shows, a load replaces it too, as that is what
     * the item will load.
     */
    @Test void anItemSavesItsUrlAndTargetWhenItOpensAndAgainOnEachCommittedNavigation() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var items = restoring(host, page -> { });
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            BrowserItem item = items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.of("docs"), Optional.empty());
            assertThat(host.restoreState(item.tab())).contains(state(EXAMPLE, "docs"));

            URI next = URI.create("https://example.org/next");
            item.load(next);
            assertThat(host.restoreState(item.tab())).as("not shown yet: what it will load").contains(state(next, "docs"));

            var parent = new JPanel();
            parent.addNotify();
            try {
                parent.add(ItemTestAccess.pageView(item));
                assertThat(ItemTestAccess.pageAsked(item)).isTrue();
                Page page = ItemTestAccess.page(item);
                URI later = URI.create("https://example.net/later");
                item.load(later);
                PageTestAccess.loadStarted(page, later.toString());
                assertThat(host.restoreState(item.tab())).as("a load that only starts").contains(state(next, "docs"));
                PageTestAccess.addressChanged(page, later.toString());
                assertThat(host.restoreState(item.tab())).as("committed").contains(state(later, "docs"));
                PageTestAccess.addressChanged(page, "https://example.net/later#section");
                assertThat(host.restoreState(item.tab())).as("within the document")
                    .contains(state(URI.create("https://example.net/later#section"), "docs"));
                PageTestAccess.addressChanged(page, "data:text/html,Hello");
                assertThat(host.restoreState(item.tab()).orElseThrow().values()).as("a data: page saves no url")
                    .containsExactly(entry("target", "docs"));
                PageTestAccess.addressChanged(page, "jasper-about:welcome");
                assertThat(host.restoreState(item.tab())).contains(state(START, "docs"));
                item.close();
                PageTestAccess.addressChanged(page, EXAMPLE.toString());
            } finally {
                parent.removeNotify();
            }
            BrowserItem plain = items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            assertThat(host.restoreState(plain.tab()).orElseThrow().values()).containsExactly(entry("url", EXAMPLE.toString()));
            assertThat(host.failures()).isEmpty();
        } });
    }

    private static RestoreState state(URI url, String target) {
        return new RestoreState(BrowserRestore.KIND, Map.of("url", url.toString(), "target", target));
    }

    private static RestoreState saved(Map<String, String> values) {
        return new RestoreState(BrowserRestore.KIND, values);
    }

    /**
     * The items of a started stand-in plugin whose pages {@code start} makes, restored as the Browser's restorer restores
     * them, with {@link #START} as the start page. UI thread.
     */
    private static BrowserItems restoring(FakePluginHost host, Consumer<Page> start) {
        return restoring(host, start, false);
    }

    /** As {@link #restoring(FakePluginHost, Consumer)}; with {@code wait}, as after a run that ended while Chromium ran. */
    private static BrowserItems restoring(FakePluginHost host, Consumer<Page> start, boolean wait) {
        var context = new AtomicReference<PluginContext>();
        var items = new AtomicReference<BrowserItems>();
        host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(), started -> {
            context.set(started);
            items.set(new BrowserItems(started, BrowserSettings.declare(started.settings())::read, start));
            started.windows().restorer(BrowserRestore.KIND, request -> {
                items.get().restore(request.state(), START, request.window(), wait);
                return Optional.empty();
            });
        });
        return items.get();
    }
}
