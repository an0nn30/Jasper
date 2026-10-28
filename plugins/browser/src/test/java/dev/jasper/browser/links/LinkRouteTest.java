package dev.jasper.browser.links;

import dev.jasper.browser.links.LinkGesture.Where;
import dev.jasper.browser.page.LastPress;
import dev.jasper.sdk.ui.TabPlacement;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Whether a page's link or popup opens, and where (spec section 8): only after a press or key Jasper forwarded to the
 * page within the 5 seconds of Chromium's user activation, only at an address the address field would load, and a split
 * to the right of a pane wider than tall, otherwise below it. Pure, so no Chromium is needed.
 */
class LinkRouteTest {
    private static final long NOW = 1_000_000;
    private static final String WEB = "https://example.com/next";
    private static final LastPress CLICK = press(0, NOW - 100), CMD_CLICK = press(InputEvent.META_DOWN_MASK, NOW - 100),
        CMD_SHIFT_CLICK = press(InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, NOW - 100);
    private static final long NO_KEY = Long.MIN_VALUE;

    @Test void aPressOrAKeyActivatesThePageForFiveSeconds() {
        assertThat(LinkRoute.activated(press(0, NOW), NO_KEY, NOW)).as("a press now").isTrue();
        assertThat(LinkRoute.activated(press(0, NOW - 5_000), NO_KEY, NOW)).as("a press 5,000 ms ago").isTrue();
        assertThat(LinkRoute.activated(press(0, NOW - 5_001), NO_KEY, NOW)).as("a press 5,001 ms ago").isFalse();
        assertThat(LinkRoute.activated(LastPress.NONE, NOW, NOW)).as("a key now").isTrue();
        assertThat(LinkRoute.activated(LastPress.NONE, NOW - 5_000, NOW)).as("a key 5,000 ms ago").isTrue();
        assertThat(LinkRoute.activated(LastPress.NONE, NOW - 5_001, NOW)).as("a key 5,001 ms ago").isFalse();
        assertThat(LinkRoute.activated(press(0, NOW - 9_000), NOW - 1_000, NOW)).as("an old press and a new key").isTrue();
        assertThat(LinkRoute.activated(LastPress.NONE, NO_KEY, NOW)).as("neither").isFalse();
        assertThat(LinkRoute.activated(press(0, NOW + 1), NOW + 1, NOW)).as("only after now").isFalse();
    }

    @Test void onlyTheAddressFieldsSchemesOpen() {
        for (String url : new String[] {"http://example.com", "https://example.com/a?b#c", "HTTPS://EXAMPLE.COM", "file:///tmp/a.html",
                "about:blank", "about:blank#blocked", "jasper-about:welcome"})
            assertThat(LinkRoute.opens(url)).as(url).isTrue();
        for (String url : new String[] {"mailto:someone@example.com", "tel:+15555550100", "javascript:alert(1)", "data:text/html,hi",
                "blob:https://example.com/0f", "chrome://settings", "ftp://example.com", "", "example.com", ":nothing", "https"})
            assertThat(LinkRoute.opens(url)).as(url).isFalse();
        assertThat(LinkRoute.opens(null)).isFalse();
    }

    @Test void aSplitGoesRightOfAPaneWiderThanTallAndBelowAnyOther() {
        assertThat(LinkRoute.split(800, 600)).isEqualTo(TabPlacement.SPLIT_RIGHT);
        assertThat(LinkRoute.split(601, 600)).isEqualTo(TabPlacement.SPLIT_RIGHT);
        assertThat(LinkRoute.split(600, 600)).as("square").isEqualTo(TabPlacement.SPLIT_DOWN);
        assertThat(LinkRoute.split(600, 800)).isEqualTo(TabPlacement.SPLIT_DOWN);
        assertThat(LinkRoute.split(0, 0)).as("not laid out").isEqualTo(TabPlacement.SPLIT_DOWN);
    }

