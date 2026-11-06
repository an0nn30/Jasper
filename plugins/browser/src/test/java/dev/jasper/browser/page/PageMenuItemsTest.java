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
 * pane items, Back, Forward and Reload over a bare page only, as in Chrome (P38), the link's items, the image's, then the
 * editing or selection items with Select All last; below them, Print… and Copy Page Title, then View Source and Inspect
 * Element, a separator between groups. Pure: no Chromium, no host.
 */
class PageMenuItemsTest {
    private static final Optional<String> NONE = Optional.empty();
    private static final String PAGE = "https://example.com/";
    private static final List<String> TRAILING = List.of("page-menu.print Print…", "page-menu.copy-title Copy Page Title", "---",
        "page-menu.view-source View Source", "page-menu.inspect-element Inspect Element");

    @Test void aBarePageGivesBackForwardAndReloadThenSelectAllAboveAndThePageItemsBelow() {
        var menu = PageMenuItems.of(context(true, false, NONE, NONE, NONE, false));
        assertThat(lines(menu.leading())).containsExactly("page-menu.back Back", "page-menu.forward Forward (disabled)",
            "page-menu.reload Reload", "---", "page-menu.select-all Select All");
        assertThat(lines(menu.trailing())).isEqualTo(TRAILING);
    }

    /** P38: over a link, an image or a selection Back, Forward and Reload are left out, and each adds its group in order. */
    @Test void aLinkAnImageAndASelectionEachAddTheirGroupInOrderWithoutBackForwardAndReload() {
        var menu = PageMenuItems.of(new Context(false, true, Optional.of("https://example.com/next"),
            Optional.of("https://example.com/cat.png"), Optional.of("two words"), false, "DuckDuckGo", PAGE, true));
        assertThat(lines(menu.leading())).containsExactly(
            "page-menu.open-link Open Link in New Tab", "page-menu.copy-link Copy Link", "---",
            "page-menu.open-image Open Image in New Tab", "page-menu.copy-image-address Copy Image Address", "---",
            "page-menu.copy Copy", "page-menu.search Search with DuckDuckGo", "page-menu.select-all Select All");
        assertThat(lines(menu.trailing())).isEqualTo(TRAILING);
    }

    /** In a field, Cut and Copy need a selection, as in Chrome; Paste and Select All are always there. No search. */
    @Test void anEditableFieldGivesCutCopyAndPasteThenSelectAllAndNoSearch() {
        var empty = PageMenuItems.of(context(true, true, NONE, NONE, NONE, true));
        assertThat(lines(empty.leading())).containsExactly("page-menu.cut Cut (disabled)", "page-menu.copy Copy (disabled)",
            "page-menu.paste Paste", "page-menu.select-all Select All");
        var selected = PageMenuItems.of(context(false, false, NONE, NONE, Optional.of("typed"), true));
        assertThat(lines(selected.leading())).containsExactly("page-menu.cut Cut", "page-menu.copy Copy", "page-menu.paste Paste",
            "page-menu.select-all Select All");
    }

    @Test void searchWithNamesTheEngineItIsGiven() {
        for (String engine : List.of("Google", "Bing", "Kagi", "search.example.com", "Custom"))
            assertThat(lines(PageMenuItems.of(new Context(true, true, NONE, NONE, Optional.of("jcef"), false, engine, PAGE, true)).leading()))
                .contains("page-menu.search Search with " + engine);
    }

    /** A selection of only white space can be copied, but there is nothing to search for. */
    @Test void aBlankSelectionCanBeCopiedButNotSearched() {
        var menu = PageMenuItems.of(context(true, true, NONE, NONE, Optional.of(" \n\t\u00A0"), false));
        assertThat(lines(menu.leading())).containsExactly("page-menu.copy Copy", "page-menu.select-all Select All");
    }

