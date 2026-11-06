package dev.jasper.browser.restore;

import dev.jasper.browser.item.AddressInput;
import dev.jasper.browser.item.BrowserItem;
import dev.jasper.browser.item.BrowserItems;
import dev.jasper.browser.item.ItemTestAccess;
import dev.jasper.browser.item.Zoom;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageTestAccess;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.browser.store.MemoryStores;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.RestoreState;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.TabPlacement;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.within;

/**
 * A browser item's restore state (spec section 16), with the zoom it saves (section 10), and its restore, which opens
 * the item at once and loads its page only the first time it shows (spec section 19). The items' pages are made by a
 * recording stand-in, as a page cannot be made without Chromium. Headless, a lightweight parent given a peer shows, so
 * adding a page's canvas to it shows the item.
 */
class BrowserRestoreTest {
    private static final String ID = "dev.jasper.browser";
    private static final URI EXAMPLE = URI.create("https://example.com/docs"), START = URI.create("jasper-about:welcome");

    // Stores in memory, one for each BrowserItems a test makes.
    private final MemoryStores memory = new MemoryStores();

    @AfterEach void closeStores() throws Exception {
        memory.close();
    }

    @Test void aUrlOverTheBudgetIsSavedWithoutItsQueryAndFragment() {
        URI url = URI.create("https://example.com/search?q=" + "a".repeat(5_000) + "#results");
        assertThat(BrowserRestore.state(url, Optional.empty(), OptionalInt.empty())).get().satisfies(state -> {
            assertThat(state.kind()).isEqualTo(BrowserRestore.KIND).isEqualTo("dev.jasper.browser.page");
            assertThat(state.values()).containsExactly(entry("url", "https://example.com/search"));
        });
        URI fragment = URI.create("https://example.com/page#" + "f".repeat(5_000));
        assertThat(BrowserRestore.state(fragment, Optional.of("docs"), OptionalInt.empty()).orElseThrow().values())
            .containsOnly(entry("url", "https://example.com/page"), entry("target", "docs"));
    }

    @Test void aUrlStillOverTheBudgetWithoutItsQuerySavesNoUrl() {
        URI url = URI.create("https://example.com/" + "p".repeat(5_000) + "?q=1");
        assertThat(BrowserRestore.state(url, Optional.empty(), OptionalInt.empty())).get().satisfies(state -> {
            assertThat(state.kind()).isEqualTo(BrowserRestore.KIND);
            assertThat(state.values()).as("no url: the item comes back at the start page").isEmpty();
        });
        assertThat(BrowserRestore.state(url, Optional.of("docs"), OptionalInt.empty()).orElseThrow().values())
            .containsExactly(entry("target", "docs"));
    }

    /** The budget counts the keys as well as the values, and the target's entry with the url's. */
    @Test void theBudgetCountsEveryKeyAndValue() {
        String prefix = "https://example.com/";
        URI fits = URI.create(prefix + "p".repeat(RestoreState.MAX_CHARACTERS - "url".length() - prefix.length()) + "?q");
        URI whole = URI.create(fits.toString().substring(0, fits.toString().length() - 2));
        assertThat(whole.toString().length() + "url".length()).isEqualTo(RestoreState.MAX_CHARACTERS);
        assertThat(BrowserRestore.state(whole, Optional.empty(), OptionalInt.empty()).orElseThrow().values())
            .containsExactly(entry("url", whole.toString()));
        assertThat(BrowserRestore.state(fits, Optional.empty(), OptionalInt.empty()).orElseThrow().values()).as("one character over, with its query")
            .containsExactly(entry("url", whole.toString()));
        assertThat(BrowserRestore.state(whole, Optional.of("t"), OptionalInt.empty()).orElseThrow().values()).as("the target's entry takes 7")
            .containsExactly(entry("target", "t"));
        String shorter = whole.toString().substring(0, whole.toString().length() - "target".length() - 1);
        assertThat(BrowserRestore.state(URI.create(shorter), Optional.of("t"), OptionalInt.empty()).orElseThrow().values())
            .containsOnly(entry("url", shorter), entry("target", "t"));
    }

