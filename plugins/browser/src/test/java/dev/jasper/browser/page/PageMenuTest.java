package dev.jasper.browser.page;

import dev.jasper.browser.item.BrowserItem;
import dev.jasper.browser.item.BrowserItems;
import dev.jasper.browser.item.ItemTestAccess;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.TabPlacement;
import java.awt.Point;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.cef.callback.CefContextMenuParams;
import org.cef.callback.CefRunContextMenuCallback;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The page's right-click menu over its item's tab on the testkit's fake host (spec section 11): the page's groups around
 * Jasper's pane items, and what each item does. Chromium's side is played by the test: what it reports under the pointer,
 * and its callback, which the menu ends exactly once, before an item runs, whether an item is chosen, the menu is
 * dismissed, one of Jasper's items runs, or the item closes under it. No Chromium.
 */
class PageMenuTest {
    private static final String ID = "dev.jasper.browser";
    private static final Optional<String> NONE = Optional.empty();
    private final AtomicInteger ended = new AtomicInteger();
    private FakePluginHost host;
    private BrowserItems items;
    private BrowserItem item;
    private Page page;

    @AfterEach void closeHost() throws Exception {
        SwingUtilities.invokeAndWait(() -> { if (host != null) host.close(); });
    }

    @Test void theMenuShowsOverTheItemsTabWithJaspersPaneItemsBetweenThePagesGroups() throws Exception {
        onUiThread(() -> {
            open();
            PageTestAccess.loadingChanged(page, false, true, false);
            show(new PageMenu.Pointed(new Point(12, 34), Optional.of("https://example.com/next"), Optional.of("https://example.com/next"),
                NONE, Optional.of("selected"), false));
            List<String> lines = host.contentMenu(item.tab());
            assertThat(lines).startsWith("item:page-menu.back", "item:page-menu.forward (disabled)", "item:page-menu.reload", "---",
                "item:page-menu.open-link", "item:page-menu.copy-link", "---", "item:page-menu.copy", "item:page-menu.search",
                "item:page-menu.select-all", "---", "host:Split");
            assertThat(lines).contains("host:Move Pane to Tab (disabled)", "host:Close Pane");
            assertThat(lines).endsWith("host:Close Pane", "---", "item:page-menu.print", "item:page-menu.copy-title", "---",
                "item:page-menu.view-source", "item:page-menu.inspect-element");
            assertThat(ended).as("Chromium's menu ends once the menu closes").hasValue(0);
            assertThat(host.failures()).isEmpty();
        });
    }

    @Test void copyLinkCopiesTheLinkAsThePageWroteItAndCopyImageAddressTheImagesAddress() throws Exception {
        onUiThread(() -> {
            open();
            var pointed = new PageMenu.Pointed(new Point(), Optional.of("about:blank#blocked"), Optional.of("file:///etc/hosts"),
                Optional.of("https://example.com/cat.png"), NONE, false);
            show(pointed);
            assertThat(host.chooseFromContentMenu(item.tab(), "item:page-menu.copy-link")).isTrue();
            assertThat(ended).hasValue(1);
            show(pointed);
            assertThat(host.chooseFromContentMenu(item.tab(), "item:page-menu.copy-image-address")).isTrue();
            assertThat(ended).hasValue(2);
            assertThat(host.clipboard().copies()).containsExactly(copied("file:///etc/hosts"), copied("https://example.com/cat.png"));
            assertThat(items.items()).as("nothing opened").hasSize(1);
        });
    }

    @Test void copyPageTitleCopiesThePagesTitleAndATitlelessPageCopiesNothing() throws Exception {
        onUiThread(() -> {
            open();
            show(plain());
            assertThat(host.chooseFromContentMenu(item.tab(), "item:page-menu.copy-title")).isTrue();
            assertThat(host.clipboard().copies()).as("no title yet").isEmpty();
            PageTestAccess.titleChanged(page, "Example Domain");
            show(plain());
            assertThat(host.chooseFromContentMenu(item.tab(), "item:page-menu.copy-title")).isTrue();
            assertThat(host.clipboard().copies()).containsExactly(copied("Example Domain"));
            assertThat(ended).hasValue(2);
        });
    }