    /** {@code onOpenURLFromTab}: a modified click Chromium would open elsewhere goes where the press says. */
    @Test void aLinkGoesWhereThePressThatActivatedThePageSays() {
        assertThat(link(WEB, CMD_CLICK, NO_KEY)).contains(Where.NEXT_TAB);
        assertThat(link(WEB, CMD_SHIFT_CLICK, NO_KEY)).contains(Where.SPLIT);
        assertThat(link(WEB, press(InputEvent.SHIFT_DOWN_MASK, NOW - 100), NO_KEY)).as("Shift-click").contains(Where.SAME_ITEM);
        assertThat(link(WEB, press(InputEvent.ALT_DOWN_MASK, NOW - 100), NO_KEY)).as("Alt-click").contains(Where.SAME_ITEM);
        assertThat(link(WEB, new LastPress(MouseEvent.BUTTON2, InputEvent.BUTTON2_DOWN_MASK, NOW - 100), NO_KEY)).as("middle click")
            .contains(Where.NEXT_TAB);
    }

    @Test void aLinkAfterOnlyAKeyLoadsInTheSameItemAndAStalePressDecidesNothing() {
        assertThat(link(WEB, LastPress.NONE, NOW - 100)).contains(Where.SAME_ITEM);
        assertThat(link(WEB, press(InputEvent.META_DOWN_MASK, NOW - 9_000), NOW - 100)).as("a Cmd-click 9 s ago, a key now")
            .contains(Where.SAME_ITEM);
    }

    @Test void aLinkWithoutActivationOrAtAnotherSchemeOpensNothing() {
        assertThat(link(WEB, press(InputEvent.META_DOWN_MASK, NOW - 5_001), NO_KEY)).as("a Cmd-click 5,001 ms ago").isEmpty();
        assertThat(link(WEB, LastPress.NONE, NO_KEY)).as("no press or key").isEmpty();
        assertThat(link("mailto:someone@example.com", CMD_CLICK, NO_KEY)).isEmpty();
        assertThat(link("javascript:alert(1)", CLICK, NOW)).isEmpty();
    }

    /** {@code onBeforePopup}: a popup the user activated opens a new tab, or a split after Cmd+Shift-click. */
    @Test void aPopupTheUserActivatedOpensANextTabOrASplit() {
        assertThat(popup(WEB, CLICK, NO_KEY)).as("a target=_blank link or window.open after a click").contains(Where.NEXT_TAB);
        assertThat(popup(WEB, CMD_CLICK, NO_KEY)).contains(Where.NEXT_TAB);
        assertThat(popup(WEB, press(InputEvent.SHIFT_DOWN_MASK, NOW - 100), NO_KEY)).as("Shift-click").contains(Where.NEXT_TAB);
        assertThat(popup(WEB, CMD_SHIFT_CLICK, NO_KEY)).contains(Where.SPLIT);
        assertThat(popup(WEB, LastPress.NONE, NOW - 100)).as("after a key").contains(Where.NEXT_TAB);
        assertThat(popup(WEB, press(InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, NOW - 9_000), NOW - 100))
            .as("a Cmd+Shift-click 9 s ago, a key now").contains(Where.NEXT_TAB);
    }

    @Test void aPopupWithoutActivationOrAtAnotherSchemeOpensNothing() {
        assertThat(popup(WEB, press(0, NOW - 5_001), NO_KEY)).isEmpty();
        assertThat(popup(WEB, LastPress.NONE, NOW - 5_001)).isEmpty();
        assertThat(popup(WEB, LastPress.NONE, NO_KEY)).isEmpty();
        assertThat(popup("", CLICK, NO_KEY)).as("window.open() with no address").isEmpty();
        assertThat(popup("data:text/html,hi", CLICK, NO_KEY)).isEmpty();
    }

    @Test void theMenuKeyIsCtrlOutsideMacOs() {
        var ctrlShift = press(InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, NOW - 100);
        assertThat(LinkRoute.link(WEB, ctrlShift, NO_KEY, NOW, false)).contains(Where.SPLIT);
        assertThat(LinkRoute.popup(WEB, ctrlShift, NO_KEY, NOW, false)).contains(Where.SPLIT);
        assertThat(LinkRoute.link(WEB, ctrlShift, NO_KEY, NOW, true)).as("Ctrl+Shift on macOS").contains(Where.SAME_ITEM);
    }

    private static Optional<Where> link(String url, LastPress press, long key) {
        return LinkRoute.link(url, press, key, NOW, true);
    }

    private static Optional<Where> popup(String url, LastPress press, long key) {
        return LinkRoute.popup(url, press, key, NOW, true);
    }

    /** A left press at {@code when} with {@code keys} held. */
    private static LastPress press(int keys, long when) {
        return new LastPress(MouseEvent.BUTTON1, keys | InputEvent.BUTTON1_DOWN_MASK, when);
    }
}
