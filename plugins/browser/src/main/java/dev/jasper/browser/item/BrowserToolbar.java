package dev.jasper.browser.item;

import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.MenuItem;
import dev.jasper.sdk.ui.Nodes;
import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * A browser item's toolbar along the top of its page (spec section 6), as SDK nodes the kit draws: Back and Forward,
 * each disabled when the page cannot go there; Reload, which is Stop while the page loads; the address field; and the
 * menu button, which holds Settings. It keeps what is typed into the field: the field shows the page's address until
 * something is typed, and again once Enter loads it or Escape gives it up. Its item handles what the nodes report. UI
 * thread.
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
    // What was typed since the field last showed the page's address; null while it shows it.
    private String typed;

    /**
     * The toolbar for a page at {@code address}.
     *
     * @param address the page's address, which the field shows unless something was typed
     * @param loading whether the page loads
     * @param canGoBack whether the page can go back
     * @param canGoForward whether the page can go forward
     * @return the toolbar's row
     */
    Nodes.Stack node(String address, boolean loading, boolean canGoBack, boolean canGoForward) {
        var reload = loading
            ? new Nodes.Button(RELOAD, ControlState.READY, "", "close", Nodes.ButtonKind.GHOST, STOP, "Stop")
            : new Nodes.Button(RELOAD, ControlState.READY, "", "refresh", Nodes.ButtonKind.GHOST, RELOAD, "Reload");
        var field = new Nodes.Field(ADDRESS, ControlState.READY, Nodes.FieldKind.TEXT, typed == null ? address : typed, PLACEHOLDER,
            "Address", ENTER, Optional.empty(), Nodes.FieldPresentation.EMBEDDED, "", ESCAPE, "");
        var menu = new Nodes.MenuButton(MENU, ControlState.READY, "", "menu", Nodes.ButtonKind.GHOST, "",
            List.of(new MenuItem(SETTINGS, "Settings", "", SETTINGS, true, false)), "Menu");
        return new Nodes.Stack(BAR, ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.Button(BACK, enabled(canGoBack), "", "back", Nodes.ButtonKind.GHOST, BACK, "Back"),
            new Nodes.Button(FORWARD, enabled(canGoForward), "", "forward", Nodes.ButtonKind.GHOST, FORWARD, "Forward"),
            reload, field, menu));
    }

    /** The field's text changed: it shows that text from now on. */
    void edited(String text) {
        typed = text;
    }

    /**
     * Enter in the field: what its text loads. When it loads something, the field shows the page's address again;
     * otherwise it keeps the text.
     *
     * @param address the page's address, which the field shows while nothing is typed
     * @return what to load; empty for nothing
     */
    Optional<URI> enter(String address) {
        Optional<URI> target = AddressInput.resolve(typed == null ? address : typed);
        if (target.isPresent()) typed = null;
        return target;
    }

    /** Escape in the field, or a load from elsewhere: the field gives up what was typed and shows the page's address. */
    void restore() {
        typed = null;
    }

    private static ControlState enabled(boolean value) {
        return new ControlState(true, value, false, "");
    }
}