    @Test void dataAndBlobPagesSaveNoUrl() {
        for (String address : List.of("data:text/html,Hello%20there", "DATA:text/plain;base64,SGk=",
                "blob:https://example.com/0c0e9c4a-4b5e-4f4c-9a39-0d2f0b6d2b8e")) {
            assertThat(BrowserRestore.state(URI.create(address), Optional.empty(), OptionalInt.empty()).orElseThrow().values()).as(address).isEmpty();
            assertThat(BrowserRestore.state(URI.create(address), Optional.of("docs"), OptionalInt.empty()).orElseThrow().values()).as(address)
                .containsExactly(entry("target", "docs"));
        }
    }

    @Test void jasperAboutPagesSaveTheirUrl() {
        assertThat(BrowserRestore.state(START, Optional.empty(), OptionalInt.empty()).orElseThrow().values())
            .containsExactly(entry("url", "jasper-about:welcome"));
        assertThat(BrowserRestore.url(BrowserRestore.state(START, Optional.empty(), OptionalInt.empty()).orElseThrow())).contains(START);
    }

    @Test void aUrlAndATargetRoundTrip() {
        URI url = URI.create("https://example.com/a%20b?q=%E2%9C%93&x=1#part");
        RestoreState state = BrowserRestore.state(url, Optional.of("docs"), OptionalInt.empty()).orElseThrow();
        assertThat(state.values()).containsOnly(entry("url", url.toString()), entry("target", "docs"));
        assertThat(BrowserRestore.url(state)).contains(url);
        assertThat(BrowserRestore.target(state)).contains("docs");
        RestoreState untargeted = BrowserRestore.state(url, Optional.empty(), OptionalInt.empty()).orElseThrow();
        assertThat(untargeted.values()).containsOnlyKeys("url");
        assertThat(BrowserRestore.target(untargeted)).isEmpty();
    }

    /** Spec section 16: an item never saves credentials, so a URL is saved without its user name and password. */
    @Test void aUrlIsSavedWithoutItsUserNameAndPassword() {
        URI url = URI.create("https://ada:s%40cret@example.com:8443/a%2Fb%20c?q=%E2%9C%93&x=1#part");
        RestoreState state = BrowserRestore.state(url, Optional.of("docs"), OptionalInt.empty()).orElseThrow();
        assertThat(state.values()).containsOnly(entry("url", "https://example.com:8443/a%2Fb%20c?q=%E2%9C%93&x=1#part"), entry("target", "docs"));
        assertThat(BrowserRestore.url(state)).contains(URI.create("https://example.com:8443/a%2Fb%20c?q=%E2%9C%93&x=1#part"));
        assertThat(BrowserRestore.state(URI.create("http://ada@example.com/"), Optional.empty(), OptionalInt.empty()).orElseThrow().values())
            .as("a user name alone").containsExactly(entry("url", "http://example.com/"));
        assertThat(BrowserRestore.state(URI.create("ftp://ada:pw@example.com"), Optional.empty(), OptionalInt.empty()).orElseThrow().values())
            .as("no path").containsExactly(entry("url", "ftp://example.com"));
        assertThat(BrowserRestore.state(URI.create("http://ada:pw@[::1]:8080/x"), Optional.empty(), OptionalInt.empty()).orElseThrow().values())
            .as("an IPv6 host").containsExactly(entry("url", "http://[::1]:8080/x"));
        // java.net.URI reads an authority whose host has an underscore as a registry, with no user information of its own.
        assertThat(BrowserRestore.state(URI.create("http://admin:hunter2@build_server:8080/job"), Optional.empty(), OptionalInt.empty())
            .orElseThrow().values()).as("a host with an underscore").containsExactly(entry("url", "http://build_server:8080/job"));
        assertThat(BrowserRestore.withoutUserInfo(URI.create("https://a@b@host.example/x"))).as("up to the last @")
            .hasToString("https://host.example/x");
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
        assertThat(BrowserRestore.state(EXAMPLE, Optional.of("bad\u0000target"), OptionalInt.empty()).orElseThrow().values())
            .as("an invalid target is not saved")
            .containsExactly(entry("url", EXAMPLE.toString()));
    }