    /**
     * R36 and 4a's opener rule: Open Link and Open Image in New Tab open, with Jasper's rights, only what the frame the
     * menu is over could open itself: http and https from any page, about: only as about:blank, file and jasper-about only
     * from a page of the same scheme, and nothing else. Copying works for any address.
     */
    @Test void openInNewTabOpensOnlyWhatTheFrameItIsOverCouldOpenItself() {
        for (String address : List.of("https://example.com", "HTTP://example.com", "about:blank", "about:blank#top"))
            assertThat(opens(PAGE, address)).as("from %s: %s", PAGE, address).isTrue();
        for (String address : List.of("file:///etc/hosts", "jasper-about:welcome", "about:version", "javascript:alert(1)",
            "mailto:ada@example.com", "data:image/png;base64,AAAA", "blob:https://example.com/1", "chrome://settings",
            "ftp://example.com", "relative/path", ""))
            assertThat(opens(PAGE, address)).as("from %s: %s", PAGE, address).isFalse();
        assertThat(opens("file:///Users/ada/index.html", "file:///Users/ada/next.html")).isTrue();
        assertThat(opens("file:///Users/ada/index.html", "jasper-about:welcome")).isFalse();
        assertThat(opens("jasper-about:welcome", "jasper-about:settings")).isTrue();
        assertThat(opens("jasper-about:welcome", "file:///etc/hosts")).isFalse();
        assertThat(opens("jasper-about:welcome", "https://example.com")).isTrue();
    }

    @Test void searchTermsAreTheSelectionWithItsWhiteSpaceCollapsedAndCutToAThousandCharacters() {
        assertThat(PageMenuItems.searchTerms("  two\n\twords\u00A0 here ")).isEqualTo("two words here");
        assertThat(PageMenuItems.searchTerms(" \n ")).isEmpty();
        assertThat(PageMenuItems.searchTerms("a".repeat(5_000))).isEqualTo("a".repeat(1_000));
        assertThat(PageMenuItems.searchTerms("a".repeat(999) + " b")).as("the cut's end space goes").isEqualTo("a".repeat(999));
        // A character outside the BMP that would straddle the cut is left out whole, not split into a lone surrogate.
        String straddling = PageMenuItems.searchTerms("a".repeat(999) + "\uD83D\uDE00" + "b");
        assertThat(straddling).isEqualTo("a".repeat(999));
        assertThat(PageMenuItems.searchTerms("a".repeat(998) + "\uD83D\uDE00" + "b")).isEqualTo("a".repeat(998) + "\uD83D\uDE00");
    }

    /** P39: Copy Page Title is disabled while the page has no title. */
    @Test void copyPageTitleIsDisabledOnAnUntitledPage() {
        var menu = PageMenuItems.of(new Context(true, true, NONE, NONE, NONE, false, "Google", PAGE, false));
        assertThat(lines(menu.trailing())).containsExactly("page-menu.print Print…", "page-menu.copy-title Copy Page Title (disabled)",
            "---", "page-menu.view-source View Source", "page-menu.inspect-element Inspect Element");
    }

