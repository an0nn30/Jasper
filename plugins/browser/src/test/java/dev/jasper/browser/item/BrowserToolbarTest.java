package dev.jasper.browser.item;

import dev.jasper.browser.BrowserPlugin;
import dev.jasper.browser.page.NavigationHistory;
import dev.jasper.browser.page.NavigationHistory.Entry;
import dev.jasper.browser.page.NavigationHistory.History;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.MenuItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.PixelImage;
import dev.jasper.sdk.ui.UiEvent;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A browser item's toolbar, as SDK nodes on the testkit's fake host: the Browser plugin opens a New Browser Tab at a
 * start page set to {@code https://example.com}. Nothing here shows the page, so nothing asks Chromium for it.
 */
class BrowserToolbarTest {
    private static final String ID = "dev.jasper.browser", START = "https://example.com";

    @Test void backAndForwardAreDisabledOnANewItem() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            FakeKitView view = newTab(host);
            assertThat(Views.button(view, BrowserToolbar.BACK).state().enabled()).isFalse();
            assertThat(Views.button(view, BrowserToolbar.FORWARD).state().enabled()).isFalse();
            assertThat(Views.button(view, BrowserToolbar.RELOAD).action()).isEqualTo(BrowserToolbar.RELOAD);
            Nodes.Field address = Views.field(view, BrowserToolbar.ADDRESS);
            assertThat(address.value()).isEqualTo(START);
            assertThat(address.hint()).isEqualTo("Search or enter website name");
            assertThat(host.failures()).isEmpty();
        } });
    }

    @Test void reloadBecomesStopWhileThePageLoads() {
        var toolbar = new BrowserToolbar();
        Nodes.Button idle = Views.button(toolbar.node(START, false, true, false, Optional.empty(), History.EMPTY), BrowserToolbar.RELOAD);
        assertThat(idle.action()).isEqualTo(BrowserToolbar.RELOAD);
        assertThat(idle.icon()).isEqualTo("refresh");
        assertThat(idle.tooltip()).isEqualTo("Reload");
        Nodes.Button loading = Views.button(toolbar.node(START, true, true, false, Optional.empty(), History.EMPTY), BrowserToolbar.RELOAD);
        assertThat(loading.action()).isEqualTo(BrowserToolbar.STOP);
        assertThat(loading.icon()).isEqualTo("close");
        assertThat(loading.tooltip()).isEqualTo("Stop");
        assertThat(Views.button(toolbar.node(START, true, true, false, Optional.empty(), History.EMPTY), BrowserToolbar.BACK).state().enabled())
            .as("Back can go").isTrue();
        assertThat(Views.button(toolbar.node(START, true, true, false, Optional.empty(), History.EMPTY), BrowserToolbar.FORWARD).state().enabled()).isFalse();
    }

    /**
     * Spec section 6: Back and Forward hold the page's history, nearest entry first, while a short press still goes back
     * or forward; with nothing that way the button holds no menu.
     */
    @Test void backAndForwardHoldThePagesHistory() {
        var toolbar = new BrowserToolbar();
        var history = new History(1, List.of(new Entry(1, "https://example.com/one", "One"), new Entry(2, START, "Here"),
            new Entry(3, "https://example.com/three", "Three"), new Entry(4, "https://example.com/four", "Four")));
        Nodes.Stack bar = toolbar.node(START, false, true, true, Optional.empty(), history);
        Nodes.Button back = Views.button(bar, BrowserToolbar.BACK), forward = Views.button(bar, BrowserToolbar.FORWARD);
        assertThat(back.holdMenu()).isEqualTo(NavigationHistory.backMenu(history)).extracting(MenuItem::label).containsExactly("One");
        assertThat(forward.holdMenu()).isEqualTo(NavigationHistory.forwardMenu(history)).extracting(MenuItem::label)
            .containsExactly("Three", "Four");
        assertThat(back.action()).isEqualTo(BrowserToolbar.BACK);
        assertThat(forward.action()).isEqualTo(BrowserToolbar.FORWARD);
        Nodes.Stack last = toolbar.node(START, false, true, false, Optional.empty(), new History(1, history.entries().subList(0, 2)));
        assertThat(Views.button(last, BrowserToolbar.FORWARD).holdMenu()).as("an empty forward list").isEmpty();
        Nodes.Stack none = toolbar.node(START, false, false, false, Optional.empty(), History.EMPTY);
        assertThat(Views.button(none, BrowserToolbar.BACK).holdMenu()).isEmpty();
        assertThat(Views.button(none, BrowserToolbar.FORWARD).holdMenu()).isEmpty();
    }

    /** Spec section 6: the field's leading slot shows the page's icon, else the globe, and drags the address it is given. */
    @Test void theFieldsLeadingSlotShowsTheIconGivenElseTheGlobeAndDragsTheAddress() {
        var toolbar = new BrowserToolbar();
        var icon = new PixelImage(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 1});
        Nodes.Field shown = (Nodes.Field) Views.node(toolbar.node(START, false, false, false, Optional.of(icon), History.EMPTY), BrowserToolbar.ADDRESS);
        assertThat(shown.leadingImage()).contains(icon);
        assertThat(shown.leadingIcon()).isEqualTo(BrowserItems.GLOBE);
        assertThat(shown.leadingName()).isEqualTo("Site icon - drag to copy URL");
        assertThat(shown.leadingDrag()).contains(START);
        Nodes.Field none = (Nodes.Field) Views.node(toolbar.node(START, false, false, false, Optional.empty(), History.EMPTY), BrowserToolbar.ADDRESS);
        assertThat(none.leadingImage()).isEmpty();
        assertThat(none.leadingIcon()).isEqualTo(BrowserItems.GLOBE);
        assertThat(none.leadingDrag()).contains(START);
        toolbar.edited("typed");
        Nodes.Field typed = (Nodes.Field) Views.node(toolbar.node(START, false, false, false, Optional.of(icon), History.EMPTY), BrowserToolbar.ADDRESS);
        assertThat(typed.value()).isEqualTo("typed");
        assertThat(typed.leadingDrag()).as("the page's address, not the text").contains(START);
        Nodes.Field blank = (Nodes.Field) Views.node(toolbar.node("", false, false, false, Optional.empty(), History.EMPTY), BrowserToolbar.ADDRESS);
        assertThat(blank.leadingDrag()).as("nothing to drag").isEmpty();
    }

    @Test void escapeRestoresThePagesAddressAndSelectsIt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            FakeKitView view = newTab(host);
            view.type(BrowserToolbar.ADDRESS, "something else");
            assertThat(Views.field(view, BrowserToolbar.ADDRESS).value()).isEqualTo("something else");
            view.emit(new UiEvent.Action(BrowserToolbar.ESCAPE));
            assertThat(Views.field(view, BrowserToolbar.ADDRESS).value()).isEqualTo(START);
            assertThat(view.fieldSelection(BrowserToolbar.ADDRESS)).containsExactly(0, START.length());
            assertThat(host.failures()).isEmpty();
        } });
    }

    @Test void enterLoadsWhatTheTypedTextResolvesTo() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            FakeKitView view = newTab(host);
            var tab = host.contentTab(ID, "example.com").orElseThrow();
            view.type(BrowserToolbar.ADDRESS, "  what is jcef ");
            view.emit(new UiEvent.Action(BrowserToolbar.ENTER));
            String search = "https://google.com/search?q=what+is+jcef";
            assertThat(Views.field(view, BrowserToolbar.ADDRESS).value()).isEqualTo(search);
            assertThat(host.contentPresentation(tab).location()).isEqualTo(search);
            assertThat(host.contentPresentation(tab).title()).isEqualTo("google.com");
            view.type(BrowserToolbar.ADDRESS, "chrome://version");
            view.emit(new UiEvent.Action(BrowserToolbar.ENTER));
            assertThat(host.contentPresentation(tab).location()).as("nothing loads").isEqualTo(search);
            assertThat(Views.field(view, BrowserToolbar.ADDRESS).value()).as("the field keeps the text").isEqualTo("chrome://version");
            assertThat(host.failures()).isEmpty();
        } });
    }

    @Test void theMenuButtonHoldsSettingsWhichOpensSettingsAtTheBrowserPage() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
            FakeKitView view = newTab(host);
            Nodes.MenuButton menu = (Nodes.MenuButton) Views.node(view.spec().root(), BrowserToolbar.MENU);
            assertThat(menu.items()).singleElement().satisfies(item -> assertThat(item.label()).isEqualTo("Settings"));
            assertThat(menu.icon()).isEqualTo("menu");
            view.emit(new UiEvent.Action(menu.items().getFirst().action()));
            assertThat(host.settingsShown(ID)).isEqualTo(1);
            assertThat(host.failures()).isEmpty();
        } });
    }

    /** Starts the Browser with its start page at {@link #START} and runs New Browser Tab in a window. UI thread. */
    static FakeKitView newTab(FakePluginHost host) {
        UUID window = host.addTerminalWindow();
        host.activateTerminalWindow(window);
        host.setSetting(ID, "start_page", START);
        host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of("browser.provide")), Set.of(), Set.of(), new BrowserPlugin());
        assertThat(host.invoke("dev.jasper.browser.new_tab", window, null)).isTrue();
        assertThat(host.contentTabs()).singleElement();
        assertThat(URI.create(host.contentPresentation(host.contentTabs().getFirst()).location())).isEqualTo(URI.create(START));
        return host.contentView(ID, "example.com").orElseThrow();
    }
}
