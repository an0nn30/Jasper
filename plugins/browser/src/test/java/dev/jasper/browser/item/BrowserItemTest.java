package dev.jasper.browser.item;

import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageTestAccess;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.ContentPresentation;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.TabPlacement;
import dev.jasper.sdk.ui.UiEvent;
import java.net.URI;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Browser items on the testkit's fake host. Their pages are made by a recording stand-in, because a page cannot be made
 * without Chromium. Headless, a lightweight parent given a peer shows, so adding a page's canvas to it shows the item.
 */
class BrowserItemTest {
    private static final String ID = "dev.jasper.browser";
    private static final URI EXAMPLE = URI.create("https://example.com");

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
            var commands = host.contentCommands(item.tab());
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

    @Test void theItemsViewIsTheToolbarAboveTheCanvasAsPreviewContent() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            items(host, page -> { }).open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            var root = host.contentView(ID, "example.com").orElseThrow().spec().root();
            assertThat(root).isInstanceOfSatisfying(dev.jasper.sdk.ui.Nodes.Split.class, split -> {
                assertThat(split.kind()).isEqualTo(dev.jasper.sdk.ui.Nodes.SplitKind.DOCUMENT);
                assertThat(split.children()).extracting(dev.jasper.sdk.ui.Nodes.Node::id).containsExactly(BrowserToolbar.BAR, BrowserItem.PAGE);
                assertThat(split.children().getLast()).isInstanceOfSatisfying(dev.jasper.sdk.ui.Nodes.Custom.class, page -> {
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
            assertThat(root.children()).extracting(Nodes.Node::id).containsExactly(BrowserToolbar.BAR, BrowserItem.PAGE, BrowserItem.WAITING);
            assertThat(root.children().get(1).state().visible()).as("the page's canvas").isFalse();
            assertThat(root.children().getLast()).isInstanceOfSatisfying(Nodes.Empty.class, placeholder -> {
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
                assertThat(root.children()).extracting(Nodes.Node::id).containsExactly(BrowserToolbar.BAR, BrowserItem.PAGE);
                assertThat(root.children().getLast().state().visible()).isTrue();
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

    /** A waiting item at {@code url}, as a restore after a run that ended while Chromium ran opens it. UI thread. */
    private static BrowserItem waiting(BrowserItems items, URI url) {
        var item = BrowserItem.open(items, url, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty(), true);
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

    /** The items of a started stand-in plugin whose pages {@code start} makes. UI thread. */
    private static BrowserItems items(FakePluginHost host, Consumer<Page> start) {
        var context = new AtomicReference<PluginContext>();
        host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(), context::set);
        return new BrowserItems(context.get(), start);
    }
}
