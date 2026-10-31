package dev.jasper.browser.links;

import dev.jasper.browser.links.LinkGesture.Where;
import dev.jasper.browser.page.Activation;
import dev.jasper.browser.page.LastPress;
import dev.jasper.browser.page.LinkSchemes;
import dev.jasper.sdk.ui.TabPlacement;
import java.util.Optional;

/**
 * Whether a page's link or popup opens, and where (spec section 8), from what Jasper forwarded to the page. Either opens
 * only at an address of a scheme the address field loads ({@code http}, {@code https}, {@code file}, {@code about} and
 * {@code jasper-about}). A link always goes somewhere: where the press that clicked it says, or in the same item. A
 * popup opens only while the page's {@link Activation} holds, and consumes it, so popups without the user's gesture are
 * blocked and each gesture opens one. Chromium's handlers ask it on Chromium's thread.
 */
final class LinkRoute {
    private LinkRoute() { }

    /**
     * Whether a link or popup to {@code url} may open: its scheme is one the address field loads
     * ({@link LinkSchemes#opens}).
     *
     * @param url the address Chromium gave
     * @return whether it opens
     */
    static boolean opens(String url) {
        return LinkSchemes.opens(url);
    }

    /**
     * Whether a page at {@code sender} may open {@code url} itself, as Chromium lets a page's own links and
     * {@code window.open} load it: {@code http}, {@code https} and {@code about:blank} from any page; local addresses,
     * {@code file} and {@code jasper-about}, only from a page of the same scheme; nothing else
     * ({@link LinkSchemes#opensFrom}). The opener bridge and {@link #link} ask this, since they open with Jasper's rights
     * rather than the page's.
     *
     * @param sender the address of the page that asks
     * @param url the address it would open
     * @return whether it may
     */
    static boolean opensFrom(String sender, String url) {
        return LinkSchemes.opensFrom(sender, url);
    }

    /**
     * Where a split from a page of this size opens.
     *
     * @param width the page's width
     * @param height the page's height
     * @return to the right of a page wider than tall, else below it
     */
    static TabPlacement split(int width, int height) {
        return width > height ? TabPlacement.SPLIT_RIGHT : TabPlacement.SPLIT_DOWN;
    }

    /**
     * Where a link Chromium would open elsewhere goes ({@code onOpenURLFromTab}: a modified or middle click, a dropped
     * link, a navigation to or from a {@code file:} page): where the last press says while it still activates the page
     * or Chromium reports the user's gesture, else in the same item. It consumes no activation. Jasper loads it with its
     * own rights, so only an address the frame it came from could open itself goes anywhere (plan 4b's P48,
     * {@link #opensFrom}).
     *
     * @param sender the address of the frame the link came from
     * @param url the address
     * @param press the page's last press
     * @param userGesture whether Chromium reports the navigation as the user's
     * @param nowMillis the time now, in milliseconds since the epoch
     * @param macOs whether the platform is macOS
     * @return where it goes; empty only for an address {@code sender} could not open
     */
    static Optional<Where> link(String sender, String url, LastPress press, boolean userGesture, long nowMillis, boolean macOs) {
        if (!opensFrom(sender, url)) return Optional.empty();
        return Optional.of(userGesture || press.withinActivation(nowMillis) ? LinkGesture.of(press, macOs) : Where.SAME_ITEM);
    }

    /**
     * Where a popup goes ({@code onBeforePopup}: a {@code target=_blank} link, or a script's {@code window.open}): a new
     * tab next to this one, or a split when the activating press asks for one. A popup that opens consumes the page's
     * activation; one at another scheme consumes nothing.
     *
     * @param url the address
     * @param press the page's last press
     * @param activation the page's activation, consumed when the popup opens
     * @param nowMillis the time now, in milliseconds since the epoch
     * @param macOs whether the platform is macOS
     * @return {@link Where#NEXT_TAB} or {@link Where#SPLIT}; empty when it opens nothing
     */
    static Optional<Where> popup(String url, LastPress press, Activation activation, long nowMillis, boolean macOs) {
        if (!opens(url) || !activation.consume(nowMillis)) return Optional.empty();
        boolean split = press.withinActivation(nowMillis) && LinkGesture.of(press, macOs) == Where.SPLIT;
        return Optional.of(split ? Where.SPLIT : Where.NEXT_TAB);
    }
}