    @Test void aZoomRoundTripsWithTheUrlAndTheTarget() {
        URI url = URI.create("https://example.com/docs?q=1#part");
        RestoreState state = BrowserRestore.state(url, Optional.of("docs"), OptionalInt.of(150)).orElseThrow();
        assertThat(state.values()).containsOnly(entry("url", url.toString()), entry("target", "docs"), entry("zoom", "150"));
        assertThat(BrowserRestore.zoom(state)).hasValue(150);
        assertThat(BrowserRestore.url(state)).contains(url);
        assertThat(BrowserRestore.target(state)).contains("docs");
        for (int percent : new int[] { Zoom.MIN, 100, Zoom.MAX })
            assertThat(BrowserRestore.zoom(BrowserRestore.state(EXAMPLE, Optional.empty(), OptionalInt.of(percent)).orElseThrow()))
                .hasValue(percent);
        RestoreState none = BrowserRestore.state(url, Optional.empty(), OptionalInt.empty()).orElseThrow();
        assertThat(none.values()).as("an item with no zoom of its own").containsOnlyKeys("url");
        assertThat(BrowserRestore.zoom(none)).isEmpty();
        assertThat(BrowserRestore.state(URI.create("data:text/html,Hi"), Optional.empty(), OptionalInt.of(90)).orElseThrow().values())
            .as("a data: page saves its zoom, not its url").containsExactly(entry("zoom", "90"));
    }

    /** Review focus 5: the zoom survives within the budget, and a url that cannot fit beside it goes first. */
    @Test void aLongUrlWithAZoomKeepsTheZoom() {
        String prefix = "https://example.com/";
        URI whole = URI.create(prefix + "p".repeat(RestoreState.MAX_CHARACTERS - "url".length() - prefix.length()));
        assertThat(BrowserRestore.state(whole, Optional.empty(), OptionalInt.empty()).orElseThrow().values()).containsOnlyKeys("url");
        assertThat(BrowserRestore.state(whole, Optional.empty(), OptionalInt.of(150)).orElseThrow().values())
            .as("the zoom's entry takes 7: the url goes").containsExactly(entry("zoom", "150"));
        String shorter = whole.toString().substring(0, whole.toString().length() - "zoom150".length());
        assertThat(BrowserRestore.state(URI.create(shorter), Optional.empty(), OptionalInt.of(150)).orElseThrow().values())
            .containsOnly(entry("url", shorter), entry("zoom", "150"));

        String base = prefix + "p".repeat(RestoreState.MAX_CHARACTERS - "url".length() - prefix.length() - 21);
        URI query = URI.create(base + "?" + "q".repeat(20));
        assertThat(query.toString().length() + "url".length()).isEqualTo(RestoreState.MAX_CHARACTERS);
        assertThat(BrowserRestore.state(query, Optional.empty(), OptionalInt.empty()).orElseThrow().values())
            .containsExactly(entry("url", query.toString()));
        assertThat(BrowserRestore.state(query, Optional.empty(), OptionalInt.of(150)).orElseThrow().values())
            .as("beside the zoom, without its query").containsOnly(entry("url", base), entry("zoom", "150"));

        URI search = URI.create("https://example.com/search?q=" + "a".repeat(5_000) + "#results");
        assertThat(BrowserRestore.state(search, Optional.of("docs"), OptionalInt.of(25)).orElseThrow().values())
            .containsOnly(entry("url", "https://example.com/search"), entry("target", "docs"), entry("zoom", "25"));
        URI path = URI.create(prefix + "p".repeat(5_000));
        assertThat(BrowserRestore.state(path, Optional.of("t".repeat(256)), OptionalInt.of(500)).orElseThrow().values())
            .containsOnly(entry("target", "t".repeat(256)), entry("zoom", "500"));
    }

