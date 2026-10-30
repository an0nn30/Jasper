package dev.jasper.browser.routing;

import dev.jasper.browser.item.BrowserItems;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.browser.settings.BrowserSettings.LinkOpening;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.ui.ActionContext;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.LinkOrigin;
import dev.jasper.sdk.ui.LinkPlacement;
import dev.jasper.sdk.ui.PluginMenu;
import dev.jasper.sdk.ui.WebBrowser;
import dev.jasper.sdk.ui.WebLink;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Open URL in Split Right and Open URL in Split Down (spec section 9, iTerm2's Vertical and Horizontal Split Pane): the
 * terminal's right-click menu shows them while the right-clicked pane's selection is one web address, and only while
 * Open Links From Terminal lets a split click open in Jasper's browser, so they come and go with that setting. Each
 * opens the selected address as a split click from the pane it was chosen in, through the Browser's role, with no
 * prompt; the address the role gives back goes to the system browser. Invoked any other way, as from the palette, an
 * action has no address and does nothing. UI thread.
 */
public final class WebAddressItems {
    static final String SPLIT_RIGHT = "dev.jasper.browser.open_url_split_right", SPLIT_DOWN = "dev.jasper.browser.open_url_split_down";
    private final PluginContext context;
    private final WebBrowser browser;
    private final PluginMenu menu;
    private List<Subscription> shown = List.of();

    private WebAddressItems(PluginContext context, WebBrowser browser) {
        this.context = context;
        this.browser = browser;
        menu = context.menus().terminalContext();
    }

    /**
     * Registers the two actions and their section of the terminal's right-click menu, which holds them while
     * {@code settings} lets links open in Jasper's browser.
     *
     * @param context the plugin's context, while it starts; the globe is registered already
     * @param browser opens each address: the Browser's role
     * @param settings the Browser's settings, whose Open Links From Terminal the items follow
     */
    public static void register(PluginContext context, WebBrowser browser, BrowserSettings.Declared settings) {
        Objects.requireNonNull(browser, "browser");
        Objects.requireNonNull(settings, "settings");
        var items = new WebAddressItems(context, browser);
        List<String> keywords = List.of("browser", "web", "link", "url", "split", "pane");
        context.actions().register(ActionSpec.of(SPLIT_RIGHT, "Open URL in Split Right").withIcon(BrowserItems.GLOBE).withKeywords(keywords),
            invoked -> items.open(invoked, LinkPlacement.SPLIT_RIGHT));
        context.actions().register(ActionSpec.of(SPLIT_DOWN, "Open URL in Split Down").withIcon(BrowserItems.GLOBE).withKeywords(keywords),
            invoked -> items.open(invoked, LinkPlacement.SPLIT_DOWN));
        items.follow(settings.read().links());
        settings.onLinksChanged(items::follow);
    }

    /** Adds the items while links may open in Jasper's browser, and takes them out while they go to the default browser. */
    private void follow(LinkOpening links) {
        boolean wanted = links != LinkOpening.DEFAULT_BROWSER;
        if (wanted == !shown.isEmpty()) return;
        if (wanted) shown = List.of(menu.addForWebAddress(SPLIT_RIGHT), menu.addForWebAddress(SPLIT_DOWN));
        else {
            shown.forEach(Subscription::close);
            shown = List.of();
        }
    }

    /**
     * Opens the item's address as a split click from the pane it was chosen in, which is focused first, since a split
     * opens beside its window's focused pane.
     */
    private void open(ActionContext invoked, LinkPlacement placement) {
        Optional<URI> address = invoked.webAddress();
        if (address.isEmpty()) return;
        invoked.pane().ifPresent(PaneHandle::focus);
        WebLink link;
        try {
            link = new WebLink(address.get(), LinkOrigin.CLICK, placement, Optional.empty(), Optional.of(invoked.window()));
        } catch (IllegalArgumentException notAWebLink) { // an authority that is no host, such as http://my_host/
            context.platform().browse(address.get());
            return;
        }
        if (!browser.open(link)) context.platform().browse(address.get());
    }
}
