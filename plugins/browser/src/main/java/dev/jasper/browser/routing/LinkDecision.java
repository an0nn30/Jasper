package dev.jasper.browser.routing;

import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.browser.settings.BrowserSettings.LinkOpening;
import dev.jasper.sdk.ui.LinkOrigin;
import dev.jasper.sdk.ui.LinkPlacement;
import dev.jasper.sdk.ui.WebLink;
import java.util.Objects;

/**
 * Where a link Jasper hands the Browser goes (spec section 9). An address from outside Jasper opens with no prompt. A
 * local file goes by Open Local Web Files. While Jasper is the system's default browser, any other link opens with no
 * prompt (rule 2): the system browser it would go back to is Jasper. Otherwise a split click opens with no prompt,
 * which iTerm2 calls implied consent, unless Open Links From Terminal sends links to the default browser, and any other
 * link goes by Open Links From Terminal.
 */
final class LinkDecision {
    /** What the Browser does with a link. */
    enum Decision {
        /** Opens it in a browser item. */
        OPEN,
        /** Asks with the "Open in Jasper?" prompt. */
        ASK,
        /** Hands it back to Jasper: the system browser opens a web address, the Default editor a local file. */
        HAND_BACK
    }

    private LinkDecision() { }

    /**
     * Decides a link.
     *
     * @param link the link Jasper hands the Browser
     * @param settings the Browser's settings now
     * @param jasperIsDefault whether Jasper is the system's default browser now
     * @return what the Browser does with it
     */
    static Decision decide(WebLink link, BrowserSettings settings, boolean jasperIsDefault) {
        Objects.requireNonNull(link, "link");
        Objects.requireNonNull(settings, "settings");
        if (link.origin() == LinkOrigin.EXTERNAL) return Decision.OPEN;
        if (localFile(link)) return switch (settings.localFiles()) {
            case ASK -> Decision.ASK;
            case BUILT_IN -> Decision.OPEN;
            case DEFAULT_EDITOR -> Decision.HAND_BACK;
        };
        if (jasperIsDefault) return Decision.OPEN;
        if (link.placement() != LinkPlacement.TAB) return settings.links() == LinkOpening.DEFAULT_BROWSER ? Decision.HAND_BACK : Decision.OPEN;
        return switch (settings.links()) {
            case ASK -> Decision.ASK;
            case BUILT_IN -> Decision.OPEN;
            case DEFAULT_BROWSER -> Decision.HAND_BACK;
        };
    }

    /**
     * Whether a link is a local file: its address is a {@code file:} one, which only a click in a tab carries.
     *
     * @param link a link
     * @return whether it is
     */
    static boolean localFile(WebLink link) {
        return "file".equalsIgnoreCase(link.address().getScheme());
    }
}
