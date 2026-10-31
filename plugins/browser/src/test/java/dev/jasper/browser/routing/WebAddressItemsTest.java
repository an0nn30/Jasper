package dev.jasper.browser.routing;

import dev.jasper.browser.BrowserPlugin;
import dev.jasper.browser.engine.Chromium;
import dev.jasper.browser.item.BrowserItem;
import dev.jasper.browser.item.BrowserItems;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.PaneInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.LinkOrigin;
import dev.jasper.sdk.ui.LinkPlacement;
import dev.jasper.sdk.ui.WebBrowser;
import dev.jasper.sdk.ui.WebLink;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Open URL in Split Right and Open URL in Split Down (spec section 9): the terminal's right-click menu shows them while
 * a web address is selected, only while Open Links From Terminal lets links open in Jasper's browser, and each opens
 * the selected address as a split click from the pane it was chosen in.
 */
class WebAddressItemsTest {
    private static final String ID = "dev.jasper.browser", LINKS = "open_links_from_terminal";
    private static final String RIGHT = "dev.jasper.browser.open_url_split_right", DOWN = "dev.jasper.browser.open_url_split_down";
    private static final PluginInfo INFO = new PluginInfo(ID, "Browser", "0.1.0", Set.of("browser.provide"));
    private static final List<String> SHOWN = List.of("web-address:" + RIGHT, "web-address:" + DOWN);