    /**
     * {@code workspace.toml} is plain text anyone can edit, so a saved zoom comes back only as a whole percent, written in
     * decimal digits, in {@link Zoom}'s range, which {@code BrowserRestore} keeps as its own as it cannot use the item
     * package. The item passes it only such a zoom to save.
     */
    @Test void aSavedZoomComesBackOnlyAsAWholePercentInZoomsRange() {
        assertThat(BrowserRestore.zoom(saved(Map.of("zoom", Integer.toString(Zoom.MIN))))).hasValue(Zoom.MIN);
        assertThat(BrowserRestore.zoom(saved(Map.of("zoom", Integer.toString(Zoom.MAX))))).hasValue(Zoom.MAX);
        assertThat(BrowserRestore.zoom(saved(Map.of("zoom", "100", "url", EXAMPLE.toString())))).hasValue(100);
        for (String zoom : List.of(Integer.toString(Zoom.MIN - 1), Integer.toString(Zoom.MAX + 1), "5", "0", "-5", "+100", "100.0", "1e2",
                " 100", "100 ", "", "abc", "99999999999", "\u0661\u0660\u0660"))
            assertThat(BrowserRestore.zoom(saved(Map.of("zoom", zoom)))).as(zoom).isEmpty();
        assertThat(BrowserRestore.zoom(saved(Map.of()))).isEmpty();
        for (int outside : new int[] { Zoom.MIN - 1, Zoom.MAX + 1 })
            assertThatThrownBy(() -> BrowserRestore.state(EXAMPLE, Optional.empty(), OptionalInt.of(outside)))
                .as(Integer.toString(outside)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void aRestoredItemOpensAtOnceAndLoadsOnlyWhenItFirstShows() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var asked = new java.util.ArrayList<Page>();
            var items = restoring(host, asked::add);
            UUID window = host.addTerminalWindow();
            RestoreState saved = BrowserRestore.state(EXAMPLE, Optional.of("docs"), OptionalInt.empty()).orElseThrow();
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
            RestoreState saved = BrowserRestore.state(EXAMPLE, Optional.of("docs"), OptionalInt.empty()).orElseThrow();
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
                    BrowserRestore.state(URI.create("https://example.com/" + i), Optional.empty(), OptionalInt.empty()).orElseThrow())).isPresent();
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
                assertThat(host.restoreState(tab.get())).contains(BrowserRestore.state(START, Optional.empty(), OptionalInt.empty()).orElseThrow());
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

