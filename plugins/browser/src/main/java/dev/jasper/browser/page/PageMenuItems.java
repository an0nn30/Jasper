package dev.jasper.browser.page;

import dev.jasper.sdk.ui.MenuItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The page's right-click menu (spec section 11) as the SDK's menu items, for what is under the pointer. Above the pane
 * items Jasper adds, in groups: Back, Forward and Reload; with a link, Open Link in New Tab and Copy Link; with an image,
 * Open Image in New Tab and Copy Image Address; then, in an editable field, Cut, Copy and Paste, or else, with a
 * selection, Copy and Search with ⟨engine⟩, and Select All last. Below them: Print… and Copy Page Title, then View
 * Source and Inspect Element. A separator parts each group from the next. Each item's id is what the menu reports when
 * it is chosen, and its action. It names no Chromium type.
 *
 * @param leading the items above Jasper's pane items
 * @param trailing the items below them
 */
record PageMenuItems(List<MenuItem> leading, List<MenuItem> trailing) {
    static final String BACK = "page-menu.back", FORWARD = "page-menu.forward", RELOAD = "page-menu.reload",
        OPEN_LINK = "page-menu.open-link", COPY_LINK = "page-menu.copy-link", OPEN_IMAGE = "page-menu.open-image",
        COPY_IMAGE_ADDRESS = "page-menu.copy-image-address", CUT = "page-menu.cut", COPY = "page-menu.copy",
        PASTE = "page-menu.paste", SEARCH = "page-menu.search", SELECT_ALL = "page-menu.select-all", PRINT = "page-menu.print",
        COPY_TITLE = "page-menu.copy-title", VIEW_SOURCE = "page-menu.view-source", INSPECT_ELEMENT = "page-menu.inspect-element";
    // The separators, each named by the group it comes before.
    private static final String BEFORE_LINK = "page-menu.before-link", BEFORE_IMAGE = "page-menu.before-image",
        BEFORE_EDITING = "page-menu.before-editing", BEFORE_TOOLS = "page-menu.before-tools";
    // The schemes the address field loads, which are those a link or image may open in a new tab (4a's R36).
    private static final Set<String> SCHEMES = Set.of("http", "https", "file", "about", "jasper-about");
    private static final Pattern SCHEME = Pattern.compile("([A-Za-z][A-Za-z0-9+.-]*):.*", Pattern.DOTALL);
    private static final Pattern WHITE_SPACE = Pattern.compile("(?U)\\s+");
    private static final List<MenuItem> TRAILING = List.of(item(PRINT, "Print…", true), item(COPY_TITLE, "Copy Page Title", true),
        separator(BEFORE_TOOLS), item(VIEW_SOURCE, "View Source", true), item(INSPECT_ELEMENT, "Inspect Element", true));

    /** Copies the lists. */
    PageMenuItems {
        leading = List.copyOf(leading);
        trailing = List.copyOf(trailing);
    }

    /**
     * What is under the pointer, and where the page can go.
     *
     * @param canBack whether the page can go back
     * @param canForward whether it can go forward
     * @param link the address of the link under the pointer; empty, or an empty address, for none
     * @param image the address of the image under the pointer; empty, or an empty address, for none
     * @param selection the selected text; empty, or empty text, for none
     * @param editable whether the pointer is in an editable field
     * @param engineName the search engine's name, as Search with says it
     */
    record Context(boolean canBack, boolean canForward, Optional<String> link, Optional<String> image, Optional<String> selection,
                   boolean editable, String engineName) {
        /** Rejects nulls; an empty address or selection is none. */
        Context {
            link = present(link, "link");
            image = present(image, "image");
            selection = present(selection, "selection");
            Objects.requireNonNull(engineName, "engineName");
        }

        private static Optional<String> present(Optional<String> value, String name) {
            return Objects.requireNonNull(value, name).filter(text -> !text.isEmpty());
        }
    }

    /**
     * The menu for {@code context}. Back and Forward are disabled where the page cannot go, Open in New Tab for an
     * address the address field does not load, and an editable field's Cut and Copy without a selection; Search with
     * shows only for a selection that is not all white space.
     *
     * @param context what is under the pointer
     * @return the items above and below Jasper's pane items
     */
    static PageMenuItems of(Context context) {
        var leading = new ArrayList<MenuItem>();
        leading.add(item(BACK, "Back", context.canBack()));
        leading.add(item(FORWARD, "Forward", context.canForward()));
        leading.add(item(RELOAD, "Reload", true));
        context.link().ifPresent(link -> {
            leading.add(separator(BEFORE_LINK));
            leading.add(item(OPEN_LINK, "Open Link in New Tab", opens(link)));
            leading.add(item(COPY_LINK, "Copy Link", true));
        });
        context.image().ifPresent(image -> {
            leading.add(separator(BEFORE_IMAGE));
            leading.add(item(OPEN_IMAGE, "Open Image in New Tab", opens(image)));
            leading.add(item(COPY_IMAGE_ADDRESS, "Copy Image Address", true));
        });
        leading.add(separator(BEFORE_EDITING));
        Optional<String> selection = context.selection();
        if (context.editable()) {
            leading.add(item(CUT, "Cut", selection.isPresent()));
            leading.add(item(COPY, "Copy", selection.isPresent()));
            leading.add(item(PASTE, "Paste", true));
        } else if (selection.isPresent()) {
            leading.add(item(COPY, "Copy", true));
            if (!searchTerms(selection.get()).isEmpty()) leading.add(item(SEARCH, "Search with " + context.engineName(), true));
        }
        leading.add(item(SELECT_ALL, "Select All", true));
        return new PageMenuItems(leading, TRAILING);
    }

    /**
     * Whether Open Link or Open Image in New Tab opens {@code address}: its scheme is one the address field loads
     * ({@code http}, {@code https}, {@code file}, {@code about} or {@code jasper-about}), as for a page's links (R36).
     *
     * @param address the link's or image's address, as Chromium gave it
     * @return whether it opens
     */
    static boolean opens(String address) {
        Matcher scheme = SCHEME.matcher(address);
        return scheme.matches() && SCHEMES.contains(scheme.group(1).toLowerCase(Locale.ROOT));
    }

    /**
     * What Search with searches for: the selection with each run of white space one space, and none at its ends.
     *
     * @param selection the selected text
     * @return the terms; empty for a selection of only white space
     */
    static String searchTerms(String selection) {
        return WHITE_SPACE.matcher(selection).replaceAll(" ").strip();
    }

    private static MenuItem item(String id, String label, boolean enabled) {
        return new MenuItem(id, label, "", id, enabled, false);
    }

    private static MenuItem separator(String id) {
        return new MenuItem(id, "", "", "", true, true);
    }
}
