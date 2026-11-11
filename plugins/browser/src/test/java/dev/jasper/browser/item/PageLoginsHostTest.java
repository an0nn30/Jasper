package dev.jasper.browser.item;

import dev.jasper.browser.logins.FakeVault;
import dev.jasper.browser.logins.FieldButtons;
import dev.jasper.browser.logins.LoginsTestAccess;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageQuery;
import dev.jasper.browser.page.PageTestAccess;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.browser.store.MemoryStores;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.ContentArea;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.TabPlacement;
import dev.jasper.sdk.ui.UiAnchor;
import dev.jasper.sdk.ui.ViewSpec;
import dev.jasper.vault.api.CredentialPickResult;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A browser item's field buttons (plan 8's Task 4b, F18): with the Credential Vault, its {@link PageLoginsHost} hands
 * the page's login queries to the item's {@link FieldButtons}, which open the Vault's picker on the field's area of the
 * item's view, at the item's zoom, over its tab's window; a /dev/null item has them too (F24). The picker closes, its
 * anchor with it, as the item's zoom changes, as its page navigates, as its canvas is resized, and as it closes (L6).
 * Without the Vault the page has no password manager. The page is a stand-in's, as one cannot be made without Chromium,
 * and the Vault is {@link FakeVault}.
 */
class PageLoginsHostTest {
    private static final String ID = "dev.jasper.browser";
    private static final String ORIGIN = "https://login.example";
    private final MemoryStores memory = new MemoryStores();
    private final FakeVault fake = new FakeVault();
    private final List<String> replies = new CopyOnWriteArrayList<>();
    private FakePluginHost host;
    private PluginContext context;
    private BrowserItems items;
    private JPanel parent;