    /** Spec section 11: Open Link and Open Image in New Tab open a browser item in the tab next to this one. */
    @Test void openLinkAndOpenImageInNewTabOpenTheirAddressInTheNextTab() throws Exception {
        onUiThread(() -> {
            open();
            var pointed = new PageMenu.Pointed(new Point(), Optional.of("https://example.org/next"), Optional.of("https://example.org/next"),
                Optional.of("https://example.org/cat.png"), NONE, false);
            show(pointed);
            assertThat(host.chooseFromContentMenu(item.tab(), "item:page-menu.open-link")).isTrue();
            show(pointed);
            assertThat(host.chooseFromContentMenu(item.tab(), "item:page-menu.open-image")).isTrue();
            assertThat(items.items()).extracting(BrowserItem::url).containsExactly(URI.create("https://example.com"),
                URI.create("https://example.org/next"), URI.create("https://example.org/cat.png"));
            assertThat(ended).hasValue(2);
            assertThat(host.failures()).isEmpty();
        });
    }

    /** R36: a link or image the address field would not load opens nothing; it can still be copied. */
    @Test void aJavascriptLinkOrDataImageCannotBeOpenedInANewTab() throws Exception {
        onUiThread(() -> {
            open();
            show(new PageMenu.Pointed(new Point(), Optional.of("javascript:alert(1)"), Optional.of("javascript:alert(1)"),
                Optional.of("data:image/png;base64,AAAA"), NONE, false));
            assertThat(host.contentMenu(item.tab())).contains("item:page-menu.open-link (disabled)", "item:page-menu.open-image (disabled)");
            assertThat(host.chooseFromContentMenu(item.tab(), "item:page-menu.open-link")).isFalse();
            assertThat(host.chooseFromContentMenu(item.tab(), "item:page-menu.open-image")).isFalse();
            assertThat(host.chooseFromContentMenu(item.tab(), "item:page-menu.copy-link")).isTrue();
            assertThat(host.clipboard().copies()).containsExactly(copied("javascript:alert(1)"));
            assertThat(items.items()).hasSize(1);
            assertThat(ended).hasValue(1);
        });
    }

    /** Spec section 11: Search with ⟨engine⟩ names the chosen engine and opens its search in the next tab. */
    @Test void searchWithTheEngineOpensASearchForTheSelectionInTheNextTab() throws Exception {
        onUiThread(() -> {
            open();
            show(new PageMenu.Pointed(new Point(), NONE, NONE, NONE, Optional.of("  two\n words "), false));
            assertThat(host.chooseFromContentMenu(item.tab(), "item:page-menu.search")).isTrue();
            assertThat(items.items()).hasSize(2);
            assertThat(items.items().get(1).url()).isEqualTo(URI.create("https://google.com/search?q=two+words"));
            assertThat(ended).hasValue(1);
        });
    }

    /** P26: the item names and searches with the engine the settings choose as the menu opens; Custom by its URL's host. */
    @Test void searchWithUsesTheEngineTheSettingsChooseWhenTheMenuOpens() throws Exception {
        onUiThread(() -> {
            open();
            MenuPlace place = page.menuPlace().orElseThrow();
            assertThat(place.engineName().get()).isEqualTo("Google");
            host.setSetting(ID, "search_engine", "duckduckgo");
            assertThat(place.engineName().get()).isEqualTo("DuckDuckGo");
            show(new PageMenu.Pointed(new Point(), NONE, NONE, NONE, Optional.of("jcef"), false));
            assertThat(host.chooseFromContentMenu(item.tab(), "item:page-menu.search")).isTrue();
            assertThat(items.items().get(1).url()).isEqualTo(URI.create("https://duckduckgo.com/?q=jcef"));
            host.setSetting(ID, "custom_search_url", "https://search.example.com/find?q=%s");
            host.setSetting(ID, "search_engine", "custom");
            assertThat(place.engineName().get()).isEqualTo("search.example.com");
            assertThat(place.search().apply("jcef")).isEqualTo(URI.create("https://search.example.com/find?q=jcef"));
        });
    }