    @Test void theItemsAreThereWhileLinksMayOpenInJasperAndGoWhileTheyGoToTheDefaultBrowser() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), new BrowserPlugin());
            assertThat(host.actions()).contains(RIGHT + "|Open URL in Split Right|true", DOWN + "|Open URL in Split Down|true");
            assertThat(host.menu("context")).as("Always Ask, the default").containsExactlyElementsOf(SHOWN);
            host.setSetting(ID, LINKS, "default");
            assertThat(host.menu("context")).as("Default Browser").isEmpty();
            host.setSetting(ID, LINKS, "builtin");
            assertThat(host.menu("context")).as("Built-in Browser").containsExactlyElementsOf(SHOWN);
            host.setSetting(ID, LINKS, "ask");
            assertThat(host.menu("context")).as("once each").containsExactlyElementsOf(SHOWN);
            host.setSetting(ID, LINKS, "default");
            assertThat(host.menu("context")).isEmpty();
            assertThat(host.failures()).isEmpty();
        } });
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            host.setSetting(ID, LINKS, "default");
            host.start(INFO, Set.of(), Set.of(), new BrowserPlugin());
            assertThat(host.menu("context")).as("absent from the start").isEmpty();
            host.setSetting(ID, LINKS, "ask");
            assertThat(host.menu("context")).containsExactlyElementsOf(SHOWN);
            assertThat(host.failures()).isEmpty();
        } });
    }

    @Test void eachOpensTheSelectedAddressAsASplitClickFromThePaneItWasChosenIn() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            UUID first = host.addTerminalPane(host.addTerminalTab(window, "one"), PaneInfo.unknown());
            UUID second = host.addTerminalPane(host.addTerminalTab(window, "two"), PaneInfo.unknown());
            host.focusTerminalPane(first);
            host.setSelection(second, "https://example.com/a");
            var recorded = new Recorder(true);
            var context = new AtomicReference<PluginContext>();
            host.start(INFO, Set.of(), Set.of(), plugin -> {
                context.set(plugin);
                recorded.context = plugin;
                var settings = BrowserSettings.declare(plugin.settings());
                new BrowserItems(plugin, settings::read, page -> { });
                WebAddressItems.register(plugin, recorded, settings);
            });
            assertThat(host.contextMenu(second)).containsExactlyElementsOf(SHOWN);
            assertThat(host.chooseFromContextMenu(second, RIGHT)).isTrue();
            assertThat(host.chooseFromContextMenu(second, DOWN)).isTrue();
            assertThat(recorded.links).extracting(WebLink::placement).containsExactly(LinkPlacement.SPLIT_RIGHT, LinkPlacement.SPLIT_DOWN);
            assertThat(recorded.links).allSatisfy(link -> {
                assertThat(link.address()).isEqualTo(URI.create("https://example.com/a"));
                assertThat(link.origin()).isEqualTo(LinkOrigin.CLICK);
                assertThat(link.target()).isEmpty();
                assertThat(link.window()).get().isEqualTo(context.get().terminals().window(window).orElseThrow());
            });
            assertThat(recorded.focused).as("the pane it was chosen in is the focused one").containsExactly(second, second);
            assertThat(host.browsed()).isEmpty();

            host.setSelection(second, "not an address");
            assertThat(host.contextMenu(second)).as("only for a web address").isEmpty();
            assertThat(host.invoke(RIGHT, window, second)).as("from the palette or a key").isTrue();
            assertThat(recorded.links).as("no address, nothing opens").hasSize(2);
            assertThat(host.failures()).isEmpty();
        } });
    }

    @Test void anAddressTheBrowserGivesBackGoesToTheSystemBrowser() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            UUID pane = host.addTerminalPane(host.addTerminalTab(window, "one"), PaneInfo.unknown());
            host.setSelection(pane, "https://example.com/b");
            var declined = new Recorder(false);
            host.start(INFO, Set.of(), Set.of(), plugin -> {
                declined.context = plugin;
                var settings = BrowserSettings.declare(plugin.settings());
                new BrowserItems(plugin, settings::read, page -> { });
                WebAddressItems.register(plugin, declined, settings);
            });
            assertThat(host.chooseFromContextMenu(pane, RIGHT)).isTrue();
            assertThat(declined.links).hasSize(1);
            assertThat(host.browsed()).containsExactly(URI.create("https://example.com/b"));
            assertThat(host.failures()).isEmpty();
        } });
    }

    @Test void withTheBrowsersRoleTheAddressOpensInASplitBesideThePane() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            UUID shell = host.addTerminalTab(window, "zsh");
            UUID pane = host.addTerminalPane(shell, PaneInfo.unknown());
            host.setSelection(pane, "https://example.com/c");
            var items = new AtomicReference<BrowserItems>();
            var chromium = new AtomicReference<>(Chromium.State.NOT_STARTED);
            host.start(INFO, Set.of(), Set.of(), plugin -> {
                var settings = BrowserSettings.declare(plugin.settings());
                items.set(new BrowserItems(plugin, settings::read, page -> { }));
                var role = new BrowserRole(plugin, items.get(), settings, chromium::get);
                plugin.platform().browser(role);
                WebAddressItems.register(plugin, role, settings);
            });
            assertThat(host.chooseFromContextMenu(pane, DOWN)).isTrue();
            assertThat(host.windows()).as("a split asks nothing").isEmpty();
            assertThat(items.get().items()).singleElement().extracting(BrowserItem::url).isEqualTo(URI.create("https://example.com/c"));
            assertThat(host.windowTabs(window)).singleElement().satisfies(tab -> assertThat(tab).hasSize(2).first().isEqualTo(shell));

            chromium.set(Chromium.State.FAILED);
            host.setSelection(pane, "https://example.com/d");
            assertThat(host.chooseFromContextMenu(pane, RIGHT)).isTrue();
            assertThat(items.get().items()).as("no page could show: nothing opens").hasSize(1);
            assertThat(host.browsed()).as("the system browser").containsExactly(URI.create("https://example.com/d"));
            assertThat(host.failures()).isEmpty();
        } });
    }

    /** A web browser that records each link it is asked to open, and the focused pane then, and answers {@code opens}. */
    private static final class Recorder implements WebBrowser {
        final List<WebLink> links = new CopyOnWriteArrayList<>();
        final List<UUID> focused = new CopyOnWriteArrayList<>();
        private final boolean opens;
        PluginContext context;

        Recorder(boolean opens) {
            this.opens = opens;
        }

        @Override public boolean open(WebLink link) {
            links.add(link);
            context.terminals().activePane().ifPresent(pane -> focused.add(pane.id()));
            return opens;
        }
    }
}