    @AfterEach void close() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            if (parent != null) parent.removeNotify();
            if (host != null) host.close();
        });
        memory.close();
    }

    @Test void aPickOpensTheVaultsPickerOnTheFieldsAreaAtTheItemsZoomOverItsWindow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            BrowserItem item = shown(open(true));
            for (int step = 0; step < 5; step++) command(item, "font-bigger");
            fake.pickAnswer = new CompletableFuture<>();
            ask(item, "password", 8, 16, 200, 24);
            assertThat(fake.picks).singleElement().satisfies(request -> {
                assertThat(request.search()).isEqualTo("login.example");
                assertThat(area(request.anchor())).as("(8, 16, 200, 24) at 125%").contains(new ContentArea(10, 20, 250, 30));
            });
            assertThat(fake.pickOwners).containsExactly(item.tab().window());
            assertThat(replies).isEmpty();
            fake.pickAnswer.complete(new CredentialPickResult.Cancelled());
            assertThat(replies).containsExactly("0 {}");
            assertThat(host.failures()).isEmpty();
        });
    }

    /** F24: a /dev/null item fills saved passwords too, as Chrome's Incognito does; nothing is written to the Vault. */
    @Test void aDevNullItemHasTheButtonsToo() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            fake.login("https://login.example (Corp)", "ada", "pw");
            items = items(true);
            BrowserItem item = shown(items.open(URI.create(ORIGIN + "/sign-in"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty(),
                items.dataStores().newDevNull()));
            assertThat(item.store().devNull()).isTrue();
            ask(item, "username", 8, 16, 200, 24);
            assertThat(replies).containsExactly("0 {\"username\":\"ada\",\"password\":\"pw\"}");
            assertThat(fake.calls).as("read, never saved").containsExactly("credential https://login.example (Corp)");
            assertThat(host.failures()).isEmpty();
        });
    }

    /** L6: the item's zoom moves the page's fields under the picker, so a zoom change closes it. */
    @Test void aZoomChangeClosesThePicker() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            BrowserItem item = shown(open(true));
            for (String zoom : List.of("font-bigger", "font-smaller", "font-reset")) {
                CompletableFuture<CredentialPickResult> picker = new CompletableFuture<>();
                fake.pickAnswer = picker;
                ask(item, "password", 8, 16, 200, 24);
                UiAnchor anchor = fake.picks.getLast().anchor();
                command(item, zoom);
                assertThat(picker).as(zoom).isCancelled();
                assertThat(anchor.isOpen()).as(zoom).isFalse();
            }
            assertThat(replies).containsExactly("0 {}", "0 {}", "0 {}");
            assertThat(host.failures()).isEmpty();
        });
    }

    /** L6, review focus 2: the page going elsewhere closes the picker, whichever way it goes. */
    @Test void aNavigationClosesThePicker() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            BrowserItem item = shown(open(true));
            List<Consumer<Page>> navigations = List.of(page -> PageTestAccess.loadStarted(page, ORIGIN + "/next"),
                page -> PageTestAccess.addressChanged(page, ORIGIN + "/sign-in#two"), page -> item.load(URI.create(ORIGIN + "/third")));
            for (Consumer<Page> navigation : navigations) {
                CompletableFuture<CredentialPickResult> picker = new CompletableFuture<>();
                fake.pickAnswer = picker;
                ask(item, "password", 8, 16, 200, 24);
                UiAnchor anchor = fake.picks.getLast().anchor();
                navigation.accept(item.page());
                assertThat(picker).isCancelled();
                assertThat(anchor.isOpen()).isFalse();
            }
            assertThat(fake.picks).hasSize(3);
            assertThat(host.failures()).isEmpty();
        });
    }

    /** L6, UiAnchor's contract: the host closes an area's anchor as the canvas is resized, and the picker goes with it. */
    @Test void aResizeOfThePagesCanvasClosesThePicker() throws Exception {
        var picker = new CompletableFuture<CredentialPickResult>();
        var item = new AtomicReference<BrowserItem>();
        SwingUtilities.invokeAndWait(() -> {
            item.set(shown(open(true)));
            fake.pickAnswer = picker;
            ask(item.get(), "password", 8, 16, 200, 24);
            item.get().pageView().setSize(700, 600);
        });
        SwingUtilities.invokeAndWait(() -> {
            assertThat(picker).as("closed once the resize was told").isCancelled();
            assertThat(fake.picks.getLast().anchor().isOpen()).isFalse();
            assertThat(replies).containsExactly("0 {}");
            fake.pickAnswer = new CompletableFuture<>();
            ask(item.get(), "password", 8, 16, 200, 24);
            assertThat(fake.picks).as("a pick after it opens").hasSize(2);
        });
    }

    @Test void closingTheItemClosesThePicker() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            BrowserItem item = shown(open(true));
            CompletableFuture<CredentialPickResult> picker = new CompletableFuture<>();
            fake.pickAnswer = picker;
            ask(item, "password", 8, 16, 200, 24);
            item.close();
            assertThat(picker).isCancelled();
            assertThat(replies).as("told nothing was chosen").containsExactly("0 {}");
            assertThat(fake.picks.getLast().anchor().isOpen()).isFalse();
            assertThat(host.failures()).isEmpty();
        });
    }

    /** A waiting item's canvas is hidden, so the host refuses an anchor on it and the page is told the field is not there. */
    @Test void aWaitingItemRefusesAPick() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            items = items(true);
            BrowserItem item = BrowserItem.open(items, URI.create(ORIGIN + "/sign-in"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty(),
                OptionalInt.empty(), true, items.dataStores().forNewItem());
            item.pageView().setSize(800, 600);
            ask(item, "password", 8, 16, 200, 24);
            assertThat(fake.picks).isEmpty();
            assertThat(replies).containsExactly(PageQuery.REFUSED + " The field is not on the page.");
            assertThat(host.failures()).isEmpty();
        });
    }

    @Test void withoutTheVaultThePageHasNoPasswordManager() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            BrowserItem item = shown(open(false));
            ask(item, "password", 8, 16, 200, 24);
            assertThat(replies).containsExactly(PageQuery.REFUSED + " This page has no password manager.");
            assertThat(host.failures()).isEmpty();
        });
    }

    /** The page's top frame asks for a pick of {@code field} at the CSS rectangle given, after a click, from {@link #ORIGIN}. */
    private void ask(BrowserItem item, String field, long x, long y, long width, long height) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("op", FieldButtons.PICK);
        request.put("field", field);
        request.put("x", x);
        request.put("y", y);
        request.put("width", width);
        request.put("height", height);
        item.page().loginQuery(new PageQuery(item.page(), Optional.of(ORIGIN), request, () -> true, () -> Optional.of(ORIGIN),
            (code, text) -> replies.add(code + " " + text)));
    }

    /**
     * Where a popover on {@code anchor} shows: the area the host would place it below, read from a popover the test
     * shows on it, as the Vault's picker would be.
     */
    private Optional<ContentArea> area(UiAnchor anchor) {
        var popover = context.windows().popover(anchor);
        popover.setContent(context.kit().create(new ViewSpec(new Nodes.Button("probe", ControlState.READY, "Probe", "",
            Nodes.ButtonKind.PRIMARY, "probe", "")), event -> { }));
        popover.show();
        List<ContentArea> shown = host.popoverAreas(ID);
        popover.close();
        return shown.isEmpty() ? Optional.empty() : Optional.of(shown.getLast());
    }

    /** An item at {@link #ORIGIN}'s sign-in page on a new fake host, with the Vault or without it. UI thread. */
    private BrowserItem open(boolean vault) {
        items = items(vault);
        return items.open(URI.create(ORIGIN + "/sign-in"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
    }

    /** The items of a started stand-in plugin, with the Vault's logins or without them. UI thread. */
    private BrowserItems items(boolean vault) {
        host = new FakePluginHost();
        var started = new AtomicReference<PluginContext>();
        var settings = new AtomicReference<BrowserSettings.Declared>();
        host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(), plugin -> {
            started.set(plugin);
            settings.set(BrowserSettings.declare(plugin.settings()));
        });
        context = started.get();
        var stores = new DataStores(memory.make(), memory::make, () -> PageTestAccess.recordingContext(new AtomicInteger()), () -> false);
        return new BrowserItems(context, settings.get()::read, stores, vault ? Optional.of(LoginsTestAccess.logins(fake.api())) : Optional.empty(),
            page -> { }, (Duration delay, Runnable task) -> () -> { }, request -> new CompletableFuture<>());
    }

    /** Shows the item's page canvas at 800 by 600: a lightweight parent given a peer shows, headless. */
    private BrowserItem shown(BrowserItem item) {
        parent = new JPanel(null);
        parent.addNotify();
        parent.add(item.pageView());
        item.pageView().setSize(800, 600);
        assertThat(item.pageView().isShowing()).isTrue();
        return item;
    }

    private void command(BrowserItem item, String id) {
        host.contentCommands(item.tab()).stream().filter(each -> each.id().equals(id)).findFirst().orElseThrow().run().run();
    }
}
