package dev.jasper.browser.item;

import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageContext;
import dev.jasper.browser.page.PagePrivacy;
import dev.jasper.browser.page.PageTestAccess;
import dev.jasper.browser.permissions.Decision;
import dev.jasper.browser.permissions.Permission;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.browser.store.MemoryStores;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.notifications.NotificationSpec;
import dev.jasper.sdk.notifications.Severity;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.TabHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.testing.FakeNotifications;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.TabPlacement;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Page notifications as Jasper notifications (spec section 12, plan 6 Task 6): information in the plugin's Page
 * notifications group, in the page's item's window, with no icon; the page's title, or its host when the title shows
 * nothing, and content that begins with the page's host (ruling V2), so no page can post a card that reads as Jasper's
 * own; at most 10 a page in a minute; a page's {@code close()} takes its notification out of history, and once the page
 * has forgotten its notifications it reaches none of them. Jasper counts the same text posted within a minute on one
 * notification (ruling V7), so the posts here differ unless they test that fold (ruling V8). The testkit's recorder plays
 * the host; pages are made without Chromium, on the UI thread.
 */
class PageNotificationsTest {
    private static final String GROUP = "dev.jasper.browser.pages";
    private static final String SITE = "https://chat.example";
    private static final WindowHandle WINDOW = new Window(UUID.randomUUID());
    private final FakeNotifications notifications = new FakeNotifications();
    private final AtomicLong now = new AtomicLong(5_000_000);
    private final PageNotifications pages = new PageNotifications(notifications.group(GROUP), now::get);
    private final MemoryStores memory = new MemoryStores();
    private FakePluginHost host;

    @AfterEach void close() throws Exception {
        if (host != null) onUi(() -> {
            host.close();
            return null;
        });
        memory.close();
    }

    @Test void theGroupIsPageNotificationsShownAsCards() {
        assertThat(PageNotifications.GROUP.id()).isEqualTo(GROUP);
        assertThat(PageNotifications.GROUP.name()).isEqualTo("Page notifications");
        assertThat(PageNotifications.GROUP.defaultMode()).isEqualTo(dev.jasper.sdk.notifications.DisplayMode.CARD);
    }

    @Test void aPostIsInformationInThePagesWindowWithItsTitleAndTheHostBeforeItsBodyAndNoIcon() throws Exception {
        onUi(() -> {
            Page page = page();
            assertThat(pages.post(page, WINDOW, SITE, "1", "  New message\t", "Hi there")).isTrue();
            NotificationSpec spec = only().spec();
            assertThat(only().group()).isEqualTo(GROUP);
            assertThat(spec.severity()).isEqualTo(Severity.INFO);
            assertThat(spec.title()).isEqualTo("New message");
            assertThat(spec.content()).isEqualTo("chat.example — Hi there");
            assertThat(spec.icon()).as("no icon").isEmpty();
            assertThat(spec.actions()).isEmpty();
            assertThat(spec.cause()).isEmpty();
            assertThat(spec.window()).contains(WINDOW);
            assertThat(spec.activate()).as("a click shows the page's item").isPresent();

            assertThat(pages.post(page, WINDOW, "https://chat.example:8443", "2", "Port", "  ")).isTrue();
            assertThat(last().content()).as("an empty body leaves the host alone, without its port").isEqualTo("chat.example");
            assertThat(pages.post(page, WINDOW, "http://[::1]:3000", "3", "Local", "up")).isTrue();
            assertThat(last().content()).isEqualTo("[::1] — up");
            assertThat(pages.post(page, WINDOW, "file://", "4", "File", "done")).isTrue();
            assertThat(last().content()).as("a file's site has no host").isEqualTo("file:// — done");
            return null;
        });
    }

    @Test void aTitleThatShowsNothingBecomesTheHost() throws Exception {
        onUi(() -> {
            Page page = page();
            String[] blank = {"", "   ", "\t\n", "\u00a0", "\u200b\u202e", "\u0007"};
            for (int i = 0; i < blank.length; i++) {
                assertThat(pages.post(page, WINDOW, SITE, "b" + i, blank[i], "body " + i)).isTrue();
                assertThat(last().title()).as("title %d", i).isEqualTo("chat.example");
            }
            assertThat(pages.post(page, WINDOW, "file://", "f", " ", "x")).isTrue();
            assertThat(last().title()).isEqualTo("file://");
            return null;
        });
    }

