package dev.jasper.browser.item;

import dev.jasper.browser.page.DevTools;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageTestAccess;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.ContentCommand;
import dev.jasper.sdk.ui.ContentTab;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.TabPlacement;
import java.awt.Point;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec section 10's Inspect Element on the testkit's fake host, through the item seam: the page's DevTools open in an item
 * of their own below the page, titled after it and never saved, one per page, which closes with the page and closes the
 * DevTools with it. The page gives the DevTools through its test seam, as a page without Chromium has no browser for
 * Chromium to make DevTools through, so Chromium's side, the DevTools ending, is played by the test. No Chromium.
 */
class DevToolsItemTest {
    private static final String ID = "dev.jasper.browser";
    private static final URI EXAMPLE = URI.create("https://example.com/");
    private FakePluginHost host;
    private BrowserItems items;
    private BrowserItem item;
    private Page page;
    private UUID window;

    @AfterEach void closeHost() throws Exception {
        SwingUtilities.invokeAndWait(() -> { if (host != null) host.close(); });
    }

    @Test void inspectElementOpensThePagesDevToolsBelowItTitledAfterThePageAndNeverSaved() throws Exception {
        onUiThread(() -> {
            open();
            PageTestAccess.titleChanged(page, "Example Domain");
            DevTools tools = inspect(40, 70);
            ContentTab devTools = host.contentTab(ID, "DevTools — Example Domain").orElseThrow();
            assertThat(host.restoreState(devTools)).as("never saved").isEmpty();
            assertThat(host.windowTabs(window)).as("a pane of the page's tab, not a tab of its own").singleElement()
                .satisfies(panes -> assertThat(panes).hasSize(2));
            assertThat(devTools.isActive()).as("shown and focused").isTrue();
            assertThat(canvas(devTools).kind()).as("one canvas, the DevTools'").isEqualTo(Nodes.CustomKind.PREVIEW);
            assertThat(tools.view()).as("a canvas of their own, not the page's").isNotSameAs(item.pageView());
            assertThat(host.contentCommands(devTools)).extracting(ContentCommand::id).as("Copy and Paste; find and zoom are DevTools' own")
                .containsExactly("copy", "paste");
            assertThat(host.failures()).isEmpty();
        });
    }

    @Test void theTitleFollowsThePagesTabTitle() throws Exception {
        onUiThread(() -> {
            open();
            inspect(0, 0);
            ContentTab devTools = host.contentTab(ID, "DevTools — example.com").orElseThrow();
            PageTestAccess.titleChanged(page, "A new title");
            assertThat(host.contentPresentation(devTools).title()).isEqualTo("DevTools — A new title");
            assertThat(host.contentPresentation(item.tab()).title()).isEqualTo("A new title");
            assertThat(host.restoreState(devTools)).as("still never saved").isEmpty();
        });
    }

    /** One DevTools item per page: Inspect Element again shows it, with the page's DevTools made anew at the new point. */
    @Test void aSecondInspectElementShowsTheSameItemWithNewDevTools() throws Exception {
        onUiThread(() -> {
            open();
            DevTools first = inspect(10, 10);
            ContentTab devTools = host.contentTab(ID, "DevTools — example.com").orElseThrow();
            String firstCanvas = canvas(devTools).contentId();
            var firstEnded = new AtomicInteger();
            first.onEnd(firstEnded::incrementAndGet);
            item.tab().select();
            assertThat(devTools.isActive()).isFalse();
            DevTools second = inspect(20, 30);
            assertThat(host.contentTabs()).as("the page's item and its one DevTools item").containsExactly(item.tab(), devTools);
            assertThat(devTools.isActive()).as("shown again").isTrue();
            assertThat(firstEnded).as("the DevTools before are closed").hasValue(1);
            assertThat(canvas(devTools).contentId()).as("the new DevTools' canvas took the old one's place").isNotEqualTo(firstCanvas);
            assertThat(second.view()).isNotSameAs(first.view());
            var secondEnded = new AtomicInteger();
            second.onEnd(secondEnded::incrementAndGet);
            assertThat(secondEnded).hasValue(0);
            assertThat(devTools.isClosed()).isFalse();
            assertThat(host.failures()).isEmpty();
        });
    }

    @Test void closingThePageClosesItsDevToolsItemAndItsDevTools() throws Exception {
        onUiThread(() -> {
            open();
            DevTools tools = inspect(5, 5);
            ContentTab devTools = host.contentTab(ID, "DevTools — example.com").orElseThrow();
            var ended = new AtomicInteger();
            tools.onEnd(ended::incrementAndGet);
            item.close();
            assertThat(devTools.isClosed()).isTrue();
            assertThat(ended).hasValue(1);
            assertThat(host.contentTabs()).isEmpty();
            assertThat(host.failures()).isEmpty();
        });
    }

