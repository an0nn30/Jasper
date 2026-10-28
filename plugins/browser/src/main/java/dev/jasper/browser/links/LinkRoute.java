package dev.jasper.browser.links;

import dev.jasper.browser.links.LinkGesture.Where;
import dev.jasper.browser.page.LastPress;
import dev.jasper.sdk.ui.TabPlacement;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Whether a page's link or popup opens, and where (spec section 8), from what Jasper forwarded to the page. It opens only
 * after a press or key the page got within Chromium's 5 seconds of user activation, so popups without the user's
 * gesture are blocked, and only at an address of a scheme the address field loads ({@code http}, {@code https},
 * {@code file}, {@code about} and {@code jasper-about}). Where it goes comes from the last mouse press, while that
 * press still activates the page. Pure: Chromium's handlers ask it on Chromium's thread.
 */
final class LinkRoute {
    private static final Set<String> SCHEMES = Set.of("http", "https", "file", "about", "jasper-about");
    private static final Pattern SCHEME = Pattern.compile("([A-Za-z][A-Za-z0-9+.-]*):.*", Pattern.DOTALL);

    private LinkRoute() { }

    /**
     * Whether the page is activated: by its last press, or its last key, within 5,000 ms before {@code nowMillis}.
     *
     * @param press the page's last press
     * @param keyMillis when the page last got a key press, or {@link Long#MIN_VALUE} for never
     * @param nowMillis the time now, in milliseconds since the epoch
     * @return whether a link or popup may open
     */
    static boolean activated(LastPress press, long keyMillis, long nowMillis) {
        return press.withinActivation(nowMillis) || LastPress.withinActivation(keyMillis, nowMillis);
    }

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
     * Where a link Chromium would open elsewhere goes ({@code onOpenURLFromTab}: a modified or middle click): where the
     * activating press says, or in the same item when only a key activated the page.
     *
     * @param url the address
     * @param press the page's last press
     * @param keyMillis when the page last got a key press, or {@link Long#MIN_VALUE} for never
     * @param nowMillis the time now, in milliseconds since the epoch
     * @param macOs whether the platform is macOS
     * @return where it goes; empty when it opens nothing
     */
    static Optional<Where> link(String url, LastPress press, long keyMillis, long nowMillis, boolean macOs) {
        if (!opens(url) || !activated(press, keyMillis, nowMillis)) return Optional.empty();
        return Optional.of(press.withinActivation(nowMillis) ? LinkGesture.of(press, macOs) : Where.SAME_ITEM);
    }

    /**
     * Where a popup goes ({@code onBeforePopup}: a {@code target=_blank} link, or a script's {@code window.open}): a new
     * tab next to this one, or a split when the activating press asks for one.
     *
     * @param url the address
     * @param press the page's last press
     * @param keyMillis when the page last got a key press, or {@link Long#MIN_VALUE} for never
     * @param nowMillis the time now, in milliseconds since the epoch
     * @param macOs whether the platform is macOS
     * @return {@link Where#NEXT_TAB} or {@link Where#SPLIT}; empty when it opens nothing
     */
    static Optional<Where> popup(String url, LastPress press, long keyMillis, long nowMillis, boolean macOs) {
        if (!opens(url) || !activated(press, keyMillis, nowMillis)) return Optional.empty();
        boolean split = press.withinActivation(nowMillis) && LinkGesture.of(press, macOs) == Where.SPLIT;
        return Optional.of(split ? Where.SPLIT : Where.NEXT_TAB);
    }
}