    /** Ruling V2: whatever the page writes, its notification's content begins with its host, which it cannot remove. */
    @Test void aPageCannotPostACardThatReadsAsJaspersOwn() throws Exception {
        onUi(() -> {
            Page page = page();
            String evil = "https://evil.example";
            String[] bodies = {"Vault locked — re-enter your password", "\n\nVault locked", "\u202eVault locked",
                "\u200b\u2028Vault locked", "chat.example — hi", "x".repeat(10_000), ""};
            for (int i = 0; i < bodies.length; i++) {
                assertThat(pages.post(page, WINDOW, evil, "v" + i, "Jasper", bodies[i])).isTrue();
                String content = last().content();
                assertThat(content).as("body %d", i).startsWith("evil.example");
                assertThat(content.equals("evil.example") || content.startsWith("evil.example — ")).as("body %d: %s", i, content).isTrue();
                assertThat(content.length()).isLessThanOrEqualTo(4_000);
            }
            // Review I2: a title's line breaks, which would push the host off the card, become single spaces.
            String[][] titles = {
                {"Vault locked" + "\n".repeat(150) + ".", "Vault locked ."},
                {"Vault\r\nlocked\t\u0085\u2028\u2029now", "Vault locked now"},
                {"\n\n\nVault locked\n\n", "Vault locked"},
                {"Vault   \u00a0 locked", "Vault locked"},
                {"Bell\u0007\u0007rings", "Bell rings"}};
            Page titled = page();
            for (int i = 0; i < titles.length; i++) {
                assertThat(pages.post(titled, WINDOW, evil, "t" + i, titles[i][0], "body " + i)).isTrue();
                assertThat(last().title()).as("title %d", i).isEqualTo(titles[i][1]);
                assertThat(last().content()).startsWith("evil.example \u2014 ");
            }
            return null;
        });
    }

    /** Review I2: History shows a notification's content whole, so a body keeps at most two blank lines in a row. */
    @Test void aBodyKeepsAtMostTwoBlankLinesInARow() throws Exception {
        onUi(() -> {
            Page page = page();
            String[][] bodies = {
                {"a" + "\n".repeat(150) + "b", "a\n\n\nb"},
                {"a\n\nb\n\n\nc", "a\n\nb\n\n\nc"},
                {"a" + "\u2028".repeat(40) + "b", "a\n\n\nb"},
                {"a" + "\r\n".repeat(40) + "b\rc\u0085d\u2029e", "a\n\n\nb\nc\nd\ne"},
                {"a" + "\n \t\u00a0\u200b".repeat(40) + "\nb", "a\n \t\u00a0\u200b\n \t\u00a0\u200b\nb"},
                {"\n\n\n\nbody\n\n\n\n", "body"}};
            for (int i = 0; i < bodies.length; i++) {
                assertThat(pages.post(page, WINDOW, SITE, "b" + i, "Body " + i, bodies[i][0])).isTrue();
                assertThat(last().content()).as("body %d", i).isEqualTo("chat.example \u2014 " + bodies[i][1]);
            }
            return null;
        });
    }

    /** Review M1: a long host is cut from the left, as Chrome elides an origin, so its registrable domain shows. */
    @Test void aLongHostIsElidedFromTheLeft() throws Exception {
        onUi(() -> {
            Page page = page();
            String labels = "x".repeat(50) + "." + "y".repeat(30) + ".evil.example";
            assertThat(pages.post(page, WINDOW, "https://" + labels, "1", "", "Body")).isTrue();
            String shown = "\u2026" + "y".repeat(30) + ".evil.example";
            assertThat(last().title()).isEqualTo(shown);
            assertThat(last().content()).isEqualTo(shown + " \u2014 Body");
            String single = "z".repeat(100) + ".com";
            assertThat(pages.post(page, WINDOW, "https://" + single + ":8443", "2", "T", "")).isTrue();
            assertThat(last().content()).as("one long label: its end and the top-level domain")
                .isEqualTo("\u2026" + "z".repeat(55) + ".com").hasSize(60);
            String sixty = "w".repeat(48) + ".example.com";
            assertThat(sixty).hasSize(60);
            assertThat(pages.post(page, WINDOW, "https://" + sixty, "3", "T", "")).isTrue();
            assertThat(last().content()).as("60 characters are kept whole").isEqualTo(sixty);
            return null;
        });
    }

