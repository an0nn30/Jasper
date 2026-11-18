package dev.jasper.browser.item;

import dev.jasper.browser.page.PageTestAccess;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The /dev/null indicator's popover (spec section 12), on the testkit's fake host: its two texts, opening from the
 * indicator and closing with its item. Escape, a click outside and a click on the indicator are the host's, which
 * the fake does not make (HostedAnchorTest covers the last); their closing is the GUI check.
 * The items are stand-ins' over stores in memory.
 */
class DevNullPopoverTest {
    private static final String ID = "dev.jasper.browser";
    private static final URI EXAMPLE = URI.create("https://example.com");
    private final MemoryStores memory = new MemoryStores();

    @AfterEach void close() throws Exception {
        memory.close();
    }

    @Test void theIndicatorOpensAPopoverWithItsTwoTexts() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var started = new Started(host);
            var item = started.devNullItem();
            FakeKitView view = host.contentView(ID, "example.com").orElseThrow();
            assertThat(host.popoverViews(ID)).isEmpty();

            view.emit(new UiEvent.Action(BrowserToolbar.DEVNULL));
            assertThat(host.popoverViews(ID)).singleElement().satisfies(popover -> {
                var stack = (Nodes.Stack) popover.spec().root();
                assertThat(stack.role()).isEqualTo(Nodes.LayoutRole.VERTICAL);
                assertThat(stack.children()).hasSize(2);
                var title = (Nodes.Text) stack.children().get(0);
                var body = (Nodes.Text) stack.children().get(1);
                assertThat(title.text()).isEqualTo("/dev/null Mode");
                assertThat(title.role()).isEqualTo(Nodes.TextRole.BODY_STRONG);
                assertThat(body.text()).isEqualTo("Your browsing activity is not being saved. No history, bookmarks, or other data will be "
                    + "stored. This mode is set in Settings › Tools › Browser › Privacy & Data.");
                assertThat(body.role()).isEqualTo(Nodes.TextRole.BODY);
            });

            view.emit(new UiEvent.Action(BrowserToolbar.DEVNULL));
            assertThat(host.popoverViews(ID)).as("an action while it is open opens no second one").hasSize(1);
            item.close();
            assertThat(host.failures()).isEmpty();
        } });
    }

    @Test void closingTheItemClosesItsPopoverAndAClosedOneIsNotOpen() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var started = new Started(host);
            var item = started.devNullItem();
            FakeKitView view = host.contentView(ID, "example.com").orElseThrow();
            view.emit(new UiEvent.Action(BrowserToolbar.DEVNULL));
            assertThat(host.popoverViews(ID)).hasSize(1);
            item.close();
            assertThat(host.popoverViews(ID)).isEmpty();

            var again = started.devNullItem();
            FakeKitView second = host.contentView(ID, "example.com").orElseThrow();
            var popover = DevNullPopover.show(started.context, second);
            assertThat(popover.open()).isTrue();
            popover.close();
            assertThat(popover.open()).isFalse();
            again.close();
            assertThat(host.failures()).isEmpty();
        } });
    }

    /** The started stand-in plugin's context and its /dev/null items, which open over stores in memory. */
    private final class Started {
        final PluginContext context;
        final BrowserItems items;

        Started(FakePluginHost host) {
            var started = new AtomicReference<PluginContext>();
            var settings = new AtomicReference<BrowserSettings.Declared>();
            host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(), plugin -> {
                started.set(plugin);
                settings.set(BrowserSettings.declare(plugin.settings()));
            });
            context = started.get();
            var stores = new DataStores(memory.make(), memory::make, () -> PageTestAccess.recordingContext(new AtomicInteger()),
                () -> false);
            items = new BrowserItems(context, settings.get()::read, stores, Optional.empty(), page -> { });
        }

        BrowserItem devNullItem() {
            return items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty(), items.dataStores().newDevNull());
        }
    }
}
