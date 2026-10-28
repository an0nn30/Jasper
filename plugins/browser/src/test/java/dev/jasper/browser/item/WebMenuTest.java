package dev.jasper.browser.item;

import dev.jasper.browser.BrowserPlugin;
import dev.jasper.browser.page.Page;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.ShortcutContext;
import dev.jasper.sdk.ui.TabPlacement;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Web menu (browser spec section 7) on the SDK's fake host: its items, their keys on each platform, the windows whose
 * bar shows it, and the browser item each acts on. Nothing here makes a page's browser, so nothing starts Chromium.
 */
class WebMenuTest {
    private static final String ID = "dev.jasper.browser";
    private static final PluginInfo INFO = new PluginInfo(ID, "Browser", "0.1.0", Set.of("browser.provide"));
    private static final String MENU = "top:dev.jasper.browser.web", NEW_TAB = "dev.jasper.browser.new_tab";
    private static final String OPEN_LOCATION = "dev.jasper.browser.open_location", BACK = "dev.jasper.browser.back",
        FORWARD = "dev.jasper.browser.forward", RELOAD = "dev.jasper.browser.reload";
    private static final URI EXAMPLE = URI.create("https://example.com"), OTHER = URI.create("https://example.org");

    /** Each item's action, title, macOS key and Linux and Windows key, in the menu's order. */
    private record Expected(String id, String title, String macOs, String other) { }

    private static final List<Expected> ITEMS = List.of(
        new Expected(OPEN_LOCATION, "Open Location…", "cmd+l", "ctrl+l"),
        new Expected(BACK, "Back", "cmd+[", "alt+left"),
        new Expected(FORWARD, "Forward", "cmd+]", "alt+right"),
        new Expected(RELOAD, "Reload Page", "cmd+r", "ctrl+r"));

    @Test void eachItemIsAnActionOfTheBrowsersItemsWithTheSpecsKeysOnMacOsAndOnLinuxAndWindows() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), new BrowserPlugin());
            for (Expected item : ITEMS) {
                assertThat(host.actions()).contains(item.id() + "|" + item.title() + "|true");
                assertThat(host.actionSpec(item.id())).as(item.id()).get().satisfies(spec -> {
                    assertThat(spec.defaultBinding()).as("macOS").contains(item.macOs());
                    assertThat(spec.otherDefaultBinding()).as("Linux and Windows").contains(item.other());
                    assertThat(spec.context()).as("its keys win while a browser item has the focus").isEqualTo(ShortcutContext.content());
                });
            }
            assertThat(host.failures()).isEmpty();
        } });
    }

    @Test void theMenuHoldsOpenLocationThenBackAndForwardThenReloadPage() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), new BrowserPlugin());
            assertThat(host.menu(MENU)).containsExactly("item:" + OPEN_LOCATION, "---", "item:" + BACK, "item:" + FORWARD,
                "---", "item:" + RELOAD);
            assertThat(host.failures()).isEmpty();
        } });
    }

    @Test void aWindowsBarShowsTheMenuOnlyWhileABrowserItemIsItsCurrentItem() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            host.addTerminalTab(window, "zsh");
            host.start(INFO, Set.of(), Set.of(), new BrowserPlugin());
            assertThat(host.menuBar(window)).as("a terminal is current").doesNotContain(MENU);
            assertThat(host.invoke(NEW_TAB, window, null)).isTrue();
            var browser = host.contentTabs().getLast();
            assertThat(host.menuBar(window)).as("a browser item is current").contains(MENU);
            UUID other = host.addTerminalWindow();
            host.addTerminalTab(other, "zsh");
            assertThat(host.menuBar(other)).as("another window, with no browser item").doesNotContain(MENU);
            host.addTerminalTab(window, "logs");
            assertThat(host.menuBar(window)).as("a terminal is current again").doesNotContain(MENU);
            browser.select();
            assertThat(host.menuBar(window)).contains(MENU);
            browser.close();
            assertThat(host.menuBar(window)).as("the item closed").doesNotContain(MENU);
            assertThat(host.failures()).isEmpty();
        } });
    }

    @Test void eachItemActsOnTheFocusedBrowserItemOfTheWindowItRunsInAndWithNoneDoesNothing() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var items = items(host);
            var ran = new ArrayList<String>();
            WebMenu.register(items.context(), items, recorder(ran));
            UUID first = host.addTerminalWindow();
            host.activateTerminalWindow(first);
            items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            UUID second = host.addTerminalWindow();
            host.activateTerminalWindow(second);
            items.open(OTHER, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            for (Expected item : ITEMS) assertThat(host.invoke(item.id(), second, null)).isTrue();
            assertThat(ran).containsExactly(OPEN_LOCATION + " " + OTHER, BACK + " " + OTHER, FORWARD + " " + OTHER, RELOAD + " " + OTHER);
            ran.clear();
            assertThat(host.invoke(BACK, first, null)).isTrue();
            assertThat(ran).as("the window it runs in").containsExactly(BACK + " " + EXAMPLE);
            ran.clear();
            host.addTerminalTab(first, "zsh");
            UUID empty = host.addTerminalWindow();
            for (Expected item : ITEMS) {
                host.invoke(item.id(), first, null);
                host.invoke(item.id(), empty, null);
            }
            assertThat(ran).as("a terminal is current, or the window has no browser item").isEmpty();
            assertThat(host.failures()).isEmpty();
        } });
    }

    @Test void openLocationFocusesTheAddressFieldOfThatWindowsItemAndSelectsItsText() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            var items = items(host);
            WebMenu.register(items.context(), items);
            UUID first = host.addTerminalWindow();
            host.activateTerminalWindow(first);
            items.open(EXAMPLE, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            UUID second = host.addTerminalWindow();
            host.activateTerminalWindow(second);
            items.open(OTHER, TabPlacement.NEW_TAB, Optional.empty(), Optional.empty());
            assertThat(host.invoke(OPEN_LOCATION, first, null)).isTrue();
            var shown = host.contentView(ID, "example.com").orElseThrow();
            assertThat(shown.focused()).contains(BrowserToolbar.ADDRESS);
            assertThat(shown.fieldSelection(BrowserToolbar.ADDRESS)).containsExactly(0, EXAMPLE.toString().length());
            assertThat(host.contentView(ID, "example.org").orElseThrow().focused()).as("the other window's item").isEmpty();
            // Back, Forward and Reload Page ask a page that has no browser yet, which does nothing.
            for (String action : List.of(BACK, FORWARD, RELOAD)) assertThat(host.invoke(action, first, null)).isTrue();
            assertThat(host.failures()).isEmpty();
        } });
    }

    /** The items of a started stand-in plugin, whose pages are never made. UI thread. */
    private static BrowserItems items(FakePluginHost host) {
        var context = new AtomicReference<PluginContext>();
        host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(), context::set);
        return new BrowserItems(context.get(), (Page page) -> { });
    }

    /** Records each item run as its action and the address of the browser item it ran on. */
    private static BiConsumer<String, BrowserItem> recorder(List<String> ran) {
        return (action, item) -> ran.add(action + " " + item.url());
    }
}