    @Test void theTitleIsCutTo200CharactersAndTheContentTo4000() throws Exception {
        onUi(() -> {
            Page page = page();
            assertThat(pages.post(page, WINDOW, SITE, "1", "t".repeat(300), "b".repeat(5_000))).isTrue();
            assertThat(last().title()).hasSize(200).isEqualTo("t".repeat(199) + "…");
            assertThat(last().content()).hasSize(4_000).startsWith("chat.example — bbb").endsWith("b…");
            // A pair of surrogates the cut would split is dropped whole.
            String emoji = "\ud83d\ude00";
            assertThat(pages.post(page, WINDOW, SITE, "2", "t".repeat(198) + emoji + "tail", "body")).isTrue();
            assertThat(last().title()).isEqualTo("t".repeat(198) + "…");
            assertThat(pages.post(page, WINDOW, SITE, "3", "t".repeat(200), "body")).isTrue();
            assertThat(last().title()).as("exactly 200 stays whole").isEqualTo("t".repeat(200));
            return null;
        });
    }

    /** Plan 6's call: at most 10 a page in any 60 s; a refused post counts for nothing, and other pages are not held. */
    @Test void theEleventhPostWithinAMinuteIsRefusedAndAllowedAgainAfterAMinute() throws Exception {
        onUi(() -> {
            Page page = page(), other = page();
            long start = now.get();
            for (int i = 0; i < 10; i++) {
                now.set(start + i);
                assertThat(pages.post(page, WINDOW, SITE, "n" + i, "Message " + i, "")).as("post %d", i).isTrue();
            }
            now.set(start + 59_999);
            assertThat(pages.post(page, WINDOW, SITE, "n10", "Message 10", "")).as("the 11th within a minute").isFalse();
            assertThat(pages.post(page, WINDOW, SITE, "n11", "Message 11", "")).as("refused again").isFalse();
            assertThat(pages.post(other, WINDOW, SITE, "o", "Other page", "")).as("another page posts").isTrue();
            assertThat(notifications.posts()).hasSize(11);
            now.set(start + 60_000);
            assertThat(pages.post(page, WINDOW, SITE, "n12", "Message 12", "")).as("a minute after the first").isTrue();
            assertThat(pages.post(page, WINDOW, SITE, "n13", "Message 13", "")).as("the second is still within its minute").isFalse();
            now.set(start + 60_001);
            assertThat(pages.post(page, WINDOW, SITE, "n14", "Message 14", "")).isTrue();
            pages.forget(page);
            assertThat(pages.post(page, WINDOW, SITE, "n15", "Message 15", "")).as("a new document is the same page").isFalse();
            assertThat(notifications.posts()).hasSize(13).allSatisfy(post -> assertThat(post.count()).isEqualTo(1));
            return null;
        });
    }

    /** Task 1's {@code remove()}: a page's {@code close()} takes its notification out of history. */
    @Test void closeRemovesThePagesNotification() throws Exception {
        onUi(() -> {
            Page page = page(), other = page();
            assertThat(pages.post(page, WINDOW, SITE, "1", "First", "")).isTrue();
            assertThat(pages.post(page, WINDOW, SITE, "2", "Second", "")).isTrue();
            assertThat(pages.post(other, WINDOW, SITE, "1", "Other's", "")).isTrue();
            pages.close(page, "1");
            assertThat(post("First").removed()).isTrue();
            assertThat(post("Second").removed()).isFalse();
            assertThat(post("Other's").removed()).as("another page's id 1").isFalse();
            pages.close(page, "1");
            pages.close(page, "unknown");
            pages.close(page(), "2");
            assertThat(post("Second").removed()).isFalse();
            return null;
        });
    }

    /** At a main-frame load start and as the page closes it forgets its notifications: what it posted stays. */
    @Test void closeAfterForgetDoesNothing() throws Exception {
        onUi(() -> {
            Page page = page();
            assertThat(pages.post(page, WINDOW, SITE, "1", "Kept", "")).isTrue();
            pages.forget(page);
            pages.close(page, "1");
            assertThat(post("Kept").removed()).isFalse();
            assertThat(post("Kept").expired()).isFalse();
            assertThat(pages.post(page, WINDOW, SITE, "1", "Next document's", "")).isTrue();
            pages.close(page, "1");
            assertThat(post("Next document's").removed()).isTrue();
            assertThat(post("Kept").removed()).isFalse();
            pages.forget(page());
            return null;
        });
    }

