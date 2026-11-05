package dev.jasper.browser.item;

import com.sun.net.httpserver.HttpServer;
import dev.jasper.browser.page.NavigationHistory.Entry;
import dev.jasper.browser.page.NavigationHistory.History;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageTestAccess;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.browser.store.MemoryStores;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.ContentPresentation;
import dev.jasper.sdk.ui.MenuItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.PixelImage;
import dev.jasper.sdk.ui.TabPlacement;
import dev.jasper.sdk.ui.UiEvent;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Browser items on the testkit's fake host. Their pages are made by a recording stand-in, because a page cannot be made
 * without Chromium. Headless, a lightweight parent given a peer shows, so adding a page's canvas to it shows the item.
 */
class BrowserItemTest {
    private static final String ID = "dev.jasper.browser";
    private static final URI EXAMPLE = URI.create("https://example.com");
    // A PNG as far as the SDK checks one: the plugin hands it on and never decodes it.
    private static final PixelImage ICON = new PixelImage(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 1});

    // Stores in memory, one for each BrowserItems a test makes.
    private final MemoryStores memory = new MemoryStores();

    @AfterEach void closeStores() throws Exception {
        memory.close();
    }

    @Test void anItemAsksForItsPageOnceTheFirstTimeItShowsAndNotWhenItOpens() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var asked = new ArrayList<Page>();
            var item = items(host, asked::add).open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            assertThat(item.pageAsked()).as("opened, not shown").isFalse();
            assertThat(asked).isEmpty();
            var parent = new JPanel();
            parent.addNotify();
            try {
                parent.add(item.pageView());
                assertThat(item.pageView().isShowing()).isTrue();
                assertThat(item.pageAsked()).isTrue();
                assertThat(asked).hasSize(1);
                parent.remove(item.pageView());
                parent.add(item.pageView());
                assertThat(asked).as("asked once").hasSize(1);
            } finally {
                parent.removeNotify();
            }
            assertThat(host.failures()).isEmpty();
        } });
    }

    @Test void itsTabShowsTheTitleTheGlobeAndTheAddressAsItsLocationAndStatus() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var items = items(host, page -> { });
            var item = items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            assertThat(host.contentPresentation(item.tab()))
                .isEqualTo(new ContentPresentation("example.com", "", BrowserItems.GLOBE, false, "https://example.com", ""));
            assertThat(host.contentStatus(item.tab())).singleElement().satisfies(status -> {
                assertThat(status.text()).isEqualTo("https://example.com");
                assertThat(status.tooltip()).isEqualTo("https://example.com");
            });
            item.load(URI.create("https://example.org/docs"));
            assertThat(item.url()).isEqualTo(URI.create("https://example.org/docs"));
            assertThat(host.contentPresentation(item.tab()).title()).isEqualTo("example.org");
            assertThat(host.contentPresentation(item.tab()).location()).isEqualTo("https://example.org/docs");
            assertThat(host.contentStatus(item.tab())).singleElement().satisfies(status -> assertThat(status.text()).isEqualTo("https://example.org/docs"));
            var welcome = items.open(URI.create("jasper-about:welcome"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            assertThat(host.contentPresentation(welcome.tab()).title()).as("no title and no host").isEqualTo("Web Browser");
            assertThat(host.failures()).isEmpty();
        } });
    }

    /** Spec section 16: the tab's location and status and the address field show an address without its credentials. */
    @Test void anAddressWithAUserNameAndPasswordShowsWithoutThemAndSavesWithoutThem() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var item = items(host, page -> { }).open(URI.create("https://ada:secret@example.com/docs"), TabPlacement.NEW_TAB,
                Optional.empty(), Optional.empty());
            String shown = "https://example.com/docs";
            assertThat(host.contentPresentation(item.tab()))
                .isEqualTo(new ContentPresentation("example.com", "", BrowserItems.GLOBE, false, shown, ""));
            assertThat(host.contentStatus(item.tab())).singleElement().satisfies(status -> {
                assertThat(status.text()).isEqualTo(shown);
                assertThat(status.tooltip()).isEqualTo(shown);
            });
            assertThat(Views.field(host.contentView(ID, "example.com").orElseThrow(), BrowserToolbar.ADDRESS).value()).isEqualTo(shown);
            assertThat(host.restoreState(item.tab()).orElseThrow().values()).containsEntry("url", shown);
            assertThat(item.url()).as("the page keeps them").isEqualTo(URI.create("https://ada:secret@example.com/docs"));
            PageTestAccess.addressChanged(item.page(), "https://ada:secret@example.org/next");
            assertThat(host.contentPresentation(item.tab()).location()).isEqualTo("https://example.org/next");
            assertThat(host.restoreState(item.tab()).orElseThrow().values()).containsEntry("url", "https://example.org/next");
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * Spec section 5: while a new document without a title loads, the tab keeps the name it had, and once loaded it
     * shows the address's host, whatever title the document before had.
     */
    @Test void aNewDocumentWithoutATitleKeepsTheTabsNameWhileItLoadsThenShowsItsHost() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var item = items(host, page -> { }).open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            Page page = item.page();
            PageTestAccess.titleChanged(page, "Example Domain");
            assertThat(host.contentPresentation(item.tab()).title()).isEqualTo("Example Domain");
            PageTestAccess.loadingChanged(page, true, true, false);
            PageTestAccess.loadStarted(page, "https://example.org/");
            PageTestAccess.addressChanged(page, "https://example.org/");
            assertThat(page.title()).as("the document before's title is gone").isEmpty();
            assertThat(host.contentPresentation(item.tab()).title()).as("loading: the name it had").isEqualTo("Example Domain");
            PageTestAccess.loadingChanged(page, false, true, false);
            assertThat(host.contentPresentation(item.tab()).title()).as("loaded without a title: its host").isEqualTo("example.org");
            PageTestAccess.titleChanged(page, "Example Org");
            assertThat(host.contentPresentation(item.tab()).title()).isEqualTo("Example Org");
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * Spec sections 5 and 6: the tab and the address field show the page's favicon once the page has loaded, and the
     * globe while it loads and once a new document starts loading. The globe stays the tab's icon and the field's
     * leading icon, for an image that does not decode.
     */
    @Test void theTabAndTheFieldShowTheFaviconOnceThePageHasLoadedAndTheGlobeOtherwise() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var item = items(host, page -> { }).open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            var view = host.contentView(ID, "example.com").orElseThrow();
            Page page = item.page();
            assertThat(host.contentPresentation(item.tab()).image()).as("no favicon yet").isEmpty();
            assertThat(Views.field(view, BrowserToolbar.ADDRESS).leadingImage()).isEmpty();
            assertThat(Views.field(view, BrowserToolbar.ADDRESS).leadingIcon()).isEqualTo(BrowserItems.GLOBE);
            PageTestAccess.loadingChanged(page, true, false, false);
            PageTestAccess.faviconArrived(page, Optional.of(ICON));
            assertThat(page.favicon()).contains(ICON);
            assertThat(host.contentPresentation(item.tab()).image()).as("loading: the globe").isEmpty();
            assertThat(Views.field(view, BrowserToolbar.ADDRESS).leadingImage()).as("loading: the globe").isEmpty();
            PageTestAccess.loadingChanged(page, false, false, false);
            assertThat(host.contentPresentation(item.tab()))
                .isEqualTo(new ContentPresentation("example.com", "", BrowserItems.GLOBE, false, "https://example.com", "").withImage(ICON));
            Nodes.Field field = Views.field(view, BrowserToolbar.ADDRESS);
            assertThat(field.leadingImage()).contains(ICON);
            assertThat(field.leadingIcon()).as("the fallback").isEqualTo(BrowserItems.GLOBE);
            PageTestAccess.loadStarted(page, "https://example.org/");
            assertThat(page.favicon()).isEmpty();
            assertThat(host.contentPresentation(item.tab()).image()).as("a new document").isEmpty();
            assertThat(Views.field(view, BrowserToolbar.ADDRESS).leadingImage()).isEmpty();
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * Spec section 6: the item's Back and Forward hold its page's history as last read, and choosing an entry sends the
     * page there as Jasper's own navigation (4a's R41 and R42); an entry the history no longer has does nothing.
     */
    @Test void backAndForwardHoldThePagesHistoryAndAChosenEntrySendsThePageThere() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var items = items(host, page -> { });
            var item = items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            var view = host.contentView(ID, "example.com").orElseThrow();
            Page page = item.page();
            assertThat(Views.button(view, BrowserToolbar.BACK).holdMenu()).as("nothing read yet").isEmpty();
            var history = new History(1, List.of(new Entry(4, "https://example.org/", "Example Org"),
                new Entry(6, "https://example.com/", "Example Domain"), new Entry(9, "https://ada:secret@example.net/", "")));
            PageTestAccess.loadingChanged(page, false, true, true);
            PageTestAccess.historyRead(page, history);
            assertThat(Views.button(view, BrowserToolbar.BACK).holdMenu()).extracting(MenuItem::label).containsExactly("Example Org");
            assertThat(Views.button(view, BrowserToolbar.FORWARD).holdMenu()).extracting(MenuItem::label)
                .containsExactly("https://example.net/");
            var navigations = new AtomicInteger();
            page.onNavigate(navigations::incrementAndGet);
            String back = Views.button(view, BrowserToolbar.BACK).holdMenu().getFirst().id();
            // The page here has no browser, so it goes nowhere once it has told its listeners.
            view.choose(BrowserToolbar.BACK, back);
            assertThat(navigations).as("told before the page goes").hasValue(1);

            PageTestAccess.historyRead(page, new History(0, history.entries().subList(1, 3)));
            assertThat(Views.button(view, BrowserToolbar.BACK).holdMenu()).isEmpty();
            view.emit(new UiEvent.Action(back));
            assertThat(navigations).as("an entry the history no longer has").hasValue(1);
            view.choose(BrowserToolbar.FORWARD, Views.button(view, BrowserToolbar.FORWARD).holdMenu().getFirst().id());
            assertThat(navigations).hasValue(2);
            assertThat(((FakeKit) items.context().kit()).failures()).isEmpty();
            assertThat(host.failures()).isEmpty();
        } });
    }

    /** Spec section 5: Jasper's own pages show Jasper's icon, once loaded, as other pages show their favicon. */
    @Test void jaspersOwnPagesShowJaspersIcon() throws Exception {
        byte[] jasper;
        try (var in = BrowserItem.class.getResourceAsStream("jasper.png")) {
            assertThat(in).as("the plugin's copy of Jasper's icon").isNotNull();
            jasper = in.readAllBytes();
        }
        var icon = new PixelImage(jasper);
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var item = items(host, page -> { }).open(URI.create("jasper-about:welcome"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            assertThat(host.contentPresentation(item.tab()).image()).contains(icon);
            assertThat(host.contentPresentation(item.tab()).icon()).isEqualTo(BrowserItems.GLOBE);
            var view = host.contentView(ID, TabTitle.UNTITLED).orElseThrow();
            assertThat(Views.field(view, BrowserToolbar.ADDRESS).leadingImage()).contains(icon);
            PageTestAccess.loadingChanged(item.page(), true, false, false);
            assertThat(host.contentPresentation(item.tab()).image()).as("loading: the globe").isEmpty();
            PageTestAccess.loadingChanged(item.page(), false, false, false);
            item.load(EXAMPLE);
            assertThat(host.contentPresentation(item.tab()).image()).as("a web page without a favicon").isEmpty();
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * Spec section 6: the field's leading slot is the site icon, so named, and dragging it drags the page's address
     * without its user name and password, whatever is typed in the field.
     */
    @Test void theSiteIconIsNamedAndDragsThePagesAddressWithoutItsUserInfo() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            items(host, page -> { }).open(URI.create("https://ada:secret@example.com/docs"), TabPlacement.NEW_TAB, Optional.empty(),
                Optional.empty());
            var view = host.contentView(ID, "example.com").orElseThrow();
            Nodes.Field field = Views.field(view, BrowserToolbar.ADDRESS);
            assertThat(field.leadingName()).isEqualTo("Site icon - drag to copy URL");
            assertThat(field.leadingDrag()).contains("https://example.com/docs");
            view.type(BrowserToolbar.ADDRESS, "something else");
            assertThat(Views.field(view, BrowserToolbar.ADDRESS).leadingDrag()).as("typing changes nothing").contains("https://example.com/docs");
            assertThat(host.failures()).isEmpty();
        } });
    }

    @Test void everyItemIsATabOfItsOwnEvenAtTheSameAddress() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var items = items(host, page -> { });
            var first = items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            var second = items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            assertThat(first.tab()).isNotSameAs(second.tab());
            assertThat(host.contentTabs()).containsExactly(first.tab(), second.tab());
            assertThat(items.items()).containsExactly(first, second);
        } });
    }

    @Test void itemsAreFoundByTheirTargetInAnyWindowAndEachWindowKnowsItsFocusedItem() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var items = items(host, page -> { });
            UUID first = host.addTerminalWindow();
            host.activateTerminalWindow(first);
            var docs = items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.of("docs"), Optional.empty());
            var news = items.open(URI.create("https://example.org"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            UUID second = host.addTerminalWindow();
            host.activateTerminalWindow(second);
            var other = items.open(URI.create("https://example.net"), TabPlacement.NEW_TAB, Optional.of("other"), Optional.empty());
            assertThat(docs.tab().window().id()).isEqualTo(first);
            assertThat(other.tab().window().id()).isEqualTo(second);
            assertThat(items.find("docs")).containsSame(docs);
            assertThat(items.find("other")).containsSame(other);
            assertThat(items.find("missing")).isEmpty();
            assertThat(items.focused(docs.tab().window())).as("the item last opened there").containsSame(news);
            assertThat(items.focused(other.tab().window())).containsSame(other);
            docs.tab().select();
            assertThat(items.focused(docs.tab().window())).containsSame(docs);
            docs.close();
            assertThat(items.find("docs")).as("a closed item is not found").isEmpty();
            assertThat(items.items()).containsExactly(news, other);
            docs.load(EXAMPLE);
            assertThat(host.failures()).isEmpty();
        } });
    }

    @Test void focusingTheAddressFocusesTheFieldAndSelectsItsText() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var item = items(host, page -> { }).open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            item.focusAddress();
            var view = host.contentView(ID, "example.com").orElseThrow();
            assertThat(view.focused()).contains(BrowserToolbar.ADDRESS);
            assertThat(view.fieldSelection(BrowserToolbar.ADDRESS)).containsExactly(0, "https://example.com".length());
        } });
    }

    /**
     * Jasper's Copy and Paste, on whatever keys the keymap gives them and from the Edit menu, run a content tab's
     * {@code copy} and {@code paste}: on macOS the page's Cmd+C and Cmd+V do nothing by themselves, as Chromium leaves
     * them to the browser's menu there. The commands name no key of their own, so the keymap's keys are the ones.
     */
    @Test void itsCopyAndPasteAreContentCommandsWithNoKeyOfTheirOwn() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var item = items(host, page -> { }).open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            var commands = host.contentCommands(item.tab()).subList(0, 2);
            assertThat(commands).extracting(dev.jasper.sdk.ui.ContentCommand::id).containsExactly("copy", "paste");
            assertThat(commands).extracting(dev.jasper.sdk.ui.ContentCommand::title).containsExactly("Copy", "Paste");
            assertThat(commands).allSatisfy(command -> {
                assertThat(command.shortcut()).isNull();
                assertThat(command.enabled()).isTrue();
            });
            commands.forEach(command -> command.run().run()); // a page with no browser yet: nothing to copy or paste into
            item.close();
            commands.forEach(command -> command.run().run());
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * Jasper's Increase, Decrease and Reset Font Size, on the keys the keymap gives them (Cmd+=, Cmd+- and Cmd+0 on macOS,
     * Ctrl+Shift+=, Ctrl+Shift+- and Ctrl+Shift+0 on Windows and Linux) and from View › Font Size, run a content tab's
     * {@code font-bigger}, {@code font-smaller} and {@code font-reset}: the item's zoom commands are the Editor's, and
     * name no key of their own.
     */
    @Test void itsZoomCommandsAreTheEditorsFontSizeCommandsWithNoKeyOfTheirOwn() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var item = items(host, page -> { }).open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            var commands = host.contentCommands(item.tab()).stream().filter(each -> each.id().startsWith("font-")).toList();
            assertThat(commands).extracting(dev.jasper.sdk.ui.ContentCommand::id).containsExactly("font-bigger", "font-smaller", "font-reset");
            assertThat(commands).extracting(dev.jasper.sdk.ui.ContentCommand::title)
                .containsExactly("Increase Font Size", "Decrease Font Size", "Reset Font Size");
            assertThat(commands).allSatisfy(command -> {
                assertThat(command.shortcut()).isNull();
                assertThat(command.enabled()).isTrue();
            });
            assertThat(host.failures()).isEmpty();
        } });
    }

    @Test void theItemsViewIsTheToolbarAboveTheCanvasAsPreviewContent() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            items(host, page -> { }).open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            var root = host.contentView(ID, "example.com").orElseThrow().spec().root();
            assertThat(root).isInstanceOfSatisfying(dev.jasper.sdk.ui.Nodes.Split.class, split -> {
                assertThat(split.kind()).isEqualTo(dev.jasper.sdk.ui.Nodes.SplitKind.DOCUMENT);
                assertThat(split.children()).extracting(dev.jasper.sdk.ui.Nodes.Node::id)
                    .containsExactly(BrowserToolbar.BAR, BrowserItem.PAGE, BrowserFindBar.ROOT);
                assertThat(split.children().get(1)).isInstanceOfSatisfying(dev.jasper.sdk.ui.Nodes.Custom.class, page -> {
                    assertThat(page.kind()).isEqualTo(dev.jasper.sdk.ui.Nodes.CustomKind.PREVIEW);
                    assertThat(page.contentId()).isEqualTo(BrowserItem.CONTENT);
                });
            });
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * A link or popup the page sends to an item of its own opens one beside this item (spec section 8): a new tab right
     * after this item's tab, or a split from this item's pane, which is selected first when another item was focused.
     * The new item loads the address Chromium gave, for no OSC 8 target.
     */
    @Test void aLinkThePageOpensElsewhereOpensAnItemNextToItsTabOrSplitFromItsPane() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var items = items(host, page -> { });
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            var opener = items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            var other = items.open(URI.create("https://example.org"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            var before = host.windowTabs(window);
            assertThat(before).hasSize(2);
            opener.tab().select();

            opener.page().openItem("https://example.com/next?q=a%20b", TabPlacement.NEXT_TAB);
            var next = items.items().getLast();
            assertThat(next.url()).isEqualTo(URI.create("https://example.com/next?q=a%20b"));
            assertThat(next.target()).isEmpty();
            assertThat(next.tab().window().id()).isEqualTo(window);
            var afterNext = host.windowTabs(window);
            assertThat(afterNext).as("right after the opener's tab").hasSize(3);
            assertThat(afterNext.get(0)).isEqualTo(before.get(0));
            assertThat(afterNext.get(1)).singleElement().isNotIn(before.get(0), before.get(1));
            assertThat(afterNext.get(2)).isEqualTo(before.get(1));
            assertThat(opener.tab().isActive()).as("the new tab shows").isFalse();

            opener.page().openItem("https://example.com/split", TabPlacement.SPLIT_RIGHT);
            var split = items.items().getLast();
            assertThat(split.url()).isEqualTo(URI.create("https://example.com/split"));
            var afterSplit = host.windowTabs(window);
            assertThat(afterSplit).as("no new tab").hasSize(3);
            assertThat(afterSplit.get(0)).as("a pane beside the opener's, though another item had the focus")
                .hasSize(2).startsWith(before.get(0).getFirst());
            assertThat(items.items()).containsExactly(opener, other, next, split);
            assertThat(host.failures()).isEmpty();
        } });
    }

    @Test void aLinkToAnAddressThatIsNoURIOrFromAClosedItemOpensNothing() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var items = items(host, page -> { });
            var opener = items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            opener.page().openItem("no address", TabPlacement.NEXT_TAB);
            assertThat(items.items()).containsExactly(opener);
            var page = opener.page();
            opener.close();
            page.openItem("https://example.com/next", TabPlacement.NEXT_TAB);
            assertThat(items.items()).isEmpty();
            assertThat(host.contentTabs()).isEmpty();
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * The opener bridge reaches a popup through pages (Task 8): the page a link or popup opens is the new item's, and a
     * page can show its item, close it, and tell who asks when it closes.
     */
    @Test void aPageReachesTheItemItOpenedAndCanShowAndCloseItsOwnItem() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var items = items(host, page -> { });
            var opener = items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            Optional<Page> opened = opener.page().openItem("https://example.com/popup", TabPlacement.NEXT_TAB);
            var popup = items.items().getLast();
            assertThat(popup).isNotSameAs(opener);
            assertThat(opened).containsSame(popup.page());
            var closes = new AtomicInteger();
            popup.page().onClose(closes::incrementAndGet);

            opener.tab().select();
            assertThat(popup.tab().isActive()).isFalse();
            popup.page().showItem();
            assertThat(popup.tab().isActive()).isTrue();

            popup.page().closeItem();
            assertThat(items.items()).containsExactly(opener);
            assertThat(closes).hasValue(1);
            popup.page().closeItem();
            popup.page().showItem();
            assertThat(closes).as("told once").hasValue(1);
            var late = new AtomicInteger();
            popup.page().onClose(late::incrementAndGet);
            assertThat(late).as("a page already closed tells at once").hasValue(1);

            assertThat(opener.page().openItem("no address", TabPlacement.NEXT_TAB)).isEmpty();
            opener.close();
            assertThat(opener.page().openItem("https://example.com/next", TabPlacement.NEXT_TAB)).isEmpty();
            assertThat(host.contentTabs()).isEmpty();
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * After a run that ended while Chromium ran, a restored item waits: in place of its page's canvas it says why, with
     * Load Page, and showing it asks for no page. Its toolbar shows, and its tab saves, the saved address, and its tab's
     * focus goes to Load Page.
     */
    @Test void aWaitingItemSaysWhyInPlaceOfItsPageAndShowingItAsksForNoPage() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var asked = new ArrayList<Page>();
            var item = waiting(items(host, asked::add), EXAMPLE);
            var view = host.contentView(ID, "example.com").orElseThrow();
            var root = (Nodes.Split) view.spec().root();
            assertThat(root.children()).extracting(Nodes.Node::id)
                .containsExactly(BrowserToolbar.BAR, BrowserItem.PAGE, BrowserItem.WAITING, BrowserFindBar.ROOT);
            assertThat(root.children().get(1).state().visible()).as("the page's canvas").isFalse();
            assertThat(root.children().get(2)).isInstanceOfSatisfying(Nodes.Empty.class, placeholder -> {
                assertThat(placeholder.state().visible()).isTrue();
                assertThat(placeholder.title()).isEqualTo("Jasper closed while this page was open.");
                assertThat(placeholder.actions()).singleElement().isInstanceOfSatisfying(Nodes.Button.class, button -> {
                    assertThat(button.label()).isEqualTo("Load Page");
                    assertThat(button.kind()).isEqualTo(Nodes.ButtonKind.PRIMARY);
                    assertThat(button.action()).isEqualTo(BrowserItem.LOAD_PAGE);
                    assertThat(button.state().visible()).isTrue();
                });
            });
            assertThat(Views.field(view, BrowserToolbar.ADDRESS).value()).isEqualTo("https://example.com");
            assertThat(host.restoreState(item.tab()).orElseThrow().values()).containsEntry("url", "https://example.com");
            var parent = show(item);
            try {
                assertThat(item.pageView().isShowing()).isTrue();
                assertThat(item.pageAsked()).as("shown, waiting").isFalse();
                assertThat(asked).isEmpty();
                item.goBack();
                item.goForward();
                item.stop();
                assertThat(item.waiting()).as("Back and Forward have no history, and Stop nothing to stop").isTrue();
            } finally {
                parent.removeNotify();
            }
            item.tab().select();
            assertThat(view.focused()).contains(BrowserItem.LOAD_PAGE);
            assertThat(host.failures()).isEmpty();
        } });
    }

    @Test void loadPageEndsTheWaitAndThePageShowsAndIsAskedForOnce() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var asked = new ArrayList<Page>();
            var item = waiting(items(host, asked::add), EXAMPLE);
            var view = host.contentView(ID, "example.com").orElseThrow();
            var parent = show(item);
            try {
                view.emit(new UiEvent.Action(BrowserItem.LOAD_PAGE));
                assertThat(item.waiting()).isFalse();
                assertThat(asked).containsExactly(item.page());
                var root = (Nodes.Split) view.spec().root();
                assertThat(root.children()).extracting(Nodes.Node::id).containsExactly(BrowserToolbar.BAR, BrowserItem.PAGE, BrowserFindBar.ROOT);
                assertThat(root.children().get(1).state().visible()).isTrue();
                assertThat(item.page().address()).as("what it loads").isEqualTo("https://example.com");
                parent.remove(item.pageView());
                parent.add(item.pageView());
                assertThat(asked).as("once").hasSize(1);
                item.tab().select();
                assertThat(view.focused()).as("the page has the focus now").isEmpty();
            } finally {
                parent.removeNotify();
            }
            assertThat(host.failures()).isEmpty();
        } });
    }

    /** Every load the user asks for in a waiting item ends the wait: Enter in its address field, Reload and a load. */
    @Test void enterReloadAndALoadEndTheWaitToo() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var asked = new ArrayList<Page>();
            var items = items(host, asked::add);
            var typed = waiting(items, EXAMPLE);
            var view = host.contentView(ID, "example.com").orElseThrow();
            view.type(BrowserToolbar.ADDRESS, "example.org/typed");
            view.emit(new UiEvent.Action(BrowserToolbar.ENTER));
            assertThat(typed.waiting()).as("Enter").isFalse();
            assertThat(typed.page().address()).isEqualTo("https://example.org/typed");
            assertThat(host.restoreState(typed.tab()).orElseThrow().values()).as("what it will load")
                .containsEntry("url", "https://example.org/typed");

            var reloaded = waiting(items, URI.create("https://example.net/saved"));
            host.contentView(ID, "example.net").orElseThrow().emit(new UiEvent.Action(BrowserToolbar.RELOAD));
            assertThat(reloaded.waiting()).as("Reload").isFalse();
            assertThat(reloaded.page().address()).isEqualTo("https://example.net/saved");

            var menu = waiting(items, URI.create("https://example.edu/"));
            menu.reload();
            assertThat(menu.waiting()).as("the Web menu's Reload").isFalse();

            var routed = waiting(items, URI.create("https://example.info/"));
            URI link = URI.create("https://example.info/linked");
            routed.load(link);
            assertThat(routed.waiting()).as("a load").isFalse();
            assertThat(routed.page().address()).isEqualTo(link.toString());
            assertThat(host.restoreState(routed.tab()).orElseThrow().values()).containsEntry("url", link.toString());

            assertThat(asked).as("none of them has shown").isEmpty();
            var parent = show(routed);
            try {
                assertThat(asked).as("it loads the first time it shows").containsExactly(routed.page());
            } finally {
                parent.removeNotify();
            }
            assertThat(host.failures()).isEmpty();
        } });
    }

    @Test void eachWaitingItemWaitsUntilItIsLoadedAndAnItemOpenedOtherwiseNeverWaits() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var asked = new ArrayList<Page>();
            var items = items(host, asked::add);
            var first = waiting(items, EXAMPLE);
            var second = waiting(items, URI.create("https://example.org"));
            host.contentView(ID, "example.com").orElseThrow().emit(new UiEvent.Action(BrowserItem.LOAD_PAGE));
            assertThat(first.waiting()).isFalse();
            assertThat(second.waiting()).isTrue();
            assertThat(Views.node(host.contentView(ID, "example.org").orElseThrow().spec().root(), BrowserItem.WAITING)).isNotNull();
            var opened = items.open(URI.create("https://example.net"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            assertThat(opened.waiting()).isFalse();
            assertThat(Views.node(host.contentView(ID, "example.net").orElseThrow().spec().root(), BrowserItem.WAITING)).isNull();
            var parent = show(second);
            try {
                assertThat(asked).as("the second still waits").isEmpty();
            } finally {
                parent.removeNotify();
            }
            first.close();
            second.close();
            assertThat(items.items()).containsExactly(opened);
            assertThat(host.failures()).isEmpty();
        } });
    }

    /** Spec section 6: the click that brings the focus into the address field selects all its text (4a's R15). */
    @Test void theClickThatFocusesTheAddressFieldSelectsAllItsText() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            items(host, page -> { }).open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            var view = host.contentView(ID, "example.com").orElseThrow();
            assertThat(Views.field(view, BrowserToolbar.ADDRESS).selectOnClick()).isTrue();
            view.clickInto(BrowserToolbar.ADDRESS);
            assertThat(view.fieldSelection(BrowserToolbar.ADDRESS)).containsExactly(0, "https://example.com".length());
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * Spec section 6: typing shows the field's own rows at once, "Loading suggestions…" after them, and asks the engine
     * 150 ms after the last keystroke; its answer joins the rows, the top one still selected.
     */
    @Test void typingShowsTheFieldsOwnRowsAtOnceAndAsksTheEngineOnceTheTypingPauses() throws Exception {
        var later = new Later();
        var fetches = new Fetches();
        var host = onUi(FakePluginHost::new);
        try {
            var view = onUi(() -> {
                items(host, page -> { }, later, fetches).open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
                var shown = host.contentView(ID, "example.com").orElseThrow();
                assertThat(Views.field(shown, BrowserToolbar.ADDRESS).suggestions()).as("nothing typed").isEmpty();
                shown.type(BrowserToolbar.ADDRESS, "jc");
                assertThat(shown.suggestions(BrowserToolbar.ADDRESS)).extracting(Nodes.Suggestion::id)
                    .containsExactly("search:jc", "navigate:https://jc");
                assertThat(Views.field(shown, BrowserToolbar.ADDRESS).suggestionsLoading()).isTrue();
                assertThat(later.waiting()).containsExactly(Duration.ofMillis(150));
                shown.type(BrowserToolbar.ADDRESS, "jce");
                assertThat(later.waiting()).as("the keystroke before waits no more").containsExactly(Duration.ofMillis(150));
                assertThat(fetches.asked).isEmpty();
                later.end();
                assertThat(fetches.asked).containsExactly(URI.create("https://suggestqueries.google.com/complete/search?client=firefox&q=jce"));
                return shown;
            });
            fetches.answers.getFirst().complete(List.of("jcef", "jcef maven"));
            onUi(() -> {
                assertThat(view.suggestions(BrowserToolbar.ADDRESS)).extracting(Nodes.Suggestion::id)
                    .containsExactly("search:jce", "search:jcef", "search:jcef maven", "navigate:https://jce");
                assertThat(Views.field(view, BrowserToolbar.ADDRESS).suggestionsLoading()).isFalse();
                assertThat(view.selectedSuggestion(BrowserToolbar.ADDRESS)).contains("search:jce");
                assertThat(host.failures()).isEmpty();
                return null;
            });
        } finally {
            onUi(() -> {
                host.close();
                return null;
            });
        }
    }

    /** Each keystroke supersedes the request before: an answer to it, arriving after the next, is dropped. */
    @Test void anAnswerForTextTypedBeforeIsDropped() throws Exception {
        var later = new Later();
        var fetches = new Fetches();
        var host = onUi(FakePluginHost::new);
        try {
            var view = onUi(() -> {
                items(host, page -> { }, later, fetches).open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
                var shown = host.contentView(ID, "example.com").orElseThrow();
                shown.type(BrowserToolbar.ADDRESS, "what");
                later.end();
                shown.type(BrowserToolbar.ADDRESS, "what is");
                later.end();
                return shown;
            });
            assertThat(fetches.asked).hasSize(2);
            assertThat(fetches.answers.getFirst()).as("its request ended with the next keystroke").isCancelled();
            fetches.answers.getFirst().complete(List.of("what time is it"));
            onUi(() -> {
                assertThat(view.suggestions(BrowserToolbar.ADDRESS)).extracting(Nodes.Suggestion::title).containsExactly("Search for \u201cwhat is\u201d");
                assertThat(Views.field(view, BrowserToolbar.ADDRESS).suggestionsLoading()).as("still waiting for its own").isTrue();
                return null;
            });
            fetches.answers.getLast().complete(List.of("what is jcef"));
            onUi(() -> {
                assertThat(view.suggestions(BrowserToolbar.ADDRESS)).extracting(Nodes.Suggestion::title)
                    .containsExactly("Search for \u201cwhat is\u201d", "what is jcef");
                return null;
            });
        } finally {
            onUi(() -> {
                host.close();
                return null;
            });
        }
    }

    /**
     * Spec section 6: Enter or Tab on a suggestion loads it, the selected one, and the field gives up the typed text; a
     * search loads with the chosen engine, and its results page then shows its terms in the field.
     */
    @Test void enterOrTabOnASuggestionLoadsIt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var later = new Later();
            var item = items(host, page -> { }, later, new Fetches()).open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            var view = host.contentView(ID, "example.com").orElseThrow();
            view.type(BrowserToolbar.ADDRESS, "example.org/docs");
            view.press(BrowserToolbar.ADDRESS, "ENTER");
            assertThat(item.page().address()).isEqualTo("https://example.org/docs");
            assertThat(Views.field(view, BrowserToolbar.ADDRESS).value()).isEqualTo("https://example.org/docs");
            assertThat(Views.field(view, BrowserToolbar.ADDRESS).suggestions()).isEmpty();
            assertThat(Views.field(view, BrowserToolbar.ADDRESS).suggestionsLoading()).isFalse();
            assertThat(later.waiting()).as("no request after the load").isEmpty();

            view.type(BrowserToolbar.ADDRESS, "example.net");
            view.press(BrowserToolbar.ADDRESS, "DOWN");
            view.press(BrowserToolbar.ADDRESS, "TAB");
            assertThat(item.page().address()).as("the second row, the search").isEqualTo("https://google.com/search?q=example.net");
            assertThat(Views.field(view, BrowserToolbar.ADDRESS).value()).as("the results page's terms").isEqualTo("example.net");
            assertThat(host.contentPresentation(item.tab()).location()).as("the tab keeps the address")
                .isEqualTo("https://google.com/search?q=example.net");

            host.setSetting(ID, "search_engine", "duckduckgo");
            view.type(BrowserToolbar.ADDRESS, "what is jcef");
            view.press(BrowserToolbar.ADDRESS, "ENTER");
            assertThat(item.page().address()).isEqualTo("https://duckduckgo.com/?q=what+is+jcef");
            view.emit(new UiEvent.SuggestionChosen(BrowserToolbar.ADDRESS, "navigate:chrome://version", false));
            assertThat(item.page().address()).as("a row that loads nothing").isEqualTo("https://duckduckgo.com/?q=what+is+jcef");
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * Spec section 6: a search engine's results page shows its terms in the field, while the tab, the saved state and
     * the site icon keep its address; Escape shows the terms again.
     */
    @Test void aResultsPageShowsItsTermsInTheField() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            String results = "https://www.bing.com/search?q=what+is+jcef&form=QBLH";
            var item = items(host, page -> { }).open(URI.create(results), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            var view = host.contentView(ID, "www.bing.com").orElseThrow();
            assertThat(Views.field(view, BrowserToolbar.ADDRESS).value()).isEqualTo("what is jcef");
            assertThat(Views.field(view, BrowserToolbar.ADDRESS).leadingDrag()).contains(results);
            assertThat(host.contentPresentation(item.tab()).location()).isEqualTo(results);
            assertThat(host.restoreState(item.tab()).orElseThrow().values()).containsEntry("url", results);
            view.clickInto(BrowserToolbar.ADDRESS);
            assertThat(view.fieldSelection(BrowserToolbar.ADDRESS)).containsExactly(0, "what is jcef".length());
            view.type(BrowserToolbar.ADDRESS, "other");
            view.press(BrowserToolbar.ADDRESS, "ESCAPE");
            assertThat(Views.field(view, BrowserToolbar.ADDRESS).value()).isEqualTo("what is jcef");
            assertThat(view.fieldSelection(BrowserToolbar.ADDRESS)).containsExactly(0, "what is jcef".length());
            assertThat(host.failures()).isEmpty();
        } });
    }

    /** Plan 4b's P24: a local path, a local scheme or a password typed into the field never reaches the search engine. */
    @Test void typedLocalPathsAndPasswordsAskTheEngineNothing() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var later = new Later();
            items(host, page -> { }, later, new Fetches()).open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            var view = host.contentView(ID, "example.com").orElseThrow();
            for (String text : List.of("/Users/me/secret.html", "~/notes", "file:///etc/hosts", "about:blank", "jasper-about:welcome",
                    "https://ada:s", "https://ada:secret@example.com/")) {
                view.type(BrowserToolbar.ADDRESS, text);
                assertThat(later.waiting()).as(text).isEmpty();
                assertThat(Views.field(view, BrowserToolbar.ADDRESS).suggestionsLoading()).as(text).isFalse();
                assertThat(view.suggestions(BrowserToolbar.ADDRESS)).as(text).isNotEmpty();
            }
            view.type(BrowserToolbar.ADDRESS, "");
            assertThat(view.suggestions(BrowserToolbar.ADDRESS)).isEmpty();
            assertThat(later.waiting()).isEmpty();
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * P45: a typed {@code chrome:} address lists no suggestion and asks the engine nothing, and Enter, through the
     * field's own path, loads nothing and leaves the text in the field.
     */
    @Test void aChromeAddressOffersNothingAndEnterLoadsNothing() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var later = new Later();
            var fetches = new Fetches();
            var item = items(host, page -> { }, later, fetches).open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            var view = host.contentView(ID, "example.com").orElseThrow();
            for (String text : List.of("chrome://version", "CHROME:settings")) {
                view.type(BrowserToolbar.ADDRESS, text);
                assertThat(view.suggestions(BrowserToolbar.ADDRESS)).as(text).isEmpty();
                assertThat(Views.field(view, BrowserToolbar.ADDRESS).suggestionsLoading()).as(text).isFalse();
                assertThat(later.waiting()).as(text).isEmpty();
                view.press(BrowserToolbar.ADDRESS, "ENTER");
                assertThat(item.page().address()).as(text).isEqualTo(EXAMPLE.toString());
                assertThat(Views.field(view, BrowserToolbar.ADDRESS).value()).as("the text stays").isEqualTo(text);
            }
            later.end();
            assertThat(fetches.asked).isEmpty();
            assertThat(host.failures()).isEmpty();
        } });
    }

    /** Plan 4b's P27: Enter right after typing {@code localhost} or a host with a port navigates there. */
    @Test void enterRightAfterTypingAHostWithAPortNavigatesThere() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var item = items(host, page -> { }).open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            var view = host.contentView(ID, "example.com").orElseThrow();
            view.type(BrowserToolbar.ADDRESS, "localhost:3000/app");
            view.press(BrowserToolbar.ADDRESS, "ENTER");
            assertThat(item.page().address()).isEqualTo("https://localhost:3000/app");
            view.type(BrowserToolbar.ADDRESS, "jcef");
            view.press(BrowserToolbar.ADDRESS, "ENTER");
            assertThat(item.page().address()).as("a bare word searches").isEqualTo("https://google.com/search?q=jcef");
            assertThat(host.failures()).isEmpty();
        } });
    }

    /** Plan 4b's S6: clearing the field while the engine is asked ends the request, and the list shows nothing. */
    @Test void clearingTheFieldEndsTheRequestAndShowsNoRows() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var later = new Later();
            var fetches = new Fetches();
            items(host, page -> { }, later, fetches).open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            var view = host.contentView(ID, "example.com").orElseThrow();
            view.type(BrowserToolbar.ADDRESS, "jc");
            later.end();
            assertThat(fetches.answers).singleElement().satisfies(answer -> assertThat(answer).isNotDone());
            view.type(BrowserToolbar.ADDRESS, "");
            assertThat(fetches.answers.getFirst()).as("its request ended").isCancelled();
            assertThat(later.waiting()).isEmpty();
            assertThat(view.suggestions(BrowserToolbar.ADDRESS)).isEmpty();
            assertThat(Views.field(view, BrowserToolbar.ADDRESS).suggestions()).isEmpty();
            assertThat(Views.field(view, BrowserToolbar.ADDRESS).suggestionsLoading()).as("no Loading suggestions").isFalse();
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * Plan 4b's S4: two items asking the engine through the plugin's own fetcher at once each get their own answer; one
     * item's keystroke never ends the other's request. The engine is a local server, as the Custom engine's suggestions.
     */
    @Test void twoItemsAskTheEngineEachOnTheirOwn() throws Exception {
        var release = new CountDownLatch(1);
        var asked = new CopyOnWriteArrayList<String>();
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/suggest", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            asked.add(query);
            try {
                if (query.equals("q=slow")) release.await(10, TimeUnit.SECONDS);
                byte[] body = ("[\"q\",[\"" + query.substring(2) + " answer\"]]").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                try (var out = exchange.getResponseBody()) {
                    out.write(body);
                }
            } catch (IOException | InterruptedException gone) {
                // The client gave up.
            } finally {
                exchange.close();
            }
        });
        server.start();
        var later = new Later();
        var host = onUi(FakePluginHost::new);
        try {
            var views = onUi(() -> {
                host.setSetting(ID, "search_engine", "custom");
                host.setSetting(ID, "custom_search_url", "https://search.example.com/?q=%s");
                host.setSetting(ID, "custom_suggestions_url", "http://127.0.0.1:" + server.getAddress().getPort() + "/suggest?q=%s");
                var items = items(host, page -> { }, later, null);
                items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
                items.open(URI.create("https://example.org"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
                var first = host.contentView(ID, "example.com").orElseThrow();
                var second = host.contentView(ID, "example.org").orElseThrow();
                first.type(BrowserToolbar.ADDRESS, "slow");
                later.end();
                host.runBackground(); // the fake host's background work runs when the test says
                return List.of(first, second);
            });
            await(() -> asked.contains("q=slow"), "the first item's request");
            onUi(() -> {
                views.get(1).type(BrowserToolbar.ADDRESS, "fast");
                later.end();
                host.runBackground();
                return null;
            });
            await(() -> onUi(() -> !Views.field(views.get(1), BrowserToolbar.ADDRESS).suggestionsLoading()), "the second item's answer");
            onUi(() -> {
                assertThat(views.get(1).suggestions(BrowserToolbar.ADDRESS)).extracting(Nodes.Suggestion::title).contains("fast answer");
                assertThat(Views.field(views.get(0), BrowserToolbar.ADDRESS).suggestionsLoading()).as("the first still waits").isTrue();
                return null;
            });
            release.countDown();
            await(() -> onUi(() -> !Views.field(views.get(0), BrowserToolbar.ADDRESS).suggestionsLoading()), "the first item's answer");
            onUi(() -> {
                assertThat(views.get(0).suggestions(BrowserToolbar.ADDRESS)).extracting(Nodes.Suggestion::title).contains("slow answer");
                assertThat(host.failures()).isEmpty();
                return null;
            });
        } finally {
            release.countDown();
            onUi(() -> {
                host.close();
                return null;
            });
            server.stop(0);
            ((ExecutorService) server.getExecutor()).shutdownNow();
        }
    }

    /** Escape and closing the item end the wait for suggestions, and an answer arriving after either is dropped. */
    @Test void escapeAndClosingEndTheSuggestions() throws Exception {
        var later = new Later();
        var fetches = new Fetches();
        var host = onUi(FakePluginHost::new);
        try {
            onUi(() -> {
                var item = items(host, page -> { }, later, fetches).open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
                var shown = host.contentView(ID, "example.com").orElseThrow();
                shown.type(BrowserToolbar.ADDRESS, "jc");
                shown.press(BrowserToolbar.ADDRESS, "ESCAPE");
                assertThat(later.waiting()).isEmpty();
                assertThat(Views.field(shown, BrowserToolbar.ADDRESS).suggestions()).isEmpty();
                assertThat(Views.field(shown, BrowserToolbar.ADDRESS).value()).isEqualTo("https://example.com");
                shown.type(BrowserToolbar.ADDRESS, "jc");
                later.end();
                shown.press(BrowserToolbar.ADDRESS, "ESCAPE");
                shown.type(BrowserToolbar.ADDRESS, "jcef");
                later.end();
                item.close();
                return null;
            });
            assertThat(fetches.answers).hasSize(2).allSatisfy(answer -> assertThat(answer).as("ended").isCancelled());
            fetches.answers.forEach(answer -> answer.complete(List.of("late")));
            onUi(() -> {
                assertThat(host.failures()).isEmpty();
                return null;
            });
        } finally {
            onUi(() -> {
                host.close();
                return null;
            });
        }
    }

    /** A waiting item at {@code url}, as a restore after a run that ended while Chromium ran opens it. UI thread. */
    private static BrowserItem waiting(BrowserItems items, URI url) {
        var item = BrowserItem.open(items, url, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty(), java.util.OptionalInt.empty(), true);
        assertThat(item.waiting()).isTrue();
        return item;
    }

    /** Shows {@code item}'s page canvas in a lightweight parent given a peer, until the parent's {@code removeNotify}. */
    private static JPanel show(BrowserItem item) {
        var parent = new JPanel();
        parent.addNotify();
        parent.add(item.pageView());
        return parent;
    }

    /**
     * The items of a started stand-in plugin whose pages {@code start} makes, with the plugin's settings. Their address
     * fields' suggestion delays wait for {@code later} and their engine suggestions come from {@code fetches}. UI thread.
     */
    private BrowserItems items(FakePluginHost host, Consumer<Page> start, Later later, Fetches fetches) {
        var context = new AtomicReference<PluginContext>();
        var settings = new AtomicReference<BrowserSettings.Declared>();
        host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(), plugin -> {
            context.set(plugin);
            settings.set(BrowserSettings.declare(plugin.settings()));
        });
        return new BrowserItems(context.get(), settings.get()::read, memory.make(), start, later, fetches);
    }

    /** As {@link #items(FakePluginHost, Consumer, Later, Fetches)}, with delays that never end and fetches nobody answers. */
    private BrowserItems items(FakePluginHost host, Consumer<Page> start) {
        return items(host, start, new Later(), new Fetches());
    }

    /** The UI thread's delays, which wait until the test runs them. UI thread. */
    private static final class Later implements BiFunction<Duration, Runnable, Runnable> {
        private final List<Delayed> waiting = new ArrayList<>();

        private record Delayed(Duration delay, Runnable task) { }

        @Override public Runnable apply(Duration delay, Runnable task) {
            var delayed = new Delayed(delay, task);
            waiting.add(delayed);
            return () -> waiting.remove(delayed);
        }

        List<Duration> waiting() {
            return waiting.stream().map(Delayed::delay).toList();
        }

        /** Each delay waiting ends, and its task runs. */
        void end() {
            var due = List.copyOf(waiting);
            waiting.clear();
            due.forEach(delayed -> delayed.task().run());
        }
    }

    /** The engine's suggestions, which the test answers: each request and its answer, in order. Any thread. */
    private static final class Fetches implements Function<URI, CompletableFuture<List<String>>> {
        final List<URI> asked = new CopyOnWriteArrayList<>();
        final List<CompletableFuture<List<String>>> answers = new CopyOnWriteArrayList<>();

        @Override public CompletableFuture<List<String>> apply(URI request) {
            asked.add(request);
            var answer = new CompletableFuture<List<String>>();
            answers.add(answer);
            return answer;
        }
    }

    private interface Check {
        boolean holds() throws Exception;
    }

    private static void await(Check check, String what) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!check.holds()) {
            if (System.nanoTime() > deadline) throw new AssertionError("timed out waiting for " + what);
            Thread.sleep(10);
        }
    }

    private static <T> T onUi(Callable<T> task) throws Exception {
        var result = new AtomicReference<T>();
        var failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(task.call());
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        if (failure.get() instanceof Error error) throw error;
        if (failure.get() instanceof Exception exception) throw exception;
        return result.get();
    }
}
