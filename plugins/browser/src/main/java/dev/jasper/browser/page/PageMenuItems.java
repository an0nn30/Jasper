package dev.jasper.browser.page;

import dev.jasper.sdk.ui.MenuItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The page's right-click menu (spec section 11) as the SDK's menu items, for what is under the pointer. Above the pane
 * items Jasper adds, in groups: over a bare page, Back, Forward and Reload, as in Chrome; with a link, Open Link in New
 * Tab and Copy Link; with an image, Open Image in New Tab and Copy Image Address; then, in an editable field, Cut, Copy
 * and Paste, or else, with a selection, Copy and Search with ⟨engine⟩, and Select All last. Below them: Print… and Copy
 * Page Title, then View Source and Inspect Element. A separator parts each group from the next. Each item's id is what
 * the menu reports when it is chosen, and its action. It names no Chromium type.
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
    /** The most of a selection Search with searches for, as find cuts its query. */
    static final int LONGEST_SEARCH = 1_000;
    // The separators, each named by the group it comes before.
    private static final String BEFORE_LINK = "page-menu.before-link", BEFORE_IMAGE = "page-menu.before-image",
        BEFORE_EDITING = "page-menu.before-editing", BEFORE_TOOLS = "page-menu.before-tools";
    private static final Pattern WHITE_SPACE = Pattern.compile("(?U)\\s+");

    /** Copies the lists. */
    PageMenuItems {
        leading = List.copyOf(leading);
        trailing = List.copyOf(trailing);
    }

    /**
     * What is under the pointer, where the page can go, and whether it has a title.
     *
     * @param canBack whether the page can go back
     * @param canForward whether it can go forward
     * @param link the address of the link under the pointer; empty, or an empty address, for none
     * @param image the address of the image under the pointer; empty, or an empty address, for none
     * @param selection the selected text; empty, or empty text, for none
     * @param editable whether the pointer is in an editable field
     * @param engineName the search engine's name, as Search with says it
     * @param frame the address of the frame the pointer is in, which decides what its link or image may open
     * @param titled whether the page has a title
     */
    record Context(boolean canBack, boolean canForward, Optional<String> link, Optional<String> image, Optional<String> selection,
                   boolean editable, String engineName, String frame, boolean titled) {
        /** Rejects nulls; an empty address or selection is none. */
        Context {
            link = present(link, "link");
            image = present(image, "image");
            selection = present(selection, "selection");
            Objects.requireNonNull(engineName, "engineName");
            Objects.requireNonNull(frame, "frame");
        }

        /** Whether the pointer is over the page itself: no link, image, selection or editable field. */
        boolean bare() {
            return link.isEmpty() && image.isEmpty() && selection.isEmpty() && !editable;
        }

        private static Optional<String> present(Optional<String> value, String name) {
            return Objects.requireNonNull(value, name).filter(text -> !text.isEmpty());
        }
    }

    /**
     * The menu for {@code context}. Back and Forward are disabled where the page cannot go; Open in New Tab for what
     * the frame could not open itself ({@link LinkSchemes#opensFrom}), as it opens with Jasper's rights; an editable
     * field's Cut and Copy without a selection; and Copy Page Title without a title. Search with shows only for a
     * selection that is not all white space.
     *
     * @param context what is under the pointer
     * @return the items above and below Jasper's pane items
     */
    static PageMenuItems of(Context context) {
        var leading = new ArrayList<MenuItem>();
        if (context.bare()) leading.addAll(List.of(item(BACK, "Back", context.canBack()), item(FORWARD, "Forward", context.canForward()),
            item(RELOAD, "Reload", true)));
        context.link().ifPresent(link -> group(leading, BEFORE_LINK, List.of(
            item(OPEN_LINK, "Open Link in New Tab", LinkSchemes.opensFrom(context.frame(), link)), item(COPY_LINK, "Copy Link", true))));
        context.image().ifPresent(image -> group(leading, BEFORE_IMAGE, List.of(
            item(OPEN_IMAGE, "Open Image in New Tab", LinkSchemes.opensFrom(context.frame(), image)),
            item(COPY_IMAGE_ADDRESS, "Copy Image Address", true))));
        var editing = new ArrayList<MenuItem>();
        Optional<String> selection = context.selection();
        if (context.editable()) {
            editing.add(item(CUT, "Cut", selection.isPresent()));
            editing.add(item(COPY, "Copy", selection.isPresent()));
            editing.add(item(PASTE, "Paste", true));
        } else if (selection.isPresent()) {
            editing.add(item(COPY, "Copy", true));
            if (!searchTerms(selection.get()).isEmpty()) editing.add(item(SEARCH, "Search with " + context.engineName(), true));
        }
        editing.add(item(SELECT_ALL, "Select All", true));
        group(leading, BEFORE_EDITING, editing);
        var trailing = new ArrayList<MenuItem>(List.of(item(PRINT, "Print…", true), item(COPY_TITLE, "Copy Page Title", context.titled())));
        group(trailing, BEFORE_TOOLS, List.of(item(VIEW_SOURCE, "View Source", true), item(INSPECT_ELEMENT, "Inspect Element", true)));
        return new PageMenuItems(leading, trailing);
    }

    /**
     * What Search with searches for: the selection with each run of white space one space and none at its ends, cut to
     * {@value #LONGEST_SEARCH} characters, or one fewer where a character's two halves would part.
     *
     * @param selection the selected text
     * @return the terms; empty for a selection of only white space
     */
    static String searchTerms(String selection) {
        String terms = WHITE_SPACE.matcher(selection).replaceAll(" ").strip();
        if (terms.length() <= LONGEST_SEARCH) return terms;
        int end = Character.isHighSurrogate(terms.charAt(LONGEST_SEARCH - 1)) ? LONGEST_SEARCH - 1 : LONGEST_SEARCH;
        return terms.substring(0, end).strip();
    }

    /** Adds {@code items} to {@code menu}, after the separator {@code separator} when the menu has items already. */
    private static void group(List<MenuItem> menu, String separator, List<MenuItem> items) {
        if (!menu.isEmpty()) menu.add(new MenuItem(separator, "", "", "", true, true));
        menu.addAll(items);
    }

    private static MenuItem item(String id, String label, boolean enabled) {
        return new MenuItem(id, label, "", id, enabled, false);
    }
}
