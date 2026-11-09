package dev.jasper.browser;

import dev.jasper.browser.adblock.FilterList;
import dev.jasper.browser.api.MarkdownPreviews;
import dev.jasper.browser.api.PreviewEvent;
import dev.jasper.browser.engine.Chromium;
import dev.jasper.browser.item.BrowserItems;
import dev.jasper.browser.item.ItemTestAccess;
import dev.jasper.browser.page.PageTestAccess;
import dev.jasper.browser.permissions.Decision;
import dev.jasper.browser.permissions.Permission;
import dev.jasper.browser.restore.BrowserRestore;
import dev.jasper.browser.settings.BrowserSettingsPanel;
import dev.jasper.browser.store.CapturedLog;
import dev.jasper.browser.store.MemoryStores;
import dev.jasper.browser.store.Reached;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.Nodes;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The plugin's registrations and its part in Chromium's life, on the SDK's fake host; nothing here starts Chromium. No
 * test downloads a filter list (F9): each plugin's list asks a client of the test, which fails the test unless the test
 * means it to be asked, and its address is on no host of the web.
 */
class BrowserPluginTest {
    private static final String ID = "dev.jasper.browser";
    private static final PluginInfo INFO = new PluginInfo(ID, "Browser", "0.1.0", Set.of("browser.provide", "palette.contribute"));
    /** The filter list's address in these tests, a reserved name. */
    private static final URI LIST = URI.create("https://filters.test/list.json");
    private final List<String> clientsAsked = new CopyOnWriteArrayList<>();

    /** F9: a plugin whose filter list fails the test if it asks for a client. */
    private BrowserPlugin plugin() {
        return new BrowserPlugin(() -> {
            clientsAsked.add("a client");
            throw new AssertionError("A test must not download a filter list");
        }, settings -> LIST);
    }

    @AfterEach void noTestDownloadedAFilterList() {
        assertThat(clientsAsked).as("F9: no filter list downloaded").isEmpty();
    }

    /**
     * F8 and spec section 19: a start with ad blocking already on reads no filter list, cached or not, and asks for no
     * client; turning it on in Settings starts the list, which then reads its cache, fresh from its address, without the
     * network. The stop ends it.
     */
    @Test void aStartWithAdBlockingOnReadsNoFilterListAndTurningItOnReadsTheCache(@TempDir Path root) throws Exception {
        Path cache = Files.createDirectories(root.resolve(ID).resolve("data").resolve("adblock"));
        Files.writeString(cache.resolve("state.json"), "{\"url\":\"" + LIST + "\",\"updated\":\"" + Instant.now() + "\",\"rules\":1,\"skipped\":0}");
        Files.writeString(cache.resolve("rules.json"), "[{\"trigger\":{\"url-filter\":\"ads.example/\"},\"action\":{\"type\":\"block\"}}]");
        var plugin = plugin();
        var host = new AtomicReference<FakePluginHost>();
        SwingUtilities.invokeAndWait(() -> {
            host.set(new FakePluginHost(root));
            host.get().setSetting(ID, "ad_blocking", true);
            host.get().start(INFO, Set.of(), Set.of(), plugin);
            host.get().runBackground();
        });
        try {
            assertThat(plugin.filters().active()).as("nothing read at the start").isEmpty();
            assertThat(plugin.filters().status()).isEqualTo(new FilterList.Status(0, Optional.empty(), false, Optional.empty()));
            SwingUtilities.invokeAndWait(() -> {
                host.get().setSetting(ID, "ad_blocking", false);
                host.get().setSetting(ID, "ad_blocking", true);
                host.get().runBackground();
            });
            assertThat(plugin.filters().active()).as("turned on, the list reads its cache").isPresent().get()
                .extracting(dev.jasper.browser.adblock.RuleSet::size).isEqualTo(1);
            assertThat(plugin.filters().status().updating()).as("fresh from its address: nothing is due").isFalse();
            SwingUtilities.invokeAndWait(() -> host.get().stopAll());
            assertThat(plugin.filters().active()).as("the stop ends the list").isEmpty();
        } finally {
            SwingUtilities.invokeAndWait(() -> host.get().close());
        }
    }

