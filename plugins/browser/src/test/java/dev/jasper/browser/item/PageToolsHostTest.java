package dev.jasper.browser.item;

import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageFind;
import dev.jasper.browser.page.PageTestAccess;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.browser.store.MemoryStores;
import dev.jasper.browser.tools.PageTools;
import dev.jasper.browser.tools.ToolScripts;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.MenuItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.TabPlacement;
import dev.jasper.sdk.ui.WorkspacePalette;
import java.awt.Color;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A browser item's page tools (plan 7's Task 5, F38): its {@link PageToolsHost} runs reader mode's check in the page's
 * top frame as its load ends, offers Reader Mode and Remove Distractions first in the menu once the page reports enough
 * reader text, shows and hides the article in the theme's colours, and follows the page's reports and load starts. The
 * page is a stand-in's, as one cannot be made without Chromium: its scripts are recorded, and its load starts, load
 * ends and reports are told by the test.
 */
class PageToolsHostTest {
    private static final String ID = "dev.jasper.browser";
    private final MemoryStores memory = new MemoryStores();
    // The scripts the item's page ran, each as its address and the script.
    private final List<String> ran = new ArrayList<>();
    private FakePluginHost host;
    private PluginContext context;
    private BrowserItem item;
    private JPanel parent;

    @AfterEach void close() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            if (parent != null) parent.removeNotify();
            if (host != null) host.close();
        });
        memory.close();
    }

    /**
     * The check runs as the shown page's load ends; a report of more than 10 characters offers the items first in the
     * menu; Reader Mode shows the article in the theme's colours and becomes Exit Reader Mode; Escape's report ends it;
     * and a load start takes the items away.
     */
    @Test void theCheckOffersTheItemsWhichShowAndEndReaderMode() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            BrowserItem item = open("https://news.example/article");
            show(item);
            Page page = item.page();
            PageTestAccess.loadStarted(page, "https://news.example/article");
            PageTestAccess.loadEnded(page);
            assertThat(ran).containsExactly(ToolScripts.READER_URL + " " + ToolScripts.readerCheck(1));
            assertThat(menu()).extracting(MenuItem::id).doesNotContain(PageTools.READER, PageTools.DISTRACTIONS);

            page.toolReported("reader", "{\"op\":\"reader\",\"check\":1,\"length\":120}");
            assertThat(menu().subList(0, 3)).extracting(MenuItem::label).containsExactly("Reader Mode", "Remove Distractions", "");
            assertThat(menu().get(3).label()).as("then the bookmark group").isEqualTo("Add Bookmark");

            view().choose(BrowserToolbar.MENU, PageTools.READER);
            var appearance = context.appearance();
            String css = PageToolsHost.readerCss(appearance.workspacePalette(), appearance.workspaceVariant() == Variant.DARK);
            assertThat(ran.getLast()).isEqualTo(ToolScripts.READER_URL + " " + ToolScripts.readerShow(css, 1));
            assertThat(menu().getFirst().label()).isEqualTo("Exit Reader Mode");

            page.toolReported("reader", "{\"op\":\"reader\",\"check\":1,\"state\":\"off\"}");
            assertThat(menu().getFirst().label()).isEqualTo("Reader Mode");
            view().choose(BrowserToolbar.MENU, PageTools.DISTRACTIONS);
            assertThat(ran).as("distraction removal is plan 7's Task 6").hasSize(2);

            PageTestAccess.loadStarted(page, "https://news.example/next");
            assertThat(menu()).extracting(MenuItem::id).doesNotContain(PageTools.READER, PageTools.DISTRACTIONS);
            assertThat(host.failures()).isEmpty();
        });
    }

    /**
     * D31: reader mode opening or closing restarts the page's find, every frame clearing its highlights, and while it
     * shows a search reaches the top frame alone, whose overlay holds the article; once it closes, every frame again.
     */
    @Test void readerModeRestartsTheFindAndKeepsItToTheTopFrame() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            BrowserItem item = open("https://news.example/article");
            show(item);
            Page page = item.page();
            var found = new ArrayList<String>();
            PageFind find = PageTestAccess.recordingFind(List.of("main", "ad"), Optional.of("main"), found, new ArrayList<>());
            PageTestAccess.useFind(page, find);
            PageTestAccess.loadStarted(page, "https://news.example/article");
            PageTestAccess.loadEnded(page);
            page.toolReported("reader", "{\"op\":\"reader\",\"check\":1,\"length\":120}");
            find.find("needle", false, false);
            assertThat(frames(found)).containsExactly("main", "ad");
            found.clear();
            view().choose(BrowserToolbar.MENU, PageTools.READER);
            assertThat(found).containsExactly("main {\"action\":\"clear\"}", "ad {\"action\":\"clear\"}");
            assertThat(find.result()).isEmpty();
            found.clear();
            find.find("needle", false, false);
            assertThat(frames(found)).as("the article's frame alone").containsExactly("main");
            found.clear();
            page.toolReported("reader", "{\"op\":\"reader\",\"check\":1,\"state\":\"off\"}");
            assertThat(found).containsExactly("main {\"action\":\"clear\"}");
            found.clear();
            find.find("needle", false, false);
            assertThat(frames(found)).containsExactly("main", "ad");
            assertThat(host.failures()).isEmpty();
        });
    }

    private static List<String> frames(List<String> ran) {
        return ran.stream().map(each -> each.substring(0, each.indexOf(' '))).toList();
    }

    /** F24: an item whose page is not showing, as a background tab's, checks its page once it shows. */
    @Test void aHiddenItemChecksItsPageWhenItShows() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            BrowserItem item = open("https://news.example/article");
            Page page = item.page();
            PageTestAccess.loadStarted(page, "https://news.example/article");
            PageTestAccess.loadEnded(page);
            assertThat(ran).isEmpty();
            show(item);
            assertThat(ran).containsExactly(ToolScripts.READER_URL + " " + ToolScripts.readerCheck(1));
        });
    }

    /** Jasper's own pages get no check, and an item that closed hears its page no more. */
    @Test void aJasperPageAndAClosedItemRunNothing() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            BrowserItem item = open("jasper-about:welcome");
            show(item);
            Page page = item.page();
            PageTestAccess.loadStarted(page, "jasper-about:welcome");
            PageTestAccess.loadEnded(page);
            assertThat(ran).isEmpty();
            PageTestAccess.loadStarted(page, "https://news.example/article");
            item.close();
            PageTestAccess.loadEnded(page);
            page.toolReported("reader", "{\"op\":\"reader\",\"check\":1,\"length\":120}");
            assertThat(ran).isEmpty();
            assertThat(host.failures()).isEmpty();
        });
    }

    /** The reader's CSS: the theme's colours on the overlay's root, as Jasper's own pages name them, and its colour scheme. */
    @Test void theReadersCssIsTheThemesColoursOnTheOverlaysRoot() {
        var palette = new WorkspacePalette(Color.WHITE, new Color(0xf0f0f0), new Color(0xe0e0e0), new Color(0x111111),
            new Color(0x666666), Color.LIGHT_GRAY, new Color(0x2266cc), Color.WHITE, Color.GRAY, new Color(0x2266cc),
            new Color(0x228833), new Color(0xcc8800), new Color(0xcc2222));
        String css = PageToolsHost.readerCss(palette, false);
        assertThat(css).startsWith(".reader {\n").contains("  --background: #ffffff;\n", "  --text: #111111;\n", "  --accent: #2266cc;\n")
            .endsWith("\n.reader { color-scheme: light; }");
        assertThat(PageToolsHost.readerCss(palette, true)).endsWith("\n.reader { color-scheme: dark; }");
    }

    /** A stand-in's item at {@code url} on a new fake host, whose page's scripts the test records. UI thread. */
    private BrowserItem open(String url) {
        host = new FakePluginHost();
        var started = new AtomicReference<PluginContext>();
        var settings = new AtomicReference<BrowserSettings.Declared>();
        host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(), plugin -> {
            started.set(plugin);
            settings.set(BrowserSettings.declare(plugin.settings()));
        });
        context = started.get();
        var items = new BrowserItems(context, settings.get()::read, new DataStores(memory.make(), () -> false), page -> { },
            (Duration delay, Runnable task) -> () -> { }, request -> new CompletableFuture<>());
        item = items.open(URI.create(url), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
        PageTestAccess.useScriptRunner(item.page(), (script, address) -> ran.add(address + " " + script));
        return item;
    }

    /** Shows the item's page canvas: a lightweight parent given a peer shows, headless. */
    private void show(BrowserItem item) {
        parent = new JPanel();
        parent.addNotify();
        parent.add(item.pageView());
        assertThat(item.pageView().isShowing()).isTrue();
    }

    private FakeKitView view() {
        return host.contentView(ID, host.contentPresentation(item.tab()).title()).orElseThrow();
    }

    private List<MenuItem> menu() {
        return ((Nodes.MenuButton) Views.node(view().spec().root(), BrowserToolbar.MENU)).items();
    }
}
