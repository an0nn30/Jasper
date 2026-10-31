package dev.jasper.browser.page;

import dev.jasper.browser.page.PageMenuItems.Context;
import dev.jasper.sdk.ui.MenuItem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The page's right-click menu for what is under the pointer (spec section 11), as the SDK's menu items: above Jasper's
 * pane items, Back, Forward and Reload, the link's items, the image's, then the editing or selection items with Select
 * All last; below them, Print… and Copy Page Title, then View Source and Inspect Element, a separator between groups.
 * Pure: no Chromium, no host.
 */
class PageMenuItemsTest {
    private static final Optional<String> NONE = Optional.empty();
    private static final List<String> TRAILING = List.of("page-menu.print Print…", "page-menu.copy-title Copy Page Title", "---",
        "page-menu.view-source View Source", "page-menu.inspect-element Inspect Element");

    @Test void aPlainPageGivesBackForwardAndReloadThenSelectAllAboveAndThePageItemsBelow() {
        var menu = PageMenuItems.of(new Context(true, false, NONE, NONE, NONE, false, "Google"));
        assertThat(lines(menu.leading())).containsExactly("page-menu.back Back", "page-menu.forward Forward (disabled)",
            "page-menu.reload Reload", "---", "page-menu.select-all Select All");
        assertThat(lines(menu.trailing())).isEqualTo(TRAILING);
    }

    @Test void aLinkAnImageAndASelectionEachAddTheirGroupInOrder() {
        var menu = PageMenuItems.of(new Context(false, true, Optional.of("https://example.com/next"),
            Optional.of("https://example.com/cat.png"), Optional.of("two words"), false, "DuckDuckGo"));
        assertThat(lines(menu.leading())).containsExactly("page-menu.back Back (disabled)", "page-menu.forward Forward",
            "page-menu.reload Reload", "---",
            "page-menu.open-link Open Link in New Tab", "page-menu.copy-link Copy Link", "---",
            "page-menu.open-image Open Image in New Tab", "page-menu.copy-image-address Copy Image Address", "---",
            "page-menu.copy Copy", "page-menu.search Search with DuckDuckGo", "page-menu.select-all Select All");
        assertThat(lines(menu.trailing())).isEqualTo(TRAILING);
    }

    /** In a field, Cut and Copy need a selection, as in Chrome; Paste and Select All are always there. No search. */
    @Test void anEditableFieldGivesCutCopyAndPasteThenSelectAllAndNoSearch() {
        var empty = PageMenuItems.of(new Context(false, false, NONE, NONE, NONE, true, "Google"));
        assertThat(lines(empty.leading())).containsExactly("page-menu.back Back (disabled)", "page-menu.forward Forward (disabled)",
            "page-menu.reload Reload", "---", "page-menu.cut Cut (disabled)", "page-menu.copy Copy (disabled)",
            "page-menu.paste Paste", "page-menu.select-all Select All");
        var selected = PageMenuItems.of(new Context(false, false, NONE, NONE, Optional.of("typed"), true, "Google"));
        assertThat(lines(selected.leading())).endsWith("---", "page-menu.cut Cut", "page-menu.copy Copy", "page-menu.paste Paste",
            "page-menu.select-all Select All");
    }

    @Test void searchWithNamesTheEngineItIsGiven() {
        for (String engine : List.of("Google", "Bing", "Kagi", "search.example.com", "Custom"))
            assertThat(lines(PageMenuItems.of(new Context(true, true, NONE, NONE, Optional.of("jcef"), false, engine)).leading()))
                .contains("page-menu.search Search with " + engine);
    }

    /** A selection of only white space can be copied, but there is nothing to search for. */
    @Test void aBlankSelectionCanBeCopiedButNotSearched() {
        var menu = PageMenuItems.of(new Context(true, true, NONE, NONE, Optional.of(" \n\t "), false, "Google"));
        assertThat(lines(menu.leading())).endsWith("---", "page-menu.copy Copy", "page-menu.select-all Select All");
    }

    /** R36: Open Link and Open Image in New Tab open only what the address field loads; copying works for any address. */
    @Test void openInNewTabIsDisabledForAddressesTheAddressFieldDoesNotLoad() {
        for (String address : List.of("https://example.com", "HTTP://example.com", "file:///tmp/a.html", "about:blank",
            "jasper-about:welcome"))
            assertThat(opens(address)).as(address).isTrue();
        for (String address : List.of("javascript:alert(1)", "mailto:ada@example.com", "data:image/png;base64,AAAA",
            "blob:https://example.com/1", "chrome://settings", "ftp://example.com", "relative/path", ""))
            assertThat(opens(address)).as(address).isFalse();
    }

    @Test void searchTermsAreTheSelectionWithItsWhiteSpaceCollapsed() {
        assertThat(PageMenuItems.searchTerms("  two\n\twords  here ")).isEqualTo("two words here");
        assertThat(PageMenuItems.searchTerms(" \n ")).isEmpty();
    }

