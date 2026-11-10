package dev.jasper.browser.item;

import com.sun.net.httpserver.HttpServer;
import dev.jasper.browser.page.NavigationHistory.Entry;
import dev.jasper.browser.page.NavigationHistory.History;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageTestAccess;
import dev.jasper.browser.pages.HistoryPage;
import dev.jasper.browser.permissions.Decision;
import dev.jasper.browser.permissions.Permission;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.browser.store.BrowserDatabase;
import dev.jasper.browser.store.DataFolder;
import dev.jasper.browser.store.HistoryStore;
import dev.jasper.browser.store.MemoryStores;
import dev.jasper.browser.store.Reached;
import dev.jasper.browser.store.Stores;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
     * page there as Jasper's own navigation (4a's R41 and R42); an entry the history no longer has does nothing. Each
     * menu's Show Full History loads Jasper's history page (plan 5's H2).
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
            assertThat(Views.button(view, BrowserToolbar.BACK).holdMenu()).extracting(MenuItem::label)
                .containsExactly("Example Org", "", "Show Full History");
            assertThat(Views.button(view, BrowserToolbar.FORWARD).holdMenu()).extracting(MenuItem::label)
                .containsExactly("https://example.net/", "", "Show Full History");
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
            view.choose(BrowserToolbar.FORWARD, Views.button(view, BrowserToolbar.FORWARD).holdMenu().getLast().id());
            assertThat(item.url()).as("Show Full History").isEqualTo(URI.create("jasper-about:history"));
            assertThat(navigations).as("a load the page tells of").hasValue(3);
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
                    .containsExactly(BrowserToolbar.BAR, AudioBar.BAR, BrowserItem.PAGE, BrowserFindBar.ROOT);
                assertThat(split.children().get(1).state().visible()).as("the audio bar, until a page's sound is muted").isFalse();
                assertThat(split.children().get(2)).isInstanceOfSatisfying(dev.jasper.sdk.ui.Nodes.Custom.class, page -> {
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
     * Plan 7's F28, as Chrome does: an item a page opened, a popup or a link's new tab or split, closes when its first
     * navigation becomes a download, before it committed any document, so no empty tab is left; the download keeps its
     * browser through the page's close hold. An item the user opened, and one that has shown a document, stay.
     */
    @Test void anItemAPageOpenedClosesWhenItsFirstNavigationBecomesADownload() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var items = items(host, page -> { });
            var opener = items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            Page popup = opener.page().openItem("https://example.com/report.pdf", TabPlacement.NEXT_TAB).orElseThrow();
            Page split = opener.page().openItem("https://example.com/archive.zip", TabPlacement.SPLIT_RIGHT).orElseThrow();
            Page shown = opener.page().openItem("https://example.com/next", TabPlacement.NEXT_TAB).orElseThrow();
            PageTestAccess.addressChanged(shown, "https://example.com/next");
            assertThat(items.items()).hasSize(4);
            popup.firstNavigationDownloaded();
            split.firstNavigationDownloaded();
            assertThat(items.items()).extracting(BrowserItem::page).containsExactly(opener.page(), shown);
            shown.firstNavigationDownloaded();
            opener.page().firstNavigationDownloaded();
            assertThat(items.items()).as("a document shown, and the user's own item").extracting(BrowserItem::page)
                .containsExactly(opener.page(), shown);
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
     * Spec section 12: an item holds its data store, the persistent one unless it opened in another, and a link or popup
     * its page opens in an item of its own opens in the opener's store: in /dev/null, the opener's group.
     */
    @Test void aLinkOrPopupOpensInItsOpenersStore() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var items = items(host, page -> { });
            DataStore devNull = items.dataStores().newDevNull();
            var opener = items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty(), devNull);
            assertThat(opener.store()).isSameAs(devNull);
            Optional<Page> opened = ItemTestAccess.page(opener).openItem("https://example.com/popup", TabPlacement.NEXT_TAB);
            var popup = items.items().getLast();
            assertThat(opened).containsSame(popup.page());
            assertThat(popup.store()).as("the opener's group").isSameAs(devNull);
            ItemTestAccess.page(opener).openItem("https://example.com/split", TabPlacement.SPLIT_RIGHT);
            assertThat(items.items().getLast().store()).isSameAs(devNull);
            var persistent = items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            assertThat(persistent.store()).isSameAs(items.dataStores().persistent());
            ItemTestAccess.page(persistent).openItem("https://example.org/", TabPlacement.NEXT_TAB);
            assertThat(items.items().getLast().store()).isSameAs(items.dataStores().persistent());
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * Plan 6: with Use /dev/null mode on, a new item opens in a new /dev/null store; turning it off changes no open item,
     * and the next new item opens in the persistent store. A page's popup stays in its opener's store whatever it says.
     */
    @Test void newItemsFollowTheSettingAndTurningItOffChangesNoOpenItem() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var items = items(host, page -> { });
            var ordinary = items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            assertThat(ordinary.devNull()).as("off by default").isFalse();
            assertThat(ordinary.store()).isSameAs(items.dataStores().persistent());

            host.setSetting(ID, "devnull_mode", true);
            var first = items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            var second = items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            assertThat(first.devNull()).isTrue();
            assertThat(first.store().devNull()).isTrue();
            assertThat(second.store()).as("each in a group of its own").isNotSameAs(first.store());
            assertThat(ordinary.devNull()).as("turning it on changes no open item").isFalse();
            ItemTestAccess.page(ordinary).openItem("https://example.org/", TabPlacement.NEXT_TAB);
            assertThat(items.items().getLast().devNull()).as("a popup follows its opener, not the setting").isFalse();

            host.setSetting(ID, "devnull_mode", false);
            assertThat(first.devNull()).as("turning it off changes no open item").isTrue();
            ItemTestAccess.page(first).openItem("https://example.org/", TabPlacement.NEXT_TAB);
            assertThat(items.items().getLast().store()).as("a popup of an open /dev/null item").isSameAs(first.store());
            var after = items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            assertThat(after.devNull()).isFalse();
            assertThat(after.store()).isSameAs(items.dataStores().persistent());
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * Plan 6 (F2): a /dev/null item saves the tab title "Web Browser", whatever page it shows; it saves no address, and
     * keeps its target and zoom. An ordinary item saves what it shows.
     */
    @Test void aDevNullItemSavesNoPageTitleAndNoAddress() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var items = items(host, page -> { });
            var devNull = items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.of("docs"), Optional.empty(), items.dataStores().newDevNull());
            assertThat(host.savedTitle(devNull.tab())).isEqualTo("Web Browser");
            PageTestAccess.titleChanged(devNull.page(), "A secret page");
            devNull.load(URI.create("https://secret.example/private?q=1"));
            assertThat(host.contentPresentation(devNull.tab()).title()).as("the tab shows it").isEqualTo("A secret page");
            assertThat(host.savedTitle(devNull.tab())).as("the record does not").isEqualTo("Web Browser");
            assertThat(host.restoreState(devNull.tab()).orElseThrow().values()).containsOnly(Map.entry("devnull", "true"),
                Map.entry("target", "docs"));

            var ordinary = items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            PageTestAccess.titleChanged(ordinary.page(), "A public page");
            assertThat(host.savedTitle(ordinary.tab())).isEqualTo("A public page");
            assertThat(host.restoreState(ordinary.tab()).orElseThrow().values()).containsEntry("url", "https://example.com");
            assertThat(host.failures()).isEmpty();
        } });
    }

    @Test void twoItemsOpenedApartInDevNullHaveTwoStores() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var items = items(host, page -> { });
            var one = items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty(), items.dataStores().newDevNull());
            var two = items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty(), items.dataStores().newDevNull());
            assertThat(one.store().devNull()).isTrue();
            assertThat(two.store().devNull()).isTrue();
            assertThat(one.store()).isNotSameAs(two.store());
            assertThat(one.store().stores()).isNotSameAs(two.store().stores());
            assertThat(one.store().context()).isNotSameAs(two.store().context());
            assertThat(host.failures()).isEmpty();
        } });
    }

    /** Spec section 12: a /dev/null group's store ends with the last of its items, its opener first or not. */
    @Test void closingTheOpenerKeepsThePopupsStoreOpenUntilThePopupCloses() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var items = items(host, page -> { });
            DataStore devNull = items.dataStores().newDevNull();
            var opener = items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty(), devNull);
            opener.page().openItem("https://example.com/popup", TabPlacement.NEXT_TAB);
            var popup = items.items().getLast();
            opener.close();
            assertThat(devNull.closed()).as("the popup still holds it").isFalse();
            assertThat(items.dataStores().live()).contains(devNull);
            popup.close();
            assertThat(devNull.closed()).isTrue();
            assertThat(items.dataStores().live()).doesNotContain(devNull);
            var persistent = items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            persistent.close();
            assertThat(items.dataStores().persistent().closed()).as("the persistent store outlives its items").isFalse();
            assertThat(host.failures()).isEmpty();
        } });
    }

    /** An item whose tab cannot open gives its store back: a /dev/null store no item holds then ends. */
    @Test void anItemWhoseTabCannotOpenReleasesItsStore() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var items = items(host, page -> { });
            var gone = items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            gone.close();
            DataStore devNull = items.dataStores().newDevNull();
            assertThatThrownBy(() -> items.open(EXAMPLE, TabPlacement.NEXT_TAB, Optional.empty(), Optional.of(gone.tab()), devNull))
                .as("a closed tab opens nothing beside it").isInstanceOf(IllegalArgumentException.class);
            assertThat(devNull.closed()).isTrue();
            assertThat(items.items()).isEmpty();
            assertThat(host.failures()).isEmpty();
        } });
    }

    /** An item that fails before its tab opens gives its store back too: a /dev/null store no item holds ends. */
    @Test void anItemThatFailsBeforeItsTabOpensReleasesItsStore() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var host = new FakePluginHost();
            var items = items(host, page -> { });
            DataStore devNull = items.dataStores().newDevNull();
            host.close();
            assertThatThrownBy(() -> items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty(), devNull))
                .as("a stopped plugin makes no item").isInstanceOf(IllegalStateException.class);
            assertThat(devNull.closed()).isTrue();
            assertThat(items.dataStores().live()).doesNotContain(devNull);
        });
    }

    /**
     * Plan 5's routing (F13): a page's browser is found in its item's store from the moment Chromium names it, on
     * Chromium's thread, so its document's first query is answered from that store; and no longer once it has closed.
     */
    @Test void aPagesBrowserIsFoundInItsStoreFromTheMomentChromiumNamesIt() throws Exception {
        var host = onUi(FakePluginHost::new);
        try {
            var items = onUi(() -> items(host, page -> { }));
            var item = onUi(() -> items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty(), items.dataStores().newDevNull()));
            assertThat(item.page().browserId()).isEmpty();
            CompletableFuture.runAsync(() -> {
                PageTestAccess.browserMade(item.page(), 31);
                assertThat(items.dataStores().forBrowser(31)).as("at once, on Chromium's thread").containsSame(item.store());
            }).get(10, TimeUnit.SECONDS);
            assertThat(item.page().browserId()).hasValue(31);
            CompletableFuture.runAsync(() -> PageTestAccess.browserClosed(item.page())).get(10, TimeUnit.SECONDS);
            assertThat(items.dataStores().forBrowser(31)).isEmpty();
            assertThat(item.page().browserId()).isEmpty();
        } finally {
            onUi(() -> {
                host.close();
                return null;
            });
        }
    }

    /**
     * Spec section 12: a visit in a /dev/null item is recorded in its group's in-memory stores alone, and nothing reaches
     * the persistent database, whose file is never even made.
     */
    @Test void aDevNullItemsVisitIsRecordedInItsOwnStoresAlone(@TempDir Path data) throws Exception {
        var persistentStores = new Stores(BrowserDatabase.file(new DataFolder(data), cause -> { }), java.awt.EventQueue::invokeLater);
        var host = onUi(FakePluginHost::new);
        try {
            var items = onUi(() -> items(host, page -> { }, persistentStores));
            var item = onUi(() -> items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty(), items.dataStores().newDevNull()));
            onUi(() -> {
                PageTestAccess.loadStarted(item.page(), "https://private.example/", Reached.LINK);
                PageTestAccess.titleChanged(item.page(), "Private");
                return null;
            });
            memory.settle();
            assertThat(item.store().stores().history().visits(10).get(10, TimeUnit.SECONDS))
                .extracting(HistoryStore.Visit::url).containsExactly("https://private.example/");
            try (var files = Files.list(data)) {
                assertThat(files.map(file -> file.getFileName().toString()).filter(name -> name.startsWith("browser.sqlite")).toList())
                    .as("the persistent database is never made").isEmpty();
            }
            assertThat(MemoryStores.started(persistentStores)).isFalse();
            onUi(() -> {
                item.close();
                return null;
            });
            assertThat(persistentStores.history().visits(10).get(10, TimeUnit.SECONDS)).as("no row in the persistent database").isEmpty();
        } finally {
            onUi(() -> {
                host.close();
                return null;
            });
            persistentStores.close();
            persistentStores.closed().get(30, TimeUnit.SECONDS);
        }
    }

    /**
     * Plan 5's channel answers from the asking browser's store: the history page's list from a /dev/null item's browser
     * lists that item's visits, and from a persistent item's browser none of them; a browser with no store is refused.
     */
    @Test void theHistoryPageListsTheVisitsOfTheAskingBrowsersStoreOnly() throws Exception {
        var host = onUi(FakePluginHost::new);
        try {
            var items = onUi(() -> items(host, page -> { }));
            var devNull = onUi(() -> items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty(), items.dataStores().newDevNull()));
            var persistent = onUi(() -> items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty()));
            CompletableFuture.runAsync(() -> {
                PageTestAccess.browserMade(devNull.page(), 41);
                PageTestAccess.browserMade(persistent.page(), 42);
            }).get(10, TimeUnit.SECONDS);
            onUi(() -> {
                PageTestAccess.loadStarted(devNull.page(), "https://private.example/", Reached.LINK);
                PageTestAccess.loadStarted(persistent.page(), "https://public.example/", Reached.LINK);
                return null;
            });
            memory.settle();
            String list = "{\"op\":\"list\",\"query\":\"\"}";
            String fromDevNull = items.dataStores().answer(41, store -> HistoryPage.answer(store.stores().history(), list)).get(10, TimeUnit.SECONDS);
            String fromPersistent = items.dataStores().answer(42, store -> HistoryPage.answer(store.stores().history(), list)).get(10, TimeUnit.SECONDS);
            assertThat(fromDevNull).contains("https://private.example/").doesNotContain("https://public.example/");
            assertThat(fromPersistent).contains("https://public.example/").doesNotContain("https://private.example/");
            assertThat(items.dataStores().answer(43, store -> HistoryPage.answer(store.stores().history(), list)))
                .as("a browser with no store").failsWithin(10, TimeUnit.SECONDS);
        } finally {
            onUi(() -> {
                host.close();
                return null;
            });
        }
    }

    /**
     * Spec sections 6 and 12: the menu button lists a Reset item for each permission its store holds for the page's site,
     * and follows the store as it changes. Choosing one revokes that permission there and reloads the items of that store
     * that show the site and have shown their page; not the item of another site, of another store or one never shown.
     */
    @Test void theMenuResetsAPermissionOfThePagesSiteAndReloadsTheItemsOfItsStoreThatShowIt() throws Exception {
        var host = onUi(FakePluginHost::new);
        var parents = new ArrayList<JPanel>();
        try {
            var items = onUi(() -> items(host, page -> { }));
            var reloads = new CopyOnWriteArrayList<String>();
            List<BrowserItem> opened = onUi(() -> {
                DataStore devNull = items.dataStores().newDevNull();
                var all = List.of(items.open(URI.create("https://camera.test/a"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty()),
                    items.open(URI.create("https://camera.test/b"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty()),
                    items.open(URI.create("https://other.test/"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty()),
                    items.open(URI.create("https://camera.test/c"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty(), devNull),
                    items.open(URI.create("https://camera.test/d"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty()));
                List<String> names = List.of("Site", "Same", "Other", "DevNull", "Unshown");
                for (int at = 0; at < all.size(); at++) {
                    String name = names.get(at);
                    PageTestAccess.titleChanged(all.get(at).page(), name);
                    PageTestAccess.useReloader(all.get(at).page(), page -> reloads.add(name));
                    if (at < 4) parents.add(show(all.get(at)));
                }
                return all;
            });
            var permissions = opened.getFirst().store().permissions();
            permissions.set("https://camera.test", Permission.CAMERA, Decision.ALLOWED).get(10, TimeUnit.SECONDS);
            permissions.set("https://camera.test", Permission.NOTIFICATIONS, Decision.BLOCKED).get(10, TimeUnit.SECONDS);
            opened.get(3).store().permissions().set("https://camera.test", Permission.CAMERA, Decision.ALLOWED).get(10, TimeUnit.SECONDS);
            onUi(() -> {
                assertThat(resets(host, "Site")).containsExactly("Reset Camera Permission (Allowed)", "Reset Notifications Permission (Blocked)");
                assertThat(resets(host, "Other")).as("another site").isEmpty();
                host.contentView(ID, "Site").orElseThrow().choose(BrowserToolbar.MENU, "browser.permission.reset.camera");
                assertThat(reloads).as("the items of its store at the site").containsExactly("Site", "Same");
                return null;
            });
            assertThat(permissions.decision("https://camera.test", Permission.CAMERA)).isEmpty();
            assertThat(permissions.decision("https://camera.test", Permission.NOTIFICATIONS)).contains(Decision.BLOCKED);
            assertThat(opened.get(3).store().permissions().decision("https://camera.test", Permission.CAMERA)).as("another store's")
                .contains(Decision.ALLOWED);
            onUi(() -> {
                assertThat(resets(host, "Site")).as("the menu follows the store").containsExactly("Reset Notifications Permission (Blocked)");
                assertThat(resets(host, "DevNull")).containsExactly("Reset Camera Permission (Allowed)");
                items.reloadShowing(opened.getFirst().store(), Set.of());
                items.reloadShowing(opened.getFirst().store(), Set.of("https://other.test"));
                assertThat(reloads).containsExactly("Site", "Same", "Other");
                assertThat(host.failures()).isEmpty();
                return null;
            });
        } finally {
            onUi(() -> {
                parents.forEach(JPanel::removeNotify);
                host.close();
                return null;
            });
        }
    }

    /**
     * Ruling V17: a Reset item revokes its permission for the site the menu offered it for, though the page has gone on
     * to another site since. The menu offered first.test's Reset Camera; the page went on to second.test, for which the
     * menu offers Reset Microphone; the Reset Camera row chosen as it went, its action arriving after, revokes
     * first.test's camera and keeps second.test's microphone, and second.test's own Reset revokes that.
     */
    @Test void aResetRevokesItsPermissionForTheSiteTheMenuOfferedItFor() throws Exception {
        var host = onUi(FakePluginHost::new);
        try {
            var items = onUi(() -> items(host, page -> { }));
            var item = onUi(() -> items.open(URI.create("https://first.test/"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty()));
            var permissions = item.store().permissions();
            permissions.set("https://first.test", Permission.CAMERA, Decision.ALLOWED).get(10, TimeUnit.SECONDS);
            permissions.set("https://second.test", Permission.MICROPHONE, Decision.ALLOWED).get(10, TimeUnit.SECONDS);
            onUi(() -> {
                assertThat(resets(view(host, item))).containsExactly("Reset Camera Permission (Allowed)");
                item.load(URI.create("https://second.test/"));
                assertThat(resets(view(host, item))).as("the menu follows the page").containsExactly("Reset Microphone Permission (Allowed)");
                view(host, item).emit(new UiEvent.Action("browser.permission.reset.camera"));
                return null;
            });
            assertThat(permissions.decision("https://first.test", Permission.CAMERA)).as("the site the menu offered it for").isEmpty();
            assertThat(permissions.decision("https://second.test", Permission.MICROPHONE)).contains(Decision.ALLOWED);
            onUi(() -> {
                view(host, item).choose(BrowserToolbar.MENU, "browser.permission.reset.microphone");
                assertThat(host.failures()).isEmpty();
                return null;
            });
            assertThat(permissions.decision("https://second.test", Permission.MICROPHONE)).isEmpty();
        } finally {
            onUi(() -> {
                host.close();
                return null;
            });
        }
    }

    /** The Reset items of the menu button of the item whose tab is called {@code title}. UI thread. */
    private static List<String> resets(FakePluginHost host, String title) {
        return resets(host.contentView(ID, title).orElseThrow());
    }

    /** The Reset items of {@code view}'s menu button. UI thread. */
    private static List<String> resets(dev.jasper.sdk.testing.FakeKitView view) {
        var menu = (Nodes.MenuButton) Views.node(view.spec().root(), BrowserToolbar.MENU);
        return menu.items().stream().map(MenuItem::label).filter(label -> label.startsWith("Reset ")).toList();
    }

    /** The view of {@code item}'s tab, found by the title it shows now. UI thread. */
    private static dev.jasper.sdk.testing.FakeKitView view(FakePluginHost host, BrowserItem item) {
        return host.contentView(ID, host.contentPresentation(item.tab()).title()).orElseThrow();
    }

    /**
     * Spec section 14: a certificate the user went on past in a /dev/null item is allowed in its group alone, and the
     * allowance ends with the group's store.
     */
    @Test void aCertificateAllowedInADevNullItemIsItsGroupsAloneAndEndsWithIt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var items = items(host, page -> { });
            DataStore devNull = items.dataStores().newDevNull();
            var opener = items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty(), devNull);
            opener.page().openItem("https://example.com/popup", TabPlacement.NEXT_TAB);
            var popup = items.items().getLast();
            var persistent = items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            var certificate = new dev.jasper.browser.page.ServerCertificate("self-signed.badssl.com",
                "d53a1b43dbe103e328aa1f2e50c9ee35d35d116b19c8912f86aeff71eda21e61", List.of("CN=*.badssl.com"), true);
            devNull.allowance().allow(certificate.host(), certificate.sha256());
            assertThat(opener.page().allowsCertificate(certificate)).isTrue();
            assertThat(popup.page().allowsCertificate(certificate)).as("its group's").isTrue();
            assertThat(persistent.page().allowsCertificate(certificate)).as("not the persistent items'").isFalse();
            opener.close();
            popup.close();
            assertThat(devNull.allowance().allows(certificate.host(), certificate.sha256())).as("gone with its store").isFalse();
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
                .containsExactly(BrowserToolbar.BAR, AudioBar.BAR, BrowserItem.PAGE, BrowserItem.WAITING, BrowserFindBar.ROOT);
            assertThat(root.children().get(2).state().visible()).as("the page's canvas").isFalse();
            assertThat(root.children().get(3)).isInstanceOfSatisfying(Nodes.Empty.class, placeholder -> {
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
                assertThat(root.children()).extracting(Nodes.Node::id).containsExactly(BrowserToolbar.BAR, AudioBar.BAR, BrowserItem.PAGE,
                    BrowserFindBar.ROOT);
                assertThat(root.children().get(2).state().visible()).isTrue();
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

    /**
     * Plan 6 Task 11 and rulings F3 and V43: the items' engine suggestions and favicons go through the HTTP proxy the
     * items are given, whether their fetcher is made after it was given or before, and follow each change; an engine on
     * this computer goes direct. Loopback stand-ins play the proxies and the engine; names under {@code .invalid} reach a
     * proxy without being looked up, so nothing leaves this computer.
     */
    @Test void theItemsFetchesGoThroughTheProxyTheyAreGivenWhenTheirFetcherIsMadeAndAfter() throws Exception {
        try (var engine = new dev.jasper.browser.http.StandIn(); var first = new dev.jasper.browser.http.StandIn();
             var second = new dev.jasper.browser.http.StandIn()) {
            URI suggest = URI.create("http://suggest.invalid/suggest?q=a"), local = URI.create("http://127.0.0.1:" + engine.port() + "/suggest?q=a");
            URI icon = URI.create("http://page.invalid/favicon.ico"), page = URI.create("http://page.invalid/");
            var host = new AtomicReference<FakePluginHost>();
            var items = new AtomicReference<BrowserItems>();
            SwingUtilities.invokeAndWait(() -> {
                host.set(new FakePluginHost());
                items.set(items(host.get(), started -> { }, new Later(), null));
                items.get().favicons();
                items.get().useProxy(Optional.of(first.address()));
                items.get().suggest(suggest);
                items.get().favicons().fetch(icon, page);
                host.get().runBackground();
            });
            try {
                assertThat(List.of(first.next(), first.next())).as("the suggestion fetcher made after, the favicons' before")
                    .containsExactlyInAnyOrder("GET " + suggest + " HTTP/1.1", "GET " + icon + " HTTP/1.1");
                SwingUtilities.invokeAndWait(() -> {
                    items.get().useProxy(Optional.of(second.address()));
                    items.get().suggest(suggest);
                    items.get().favicons().fetch(icon, page);
                    items.get().suggest(local);
                    host.get().runBackground();
                });
                assertThat(List.of(second.next(), second.next())).as("both fetchers follow the change")
                    .containsExactlyInAnyOrder("GET " + suggest + " HTTP/1.1", "GET " + icon + " HTTP/1.1");
                assertThat(engine.next()).as("V43: an engine on this computer, direct").isEqualTo("GET /suggest?q=a HTTP/1.1");
                assertThat(engine.asked() || first.asked() || second.asked()).as("each fetch asked once").isFalse();
            } finally {
                SwingUtilities.invokeAndWait(() -> {
                    items.get().close();
                    host.get().close();
                });
            }
        }
    }

    /** A waiting item at {@code url}, as a restore after a run that ended while Chromium ran opens it. UI thread. */
    private static BrowserItem waiting(BrowserItems items, URI url) {
        var item = BrowserItem.open(items, url, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty(), java.util.OptionalInt.empty(), true,
            items.dataStores().persistent());
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
        return items(host, start, later, fetches, memory.make());
    }

    /**
     * As {@link #items(FakePluginHost, Consumer, Later, Fetches)}, with {@code persistent} as the persistent store's
     * history and bookmarks. Each /dev/null store's are in memory, and its context a recording stand-in. UI thread.
     */
    private BrowserItems items(FakePluginHost host, Consumer<Page> start, Later later, Fetches fetches, Stores persistent) {
        var context = new AtomicReference<PluginContext>();
        var settings = new AtomicReference<BrowserSettings.Declared>();
        host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(), plugin -> {
            context.set(plugin);
            settings.set(BrowserSettings.declare(plugin.settings()));
        });
        var stores = new DataStores(persistent, memory::make, () -> PageTestAccess.recordingContext(new AtomicInteger()),
            () -> settings.get().read().devNullMode());
        return new BrowserItems(context.get(), settings.get()::read, stores, start, later, fetches);
    }

    /** As {@link #items(FakePluginHost, Consumer, Later, Fetches)}, with delays that never end and fetches nobody answers. */
    private BrowserItems items(FakePluginHost host, Consumer<Page> start) {
        return items(host, start, new Later(), new Fetches());
    }

    /** As {@link #items(FakePluginHost, Consumer)}, with {@code persistent} as the persistent store's history and bookmarks. */
    private BrowserItems items(FakePluginHost host, Consumer<Page> start, Stores persistent) {
        return items(host, start, new Later(), new Fetches(), persistent);
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
