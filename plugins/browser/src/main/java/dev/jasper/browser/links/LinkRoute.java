package dev.jasper.browser.links;

import dev.jasper.browser.links.LinkGesture.Where;
import dev.jasper.browser.page.Activation;
import dev.jasper.browser.page.LastPress;
import dev.jasper.sdk.ui.TabPlacement;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Whether a page's link or popup opens, and where (spec section 8), from what Jasper forwarded to the page. Either opens
 * only at an address of a scheme the address field loads ({@code http}, {@code https}, {@code file}, {@code about} and
 * {@code jasper-about}). A link always goes somewhere: where the press that clicked it says, or in the same item. A
 * popup opens only while the page's {@link Activation} holds, and consumes it, so popups without the user's gesture are
 * blocked and each gesture opens one. Chromium's handlers ask it on Chromium's thread.
 */
final class LinkRoute {
    private static final Set<String> SCHEMES = Set.of("http", "https", "file", "about", "jasper-about");
    private static final Pattern SCHEME = Pattern.compile("([A-Za-z][A-Za-z0-9+.-]*):.*", Pattern.DOTALL);

    private LinkRoute() { }

    /**
     * Whether a link or popup to {@code url} may open: its scheme is one the address field loads.
     *
     * @param url the address Chromium gave
     * @return whether it opens
     */
    static boolean opens(String url) {
        if (url == null) return false;
        Matcher scheme = SCHEME.matcher(url);
        return scheme.matches() && SCHEMES.contains(scheme.group(1).toLowerCase(Locale.ROOT));
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
     * or Chromium reports the user's gesture, else in the same item. It consumes no activation.
     *
     * @param url the address
     * @param press the page's last press
     * @param userGesture whether Chromium reports the navigation as the user's
     * @param nowMillis the time now, in milliseconds since the epoch
     * @param macOs whether the platform is macOS
     * @return where it goes; empty only at a scheme the address field does not load
     */
    static Optional<Where> link(String url, LastPress press, boolean userGesture, long nowMillis, boolean macOs) {
        if (!opens(url)) return Optional.empty();
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
