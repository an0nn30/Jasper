package dev.jasper.browser.links;

import dev.jasper.browser.links.LinkGesture.Where;
import dev.jasper.browser.page.Activation;
import dev.jasper.browser.page.LastPress;
import dev.jasper.sdk.ui.TabPlacement;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Whether a page's link or popup opens, and where (spec section 8): only at an address the address field would load; a
 * link always goes somewhere, and a popup only while the page's activation holds, which it consumes; a split goes to the
 * right of a pane wider than tall, otherwise below it. No Chromium is needed.
 */
class LinkRouteTest {
    private static final long NOW = 1_000_000;
    private static final String WEB = "https://example.com/next";
    private static final LastPress CLICK = press(0, NOW - 100), CMD_CLICK = press(InputEvent.META_DOWN_MASK, NOW - 100),
        CMD_SHIFT_CLICK = press(InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, NOW - 100);

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
        for (boolean gesture : new boolean[] {true, false}) {
            assertThat(link(WEB, CMD_CLICK, gesture)).contains(Where.NEXT_TAB);
            assertThat(link(WEB, CMD_SHIFT_CLICK, gesture)).contains(Where.SPLIT);
            assertThat(link(WEB, press(InputEvent.SHIFT_DOWN_MASK, NOW - 100), gesture)).as("Shift-click").contains(Where.SAME_ITEM);
            assertThat(link(WEB, press(InputEvent.ALT_DOWN_MASK, NOW - 100), gesture)).as("Alt-click").contains(Where.SAME_ITEM);
            assertThat(link(WEB, new LastPress(MouseEvent.BUTTON2, InputEvent.BUTTON2_DOWN_MASK, NOW - 100), gesture)).as("middle click")
                .contains(Where.NEXT_TAB);
        }
    }

    /** A click held longer than the activation, or a renderer that was busy as long: Chromium still reports the gesture. */
    @Test void aLinkChromiumReportsAsTheUsersGoesWhereAnOlderPressSays() {
        assertThat(link(WEB, press(InputEvent.META_DOWN_MASK, NOW - 9_000), true)).contains(Where.NEXT_TAB);
        assertThat(link(WEB, press(InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, NOW - 9_000), true)).contains(Where.SPLIT);
    }

    /**
     * A link with neither the press's activation nor the user's gesture (a dropped link, a navigation to or from a
     * {@code file:} page) still goes somewhere: into the same item, whatever an older press said.
     */
    @Test void aLinkWithoutActivationOrGestureLoadsInTheSameItem() {
        assertThat(link(WEB, press(InputEvent.META_DOWN_MASK, NOW - 5_001), false)).as("a Cmd-click 5,001 ms ago")
            .contains(Where.SAME_ITEM);
        assertThat(link(WEB, LastPress.NONE, false)).as("no press").contains(Where.SAME_ITEM);
        assertThat(link("file:///tmp/dropped.html", LastPress.NONE, false)).contains(Where.SAME_ITEM);
    }

    @Test void aLinkAtAnotherSchemeOpensNothing() {
        for (boolean gesture : new boolean[] {true, false}) {
            assertThat(link("mailto:someone@example.com", CMD_CLICK, gesture)).isEmpty();
            assertThat(link("javascript:alert(1)", CLICK, gesture)).isEmpty();
            assertThat(link("data:text/html,hi", LastPress.NONE, gesture)).isEmpty();
        }
    }

    /** {@code onBeforePopup}: a popup the user activated opens a new tab, or a split after Cmd+Shift-click. */
    @Test void aPopupTheUserActivatedOpensANextTabOrASplit() {
        assertThat(popup(WEB, CLICK, activatedAt(NOW - 100))).as("a target=_blank link or window.open after a click")
            .contains(Where.NEXT_TAB);
        assertThat(popup(WEB, CMD_CLICK, activatedAt(NOW - 100))).contains(Where.NEXT_TAB);
        assertThat(popup(WEB, press(InputEvent.SHIFT_DOWN_MASK, NOW - 100), activatedAt(NOW - 100))).as("Shift-click")
            .contains(Where.NEXT_TAB);
        assertThat(popup(WEB, CMD_SHIFT_CLICK, activatedAt(NOW - 100))).contains(Where.SPLIT);
        assertThat(popup(WEB, LastPress.NONE, activatedAt(NOW - 100))).as("after a key").contains(Where.NEXT_TAB);
        assertThat(popup(WEB, press(InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, NOW - 9_000), activatedAt(NOW - 100)))
            .as("a Cmd+Shift-click 9 s ago, a key now").contains(Where.NEXT_TAB);
    }

    @Test void aPopupWithoutActivationOrAtAnotherSchemeOpensNothing() {
        assertThat(popup(WEB, press(0, NOW - 5_001), activatedAt(NOW - 5_001))).isEmpty();
        assertThat(popup(WEB, LastPress.NONE, new Activation())).isEmpty();
        assertThat(popup("", CLICK, activatedAt(NOW - 100))).as("window.open() with no address").isEmpty();
        assertThat(popup("data:text/html,hi", CLICK, activatedAt(NOW - 100))).isEmpty();
    }

    /** One gesture, one popup: the popup that opens consumes the activation, and the next press or key brings it back. */
    @Test void eachPressOrKeyOpensOnePopup() {
        var activation = activatedAt(NOW - 100);
        assertThat(popup(WEB, CLICK, activation)).contains(Where.NEXT_TAB);
        assertThat(popup(WEB, CLICK, activation)).as("a second popup after the same press").isEmpty();
        assertThat(popup(WEB, CLICK, activation)).as("and a third").isEmpty();
        activation.activate(NOW - 10);
        assertThat(popup(WEB, CLICK, activation)).as("after a new press").contains(Where.NEXT_TAB);
        assertThat(popup(WEB, CLICK, activation)).isEmpty();
    }

    @Test void aPopupAtAnotherSchemeConsumesNothing() {
        var activation = activatedAt(NOW - 100);
        assertThat(popup("mailto:someone@example.com", CLICK, activation)).isEmpty();
        assertThat(activation.active(NOW)).isTrue();
        assertThat(popup(WEB, CLICK, activation)).contains(Where.NEXT_TAB);
    }

    @Test void theMenuKeyIsCtrlOutsideMacOs() {
        var ctrlShift = press(InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, NOW - 100);
        assertThat(LinkRoute.link(WEB, ctrlShift, true, NOW, false)).contains(Where.SPLIT);
        assertThat(LinkRoute.popup(WEB, ctrlShift, activatedAt(NOW - 100), NOW, false)).contains(Where.SPLIT);
        assertThat(LinkRoute.link(WEB, ctrlShift, true, NOW, true)).as("Ctrl+Shift on macOS").contains(Where.SAME_ITEM);
    }

    private static Optional<Where> link(String url, LastPress press, boolean userGesture) {
        return LinkRoute.link(url, press, userGesture, NOW, true);
    }

    private static Optional<Where> popup(String url, LastPress press, Activation activation) {
        return LinkRoute.popup(url, press, activation, NOW, true);
    }

    private static Activation activatedAt(long when) {
        var activation = new Activation();
        activation.activate(when);
        return activation;
    }

    /** A left press at {@code when} with {@code keys} held. */
    private static LastPress press(int keys, long when) {
        return new LastPress(MouseEvent.BUTTON1, keys | InputEvent.BUTTON1_DOWN_MASK, when);
    }
}
