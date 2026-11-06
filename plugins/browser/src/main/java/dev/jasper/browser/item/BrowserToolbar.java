package dev.jasper.browser.item;

import dev.jasper.browser.page.NavigationHistory;
import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.browser.store.Addresses;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.MenuItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.PixelImage;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;

/**
 * A browser item's toolbar along the top of its page (spec section 6), as SDK nodes the kit draws: Back and Forward,
 * each disabled when the page cannot go there and each holding the page's twelve nearest history entries that way in
 * its hold menu, then, as Chrome's do, a separator and Show Full History, which opens Jasper's history page (plan 5's
 * H2); Reload, which is Stop while the page loads; the address field, with the site icon at its left; and the menu
 * button, which holds Add Bookmark, or Remove Bookmark while the page is bookmarked, a separator, Manage Bookmarks and
 * History, then a separator and Settings (spec section 6). The site icon is the page's icon, else the globe, and
 * dragging it drags the page's address; while the page is one Jasper goes on to past a certificate error, "Not secure",
 * in the warning colour, stands in its place (spec section 14). The click that brings the focus into the field selects
 * all its text. The toolbar keeps what is typed into the field and the engine's suggestions for it: the field shows the
 * page's address, or a results page's search terms, until something is typed, then that text and its
 * {@link Suggestions}, the rows of Jasper's bookmarks and history for it last, and the page's again once a load or
 * Escape gives the text up. Its item handles what the nodes report. UI thread.
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
    /** What the menu's Add Bookmark, Remove Bookmark, Manage Bookmarks and History report. */
    static final String BOOKMARK_ADD = "browser.bookmark.add", BOOKMARK_REMOVE = "browser.bookmark.remove", BOOKMARKS = "browser.bookmarks",
        HISTORY = "browser.history";
    /** What Show Full History, at the end of Back's and Forward's hold menus, reports. */
    static final String SHOW_HISTORY = "browser.history.show";
    // What ends a hold menu that lists entries of the page's history: a separator, then Show Full History.
    private static final List<MenuItem> FULL_HISTORY = List.of(new MenuItem("browser.history.separator", "", "", "", true, true),
        new MenuItem(SHOW_HISTORY, "Show Full History", "", SHOW_HISTORY, true, false));
    private static final MenuItem SETTINGS_ITEM = new MenuItem(SETTINGS, "Settings", "", SETTINGS, true, false);
    static final String PLACEHOLDER = "Search or enter website name";
    /** What says the page is not secure, in place of the site icon. */
    static final String NOT_SECURE = "browser.address.not-secure";
    /** The site icon's accessible name and tooltip. */
    static final String SITE_ICON = "Site icon - drag to copy URL";
    // Given the typed text, the rows of Jasper's bookmarks and history for it.
    private final Function<String, List<Nodes.Suggestion>> stores;
    // What was typed since the field last showed the page's address; null while it shows it.
    private String typed;
    // The engine's suggestions for the typed text, and whether they load.
    private List<String> suggested = List.of();
    private boolean suggesting;

    /**
     * A toolbar whose field lists {@code stores}' rows for the typed text after its own.
     *
     * @param stores given the typed text, the rows of Jasper's bookmarks and history for it, asked only while something
     *     is typed
     */
    BrowserToolbar(Function<String, List<Nodes.Suggestion>> stores) {
        this.stores = Objects.requireNonNull(stores, "stores");
    }

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
     * @param notSecure whether the page is one Jasper goes on to past a certificate error: "Not secure" then stands before
     *     the field, which shows no site icon
     * @param bookmarked whether the page's address is bookmarked, which decides between Add Bookmark and Remove Bookmark;
     *     empty for a menu without the bookmark items, as in plan 6's /dev/null mode
     * @return the toolbar's row
     */
    Nodes.Stack node(String address, boolean loading, boolean canGoBack, boolean canGoForward, Optional<PixelImage> icon,
                     NavigationHistory.History history, BrowserSettings settings, boolean notSecure, Optional<Boolean> bookmarked) {
        var reload = loading
            ? new Nodes.Button(RELOAD, ControlState.READY, "", "close", Nodes.ButtonKind.GHOST, STOP, "Stop")
            : new Nodes.Button(RELOAD, ControlState.READY, "", "refresh", Nodes.ButtonKind.GHOST, RELOAD, "Reload");
        String value = typed != null ? typed
            : AddressInput.parse(address).flatMap(page -> SearchEngine.queryOf(page, settings)).orElse(address);
        List<Nodes.Suggestion> rows = typed == null ? List.of() : Suggestions.rank(typed, settings, suggested, stores.apply(typed));
        // The site icon's slot is empty while "Not secure" stands in its place, so it is neither named nor dragged.
        Optional<PixelImage> image = notSecure ? Optional.empty() : icon;
        String glyph = notSecure ? "" : BrowserItems.GLOBE, name = notSecure ? "" : SITE_ICON;
        Optional<String> drag = notSecure || address.isBlank() ? Optional.empty() : Optional.of(address);
        var field = new Nodes.Field(ADDRESS, ControlState.READY, Nodes.FieldKind.TEXT, value, PLACEHOLDER, "Address", ENTER, Optional.empty(),
            Nodes.FieldPresentation.EMBEDDED, "", ESCAPE, "", false, image, glyph, name, drag, true, rows, typed != null && suggesting);
        var menu = new Nodes.MenuButton(MENU, ControlState.READY, "", "menu", Nodes.ButtonKind.GHOST, "", menu(address, bookmarked), "Menu");
        var children = new ArrayList<Nodes.Node>(List.of(
            historyButton(BACK, canGoBack, "back", "Back", NavigationHistory.backMenu(history)),
            historyButton(FORWARD, canGoForward, "forward", "Forward", NavigationHistory.forwardMenu(history)), reload));
        if (notSecure) children.add(new Nodes.Text(NOT_SECURE, ControlState.READY, "Not secure", Nodes.TextRole.CAPTION,
            Optional.of(Nodes.Severity.WARNING), OptionalInt.empty()));
        children.add(field);
        children.add(menu);
        return new Nodes.Stack(BAR, ControlState.READY, Nodes.LayoutRole.INLINE, children);
    }

    /** Whether something is typed in the field, which then lists its suggestions. */
    boolean typing() {
        return typed != null;
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

    /**
     * The menu button's items: with a bookmark state, Add Bookmark (disabled for an address bookmarks never keep) or Remove
     * Bookmark, a separator, Manage Bookmarks, History and a separator; then Settings.
     */
    private static List<MenuItem> menu(String address, Optional<Boolean> bookmarked) {
        if (bookmarked.isEmpty()) return List.of(SETTINGS_ITEM);
        MenuItem bookmark = bookmarked.get() ? new MenuItem(BOOKMARK_REMOVE, "Remove Bookmark", "", BOOKMARK_REMOVE, true, false)
            : new MenuItem(BOOKMARK_ADD, "Add Bookmark", "", BOOKMARK_ADD, Addresses.storable(address).isPresent(), false);
        return List.of(bookmark, new MenuItem("browser.bookmarks.separator", "", "", "", true, true),
            new MenuItem(BOOKMARKS, "Manage Bookmarks", "", BOOKMARKS, true, false), new MenuItem(HISTORY, "History", "", HISTORY, true, false),
            new MenuItem("browser.settings.separator", "", "", "", true, true), SETTINGS_ITEM);
    }

    /**
     * Back or Forward: a press goes there, and a held one opens {@code entries}, the page's history that way, then Show
     * Full History; with no entries that way, it holds no menu.
     */
    private static Nodes.Button historyButton(String id, boolean enabled, String icon, String tooltip, List<MenuItem> entries) {
        var menu = new ArrayList<MenuItem>(entries);
        if (!menu.isEmpty()) menu.addAll(FULL_HISTORY);
        return new Nodes.Button(id, enabled(enabled), "", icon, Nodes.ButtonKind.GHOST, id, tooltip,
            Nodes.ButtonPresentation.STANDARD, Optional.empty(), Optional.empty(), menu);
    }

    private static ControlState enabled(boolean value) {
        return new ControlState(true, value, false, "");
    }
}
