package dev.jasper.browser.settings;

import dev.jasper.browser.settings.BrowserSettingsPanel.Actions;
import dev.jasper.browser.settings.BrowserSettingsPanel.Prompt;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import java.awt.EventQueue;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plan 6 Task 8: Settings › Tools › Browser is the Browser's own page. It shows every declared setting as Settings'
 * standard row, by section, as the generated page did, and Privacy &amp; Data adds Clear All Cookies, Clear Website Data
 * and Manage Site Permissions, with iTerm2's labels. Each clearing asks first, and the actions run only once the user
 * accepts; Clear Website Data tells its success only once the clearing has completed. Driven as Settings drives it, on
 * the SDK's fake host, with recording actions: nothing here clears anything or shows a real dialog.
 */
class BrowserSettingsPanelTest {
    private static final String ID = "dev.jasper.browser";
    private static final PluginInfo INFO = new PluginInfo(ID, "Browser", "0.2.0", Set.of());
    private final List<Prompt> prompts = new CopyOnWriteArrayList<>();
    private final List<String> ran = new CopyOnWriteArrayList<>();
    private volatile CompletableFuture<Void> cookies = new CompletableFuture<>(), data = new CompletableFuture<>();
    private FakePluginHost host;
    private BrowserSettingsPanel panel;

    @BeforeEach void open() throws Exception {
        EventQueue.invokeAndWait(() -> host = new FakePluginHost());
    }

    @AfterEach void close() throws Exception {
        EventQueue.invokeAndWait(() -> host.close());
    }

    @Test void theDeclaredRowsComeBySectionThenPrivacyAndDataAddsTheThreeRows() throws Exception {
        onUi(() -> {
            FakeKitView view = page();
            var page = (Nodes.Stack) view.spec().root();
            assertThat(page.role()).isEqualTo(Nodes.LayoutRole.SETTINGS_PAGE);
            assertThat(page.children()).extracting(node -> ((Nodes.Section) node).title())
                .containsExactly("General", "Link Opening", "Search Engine", "Privacy & Data");
            assertThat(rows(view, "browser.settings.general")).containsExactly("settings:start_page:row", "settings:page_zoom:row");
            assertThat(rows(view, "browser.settings.link-opening"))
                .containsExactly("settings:open_links_from_terminal:row", "settings:open_local_web_files:row");
            assertThat(rows(view, "browser.settings.search-engine"))
                .containsExactly("settings:search_engine:row", "settings:custom_search_url:row", "settings:custom_suggestions_url:row");
            assertThat(rows(view, "browser.settings.privacy-data")).containsExactly("settings:devnull_mode:row",
                BrowserSettingsPanel.CLEAR_COOKIES + ":row", BrowserSettingsPanel.CLEAR_DATA + ":row", BrowserSettingsPanel.PERMISSIONS + ":row");
            for (String section : List.of("general", "link-opening", "search-engine", "privacy-data"))
                assertThat(node(view, "browser.settings." + section + ".card", Nodes.Card.class).kind()).as(section).isEqualTo(Nodes.CardKind.SETTINGS);

            var cookiesRow = node(view, BrowserSettingsPanel.CLEAR_COOKIES + ":row", Nodes.SettingRow.class);
            assertThat(cookiesRow.title()).isEqualTo("Clear All Cookies");
            assertThat(button(view, BrowserSettingsPanel.CLEAR_COOKIES).label()).isEqualTo("Clear Cookies");
            var dataRow = node(view, BrowserSettingsPanel.CLEAR_DATA + ":row", Nodes.SettingRow.class);
            assertThat(dataRow.title()).isEqualTo("Clear Website Data");
            assertThat(button(view, BrowserSettingsPanel.CLEAR_DATA).label()).isEqualTo("Clear All Data");
            var permissionsRow = node(view, BrowserSettingsPanel.PERMISSIONS + ":row", Nodes.SettingRow.class);
            assertThat(permissionsRow.title()).isEqualTo("Manage Site Permissions");
            assertThat(permissionsRow.description()).isEqualTo(
                "View and revoke permissions granted to websites (camera, microphone, notifications, audio playback).");
            assertThat(button(view, BrowserSettingsPanel.PERMISSIONS).label()).isEqualTo("Manage Permissions");
            assertThat(prompts).as("showing the page asks nothing").isEmpty();
            assertThat(ran).isEmpty();
            assertThat(host.failures()).isEmpty();
        });
    }