    /**
     * Every combination of what can be under the pointer: Back, Forward and Reload come first; the link's group shows
     * only with a link, the image's only with an image, each after a separator; the editing or selection group comes
     * last above, after a separator, with Select All last; no separator starts or ends a list or follows another; the
     * ids are unique; and the items below never change.
     */
    @Test void everyContextGivesItsGroupsInOrderWithSeparatorsBetweenThem() {
        for (int bits = 0; bits < 1 << 6; bits++) {
            boolean back = (bits & 1) != 0, forward = (bits & 2) != 0, editable = (bits & 32) != 0;
            Optional<String> link = (bits & 4) != 0 ? Optional.of("https://example.com/link") : NONE;
            Optional<String> image = (bits & 8) != 0 ? Optional.of("https://example.com/image.png") : NONE;
            Optional<String> selection = (bits & 16) != 0 ? Optional.of("selected") : NONE;
            var context = new Context(back, forward, link, image, selection, editable, "Google");
            var menu = PageMenuItems.of(context);
            String as = context.toString();

            List<List<String>> groups = groups(menu.leading());
            assertThat(groups.getFirst()).as(as).containsExactly("page-menu.back Back" + (back ? "" : " (disabled)"),
                "page-menu.forward Forward" + (forward ? "" : " (disabled)"), "page-menu.reload Reload");
            var expected = new ArrayList<List<String>>();
            expected.add(groups.getFirst());
            if (link.isPresent()) expected.add(List.of("page-menu.open-link Open Link in New Tab", "page-menu.copy-link Copy Link"));
            if (image.isPresent())
                expected.add(List.of("page-menu.open-image Open Image in New Tab", "page-menu.copy-image-address Copy Image Address"));
            String cutAndCopy = selection.isPresent() ? "" : " (disabled)";
            if (editable) expected.add(List.of("page-menu.cut Cut" + cutAndCopy, "page-menu.copy Copy" + cutAndCopy, "page-menu.paste Paste",
                "page-menu.select-all Select All"));
            else if (selection.isPresent())
                expected.add(List.of("page-menu.copy Copy", "page-menu.search Search with Google", "page-menu.select-all Select All"));
            else expected.add(List.of("page-menu.select-all Select All"));
            assertThat(groups).as(as).isEqualTo(expected);

            assertThat(lines(menu.trailing())).as(as).isEqualTo(TRAILING);
            for (List<MenuItem> list : List.of(menu.leading(), menu.trailing())) {
                assertThat(list.getFirst().separator()).as(as).isFalse();
                assertThat(list.getLast().separator()).as(as).isFalse();
                for (int i = 1; i < list.size(); i++)
                    assertThat(list.get(i - 1).separator() && list.get(i).separator()).as(as + " at " + i).isFalse();
            }
            var ids = new HashSet<String>();
            for (MenuItem item : menu.leading()) assertThat(ids.add(item.id())).as(as + " " + item.id()).isTrue();
            for (MenuItem item : menu.trailing()) assertThat(ids.add(item.id())).as(as + " " + item.id()).isTrue();
        }
    }

    /** What the menu reports is the item's id, which is its action too; no item opens a submenu, shows an icon or a key. */
    @Test void eachItemReportsItsIdAndHasNoIconShortcutOrSubmenu() {
        var menu = PageMenuItems.of(new Context(true, true, Optional.of("https://example.com"), Optional.of("https://example.com/a.png"),
            Optional.of("text"), false, "Google"));
        var all = new ArrayList<>(menu.leading());
        all.addAll(menu.trailing());
        for (MenuItem item : all) {
            if (!item.separator()) assertThat(item.action()).isEqualTo(item.id());
            assertThat(item.icon()).isEmpty();
            assertThat(item.shortcut()).isEmpty();
            assertThat(item.children()).isEmpty();
        }
    }

    @Test void aContextWithEmptyAddressesOrSelectionHasNone() {
        var context = new Context(true, true, Optional.of(""), Optional.of(""), Optional.of(""), false, "Google");
        assertThat(context.link()).isEmpty();
        assertThat(context.image()).isEmpty();
        assertThat(context.selection()).isEmpty();
        assertThat(lines(PageMenuItems.of(context).leading())).containsExactly("page-menu.back Back", "page-menu.forward Forward",
            "page-menu.reload Reload", "---", "page-menu.select-all Select All");
    }

    private static boolean opens(String address) {
        return PageMenuItems.opens(address);
    }

    /** Each item as {@code id label}, {@code " (disabled)"} after a disabled one, and {@code ---} for a separator. */
    private static List<String> lines(List<MenuItem> items) {
        return items.stream().map(item -> item.separator() ? "---" : item.id() + " " + item.label() + (item.enabled() ? "" : " (disabled)"))
            .toList();
    }

    /** The lines of {@code items} in groups, split at the separators. */
    private static List<List<String>> groups(List<MenuItem> items) {
        var groups = new ArrayList<List<String>>();
        var group = new ArrayList<String>();
        for (String line : lines(items)) {
            if (line.equals("---")) {
                groups.add(List.copyOf(group));
                group.clear();
            } else group.add(line);
        }
        groups.add(List.copyOf(group));
        return groups;
    }
}
