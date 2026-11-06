package dev.jasper.browser.item;

import dev.jasper.browser.dialogs.DialogsTestAccess;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageTestAccess;
import dev.jasper.browser.permissions.Decision;
import dev.jasper.browser.permissions.Permission;
import dev.jasper.browser.permissions.PermissionRequest;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.browser.store.MemoryStores;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.TabPlacement;
import dev.jasper.sdk.ui.UiEvent;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * An item's site permissions (spec section 12, plan 6 Task 5): a request of its page is answered from its store's
 * stored decision at once, or else its prompt asks the user. Allow and Block are stored in the item's store, a
 * dismissal is not, and the same request is then refused without a prompt until the page's next main-frame load
 * (ruling F25). A /dev/null item's answers stay in its group's store. The plugin's stop dismisses an item's prompts and
 * asks nothing more. Stand-in items over stores in memory, on the testkit's fake host; no Chromium. The prompts read a
 * clock each of whose readings is 10 s after the last, so the user answers each after its double-click interval.
 */
class SitePermissionsTest {
    private static final String ID = "dev.jasper.browser";
    private static final String SITE = "https://camera.example";
    private static final URI PAGE = URI.create("https://camera.example/call");
    private static final PermissionRequest CAMERA = new PermissionRequest(SITE, Permission.CAMERA),
        MICROPHONE = new PermissionRequest(SITE, Permission.MICROPHONE);
    private final MemoryStores memory = new MemoryStores();
    private FakePluginHost host;
    private AutoCloseable unhurried;

    @BeforeEach void unhurriedPrompts() {
        var now = new AtomicLong();
        unhurried = DialogsTestAccess.promptsTimedBy(() -> now.addAndGet(10_000));
    }

    @AfterEach void close() throws Exception {
        if (host != null) onUi(() -> {
            host.close();
            return null;
        });
        memory.close();
        unhurried.close();
    }

    @Test void aStoredDecisionAnswersAtOnceWithoutAPrompt() throws Exception {
        BrowserItems items = items();
        BrowserItem item = onUi(() -> items.open(PAGE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty()));
        item.store().permissions().set(SITE, Permission.CAMERA, Decision.ALLOWED).get(10, TimeUnit.SECONDS);
        item.store().permissions().set(SITE, Permission.MICROPHONE, Decision.BLOCKED).get(10, TimeUnit.SECONDS);
        var camera = request(item, CAMERA);
        var microphone = request(item, MICROPHONE);
        onUi(() -> {
            assertThat(camera).containsExactly(true);
            assertThat(microphone).containsExactly(false);
            assertThat(host.windows()).as("no prompt").isEmpty();
            assertThat(host.failures()).isEmpty();
            return null;
        });
    }

    @Test void allowStoresAllowedAndAnswersTrueAndBlockStoresBlockedAndAnswersFalse() throws Exception {
        BrowserItems items = items();
        BrowserItem item = onUi(() -> items.open(PAGE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty()));
        var camera = request(item, CAMERA);
        onUi(() -> {
            assertThat(camera).as("asked").isEmpty();
            press("Allow Camera", form("Allow Camera").actions().getLast().action());
            assertThat(camera).containsExactly(true);
            return null;
        });
        var microphone = request(item, MICROPHONE);
        onUi(() -> {
            press("Allow Microphone", form("Allow Microphone").actions().getFirst().action());
            assertThat(microphone).containsExactly(false);
            return null;
        });
        assertThat(item.store().permissions().decision(SITE, Permission.CAMERA)).contains(Decision.ALLOWED);
        assertThat(item.store().permissions().decision(SITE, Permission.MICROPHONE)).contains(Decision.BLOCKED);
        var again = request(item, CAMERA);
        onUi(() -> {
            assertThat(again).as("from the store now").containsExactly(true);
            assertThat(host.windows()).isEmpty();
            assertThat(host.failures()).isEmpty();
            return null;
        });
    }

    /**
     * Ruling F25: a dismissed prompt stores nothing, and the same request is then refused without asking until the
     * page's next main-frame load; a prompt the load itself takes away is not counted as dismissed.
     */
    @Test void aDismissalStoresNothingAndTheSameRequestIsRefusedUnaskedUntilTheNextLoad() throws Exception {
        BrowserItems items = items();
        BrowserItem item = onUi(() -> items.open(PAGE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty()));
        var dismissed = request(item, CAMERA);
        onUi(() -> {
            press("Allow Camera", form("Allow Camera").cancelAction());
            assertThat(dismissed).containsExactly(false);
            return null;
        });
        assertThat(item.store().permissions().decision(SITE, Permission.CAMERA)).as("nothing stored").isEmpty();
        var nagging = request(item, CAMERA);
        var other = request(item, MICROPHONE);
        onUi(() -> {
            assertThat(nagging).as("refused unasked").containsExactly(false);
            assertThat(host.windowView(ID, "Allow Camera")).isEmpty();
            assertThat(host.windowView(ID, "Allow Microphone")).as("another permission still asks").isPresent();
            press("Allow Microphone", form("Allow Microphone").cancelAction());
            assertThat(other).containsExactly(false);
            PageTestAccess.loadStarted(ItemTestAccess.page(item), "https://camera.example/again");
            return null;
        });
        var reloaded = request(item, CAMERA);
        onUi(() -> {
            assertThat(host.windowView(ID, "Allow Camera")).as("asked again after a load").isPresent();
            assertThat(reloaded).isEmpty();
            PageTestAccess.loadStarted(ItemTestAccess.page(item), "https://camera.example/away");
            assertThat(reloaded).as("the load took the prompt away").containsExactly(false);
            return null;
        });
        var afterTheLoad = request(item, CAMERA);
        onUi(() -> {
            assertThat(host.windowView(ID, "Allow Camera")).as("a prompt the load took away was no dismissal").isPresent();
            press("Allow Camera", form("Allow Camera").actions().getLast().action());
            assertThat(afterTheLoad).containsExactly(true);
            assertThat(host.failures()).isEmpty();
            return null;
        });
        assertThat(item.store().permissions().decision(SITE, Permission.CAMERA)).contains(Decision.ALLOWED);
        assertThat(item.store().permissions().decision(SITE, Permission.MICROPHONE)).isEmpty();
    }

