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
 * Whether a page's link or popup opens, and where (spec section 8): only at an address the address field would load, and
 * a link only at one its frame could open itself (plan 4b's P48); a link always goes somewhere, and a popup only while
 * the page's activation holds, which it consumes; a split goes to the right of a pane wider than tall, otherwise below
 * it. No Chromium is needed.
 */
class LinkRouteTest {
    private static final long NOW = 1_000_000;
    private static final String PAGE = "https://example.com/", WEB = "https://example.com/next";
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
            assertThat(link(WEB, new LastPress(MouseEvent.BUTTON2, InputEvent.BUTTON2_DOWN_MASK, NOW - 100, 0, 0), gesture)).as("middle click")
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
        assertThat(LinkRoute.link("file:///tmp/a.html", "file:///tmp/dropped.html", LastPress.NONE, false, NOW, true))
            .contains(Where.SAME_ITEM);
        assertThat(LinkRoute.link("file:///tmp/a.html", WEB, LastPress.NONE, false, NOW, true)).as("from a file: page")
            .contains(Where.SAME_ITEM);
    }

    @Test void aLinkAtAnotherSchemeOpensNothing() {
        for (boolean gesture : new boolean[] {true, false}) {
            assertThat(link("mailto:someone@example.com", CMD_CLICK, gesture)).isEmpty();
            assertThat(link("javascript:alert(1)", CLICK, gesture)).isEmpty();
            assertThat(link("data:text/html,hi", LastPress.NONE, gesture)).isEmpty();
            assertThat(link("about:version", CMD_CLICK, gesture)).isEmpty();
        }
    }

    /**
     * Plan 4b's P48: Jasper loads the link with its own rights, so it opens only what the frame it came from could open
     * itself (4a's R36): no {@code file:} or {@code jasper-about:} address from a web page, whatever the press.
     */
    @Test void aLinkOpensOnlyWhatItsFrameCouldOpenItself() {
        for (boolean gesture : new boolean[] {true, false}) {
            for (String local : new String[] {"file:///etc/hosts", "jasper-about:settings"}) {
                assertThat(LinkRoute.link(WEB, local, CMD_CLICK, gesture, NOW, true)).as(local).isEmpty();
                assertThat(LinkRoute.link("", local, LastPress.NONE, gesture, NOW, true)).as(local + " from no page").isEmpty();
            }
            assertThat(LinkRoute.link("file:///tmp/a.html", "jasper-about:settings", CLICK, gesture, NOW, true)).isEmpty();
            assertThat(LinkRoute.link("jasper-about:welcome", "jasper-about:settings", CMD_CLICK, gesture, NOW, true))
                .contains(Where.NEXT_TAB);
            assertThat(LinkRoute.link("file:///tmp/a.html", "file:///tmp/b.html", CMD_CLICK, gesture, NOW, true)).contains(Where.NEXT_TAB);
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
        assertThat(LinkRoute.link(PAGE, WEB, ctrlShift, true, NOW, false)).contains(Where.SPLIT);
        assertThat(LinkRoute.popup(WEB, ctrlShift, activatedAt(NOW - 100), NOW, false)).contains(Where.SPLIT);
        assertThat(LinkRoute.link(PAGE, WEB, ctrlShift, true, NOW, true)).as("Ctrl+Shift on macOS").contains(Where.SAME_ITEM);
    }

    /** A link from a web page's frame. */
    private static Optional<Where> link(String url, LastPress press, boolean userGesture) {
        return LinkRoute.link(PAGE, url, press, userGesture, NOW, true);
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
        return new LastPress(MouseEvent.BUTTON1, keys | InputEvent.BUTTON1_DOWN_MASK, when, 0, 0);
    }
}