    /**
     * Back, Forward, Reload, the editing items, Print… and the page tools act on the page's browser, which a page made
     * without Chromium does not have: each closes the menu, ends Chromium's menu once and fails nothing.
     */
    @Test void everyOtherItemEndsChromiumsMenuOnceAndFailsNothingOnAPageWithoutABrowser() throws Exception {
        onUiThread(() -> {
            open();
            PageTestAccess.loadingChanged(page, false, true, true);
            int count = 0;
            for (String id : List.of("page-menu.back", "page-menu.forward", "page-menu.reload", "page-menu.cut", "page-menu.copy",
                "page-menu.paste", "page-menu.select-all", "page-menu.print", "page-menu.view-source", "page-menu.inspect-element")) {
                show(new PageMenu.Pointed(new Point(), NONE, NONE, NONE, Optional.of("text"), true));
                assertThat(host.chooseFromContentMenu(item.tab(), "item:" + id)).as(id).isTrue();
                assertThat(ended).as(id).hasValue(++count);
                assertThat(host.contentMenu(item.tab())).as("closed").isEmpty();
            }
            assertThat(items.items()).hasSize(1);
            assertThat(host.clipboard().copies()).isEmpty();
            assertThat(host.failures()).isEmpty();
        });
    }

    @Test void dismissingTheMenuOrRunningOneOfJaspersItemsEndsChromiumsMenuOnceAndRunsNothingOfThePages() throws Exception {
        onUiThread(() -> {
            open();
            show(new PageMenu.Pointed(new Point(), Optional.of("https://example.org"), Optional.of("https://example.org"), NONE, NONE, false));
            assertThat(host.dismissContentMenu(item.tab())).isTrue();
            assertThat(ended).hasValue(1);
            show(plain());
            assertThat(host.chooseFromContentMenu(item.tab(), "host:Close Pane")).isTrue();
            assertThat(ended).hasValue(2);
            assertThat(items.items()).as("Close Pane closed the item").isEmpty();
            assertThat(host.clipboard().copies()).isEmpty();
        });
    }

    /** A page that closes while its menu shows dismisses it: its item's tab closes, and Chromium's menu ends once. */
    @Test void closingTheItemWhileItsMenuShowsEndsChromiumsMenuOnce() throws Exception {
        onUiThread(() -> {
            open();
            show(plain());
            item.close();
            assertThat(ended).hasValue(1);
            assertThat(host.contentMenu(item.tab())).isEmpty();
        });
    }

    /** A menu Chromium asks for after the page closed, or before its item gave it a tab, shows nothing and ends at once. */
    @Test void aClosedPageShowsNoMenuAndEndsChromiumsAtOnce() throws Exception {
        onUiThread(() -> {
            open();
            var tab = item.tab();
            item.close();
            show(plain());
            assertThat(ended).hasValue(1);
            assertThat(host.contentMenu(tab)).isEmpty();
            var unplaced = new Page(URI.create("https://example.com"), () -> { }, PageTestAccess.noFavicons());
            PageMenu.show(unplaced, plain(), ended::incrementAndGet);
            assertThat(ended).hasValue(2);
            unplaced.close();
        });
    }

    /**
     * Chromium's callback is ended once however often its end is asked, and a browser that is no page's gets no menu:
     * Chromium is told the menu is handled, and its callback is cancelled.
     */
    @Test void chromiumsCallbackIsCancelledOnceAndABrowserThatIsNoPagesGetsNoMenu() {
        var callback = new Callback();
        Runnable end = PageMenu.ending(callback);
        end.run();
        end.run();
        assertThat(callback.calls).containsExactly("cancel");
        var other = new Callback();
        assertThat(new PageMenu.Handler().runContextMenu(null, null, params(Map.of()), null, other)).isTrue();
        assertThat(other.calls).containsExactly("cancel");
    }