    /**
     * F9's proof: turning ad blocking on in the fake host, here through the setup page's Ad Blocker switch, which shows
     * and sets Enable Ad Blocking, downloads the list only through the client the test gave the plugin, for the address
     * it gave; that client opens no connection, and its failure is the list's status.
     */
    @Test void turningAdBlockingOnFromTheSetupPageDownloadsOnlyThroughTheGivenClient(@TempDir Path root) throws Exception {
        var asked = new CopyOnWriteArrayList<String>();
        var plugin = new BrowserPlugin(() -> {
            asked.add("a client");
            throw new IllegalStateException("no network in tests");
        }, settings -> LIST);
        var host = new AtomicReference<FakePluginHost>();
        SwingUtilities.invokeAndWait(() -> {
            host.set(new FakePluginHost(root));
            host.get().start(INFO, Set.of(), Set.of(), plugin);
        });
        try {
            assertThat(ask(plugin, "onboarding-setup", 1, "{\"op\":\"switches\"}")).isEqualTo("{\"switches\":[{\"id\":\"ad-blocker\",\"title\":"
                + "\"Ad Blocker\",\"description\":\"Block ads with a filter list that updates every day.\",\"on\":false}]}");
            assertThat(ask(plugin, "onboarding-setup", 1, "{\"op\":\"switch\",\"id\":\"ad-blocker\",\"on\":\"true\"}")).isEqualTo("{\"on\":true}");
            SwingUtilities.invokeAndWait(() -> {
                assertThat(host.get().savedSettings(ID)).containsEntry("ad_blocking", true);
                host.get().runBackground();
            });
            assertThat(asked).as("the given client alone, asked once").containsExactly("a client");
            assertThat(plugin.filters().status()).isEqualTo(new FilterList.Status(0, Optional.empty(), false, Optional.of("no network in tests")));
            assertThat(plugin.filters().active()).isEmpty();
            assertThat(root.resolve(ID).resolve("data").resolve("adblock").resolve("rules.json")).doesNotExist();
            SwingUtilities.invokeAndWait(() -> assertThat(host.get().failures()).isEmpty());
        } finally {
            SwingUtilities.invokeAndWait(() -> host.get().close());
        }
    }