    /**
     * Every combination of what can be under the pointer: Back, Forward and Reload come first over a bare page only; the
     * link's group shows only with a link, the image's only with an image; the editing or selection group comes last
     * above, with Select All last; a separator parts each group from the next, and none starts or ends a list or follows
     * another; the ids are unique; and the items below never change for a titled page.
     */
    @Test void everyContextGivesItsGroupsInOrderWithSeparatorsBetweenThem() {
        for (int bits = 0; bits < 1 << 6; bits++) {
            boolean back = (bits & 1) != 0, forward = (bits & 2) != 0, editable = (bits & 32) != 0;
            Optional<String> link = (bits & 4) != 0 ? Optional.of("https://example.com/link") : NONE;
            Optional<String> image = (bits & 8) != 0 ? Optional.of("https://example.com/image.png") : NONE;
            Optional<String> selection = (bits & 16) != 0 ? Optional.of("selected") : NONE;
            var context = context(back, forward, link, image, selection, editable);
            var menu = PageMenuItems.of(context);
            String as = context.toString();

            var expected = new ArrayList<List<String>>();
            if (link.isEmpty() && image.isEmpty() && selection.isEmpty() && !editable)
                expected.add(List.of("page-menu.back Back" + (back ? "" : " (disabled)"),
                    "page-menu.forward Forward" + (forward ? "" : " (disabled)"), "page-menu.reload Reload"));
            if (link.isPresent()) expected.add(List.of("page-menu.open-link Open Link in New Tab", "page-menu.copy-link Copy Link"));
            if (image.isPresent())
                expected.add(List.of("page-menu.open-image Open Image in New Tab", "page-menu.copy-image-address Copy Image Address"));
            String cutAndCopy = selection.isPresent() ? "" : " (disabled)";
            if (editable) expected.add(List.of("page-menu.cut Cut" + cutAndCopy, "page-menu.copy Copy" + cutAndCopy, "page-menu.paste Paste",
                "page-menu.select-all Select All"));
            else if (selection.isPresent())
                expected.add(List.of("page-menu.copy Copy", "page-menu.search Search with Google", "page-menu.select-all Select All"));
            else expected.add(List.of("page-menu.select-all Select All"));
            assertThat(groups(menu.leading())).as(as).isEqualTo(expected);

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
        for (var context : List.of(context(true, true, NONE, NONE, NONE, false), context(true, true, Optional.of("https://example.com"),
            Optional.of("https://example.com/a.png"), Optional.of("text"), false), context(true, true, NONE, NONE, NONE, true))) {
            var menu = PageMenuItems.of(context);
            var all = new ArrayList<>(menu.leading());
            all.addAll(menu.trailing());
            for (MenuItem item : all) {
                if (!item.separator()) assertThat(item.action()).isEqualTo(item.id());
                assertThat(item.icon()).isEmpty();
                assertThat(item.shortcut()).isEmpty();
                assertThat(item.children()).isEmpty();
            }
        }
    }

    @Test void aContextWithEmptyAddressesOrSelectionHasNone() {
        var context = context(true, true, Optional.of(""), Optional.of(""), Optional.of(""), false);
        assertThat(context.link()).isEmpty();
        assertThat(context.image()).isEmpty();
        assertThat(context.selection()).isEmpty();
        assertThat(lines(PageMenuItems.of(context).leading())).containsExactly("page-menu.back Back", "page-menu.forward Forward",
            "page-menu.reload Reload", "---", "page-menu.select-all Select All");
    }

    /** A titled page's context over {@link #PAGE}, searching with Google. */
    private static Context context(boolean canBack, boolean canForward, Optional<String> link, Optional<String> image,
                                   Optional<String> selection, boolean editable) {
        return new Context(canBack, canForward, link, image, selection, editable, "Google", PAGE, true);
    }

    /** Whether Open Link in New Tab is enabled for a link to {@code address} in a frame at {@code frame}. */
    private static boolean opens(String frame, String address) {
        var link = PageMenuItems.of(new Context(true, true, Optional.of(address), NONE, NONE, false, "Google", frame, true))
            .leading().stream().filter(item -> item.id().equals(PageMenuItems.OPEN_LINK)).findFirst();
        var image = PageMenuItems.of(new Context(true, true, NONE, Optional.of(address), NONE, false, "Google", frame, true))
            .leading().stream().filter(item -> item.id().equals(PageMenuItems.OPEN_IMAGE)).findFirst();
        if (address.isEmpty()) {
            assertThat(link).as("an empty address is none").isEmpty();
            return false;
        }
        assertThat(image.orElseThrow().enabled()).as("Open Image follows the same rule").isEqualTo(link.orElseThrow().enabled());
        assertThat(LinkSchemes.opensFrom(frame, address)).as("the shared rule").isEqualTo(link.orElseThrow().enabled());
        return link.orElseThrow().enabled();
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