    @Test void aClickShowsThePagesItemUntilThePageCloses() throws Exception {
        onUi(() -> {
            Page page = page();
            var shown = new AtomicInteger();
            page.onShowItem(shown::incrementAndGet);
            assertThat(pages.post(page, WINDOW, SITE, "1", "Click me", "")).isTrue();
            assertThat(notifications.click("Click me")).isTrue();
            assertThat(shown).hasValue(1);
            page.close();
            assertThat(notifications.click("Click me")).isTrue();
            assertThat(shown).as("a closed page shows no item").hasValue(1);
            return null;
        });
    }

    /**
     * Ruling V8: two tabs of one site posting the same text within a minute make one notification (ruling V2's host makes
     * the texts equal), and closing either one leaves the notification, and the other tab's links, live.
     */
    @Test void closingOneOfTwoFoldedNotificationsKeepsTheOthersLinks() throws Exception {
        onUi(() -> {
            var shown = new ArrayList<String>();
            Page first = page(), second = page();
            first.onShowItem(() -> shown.add("first"));
            second.onShowItem(() -> shown.add("second"));
            assertThat(pages.post(first, WINDOW, SITE, "1", "Hi", "There")).isTrue();
            assertThat(pages.post(second, WINDOW, SITE, "1", "Hi", "There")).isTrue();
            assertThat(notifications.posts()).as("one notification").hasSize(1);
            assertThat(post("Hi").count()).isEqualTo(2);
            pages.close(second, "1");
            assertThat(post("Hi").removed()).isFalse();
            assertThat(notifications.click("Hi")).isTrue();
            assertThat(shown).as("the first tab's link is live").containsExactly("first");
            pages.close(first, "1");
            assertThat(post("Hi").removed()).isTrue();

            shown.clear();
            Page third = page(), fourth = page();
            third.onShowItem(() -> shown.add("third"));
            fourth.onShowItem(() -> shown.add("fourth"));
            assertThat(pages.post(third, WINDOW, SITE, "1", "Yo", "")).isTrue();
            assertThat(pages.post(fourth, WINDOW, SITE, "1", "Yo", "")).isTrue();
            pages.close(third, "1");
            assertThat(post("Yo").removed()).isFalse();
            assertThat(notifications.click("Yo")).isTrue();
            assertThat(shown).as("the fourth tab's link is live").containsExactly("fourth");
            return null;
        });
    }