    @Test void closingTheDevToolsItemClosesItsDevToolsAndLeavesThePageAndTheNextInspectOpensAnother() throws Exception {
        onUiThread(() -> {
            open();
            DevTools tools = inspect(5, 5);
            ContentTab devTools = host.contentTab(ID, "DevTools — example.com").orElseThrow();
            var ended = new AtomicInteger();
            tools.onEnd(ended::incrementAndGet);
            devTools.close();
            assertThat(ended).hasValue(1);
            assertThat(item.tab().isClosed()).isFalse();
            assertThat(item.devToolsItem()).as("the page's item keeps no closed DevTools item").isEmpty();
            inspect(6, 6);
            ContentTab again = host.contentTab(ID, "DevTools — example.com").orElseThrow();
            assertThat(again).isNotSameAs(devTools);
            assertThat(host.contentTabs()).containsExactly(item.tab(), again);
            assertThat(host.failures()).isEmpty();
        });
    }

    /** DevTools that Chromium closes, as it does when the page's browser goes, take their item with them. */
    @Test void devToolsChromiumClosesCloseTheirItem() throws Exception {
        onUiThread(() -> {
            open();
            DevTools tools = inspect(5, 5);
            ContentTab devTools = host.contentTab(ID, "DevTools — example.com").orElseThrow();
            PageTestAccess.devToolsEnded(tools);
            assertThat(devTools.isClosed()).isTrue();
            assertThat(item.tab().isClosed()).isFalse();
            assertThat(host.failures()).isEmpty();
        });
    }

    /** Spec section 7: the Web menu's keys win in the plugin's content tabs, the DevTools item among them, and act on its page. */
    @Test void theWebMenuActsOnThePageFromItsDevToolsItem() throws Exception {
        onUiThread(() -> {
            open();
            var ran = new ArrayList<String>();
            WebMenu.register(items.context(), items, (action, browser) -> ran.add(action + " " + browser.url()));
            inspect(5, 5);
            assertThat(host.contentTab(ID, "DevTools — example.com").orElseThrow().isActive()).isTrue();
            for (String action : List.of(WebMenu.RELOAD, WebMenu.BACK, WebMenu.OPEN_LOCATION))
                assertThat(host.invoke(action, window, null)).as(action).isTrue();
            assertThat(ran).containsExactly(WebMenu.RELOAD + " " + EXAMPLE, WebMenu.BACK + " " + EXAMPLE,
                WebMenu.OPEN_LOCATION + " " + EXAMPLE);
            assertThat(host.failures()).isEmpty();
        });
    }

    /**
     * A link the DevTools open in a new tab, as a URL clicked in their Console, opens a browser item next to the page's,
     * when the page could open it itself (4a's R36).
     */
    @Test void aLinkTheDevToolsOpenOpensNextToThePageWhenThePageCouldOpenIt() throws Exception {
        onUiThread(() -> {
            open();
            inspect(5, 5);
            page.openFromDevTools("file:///etc/hosts");
            page.openFromDevTools("jasper-about:welcome");
            assertThat(items.items()).as("an https page opens no local address").containsExactly(item);
            page.openFromDevTools("https://example.org/docs");
            assertThat(items.items()).hasSize(2);
            assertThat(items.items().get(1).url()).isEqualTo(URI.create("https://example.org/docs"));
            assertThat(host.windowTabs(window)).as("a tab of its own, next to the page's").hasSize(2);
            assertThat(host.failures()).isEmpty();
        });
    }

    /** A page that is not ready, as one without Chromium never is, opens no DevTools from the menu's Inspect Element. */
    @Test void inspectElementOnAPageWithoutItsBrowserOpensNothing() throws Exception {
        onUiThread(() -> {
            open();
            item.inspect(new Point(1, 1));
            assertThat(host.contentTabs()).containsExactly(item.tab());
            assertThat(host.failures()).isEmpty();
        });
    }

    /** The slot the DevTools item's canvas fills. UI thread. */
    private Nodes.Custom canvas(ContentTab devTools) {
        return (Nodes.Custom) host.contentView(ID, host.contentPresentation(devTools).title()).orElseThrow().spec().root();
    }

    /** The page's DevTools at a point on its canvas, through its test seam, shown as Inspect Element shows them. UI thread. */
    private DevTools inspect(int x, int y) {
        DevTools tools = PageTestAccess.devTools(page, new Point(x, y));
        item.showDevTools(tools);
        return tools;
    }

    /** Opens the plugin on a fake host with one browser item at {@link #EXAMPLE} in a window. UI thread. */
    private void open() {
        host = new FakePluginHost();
        var context = new AtomicReference<PluginContext>();
        var settings = new AtomicReference<BrowserSettings.Declared>();
        host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(), plugin -> {
            context.set(plugin);
            settings.set(BrowserSettings.declare(plugin.settings()));
        });
        items = new BrowserItems(context.get(), settings.get()::read, started -> { });
        window = host.addTerminalWindow();
        host.activateTerminalWindow(window);
        item = items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
        page = item.page();
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