    /** A declared row is Settings' own: its edit is staged for Apply, as on the generated page. */
    @Test void aDeclaredRowStagesItsValueForApply() throws Exception {
        onUi(() -> {
            FakeKitView view = page();
            view.emit(new UiEvent.Toggled("settings:devnull_mode", true));
            assertThat(host.pendingSettings(ID)).containsEntry("devnull_mode", true);
            host.applySettingsPanel(ID);
            assertThat(host.savedSettings(ID)).containsEntry("devnull_mode", true);
            assertThat(host.failures()).isEmpty();
        });
    }

    @Test void eachClearingAsksWithItsOwnTextsAndRunsOnlyOnceAccepted() throws Exception {
        onUi(() -> {
            FakeKitView view = page();
            view.emit(new UiEvent.Action(BrowserSettingsPanel.CLEAR_COOKIES));
            assertThat(prompts).singleElement().satisfies(prompt -> {
                assertThat(prompt.title()).isEqualTo("Clear Cookies");
                assertThat(prompt.message()).isEqualTo("This will remove all cookies from all websites. Continue?");
                assertThat(prompt.question()).as("OK and Cancel").isTrue();
            });
            assertThat(ran).as("asked, not yet accepted").isEmpty();
            prompts.getFirst().accepted().run();
            assertThat(ran).containsExactly("cookies");

            view.emit(new UiEvent.Action(BrowserSettingsPanel.CLEAR_DATA));
            assertThat(prompts).hasSize(2);
            Prompt asked = prompts.get(1);
            assertThat(asked.title()).isEqualTo("Clear Website Data");
            assertThat(asked.message()).as("iTerm2's text, with the next start's sign-out (V1)").isEqualTo(
                "This will remove all browsing data including cookies, cache, and local storage. The next time Jasper starts, "
                    + "it also signs you out of any site you sign in to before then. Continue?");
            assertThat(asked.question()).isTrue();
            assertThat(ran).containsExactly("cookies");
            asked.accepted().run();
            assertThat(ran).containsExactly("cookies", "data");
            assertThat(host.failures()).isEmpty();
        });
    }

    /** Cancel, or Escape, closes the prompt without running its action: the action runs only from {@code accepted}. */
    @Test void aPromptThatIsNotAcceptedRunsNothing() throws Exception {
        onUi(() -> {
            FakeKitView view = page();
            view.emit(new UiEvent.Action(BrowserSettingsPanel.CLEAR_COOKIES));
            view.emit(new UiEvent.Action(BrowserSettingsPanel.CLEAR_DATA));
            assertThat(prompts).hasSize(2);
        });
        flushUi();
        assertThat(ran).isEmpty();
        assertThat(prompts).as("no success without a clearing").hasSize(2);
    }

    @Test void theSuccessIsToldOnlyOnceTheClearingHasCompletedAndNeverOnFailure() throws Exception {
        onUi(() -> {
            FakeKitView view = page();
            view.emit(new UiEvent.Action(BrowserSettingsPanel.CLEAR_DATA));
            prompts.getFirst().accepted().run();
        });
        flushUi();
        assertThat(ran).containsExactly("data");
        assertThat(prompts).as("still clearing").hasSize(1);
        data.complete(null);
        flushUi();
        assertThat(prompts).hasSize(2);
        assertThat(prompts.get(1)).satisfies(told -> {
            assertThat(told.title()).isEqualTo("Clear Website Data");
            assertThat(told.message()).isEqualTo("All website data has been cleared successfully!");
            assertThat(told.question()).as("OK only").isFalse();
        });

        data = new CompletableFuture<>();
        onUi(() -> {
            host.settingsPanelView(ID).orElseThrow().emit(new UiEvent.Action(BrowserSettingsPanel.CLEAR_DATA));
            prompts.get(2).accepted().run();
        });
        data.completeExceptionally(new IllegalStateException("Chromium did not clear its data within 60 seconds"));
        flushUi();
        assertThat(ran).containsExactly("data", "data");
        assertThat(prompts).as("a failure is the clearing's own notification, and no success").hasSize(3);

        onUi(() -> {
            host.settingsPanelView(ID).orElseThrow().emit(new UiEvent.Action(BrowserSettingsPanel.CLEAR_COOKIES));
            prompts.get(3).accepted().run();
        });
        cookies.complete(null);
        flushUi();
        assertThat(prompts).as("Clear Cookies tells nothing after").hasSize(4);
        assertThat(host.failures()).isEmpty();
    }

    @Test void manageSitePermissionsRunsItsActionWithoutAsking() throws Exception {
        onUi(() -> {
            FakeKitView view = page();
            view.emit(new UiEvent.Action(BrowserSettingsPanel.PERMISSIONS));
            assertThat(ran).containsExactly("permissions");
            assertThat(prompts).isEmpty();
            assertThat(host.failures()).isEmpty();
        });
    }