    /**
     * The item side: its page's stored decisions are its store's, and its page's notifications post in the plugin's
     * Page notifications group, in the item's window; a click selects the item's tab; a main-frame load start and the
     * item's close forget them. Stand-in items over stores in memory, on the testkit's fake host.
     */
    @Test void anItemsPageNotificationShowsInItsWindowAndItsClickSelectsItsTab() throws Exception {
        BrowserItems items = items();
        BrowserItem chat = onUi(() -> items.open(URI.create(SITE + "/room"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty()));
        BrowserItem other = onUi(() -> items.open(URI.create("https://other.example/"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty()));
        chat.store().permissions().set(SITE, Permission.NOTIFICATIONS, Decision.ALLOWED).get(10, TimeUnit.SECONDS);
        onUi(() -> {
            PagePrivacy privacy = ItemTestAccess.page(chat).privacy();
            assertThat(privacy.storedDecision(SITE, Permission.NOTIFICATIONS)).as("the item's store").contains(Decision.ALLOWED);
            assertThat(privacy.storedDecision(SITE, Permission.CAMERA)).isEmpty();
            assertThat(privacy.showNotification(new PagePrivacy.Notice(SITE, "1", "Ping", "From the room"))).isTrue();
            FakeNotifications.Post post = host.notifications().posts().getLast();
            assertThat(post.line()).isEqualTo(GROUP + "|INFO|Ping|chat.example — From the room");
            WindowHandle window = post.spec().window().orElseThrow();
            assertThat(window.id()).as("the item's window").isEqualTo(host.contentTabs().getFirst().window().id());
            assertThat(other.focusedIn(window)).as("the other item is shown").isTrue();
            assertThat(host.notifications().click("Ping")).isTrue();
            assertThat(chat.focusedIn(window)).as("the click selected the item's tab").isTrue();

            privacy.closeNotification("1");
            assertThat(post.removed()).isTrue();
            assertThat(privacy.showNotification(new PagePrivacy.Notice(SITE, "2", "Before the load", ""))).isTrue();
            PageTestAccess.loadStarted(ItemTestAccess.page(chat), SITE + "/next");
            privacy.closeNotification("2");
            assertThat(host.notifications().posts().getLast().removed()).as("forgotten at the load start").isFalse();
            assertThat(privacy.showNotification(new PagePrivacy.Notice(SITE, "3", "Before the close", ""))).isTrue();
            FakeNotifications.Post beforeClose = host.notifications().posts().getLast();
            chat.close();
            assertThat(privacy.showNotification(new PagePrivacy.Notice(SITE, "4", "After the close", ""))).as("a closed item's page").isFalse();
            privacy.closeNotification("3");
            assertThat(beforeClose.removed()).as("what it posted stays").isFalse();
            assertThat(host.failures()).isEmpty();
            return null;
        });
    }

    /** Ruling V20: a /dev/null item's page shows no notification, whatever its store holds, as Chrome's Incognito. */
    @Test void aDevNullItemsPageShowsNoNotification() throws Exception {
        BrowserItems items = items();
        BrowserItem item = onUi(() -> items.open(URI.create(SITE + "/room"), TabPlacement.NEW_TAB, Optional.empty(), Optional.empty(),
            items.dataStores().newDevNull()));
        item.store().permissions().set(SITE, Permission.NOTIFICATIONS, Decision.ALLOWED).get(10, TimeUnit.SECONDS);
        onUi(() -> {
            PagePrivacy privacy = ItemTestAccess.page(item).privacy();
            assertThat(privacy.showNotification(new PagePrivacy.Notice(SITE, "1", "Ping", "From the room"))).isFalse();
            assertThat(host.notifications().posts()).isEmpty();
            assertThat(host.failures()).isEmpty();
            return null;
        });
    }

    /** The one post made. */
    private FakeNotifications.Post only() {
        assertThat(notifications.posts()).hasSize(1);
        return notifications.posts().getFirst();
    }

    /** The spec of the post made last. */
    private NotificationSpec last() {
        return notifications.posts().getLast().spec();
    }

    /** The post titled {@code title}. */
    private FakeNotifications.Post post(String title) {
        return notifications.posts().stream().filter(post -> post.spec().title().equals(title)).findFirst()
            .orElseThrow(() -> new AssertionError("no post " + title));
    }

    private static Page page() {
        return new Page(URI.create(SITE + "/"), () -> { }, PageTestAccess.noFavicons(), Runnable::run, PageContext.persistent());
    }

    /** The items of a started stand-in plugin on a fake host with a window, whose stores are in memory. */
    private BrowserItems items() throws Exception {
        return onUi(() -> {
            host = new FakePluginHost();
            var context = new AtomicReference<PluginContext>();
            var settings = new AtomicReference<BrowserSettings.Declared>();
            host.start(new PluginInfo("dev.jasper.browser", "Browser", "0.1.0", Set.of()), Set.of(), Set.of(), plugin -> {
                context.set(plugin);
                settings.set(BrowserSettings.declare(plugin.settings()));
            });
            host.activateTerminalWindow(host.addTerminalWindow());
            var stores = new DataStores(memory.make(), memory::make, () -> PageTestAccess.recordingContext(new AtomicInteger()), () -> false);
            return new BrowserItems(context.get(), settings.get()::read, stores, Optional.empty(), page -> { });
        });
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
        SwingUtilities.invokeAndWait(() -> { });
        if (failure.get() instanceof Error error) throw error;
        if (failure.get() instanceof Exception exception) throw exception;
        return result.get();
    }

    /** A window that is open and shows nothing. */
    private record Window(UUID id) implements WindowHandle {
        @Override public List<TabHandle> tabs() { return List.of(); }
        @Override public Optional<TabHandle> activeTab() { return Optional.empty(); }
        @Override public boolean isActive() { return true; }
        @Override public boolean isOpen() { return true; }
        @Override public void toFront() { }
    }
}