    /** What is under the pointer, read from Chromium's parameters while it asks, as its parameters last only that long. */
    @Test void whatIsUnderThePointerIsReadFromChromiumsParameters() {
        int link = CefContextMenuParams.TypeFlags.CM_TYPEFLAG_LINK, media = CefContextMenuParams.TypeFlags.CM_TYPEFLAG_MEDIA;
        var everything = PageMenu.Pointed.of(params(Map.of("getXCoord", 40, "getYCoord", 70, "getTypeFlags", link | media,
            "getLinkUrl", "https://example.com/next", "getUnfilteredLinkUrl", "https://ada:pw@example.com/next",
            "getMediaType", CefContextMenuParams.MediaType.CM_MEDIATYPE_IMAGE, "getSourceUrl", "https://example.com/cat.png",
            "getSelectionText", "cat", "isEditable", true)));
        assertThat(everything).isEqualTo(new PageMenu.Pointed(new Point(40, 70), Optional.of("https://example.com/next"),
            Optional.of("https://ada:pw@example.com/next"), Optional.of("https://example.com/cat.png"), Optional.of("cat"), true));
        var nothing = PageMenu.Pointed.of(params(Map.of("getLinkUrl", "https://example.com/stale", "getMediaType",
            CefContextMenuParams.MediaType.CM_MEDIATYPE_VIDEO, "getSourceUrl", "https://example.com/clip.mp4")));
        assertThat(nothing).as("no link flag, and a video").isEqualTo(new PageMenu.Pointed(new Point(), NONE, NONE, NONE, NONE, false));
        var filteredOnly = PageMenu.Pointed.of(params(Map.of("getTypeFlags", link, "getLinkUrl", "https://example.com/a")));
        assertThat(filteredOnly.linkAsWritten()).as("copied as Chromium checked it").contains("https://example.com/a");
    }

    /** Opens the plugin on a fake host with one browser item in a window. UI thread. */
    private void open() {
        host = new FakePluginHost();
        var started = new AtomicReference<BrowserItems>();
        host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(),
            plugin -> started.set(new BrowserItems(plugin, BrowserSettings.declare(plugin.settings())::read, any -> { })));
        items = started.get();
        host.activateTerminalWindow(host.addTerminalWindow());
        item = items.open(URI.create("https://example.com"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
        page = ItemTestAccess.page(item);
    }

    private void show(PageMenu.Pointed pointed) {
        PageMenu.show(page, pointed, ended::incrementAndGet);
    }

    /** How the fake clipboard records a plain copy of {@code text} by the plugin, as no secret. */
    private static String copied(String text) {
        return "copy|" + ID + "|" + text;
    }

    private static PageMenu.Pointed plain() {
        return new PageMenu.Pointed(new Point(), NONE, NONE, NONE, NONE, false);
    }

    /** Chromium's parameters answering {@code answers} by method name, and as for nothing under the pointer otherwise. */
    private static CefContextMenuParams params(Map<String, Object> answers) {
        return (CefContextMenuParams) Proxy.newProxyInstance(CefContextMenuParams.class.getClassLoader(),
            new Class<?>[] {CefContextMenuParams.class}, (proxy, method, args) -> {
                if (answers.containsKey(method.getName())) return answers.get(method.getName());
                Class<?> type = method.getReturnType();
                if (type == int.class) return 0;
                if (type == boolean.class) return false;
                if (type == String.class) return "";
                if (type == CefContextMenuParams.MediaType.class) return CefContextMenuParams.MediaType.CM_MEDIATYPE_NONE;
                return null;
            });
    }

    /** Chromium's callback, recording how the menu ended. */
    private static final class Callback implements CefRunContextMenuCallback {
        final List<String> calls = new ArrayList<>();

        @Override public void Continue(int commandId, int eventFlags) {
            calls.add("continue " + commandId);
        }

        @Override public void cancel() {
            calls.add("cancel");
        }
    }

    private static void onUiThread(Runnable body) throws Exception {
        var failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                body.run();
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        if (failure.get() instanceof Exception exception) throw exception;
        if (failure.get() instanceof Error error) throw error;
    }
}
