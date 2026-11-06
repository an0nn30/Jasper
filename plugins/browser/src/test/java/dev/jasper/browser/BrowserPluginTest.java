package dev.jasper.browser;

import dev.jasper.browser.api.MarkdownPreviews;
import dev.jasper.browser.api.PreviewEvent;
import dev.jasper.browser.engine.Chromium;
import dev.jasper.browser.item.BrowserItems;
import dev.jasper.browser.restore.BrowserRestore;
import dev.jasper.browser.store.CapturedLog;
import dev.jasper.browser.store.MemoryStores;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.Nodes;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** The plugin's registrations and its part in Chromium's life, on the SDK's fake host; nothing here starts Chromium. */
class BrowserPluginTest {
    private static final String ID = "dev.jasper.browser";
    private static final PluginInfo INFO = new PluginInfo(ID, "Browser", "0.1.0", Set.of("browser.provide", "palette.contribute"));

    @Test void aChromiumThatCannotStartPostsOneNotificationWithTheReasonWhileThePluginRuns() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var plugin = new BrowserPlugin();
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

    /** Plan 5: a database that cannot be used posts one notification with the reason, while the plugin runs. */
    @Test void historyAndBookmarksThatAreUnavailablePostANotificationWithTheReasonWhileThePluginRuns() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost(); var log = new CapturedLog("dev.jasper.plugins." + ID)) {
            var plugin = new BrowserPlugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            plugin.storesUnavailable(new java.sql.SQLException("[SQLITE_NOTADB] file is not a database"));
            assertThat(host.notifications().lines())
                .containsExactly("dev.jasper.browser|WARNING|Browser history and bookmarks are unavailable|[SQLITE_NOTADB] file is not a database");
            host.stopAll();
            plugin.storesUnavailable(new java.sql.SQLException("late"));
            assertThat(host.notifications().lines()).as("a stopped plugin only logs").hasSize(1);
            assertThat(log.records()).extracting(LogRecord::getLevel, LogRecord::getMessage).containsExactly(
                tuple(Level.WARNING, "Browser history and bookmarks are unavailable: [SQLITE_NOTADB] file is not a database"),
                tuple(Level.WARNING, "Browser history and bookmarks are unavailable: late"));
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
            var plugin = new BrowserPlugin();
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
        var plugin = new BrowserPlugin();
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
            var plugin = new BrowserPlugin();
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
            var plugin = new BrowserPlugin();
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
            var plugin = new BrowserPlugin();
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
            var plugin = new BrowserPlugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            assertThat(host.invoke(BrowserPlugin.NEW_TAB, window, null)).isTrue();
            assertThat(host.contentTabs()).as("not started: it opens").hasSize(1);
            plugin.chromium().close();
            assertThat(plugin.chromium().state()).isEqualTo(Chromium.State.STOPPED);
            for (String action : new String[] { BrowserPlugin.NEW_TAB, BrowserPlugin.NEW_WINDOW, BrowserPlugin.SPLIT.rightAction(),
                    BrowserPlugin.SPLIT.downAction() })
                assertThat(host.invoke(action, window, null)).as(action).isTrue();
            assertThat(host.contentTabs()).as("no more items").hasSize(1);
            assertThat(host.notifications().lines()).hasSize(4).containsOnly("dev.jasper.browser|ERROR|The browser could not start|"
                + "Chromium can start once each time Jasper runs. Quit and reopen Jasper to use the browser again.");
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
            host.start(INFO, Set.of(), Set.of(), new BrowserPlugin());
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
            host.start(INFO, Set.of(), Set.of(), new BrowserPlugin());
            assertThat(host.notifications().groups()).contains("dev.jasper.browser.bookmarks|Bookmarks|CARD");
            assertThat(host.failures()).isEmpty();
        } });
    }

    @Test void aNewTabOpensTheStartPageInItsWindowAndANewWindowInAWindowOfItsOwn() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            host.start(INFO, Set.of(), Set.of(), new BrowserPlugin());
            assertThat(host.declared(ID)).containsExactly("start_page", "page_zoom", "open_links_from_terminal", "open_local_web_files",
                "search_engine", "custom_search_url", "custom_suggestions_url");
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
            host.start(INFO, Set.of(), Set.of(), new BrowserPlugin());
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
            host.start(INFO, Set.of(), Set.of(), new BrowserPlugin());
            var saved = BrowserRestore.state(URI.create("https://example.com/docs"), Optional.of("docs"), OptionalInt.empty()).orElseThrow();
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
     * A run that ended while Chromium ran left its marker in the plugin's data directory. The start deletes it, so only
     * this launch's restored items wait; New Browser Tab, New Browser Window and the split item open items that do not.
     */
    @Test void afterARunThatEndedWhileChromiumRanThisLaunchsRestoredItemsWait(@TempDir Path root) throws Exception {
        Path marker = Files.createDirectories(root.resolve(ID).resolve("data")).resolve("chromium.running");
        Files.createFile(marker);
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost(root)) {
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            host.start(INFO, Set.of(), Set.of(), new BrowserPlugin());
            assertThat(marker).as("read once").doesNotExist();
            for (String address : new String[] { "https://example.com/docs", "https://example.org/" })
                assertThat(host.restoreTab(ID, window, "Saved",
                    BrowserRestore.state(URI.create(address), Optional.empty(), OptionalInt.empty()).orElseThrow())).isPresent();
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

    /** Whether the browser item whose tab is called {@code title} shows the placeholder of an item that waits. UI thread. */
    private static boolean waits(FakePluginHost host, String title) {
        var root = (Nodes.Split) host.contentView(ID, title).orElseThrow().spec().root();
        return root.children().stream().anyMatch(node -> node instanceof Nodes.Empty empty && empty.state().visible()
            && empty.title().equals("Jasper closed while this page was open."));
    }
}