    /**
     * Spec section 10 and review focus 5: a new item shows at Page zoom and saves no zoom of its own. Increase and Decrease
     * Font Size, which Jasper's keys and menu run, zoom its page 5% at a time, and the item saves each such zoom, beside
     * each address it commits after. Reset Font Size goes back to Page zoom and saves no zoom, so the item comes back at
     * Page zoom.
     */
    @Test void theFontSizeCommandsZoomThePageInStepsAndTheItemSavesItsZoom() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            host.setSetting(ID, "page_zoom", 120L);
            var items = restoring(host, page -> { });
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            BrowserItem item = items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.of("docs"), Optional.empty());
            Page page = ItemTestAccess.page(item);
            assertThat(PageTestAccess.zoomLevel(page)).as("Page zoom, as Chromium's level").isCloseTo(1.0, within(1e-12));
            assertThat(host.restoreState(item.tab())).as("no zoom of its own").contains(state(EXAMPLE, "docs"));

            command(host, item, "font-bigger");
            assertThat(PageTestAccess.zoomLevel(page)).isEqualTo(Zoom.cefLevel(125));
            assertThat(host.restoreState(item.tab())).contains(state(EXAMPLE, "docs", 125));
            command(host, item, "font-smaller");
            command(host, item, "font-smaller");
            assertThat(PageTestAccess.zoomLevel(page)).isEqualTo(Zoom.cefLevel(115));
            assertThat(host.restoreState(item.tab())).contains(state(EXAMPLE, "docs", 115));
            URI next = URI.create("https://example.org/next");
            item.load(next);
            assertThat(host.restoreState(item.tab())).as("not shown yet: what it will load, at its zoom").contains(state(next, "docs", 115));

            var parent = new JPanel();
            parent.addNotify();
            try {
                parent.add(ItemTestAccess.pageView(item));
                URI later = URI.create("https://example.net/later");
                PageTestAccess.addressChanged(page, later.toString());
                assertThat(host.restoreState(item.tab())).as("committed").contains(state(later, "docs", 115));
                command(host, item, "font-reset");
                assertThat(PageTestAccess.zoomLevel(page)).as("Page zoom").isEqualTo(Zoom.cefLevel(120));
                assertThat(host.restoreState(item.tab())).as("no zoom of its own again").contains(state(later, "docs"));
                command(host, item, "font-bigger");
                PageTestAccess.addressChanged(page, "data:text/html,Hello");
                assertThat(host.restoreState(item.tab()).orElseThrow().values()).as("a data: page saves its zoom, not its url")
                    .containsOnly(entry("target", "docs"), entry("zoom", "125"));
            } finally {
                parent.removeNotify();
            }
            item.close();
            for (String id : List.of("font-bigger", "font-smaller", "font-reset")) command(host, item, id);
            assertThat(PageTestAccess.zoomLevel(page)).as("closed: nothing changes").isEqualTo(Zoom.cefLevel(125));
            assertThat(host.failures()).isEmpty();
        } });
    }

    /** Review focus 5 and plan 4b's P33: 25% and 500%, Chromium's smallest and largest zoom, stop the steps. */
    @Test void theStepsStopAtTwentyFiveAndFiveHundredPercent() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var items = restoring(host, page -> { });
            UUID window = host.addTerminalWindow();
            Optional<UUID> large = host.restoreTab(ID, window, "Large",
                BrowserRestore.state(EXAMPLE, Optional.empty(), OptionalInt.of(498)).orElseThrow());
            BrowserItem item = items.items().getLast();
            command(host, item, "font-bigger");
            assertThat(BrowserRestore.zoom(host.restoreState(large.orElseThrow()).orElseThrow())).hasValue(500);
            command(host, item, "font-bigger");
            assertThat(BrowserRestore.zoom(host.restoreState(large.orElseThrow()).orElseThrow())).hasValue(500);
            assertThat(PageTestAccess.zoomLevel(ItemTestAccess.page(item))).isEqualTo(Zoom.cefLevel(500));

            Optional<UUID> small = host.restoreTab(ID, window, "Small",
                BrowserRestore.state(EXAMPLE, Optional.empty(), OptionalInt.of(27)).orElseThrow());
            item = items.items().getLast();
            command(host, item, "font-smaller");
            assertThat(BrowserRestore.zoom(host.restoreState(small.orElseThrow()).orElseThrow())).hasValue(25);
            command(host, item, "font-smaller");
            assertThat(BrowserRestore.zoom(host.restoreState(small.orElseThrow()).orElseThrow())).hasValue(25);
            assertThat(PageTestAccess.zoomLevel(ItemTestAccess.page(item))).isEqualTo(Zoom.cefLevel(25));
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * A restored item shows at the zoom it saved and saves it again, also while it waits after a run that ended while
     * Chromium ran; its page takes the zoom once it has a browser. A saved zoom outside 25% to 500% is ignored: the item
     * comes back at Page zoom.
     */
    @Test void aRestoredItemComesBackAtItsSavedZoomAlsoWhileItWaits() throws Exception {
        for (boolean wait : new boolean[] { false, true }) {
            SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
                var items = restoring(host, page -> { }, wait);
                UUID window = host.addTerminalWindow();
                RestoreState saved = BrowserRestore.state(EXAMPLE, Optional.of("docs"), OptionalInt.of(150)).orElseThrow();
                Optional<UUID> tab = host.restoreTab(ID, window, "Docs", saved);
                BrowserItem item = items.items().getLast();
                assertThat(ItemTestAccess.waiting(item)).isEqualTo(wait);
                assertThat(PageTestAccess.zoomLevel(ItemTestAccess.page(item))).isEqualTo(Zoom.cefLevel(150));
                assertThat(host.restoreState(tab.orElseThrow())).as("it saves what it was saved as").contains(saved);

                Optional<UUID> outside = host.restoreTab(ID, window, "Docs", saved(Map.of("url", EXAMPLE.toString(), "zoom", "501")));
                item = items.items().getLast();
                assertThat(PageTestAccess.zoomLevel(ItemTestAccess.page(item))).as("Page zoom").isEqualTo(0.0);
                assertThat(host.restoreState(outside.orElseThrow()).orElseThrow().values()).containsExactly(entry("url", EXAMPLE.toString()));
                assertThat(host.declinedRestores()).isEmpty();
                assertThat(host.failures()).isEmpty();
            } });
        }
    }

    /**
     * Spec sections 7 and 10: on Windows and Linux, Ctrl+wheel over the page, which touchpad pinch sends on Windows,
     * magnifies it a step a turn and is not saved; the font size commands step from the zoom it shows. On macOS Ctrl+wheel
     * is the page's.
     */
    @Test void ctrlWheelOverThePageStepsItsZoomWithoutSavingIt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var items = restoring(host, page -> { });
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            BrowserItem item = items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            Page page = ItemTestAccess.page(item);
            PageTestAccess.useOsName(page, "Windows 11");
            command(host, item, "font-bigger");
            RestoreState saved = host.restoreState(item.tab()).orElseThrow();
            assertThat(BrowserRestore.zoom(saved)).hasValue(105);

            MouseWheelEvent away = wheel(item, InputEvent.CTRL_DOWN_MASK, -1);
            ItemTestAccess.pageView(item).dispatchEvent(away);
            assertThat(away.isConsumed()).as("never the page's").isTrue();
            assertThat(PageTestAccess.zoomLevel(page)).isEqualTo(Zoom.cefLevel(110));
            assertThat(host.restoreState(item.tab())).as("not saved").contains(saved);
            ItemTestAccess.pageView(item).dispatchEvent(wheel(item, InputEvent.CTRL_DOWN_MASK, -1));
            ItemTestAccess.pageView(item).dispatchEvent(wheel(item, InputEvent.CTRL_DOWN_MASK, 3));
            assertThat(PageTestAccess.zoomLevel(page)).as("a step a turn").isEqualTo(Zoom.cefLevel(110));
            assertThat(host.restoreState(item.tab())).contains(saved);
            MouseWheelEvent plain = wheel(item, 0, -1);
            ItemTestAccess.pageView(item).dispatchEvent(plain);
            assertThat(plain.isConsumed()).as("the page's").isFalse();
            assertThat(PageTestAccess.zoomLevel(page)).isEqualTo(Zoom.cefLevel(110));

            PageTestAccess.useOsName(page, "Mac OS X");
            MouseWheelEvent mac = wheel(item, InputEvent.CTRL_DOWN_MASK, -1);
            ItemTestAccess.pageView(item).dispatchEvent(mac);
            assertThat(mac.isConsumed()).as("on macOS, the page's").isFalse();
            assertThat(PageTestAccess.zoomLevel(page)).isEqualTo(Zoom.cefLevel(110));

            command(host, item, "font-bigger");
            assertThat(BrowserRestore.zoom(host.restoreState(item.tab()).orElseThrow())).as("a step from the zoom shown").hasValue(115);
            assertThat(host.failures()).isEmpty();
        } });
    }

    private static RestoreState state(URI url, String target) {
        return new RestoreState(BrowserRestore.KIND, Map.of("url", url.toString(), "target", target));
    }

    private static RestoreState state(URI url, String target, int zoom) {
        return new RestoreState(BrowserRestore.KIND, Map.of("url", url.toString(), "target", target, "zoom", Integer.toString(zoom)));
    }

    /** Runs the item's content command {@code id}, as Jasper's action of that name does. */
    private static void command(FakePluginHost host, BrowserItem item, String id) {
        host.contentCommands(item.tab()).stream().filter(each -> each.id().equals(id)).findFirst().orElseThrow().run().run();
    }

    /** A turn of the wheel over the item's page, of {@code notches}, away from the user when negative. */
    private static MouseWheelEvent wheel(BrowserItem item, int modifiers, double notches) {
        return new MouseWheelEvent(ItemTestAccess.pageView(item), MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), modifiers, 10, 10,
            10, 10, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, (int) notches, notches);
    }

    private static RestoreState saved(Map<String, String> values) {
        return new RestoreState(BrowserRestore.KIND, values);
    }

    /**
     * The items of a started stand-in plugin whose pages {@code start} makes, restored as the Browser's restorer restores
     * them, with {@link #START} as the start page. UI thread.
     */
    private BrowserItems restoring(FakePluginHost host, Consumer<Page> start) {
        return restoring(host, start, false);
    }

    /** As {@link #restoring(FakePluginHost, Consumer)}; with {@code wait}, as after a run that ended while Chromium ran. */
    private BrowserItems restoring(FakePluginHost host, Consumer<Page> start, boolean wait) {
        var context = new AtomicReference<PluginContext>();
        var items = new AtomicReference<BrowserItems>();
        host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(), started -> {
            context.set(started);
            items.set(new BrowserItems(started, BrowserSettings.declare(started.settings())::read, memory.make(), start));
            started.windows().restorer(BrowserRestore.KIND, request -> {
                items.get().restore(request.state(), START, request.window(), wait);
                return Optional.empty();
            });
        });
        return items.get();
    }
}
