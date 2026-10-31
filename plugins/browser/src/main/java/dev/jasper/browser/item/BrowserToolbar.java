package dev.jasper.browser.item;

import dev.jasper.browser.page.NavigationHistory;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.MenuItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.PixelImage;
import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * A browser item's toolbar along the top of its page (spec section 6), as SDK nodes the kit draws: Back and Forward,
 * each disabled when the page cannot go there and each holding the page's twelve nearest history entries that way in
 * its hold menu; Reload, which is Stop while the page loads; the address field, with the site icon at its left; and the
 * menu button, which holds Settings. The site icon is the page's icon, else the globe, and dragging it drags the page's
 * address. The click that brings the focus into the field selects all its text. The toolbar keeps what is typed into
 * the field and the engine's suggestions for it: the field shows the page's address, or a results page's search terms,
 * until something is typed, then that text and its {@link Suggestions}, and the page's again once a load or Escape
 * gives the text up. Its item handles what the nodes report. UI thread.
 */
final class BrowserToolbar {
    /** The toolbar's row and its nodes; a button reports its own id. */
    static final String BAR = "browser.toolbar", BACK = "browser.back", FORWARD = "browser.forward", RELOAD = "browser.reload",
        ADDRESS = "browser.address", MENU = "browser.menu";
    /** What Reload reports while the page loads. */
    static final String STOP = "browser.stop";
    /** What Enter and Escape in the address field report. */
    static final String ENTER = "browser.address.enter", ESCAPE = "browser.address.escape";
    /** What the menu's Settings reports. */
    static final String SETTINGS = "browser.settings";
    static final String PLACEHOLDER = "Search or enter website name";
    /** The site icon's accessible name and tooltip. */
    static final String SITE_ICON = "Site icon - drag to copy URL";
    // What was typed since the field last showed the page's address; null while it shows it.
    private String typed;
    // The engine's suggestions for the typed text, and whether they load.
    private List<String> suggested = List.of();
    private boolean suggesting;

    /**
     * The toolbar for a page at {@code address}.
     *
     * @param address the page's address, which the field shows unless something was typed, and which dragging the site
     *     icon drags
     * @param loading whether the page loads
     * @param canGoBack whether the page can go back
     * @param canGoForward whether the page can go forward
     * @param icon the site icon: the page's favicon, or empty for the globe
     * @param history the page's history, which Back's and Forward's hold menus list
     * @param settings the settings, which say what a results page is
     * @return the toolbar's row
     */
    Nodes.Stack node(String address, boolean loading, boolean canGoBack, boolean canGoForward, Optional<PixelImage> icon,
                     NavigationHistory.History history, BrowserSettings settings) {
        var reload = loading
            ? new Nodes.Button(RELOAD, ControlState.READY, "", "close", Nodes.ButtonKind.GHOST, STOP, "Stop")
            : new Nodes.Button(RELOAD, ControlState.READY, "", "refresh", Nodes.ButtonKind.GHOST, RELOAD, "Reload");
        String value = typed != null ? typed
            : AddressInput.parse(address).flatMap(page -> SearchEngine.queryOf(page, settings)).orElse(address);
        var field = new Nodes.Field(ADDRESS, ControlState.READY, Nodes.FieldKind.TEXT, value, PLACEHOLDER, "Address", ENTER, Optional.empty(),
            Nodes.FieldPresentation.EMBEDDED, "", ESCAPE, "", false, icon, BrowserItems.GLOBE, SITE_ICON,
            address.isBlank() ? Optional.empty() : Optional.of(address), true,
            typed == null ? List.of() : Suggestions.rank(typed, settings, suggested, List.of()), typed != null && suggesting);
        var menu = new Nodes.MenuButton(MENU, ControlState.READY, "", "menu", Nodes.ButtonKind.GHOST, "",
            List.of(new MenuItem(SETTINGS, "Settings", "", SETTINGS, true, false)), "Menu");
        return new Nodes.Stack(BAR, ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
            historyButton(BACK, canGoBack, "back", "Back", NavigationHistory.backMenu(history)),
            historyButton(FORWARD, canGoForward, "forward", "Forward", NavigationHistory.forwardMenu(history)),
            reload, field, menu));
    }

    /** The field's text changed: it shows that text from now on, and no suggestions of the engine's yet. */
    void edited(String text) {
        typed = text;
        suggested = List.of();
        suggesting = false;
    }

    /** The engine's suggestions for the typed text load, which the field's list says after its rows. */
    void suggesting() {
        suggesting = true;
    }

    /** The engine's suggestions for the typed text arrived: the field lists them, and loads no more. */
    void suggested(List<String> suggestions) {
        suggested = List.copyOf(suggestions);
        suggesting = false;
    }

    /**
     * Enter in the field with no suggestion shown: what its text loads. When it loads something, the field shows the
     * page's address again; otherwise it keeps the text.
     *
     * @param address the page's address, which the field shows while nothing is typed
     * @param settings the settings, which choose the engine a search goes to
     * @return what to load; empty for nothing
     */
    Optional<URI> enter(String address, BrowserSettings settings) {
        Optional<URI> target = AddressInput.resolve(typed == null ? address : typed, settings);
        if (target.isPresent()) restore();
        return target;
    }

    /**
     * Escape in the field, or a load: the field gives up what was typed and its suggestions, and shows the page's
     * address.
     */
    void restore() {
        typed = null;
        suggested = List.of();
        suggesting = false;
    }

    /** Back or Forward: a press goes there, and a held one opens {@code menu}, the page's history that way. */
    private static Nodes.Button historyButton(String id, boolean enabled, String icon, String tooltip, List<MenuItem> menu) {
        return new Nodes.Button(id, enabled(enabled), "", icon, Nodes.ButtonKind.GHOST, id, tooltip,
            Nodes.ButtonPresentation.STANDARD, Optional.empty(), Optional.empty(), menu);
    }

    private static ControlState enabled(boolean value) {
        return new ControlState(true, value, false, "");
    }
}