    @Test void settingsSearchFindsThePageAndItsOwnRowsMarkThemselves() throws Exception {
        onUi(() -> {
            FakeKitView view = page();
            assertThat(panel.keywords()).containsExactly("cookies", "website data", "cache", "permissions", "privacy", "/dev/null",
                "ad blocking", "filter", "proxy");
            host.highlightSettingsPanel(ID, "cookies");
            assertThat(node(view, BrowserSettingsPanel.CLEAR_COOKIES + ":row", Nodes.SettingRow.class).highlighted()).isTrue();
            assertThat(node(view, BrowserSettingsPanel.PERMISSIONS + ":row", Nodes.SettingRow.class).highlighted()).isFalse();
            host.highlightSettingsPanel(ID, "site permissions");
            assertThat(node(view, BrowserSettingsPanel.CLEAR_COOKIES + ":row", Nodes.SettingRow.class).highlighted()).isFalse();
            assertThat(node(view, BrowserSettingsPanel.PERMISSIONS + ":row", Nodes.SettingRow.class).highlighted()).isTrue();
            host.highlightSettingsPanel(ID, "");
            assertThat(node(view, BrowserSettingsPanel.PERMISSIONS + ":row", Nodes.SettingRow.class).highlighted()).isFalse();
            assertThat(host.failures()).isEmpty();
        });
    }

    /** A closed page hears nothing more, and a page opened again shows the same rows. */
    @Test void aPageClosedAndOpenedAgainShowsItsRowsAgain() throws Exception {
        onUi(() -> {
            page();
            host.closeSettingsPanel(ID);
            host.settingsPanel(ID).orElseThrow();
            FakeKitView view = host.settingsPanelView(ID).orElseThrow();
            assertThat(rows(view, "browser.settings.privacy-data")).endsWith(BrowserSettingsPanel.PERMISSIONS + ":row");
            view.emit(new UiEvent.Action(BrowserSettingsPanel.PERMISSIONS));
            assertThat(ran).containsExactly("permissions");
            assertThat(host.failures()).isEmpty();
        });
    }

    /** Starts a plugin that gives the Browser's page over its real declarations, then opens the page as Settings would. */
    private FakeKitView page() {
        host.start(INFO, Set.of(), Set.of(), context -> {
            BrowserSettings.Declared settings = BrowserSettings.declare(context.settings());
            panel = new BrowserSettingsPanel(context.kit(), settings, new Actions(() -> {
                ran.add("cookies");
                return cookies;
            }, () -> {
                ran.add("data");
                return data;
            }, () -> ran.add("permissions"), prompts::add));
            context.settings().page(panel);
        });
        host.settingsPanel(ID).orElseThrow();
        return host.settingsPanelView(ID).orElseThrow();
    }

    private static List<String> rows(FakeKitView view, String section) {
        return node(view, section + ".card", Nodes.Card.class).children().stream().map(Nodes.Node::id).toList();
    }

    private static Nodes.Button button(FakeKitView view, String id) {
        return node(view, id, Nodes.Button.class);
    }

    private static <T extends Nodes.Node> T node(FakeKitView view, String id, Class<T> type) {
        Nodes.Node found = find(view.spec().root(), id);
        assertThat(found).as(id).isInstanceOf(type);
        return type.cast(found);
    }

    private static Nodes.Node find(Nodes.Node node, String id) {
        if (node.id().equals(id)) return node;
        List<Nodes.Node> children = switch (node) {
            case Nodes.Stack stack -> stack.children();
            case Nodes.Section section -> List.of(section.body());
            case Nodes.Card card -> card.children();
            case Nodes.SettingRow row -> List.of(row.control());
            default -> List.of();
        };
        for (Nodes.Node child : children) {
            Nodes.Node found = find(child, id);
            if (found != null) return found;
        }
        return null;
    }

    /** Runs {@code check} on the UI thread, where Settings drives its pages, and rethrows what it threw. */
    private static void onUi(Runnable check) throws Exception {
        var thrown = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        EventQueue.invokeAndWait(() -> {
            try {
                check.run();
            } catch (Throwable failure) {
                thrown.set(failure);
            }
        });
        if (thrown.get() instanceof Error error) throw error;
        if (thrown.get() instanceof Exception exception) throw exception;
    }

    /** Runs what the UI thread has queued, such as a success told after a clearing. */
    private static void flushUi() throws Exception {
        EventQueue.invokeAndWait(() -> { });
    }
}