    @Test void aChromiumThatCannotStartPostsOneNotificationWithTheReasonWhileThePluginRuns() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            plugin.startFailed(new IllegalStateException("Chromium did not start within 90 seconds"));
            assertThat(host.notifications().lines())
                .containsExactly("dev.jasper.browser|ERROR|The browser could not start|Chromium did not start within 90 seconds");
            host.stopAll();
            plugin.startFailed(new IllegalStateException("late"));
            assertThat(host.notifications().lines()).as("a stopped plugin only logs").hasSize(1);
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * Plan 5: a database that cannot be used posts one notification with the reason, while the plugin runs; it names the
     * site permissions too, which plan 6 keeps there (ruling V15).
     */
    @Test void historyBookmarksAndSitePermissionsThatAreUnavailablePostANotificationWithTheReasonWhileThePluginRuns() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost(); var log = new CapturedLog("dev.jasper.plugins." + ID)) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            plugin.storesUnavailable(new java.sql.SQLException("[SQLITE_NOTADB] file is not a database"));
            assertThat(host.notifications().lines())
                .containsExactly("dev.jasper.browser|WARNING|Browser history, bookmarks and site permissions are unavailable|[SQLITE_NOTADB] file is not a database");
            host.stopAll();
            plugin.storesUnavailable(new java.sql.SQLException("late"));
            assertThat(host.notifications().lines()).as("a stopped plugin only logs").hasSize(1);
            assertThat(log.records()).extracting(LogRecord::getLevel, LogRecord::getMessage).containsExactly(
                tuple(Level.WARNING, "Browser history, bookmarks and site permissions are unavailable: [SQLITE_NOTADB] file is not a database"),
                tuple(Level.WARNING, "Browser history, bookmarks and site permissions are unavailable: late"));
            assertThat(log.records()).allSatisfy(logged -> assertThat(logged.getThrown()).isInstanceOf(java.sql.SQLException.class));
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * Spec section 19: the start makes the plugin's history and bookmarks without opening their database, and the stop
     * closes them, the database never opened.
     */
    @Test void theStartMakesTheStoresWithoutOpeningThemAndTheStopClosesThem() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            assertThat(MemoryStores.started(plugin.stores())).isFalse();
            assertThat(plugin.stores().closed()).isNotDone();
            host.stopAll();
            assertThat(plugin.stores().closed()).isDone();
            assertThat(MemoryStores.started(plugin.stores())).isFalse();
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * Plan 5's H37: the data folder, which holds the history and Chromium's cookies, is its owner's alone once the
     * database first opens; the start leaves it as it was.
     */
    @Test @DisabledOnOs(OS.WINDOWS) void theDataFolderIsItsOwnersAloneOnceTheDatabaseOpens(@TempDir Path root) throws Exception {
        Path data = Files.createDirectories(root.resolve(ID).resolve("data"));
        Files.setPosixFilePermissions(data, PosixFilePermissions.fromString("rwxr-xr-x"));
        var plugin = plugin();
        var host = new AtomicReference<FakePluginHost>();
        SwingUtilities.invokeAndWait(() -> {
            host.set(new FakePluginHost(root));
            host.get().start(INFO, Set.of(), Set.of(), plugin);
        });
        try {
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(data))).as("after the start").isEqualTo("rwxr-xr-x");
            plugin.stores().bookmarks().all().get(10, TimeUnit.SECONDS);
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(data))).isEqualTo("rwx------");
        } finally {
            SwingUtilities.invokeAndWait(() -> host.get().close());
        }
    }

    /** Spec section 11: the start registers the Bookmarks and Browser History scopes, which read nothing until searched. */
    @Test void theStartRegistersTheBookmarksAndBrowserHistoryScopesWithoutOpeningTheDatabase() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var plugin = plugin();
            UUID window = host.addTerminalWindow();
            host.start(INFO, Set.of(), Set.of(), plugin);
            assertThat(host.searchScope("dev.jasper.browser.scope.bookmarks", "", window, null)).isEmpty();
            assertThat(host.searchScope("dev.jasper.browser.scope.history", "", window, null)).isEmpty();
            assertThat(MemoryStores.started(plugin.stores())).isFalse();
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * Spec section 18: a plugin that requires the Browser, optionally, finds the Markdown previews' service; each open
     * preview hears once when Chromium's start fails, and a closed one hears nothing. Nothing starts Chromium.
     */
    @Test void thePluginPublishesTheMarkdownPreviewsAndTellsThemWhenChromiumCannotStart() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            MarkdownPreviews previews = previews(host);
            assertThat(previews.available()).isTrue();
            List<PreviewEvent> told = new ArrayList<>(), closedTold = new ArrayList<>();
            var preview = previews.create(told::add);
            previews.create(closedTold::add).close();
            plugin.startFailed(new IllegalStateException("Chromium did not start within 90 seconds"));
            assertThat(told).containsExactly(new PreviewEvent.Unavailable("Chromium did not start within 90 seconds"));
            assertThat(closedTold).isEmpty();
            preview.close();
            assertThat(plugin.chromium().state()).as("nothing asked for Chromium").isEqualTo(Chromium.State.NOT_STARTED);
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        } });
    }

    /** Stopping the Browser stops Chromium: every preview still open is unavailable, and no preview is available after. */
    @Test void stoppingThePluginTellsEachOpenPreviewItIsUnavailable() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            MarkdownPreviews previews = previews(host);
            List<PreviewEvent> told = new ArrayList<>();
            previews.create(told::add);
            host.stopAll();
            assertThat(told).containsExactly(new PreviewEvent.Unavailable(
                "Chromium can start once each time Jasper runs. Quit and reopen Jasper to use the browser again."));
            assertThat(previews.available()).isFalse();
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * Once Chromium cannot serve pages, New Browser Tab, New Browser Window and the split item post the notification of
     * why again, instead of opening an item no page could show. Closing a Chromium that never started touches no
     * Chromium class.
     */
    @Test void onceChromiumCannotServePagesANewItemPostsWhyAgainInsteadOfOpening() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            assertThat(host.invoke(BrowserPlugin.NEW_TAB, window, null)).isTrue();
            assertThat(host.contentTabs()).as("not started: it opens").hasSize(1);
            plugin.chromium().close();
            assertThat(plugin.chromium().state()).isEqualTo(Chromium.State.STOPPED);
            for (String action : new String[] { BrowserPlugin.NEW_TAB, BrowserPlugin.NEW_WINDOW, BrowserPlugin.SPLIT.rightAction(),
                    BrowserPlugin.SPLIT.downAction() })
                assertThat(host.invoke(action, window, null)).as(action).isTrue();
            assertThat(host.contentTabs()).as("no more items").hasSize(1);
            assertThat(host.notifications().posts()).as("each posts it again, counted on one notification").singleElement().satisfies(post -> {
                assertThat(post.line()).isEqualTo("dev.jasper.browser|ERROR|The browser could not start|"
                    + "Chromium can start once each time Jasper runs. Quit and reopen Jasper to use the browser again.");
                assertThat(post.count()).isEqualTo(4);
            });
            assertThat(host.failures()).isEmpty();
        } });
    }

    /** The service a plugin that requires the Browser, optionally, as the Editor does, finds. */
    private static MarkdownPreviews previews(FakePluginHost host) {
        var found = new AtomicReference<Optional<MarkdownPreviews>>();
        host.start(new PluginInfo("dev.jasper.consumer", "Consumer", "0.1.0", Set.of()), Set.of(), Set.of(ID),
            context -> found.set(context.services().find(MarkdownPreviews.class)));
        return found.get().orElseThrow();
    }

    @Test void newBrowserTabAndNewBrowserWindowAreInTheFileMenuWithTheGlobeAndNoDefaultKey() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            assertThat(host.menu("FILE")).containsExactly("item:" + BrowserPlugin.NEW_TAB, "item:" + BrowserPlugin.NEW_WINDOW);
            assertThat(host.actions()).contains(BrowserPlugin.NEW_TAB + "|New Browser Tab|true", BrowserPlugin.NEW_WINDOW + "|New Browser Window|true");
            for (String action : new String[] { BrowserPlugin.NEW_TAB, BrowserPlugin.NEW_WINDOW })
                assertThat(host.actionSpec(action)).get().satisfies(spec -> {
                    assertThat(spec.icon()).contains(BrowserItems.GLOBE);
                    assertThat(spec.defaultBinding()).isEmpty();
                    assertThat(spec.otherDefaultBinding()).isEmpty();
                });
            assertThat(BrowserPlugin.SPLIT.id()).isEqualTo("dev.jasper.browser.browser");
            assertThat(BrowserPlugin.SPLIT.label()).isEqualTo("Browser");
            assertThat(BrowserPlugin.SPLIT.icon()).contains(BrowserItems.GLOBE);
            assertThat(host.actionSpec(BrowserPlugin.SPLIT.rightAction())).as("the split item's Browser Right").isPresent();
            assertThat(host.actionSpec(BrowserPlugin.SPLIT.downAction())).isPresent();
            assertThat(host.failures()).isEmpty();
        } });
    }

    /** Spec section 15: the bookmark notifications are a group of their own, which Settings lists from the start. */
    @Test void theStartDeclaresTheBookmarksNotificationGroup() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            assertThat(host.notifications().groups()).contains("dev.jasper.browser.bookmarks|Bookmarks|CARD");
            assertThat(host.failures()).isEmpty();
        } });
    }

    /** Spec section 12: pages' notifications post in a group of their own, which Settings lists from the start. */
    @Test void theStartDeclaresThePageNotificationsGroup() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            assertThat(host.notifications().groups()).contains("dev.jasper.browser.pages|Page notifications|CARD");
            assertThat(host.failures()).isEmpty();
        } });
    }

    @Test void aNewTabOpensTheStartPageInItsWindowAndANewWindowInAWindowOfItsOwn() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            host.start(INFO, Set.of(), Set.of(), plugin());
            assertThat(host.declared(ID)).containsExactly("start_page", "page_zoom", "open_links_from_terminal", "open_local_web_files",
                "search_engine", "custom_search_url", "custom_suggestions_url", "devnull_mode", "ad_blocking", "filter_list_url");
            assertThat(host.browserPlugin()).as("Jasper's web browser").contains(ID);
            assertThat(host.invoke(BrowserPlugin.NEW_TAB, window, null)).isTrue();
            var tab = host.contentTabs().getLast();
            assertThat(tab.window().id()).isEqualTo(window);
            assertThat(host.contentPresentation(tab).location()).isEqualTo("jasper-about:welcome");
            assertThat(host.contentPresentation(tab).title()).isEqualTo("Web Browser");
            assertThat(host.contentPresentation(tab).icon()).isEqualTo(BrowserItems.GLOBE);
            assertThat(host.invoke(BrowserPlugin.NEW_WINDOW, window, null)).isTrue();
            var own = host.contentTabs().getLast();
            assertThat(own).isNotSameAs(tab);
            assertThat(own.window().id()).as("a full window of its own").isNotEqualTo(window);
            assertThat(host.invoke(BrowserPlugin.SPLIT.rightAction(), window, null)).isTrue();
            assertThat(host.contentTabs()).hasSize(3);
            assertThat(host.failures()).isEmpty();
        } });
    }

    @Test void theStartPageSettingDecidesWhatANewItemLoadsAndRefusesWhatTheAddressFieldWouldNotLoad() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            host.setSetting(ID, "start_page", "example.com");
            host.start(INFO, Set.of(), Set.of(), plugin());
            assertThat(host.invoke(BrowserPlugin.NEW_TAB, window, null)).isTrue();
            assertThat(host.contentPresentation(host.contentTabs().getLast()).location()).as("as the address field loads it")
                .isEqualTo("https://example.com");
            host.setSetting(ID, "start_page", "chrome://version");
            assertThat(host.settingsReports(ID)).singleElement().asString().startsWith("start_page");
            assertThat(host.invoke(BrowserPlugin.NEW_TAB, window, null)).isTrue();
            assertThat(host.contentPresentation(host.contentTabs().getLast()).location()).as("a refused value is not used")
                .isEqualTo("jasper-about:welcome");
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * Spec section 16: the plugin restores its saved browser items, each at once, unshown, at its saved address, or at
     * the start page when it saved none.
     */
    @Test void savedBrowserItemsComeBackAtTheirAddressOrElseTheStartPage() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            UUID window = host.addTerminalWindow();
            host.setSetting(ID, "start_page", "example.org");
            host.start(INFO, Set.of(), Set.of(), plugin());
            var saved = BrowserRestore.state(URI.create("https://example.com/docs"), Optional.of("docs"), OptionalInt.empty(), false).orElseThrow();
            assertThat(host.restoreTab(ID, window, "Docs", saved)).isPresent();
            var docs = host.contentTabs().getLast();
            assertThat(docs.window().id()).isEqualTo(window);
            assertThat(docs.isActive()).as("placed, not shown").isFalse();
            assertThat(host.contentPresentation(docs).location()).isEqualTo("https://example.com/docs");
            assertThat(host.contentPresentation(docs).icon()).isEqualTo(BrowserItems.GLOBE);
            assertThat(host.restoreState(docs)).contains(saved);
            assertThat(waits(host, "example.com")).as("no run ended while Chromium ran: it loads the first time it shows").isFalse();
            assertThat(host.restoreTab(ID, window, "Data", new dev.jasper.sdk.RestoreState(BrowserRestore.KIND, Map.of()))).isPresent();
            assertThat(host.contentPresentation(host.contentTabs().getLast()).location()).as("the start page")
                .isEqualTo("https://example.org");
            assertThat(host.contentTabs()).hasSize(2);
            assertThat(host.declinedRestores()).isEmpty();
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * Plan 6: with Use /dev/null mode on, New Browser Tab, New Browser Window and the split item open in a store of their
     * own each, with the saved title "Web Browser"; a saved /dev/null item comes back in a new /dev/null store whatever
     * the setting says, and any other saved item in the persistent one.
     */
    @Test void devNullModeChoosesTheStoreOfNewAndRestoredItems() throws Exception {
        var plugin = plugin();
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            host.start(INFO, Set.of(), Set.of(), plugin);
            assertThat(host.invoke(BrowserPlugin.NEW_TAB, window, null)).isTrue();
            assertThat(plugin.items().items().getLast().devNull()).as("off").isFalse();

            host.setSetting(ID, "devnull_mode", true);
            for (String action : new String[] { BrowserPlugin.NEW_TAB, BrowserPlugin.NEW_WINDOW, BrowserPlugin.SPLIT.rightAction() }) {
                assertThat(host.invoke(action, window, null)).as(action).isTrue();
                var item = plugin.items().items().getLast();
                assertThat(item.devNull()).as(action).isTrue();
                assertThat(host.savedTitle(item.tab())).as(action).isEqualTo("Web Browser");
                assertThat(host.restoreState(item.tab()).orElseThrow().values()).as(action).containsOnly(Map.entry("devnull", "true"));
            }
            assertThat(plugin.items().items().get(1).store()).isNotSameAs(plugin.items().items().get(2).store());

            host.setSetting(ID, "devnull_mode", false);
            assertThat(host.restoreTab(ID, window, "Web Browser",
                BrowserRestore.state(URI.create("https://secret.example/"), Optional.empty(), OptionalInt.empty(), true).orElseThrow())).isPresent();
            var restored = plugin.items().items().getLast();
            assertThat(restored.devNull()).as("restored in the saved mode, whatever the setting").isTrue();
            assertThat(restored.url()).isEqualTo(URI.create("jasper-about:welcome"));
            assertThat(plugin.items().items().stream().filter(item -> item.store() == restored.store())).hasSize(1);
            assertThat(host.restoreTab(ID, window, "Docs",
                BrowserRestore.state(URI.create("https://example.com/docs"), Optional.empty(), OptionalInt.empty(), false).orElseThrow())).isPresent();
            assertThat(plugin.items().items().getLast().devNull()).isFalse();

            host.setSetting(ID, "devnull_mode", true);
            for (String devnull : new String[] { "TRUE", "1" })
                assertThat(host.restoreTab(ID, window, "Docs", new dev.jasper.sdk.RestoreState(BrowserRestore.KIND,
                    Map.of("url", "https://example.com/docs", "devnull", devnull)))).isPresent();
            assertThat(plugin.items().items().subList(plugin.items().items().size() - 2, plugin.items().items().size()))
                .as("with the setting on, a saved ordinary item comes back persistent").allSatisfy(item -> assertThat(item.devNull()).isFalse());
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * A run that ended while Chromium ran left its marker in the plugin's data directory. The start deletes it, so only
     * this launch's restored items wait; New Browser Tab, New Browser Window and the split item open items that do not.
     */
    @Test void afterARunThatEndedWhileChromiumRanThisLaunchsRestoredItemsWait(@TempDir Path root) throws Exception {
        Path marker = Files.createDirectories(root.resolve(ID).resolve("data")).resolve("chromium.running");
        Files.createFile(marker);
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost(root)) {
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            host.start(INFO, Set.of(), Set.of(), plugin());
            assertThat(marker).as("read once").doesNotExist();
            for (String address : new String[] { "https://example.com/docs", "https://example.org/" })
                assertThat(host.restoreTab(ID, window, "Saved",
                    BrowserRestore.state(URI.create(address), Optional.empty(), OptionalInt.empty(), false).orElseThrow())).isPresent();
            assertThat(waits(host, "example.com")).isTrue();
            assertThat(waits(host, "example.org")).isTrue();
            assertThat(host.contentPresentation(host.contentTab(ID, "example.com").orElseThrow()).location())
                .isEqualTo("https://example.com/docs");
            for (String action : new String[] { BrowserPlugin.NEW_TAB, BrowserPlugin.NEW_WINDOW, BrowserPlugin.SPLIT.rightAction() }) {
                assertThat(host.invoke(action, window, null)).as(action).isTrue();
                assertThat(waits(host, "Web Browser")).as(action).isFalse();
                host.contentTab(ID, "Web Browser").orElseThrow().close();
            }
            assertThat(waits(host, "example.org")).as("still waiting").isTrue();
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * Plan 6: Jasper's history, bookmarks and welcome pages answer each query, through the plugin's own registrations,
     * from the asking browser's store: a /dev/null item's browser gets its group's history and bookmarks, a persistent
     * item's browser none of them, and a browser no page has is refused.
     */
    @Test void jaspersPagesAnswerEachQueryFromTheAskingBrowsersStore(@TempDir Path root) throws Exception {
        var plugin = plugin();
        var host = new AtomicReference<FakePluginHost>();
        SwingUtilities.invokeAndWait(() -> {
            host.set(new FakePluginHost(root));
            host.get().start(INFO, Set.of(), Set.of(), plugin);
        });
        try {
            var devNull = new AtomicReference<dev.jasper.browser.item.DataStore>();
            SwingUtilities.invokeAndWait(() -> {
                devNull.set(plugin.dataStores().newDevNull());
                var inDevNull = plugin.items().open(URI.create("https://private.example/"), dev.jasper.sdk.ui.TabPlacement.NEW_TAB,
                    Optional.empty(), Optional.empty(), devNull.get());
                var persistent = plugin.items().open(URI.create("https://public.example/"), dev.jasper.sdk.ui.TabPlacement.NEW_TAB,
                    Optional.empty(), Optional.empty());
                PageTestAccess.browserMade(ItemTestAccess.page(inDevNull), 61);
                PageTestAccess.browserMade(ItemTestAccess.page(persistent), 62);
            });
            devNull.get().stores().history().record("https://private.example/", Reached.LINK, 1_000).get(10, TimeUnit.SECONDS);
            devNull.get().stores().bookmarks().add("https://private.example/", "Private", 1_000).get(10, TimeUnit.SECONDS);
            devNull.get().permissions().set("https://private.example", Permission.CAMERA, Decision.ALLOWED).get(10, TimeUnit.SECONDS);
            String list = "{\"op\":\"list\",\"query\":\"\"}", top = "{\"op\":\"top-sites\"}", grants = "{\"op\":\"list\"}";
            assertThat(ask(plugin, "history", 61, list)).contains("https://private.example/");
            assertThat(ask(plugin, "bookmarks", 61, list)).contains("https://private.example/");
            assertThat(ask(plugin, "welcome", 61, top)).contains("https://private.example/");
            assertThat(ask(plugin, "permissions", 61, grants)).contains("https://private.example");
            assertThat(ask(plugin, "history", 62, list)).doesNotContain("private.example");
            assertThat(ask(plugin, "bookmarks", 62, list)).doesNotContain("private.example");
            assertThat(ask(plugin, "welcome", 62, top)).doesNotContain("private.example");
            assertThat(ask(plugin, "permissions", 62, grants)).doesNotContain("private.example");
            assertThat(ask(plugin, "permissions", 61, "{\"op\":\"clear-site\",\"origin\":\"https://private.example\"}")).isEqualTo("{}");
            assertThat(devNull.get().permissions().all()).as("the asking browser's store changed").isEmpty();
            for (String page : List.of("history", "bookmarks", "welcome", "permissions")) {
                var refused = plugin.about().query("jasper-about:" + page, true, 63, plugin.about().secret(page).orElseThrow() + list);
                assertThat(refused).as(page).isPresent();
                assertThat(refused.get()).as(page + " from a browser no page has").failsWithin(10, TimeUnit.SECONDS)
                    .withThrowableThat().withMessageContaining("Jasper could not answer this request.");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> host.get().close());
        }
    }

    /** Spec section 12: the start registers {@code jasper-about:permissions}, "Site Permissions", with its queries. */
    @Test void theStartRegistersThePermissionsPageAndItsQueries() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            assertThat(plugin.about().secret("permissions")).as("its queries").isPresent();
            var answer = plugin.about().answer("jasper-about:permissions");
            assertThat(answer.status()).isEqualTo(200);
            assertThat(answer.html()).contains("<title>Site Permissions</title>", "Clear All Permissions");
            assertThat(plugin.stores().opened()).as("no database opened").isFalse();
            assertThat(host.failures()).isEmpty();
        } });
    }

    /** Plan 6: a site permission that could not be saved posts "Site permissions could not be saved", while the plugin runs. */
    @Test void aSitePermissionThatCannotBeSavedPostsAnErrorWhileThePluginRuns(@TempDir Path root) throws Exception {
        var plugin = plugin();
        var host = new AtomicReference<FakePluginHost>();
        SwingUtilities.invokeAndWait(() -> {
            host.set(new FakePluginHost(root));
            host.get().start(INFO, Set.of(), Set.of(), plugin);
        });
        try (var log = new CapturedLog(dev.jasper.browser.permissions.PermissionStore.class.getName())) {
            plugin.stores().database().call(connection -> {
                try (var statement = connection.createStatement()) {
                    statement.execute("CREATE TEMP TRIGGER refused BEFORE INSERT ON permissions BEGIN SELECT RAISE(ABORT, 'refused'); END");
                }
                return null;
            }).get(10, TimeUnit.SECONDS);
            var persistent = new AtomicReference<dev.jasper.browser.item.DataStore>();
            SwingUtilities.invokeAndWait(() -> persistent.set(plugin.dataStores().persistent()));
            assertThat(persistent.get().permissions().set("https://a.test", Permission.CAMERA, Decision.ALLOWED)).failsWithin(10, TimeUnit.SECONDS);
            SwingUtilities.invokeAndWait(() -> { });
            SwingUtilities.invokeAndWait(() -> assertThat(host.get().notifications().lines()).singleElement().asString()
                .startsWith("dev.jasper.browser|ERROR|Site permissions could not be saved|"));
            assertThat(log.records()).as("the store logs why").hasSize(1);
        } finally {
            SwingUtilities.invokeAndWait(() -> host.get().close());
        }
    }

    /**
     * Plan 6 Task 8: Settings › Tools › Browser is the Browser's own page, and its prompts show as the Indexer's do, as a
     * sheet over the active window: a question with Cancel and OK, whose OK alone runs what was asked, and a message
     * with OK alone. A stopped plugin shows none.
     */
    @Test void theSettingsPageIsTheBrowsersOwnAndItsPromptsAskOverTheActiveWindow() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            assertThat(host.settingsPanel(ID)).as("the Browser's own page").isPresent();
            var accepted = new java.util.concurrent.atomic.AtomicInteger();
            var question = new BrowserSettingsPanel.Prompt("Clear Cookies", "This will remove all cookies from all websites. Continue?",
                true, accepted::incrementAndGet);
            plugin.show(question);
            var sheet = host.windowView(ID, "Clear Cookies").orElseThrow();
            var form = (Nodes.Form) sheet.spec().root();
            assertThat(form.kind()).isEqualTo(Nodes.FormKind.SHEET);
            assertThat(form.title()).isEqualTo("Clear Cookies");
            assertThat(form.sections()).singleElement().isInstanceOfSatisfying(Nodes.Text.class,
                text -> assertThat(text.text()).isEqualTo("This will remove all cookies from all websites. Continue?"));
            assertThat(form.actions()).extracting(Nodes.Button::label, Nodes.Button::kind)
                .containsExactly(tuple("Cancel", Nodes.ButtonKind.SECONDARY), tuple("OK", Nodes.ButtonKind.PRIMARY));
            assertThat(form.cancelAction()).isEqualTo(BrowserPlugin.PROMPT_CANCEL);
            sheet.emit(new dev.jasper.sdk.ui.UiEvent.Action(BrowserPlugin.PROMPT_CANCEL));
            assertThat(host.windowView(ID, "Clear Cookies")).as("Cancel closes it").isEmpty();
            assertThat(accepted).as("and runs nothing").hasValue(0);
            plugin.show(question);
            host.windowView(ID, "Clear Cookies").orElseThrow().emit(new dev.jasper.sdk.ui.UiEvent.Action(BrowserPlugin.PROMPT_OK));
            assertThat(host.windowView(ID, "Clear Cookies")).isEmpty();
            assertThat(accepted).hasValue(1);

            plugin.show(new BrowserSettingsPanel.Prompt("Clear Website Data", "All website data has been cleared successfully!", false, () -> { }));
            var told = (Nodes.Form) host.windowView(ID, "Clear Website Data").orElseThrow().spec().root();
            assertThat(told.actions()).extracting(Nodes.Button::label).containsExactly("OK");
            assertThat(told.cancelAction()).as("Escape closes it").isEqualTo(BrowserPlugin.PROMPT_OK);
            host.stopAll();
            plugin.show(question);
            assertThat(host.windowView(ID, "Clear Cookies")).as("a stopped plugin asks nothing").isEmpty();
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * The plan's call: Settings' Manage Permissions opens {@code jasper-about:permissions} in a new tab, in the
     * persistent store, whatever the /dev/null setting; once Chromium cannot serve pages it posts why instead.
     */
    @Test void managePermissionsOpensThePersistentStoresPermissionsPage() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            host.setSetting(ID, "devnull_mode", true);
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            plugin.managePermissions();
            assertThat(plugin.items().items()).singleElement().satisfies(item -> {
                assertThat(item.url()).isEqualTo(dev.jasper.browser.pages.PermissionsPage.ADDRESS);
                assertThat(item.devNull()).isFalse();
            });
            assertThat(host.contentTabs()).hasSize(1);
            plugin.chromium().close();
            plugin.managePermissions();
            assertThat(plugin.items().items()).hasSize(1);
            assertThat(host.notifications().lines()).singleElement().asString().startsWith("dev.jasper.browser|ERROR|The browser could not start|");
            assertThat(host.failures()).isEmpty();
        } });
    }

    /**
     * Ruling V25: a database file a Clear Website Data could not reach, as the database ran in memory, is deleted before
     * the plugin's database next opens; the profile's wipe stays marked for Chromium's start.
     */
    @Test void aDatabaseFileAClearingMarkedIsDeletedBeforeThePluginsDatabaseOpens(@TempDir Path root) throws Exception {
        Path data = Files.createDirectories(root.resolve(ID).resolve("data"));
        dev.jasper.browser.store.DatabaseFixtures.plan5(data, "https://cleared.example/");
        Files.writeString(data.resolve("chromium-clear"), "all database");
        var plugin = plugin();
        var host = new AtomicReference<FakePluginHost>();
        SwingUtilities.invokeAndWait(() -> {
            host.set(new FakePluginHost(root));
            host.get().start(INFO, Set.of(), Set.of(), plugin);
        });
        try {
            assertThat(plugin.stores().history().list("", Optional.empty(), 10).get(10, TimeUnit.SECONDS).entries()).as("a new file").isEmpty();
            assertThat(Files.readString(data.resolve("chromium-clear"))).isEqualTo("all");
        } finally {
            SwingUtilities.invokeAndWait(() -> host.get().close());
        }
    }

    /** A clearing that failed posts its error with the reason, while the plugin runs. */
    @Test void aClearingThatFailedPostsAnErrorWhileThePluginRuns() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            plugin.notCleared(dev.jasper.browser.item.WebsiteData.NOT_CLEARED,
                new java.util.concurrent.TimeoutException("Chromium did not clear its data within 60 seconds"));
            assertThat(host.notifications().lines())
                .containsExactly("dev.jasper.browser|ERROR|Website data could not be cleared|Chromium did not clear its data within 60 seconds");
            host.stopAll();
            plugin.notCleared(dev.jasper.browser.item.WebsiteData.NOT_CLEARED, new IllegalStateException("late"));
            assertThat(host.notifications().lines()).hasSize(1);
            assertThat(host.failures()).isEmpty();
        } });
    }

    /** What {@code page}'s script is answered for {@code request} from browser {@code browser}, as its own document asks. */
    private static String ask(BrowserPlugin plugin, String page, int browser, String request) throws Exception {
        String secret = plugin.about().secret(page).orElseThrow();
        return plugin.about().query("jasper-about:" + page, true, browser, secret + request).orElseThrow().get(10, TimeUnit.SECONDS);
    }

    /** Whether the browser item whose tab is called {@code title} shows the placeholder of an item that waits. UI thread. */
    private static boolean waits(FakePluginHost host, String title) {
        var root = (Nodes.Split) host.contentView(ID, title).orElseThrow().spec().root();
        return root.children().stream().anyMatch(node -> node instanceof Nodes.Empty empty && empty.state().visible()
            && empty.title().equals("Jasper closed while this page was open."));
    }
}