    /** Spec section 12: a /dev/null item's answer stays in its group's store, never in the persistent one. */
    @Test void aDevNullItemsAnswerIsInItsGroupsStoreAndAbsentFromThePersistentOne() throws Exception {
        BrowserItems items = items();
        BrowserItem item = onUi(() -> items.open(PAGE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty(), items.dataStores().newDevNull()));
        var camera = request(item, CAMERA);
        onUi(() -> {
            press("Allow Camera", form("Allow Camera").actions().getLast().action());
            assertThat(camera).containsExactly(true);
            return null;
        });
        assertThat(item.store().devNull()).isTrue();
        assertThat(item.store().permissions().decision(SITE, Permission.CAMERA)).contains(Decision.ALLOWED);
        DataStore persistent = onUi(() -> items.dataStores().persistent());
        assertThat(persistent.permissions().decision(SITE, Permission.CAMERA)).isEmpty();
        persistent.permissions().load().get(10, TimeUnit.SECONDS);
        assertThat(persistent.permissions().all()).as("not even after the persistent rows load").isEmpty();
        BrowserItem ordinary = onUi(() -> items.open(PAGE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty()));
        var asked = request(ordinary, CAMERA);
        onUi(() -> {
            assertThat(host.windowView(ID, "Allow Camera")).as("a persistent item is asked").isPresent();
            assertThat(asked).isEmpty();
            return null;
        });
    }

    /**
     * Ruling F43: the plugin's stop dismisses an item's prompt, shown or waiting, before Chromium closes, and the item
     * asks nothing more; a closed item's page refuses at once.
     */
    @Test void thePluginsStopDismissesAnItemsPromptsAndItAsksNothingMore() throws Exception {
        BrowserItems items = items();
        BrowserItem item = onUi(() -> items.open(PAGE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty()));
        var camera = request(item, CAMERA);
        var microphone = request(item, MICROPHONE);
        onUi(() -> {
            assertThat(host.windowView(ID, "Allow Camera")).isPresent();
            items.close();
            assertThat(camera).containsExactly(false);
            assertThat(microphone).containsExactly(false);
            return null;
        });
        var late = request(item, CAMERA);
        onUi(() -> {
            assertThat(late).as("refused unasked").containsExactly(false);
            assertThat(host.windows()).isEmpty();
            item.close();
            return null;
        });
        var closed = request(item, MICROPHONE);
        onUi(() -> {
            assertThat(closed).containsExactly(false);
            assertThat(camera).containsExactly(false);
            assertThat(host.failures()).isEmpty();
            return null;
        });
        assertThat(item.store().permissions().all()).isEmpty();
    }

    /** The item's page asks {@code request} as Chromium's handler would, from this thread: the answers it gets. */
    private static List<Boolean> request(BrowserItem item, PermissionRequest request) throws Exception {
        var answers = new CopyOnWriteArrayList<Boolean>();
        Page page = ItemTestAccess.page(item);
        page.privacy().request(request, answers::add);
        onUi(() -> null);
        return answers;
    }

    /** The form of the open dialog titled {@code title}. UI thread. */
    private Nodes.Form form(String title) {
        return (Nodes.Form) view(title).spec().root();
    }

    private FakeKitView view(String title) {
        return host.windowView(ID, title).orElseThrow(() -> new AssertionError("no dialog " + title));
    }

    /** Presses the button, or the key, that reports {@code action}. UI thread. */
    private void press(String title, String action) {
        view(title).emit(new UiEvent.Action(action));
    }

    /** The items of a started stand-in plugin on a fake host with a window, whose stores are in memory. */
    private BrowserItems items() throws Exception {
        return onUi(() -> {
            host = new FakePluginHost();
            var context = new AtomicReference<PluginContext>();
            var settings = new AtomicReference<BrowserSettings.Declared>();
            host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(), plugin -> {
                context.set(plugin);
                settings.set(BrowserSettings.declare(plugin.settings()));
            });
            host.activateTerminalWindow(host.addTerminalWindow());
            var stores = new DataStores(memory.make(), memory::make, () -> PageTestAccess.recordingContext(new AtomicInteger()), () -> false);
            return new BrowserItems(context.get(), settings.get()::read, stores, page -> { });
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
        // What the task posted, such as the next prompt's show, runs before the next step.
        SwingUtilities.invokeAndWait(() -> { });
        if (failure.get() instanceof Error error) throw error;
        if (failure.get() instanceof Exception exception) throw exception;
        return result.get();
    }
}
