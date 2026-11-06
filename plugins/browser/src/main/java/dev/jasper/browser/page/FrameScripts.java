package dev.jasper.browser.page;

import dev.jasper.browser.engine.Chromium;
import dev.jasper.browser.json.StrictJson;
import dev.jasper.browser.permissions.Decision;
import dev.jasper.browser.permissions.Origins;
import dev.jasper.browser.permissions.Permission;
import java.util.Optional;
import java.util.function.Consumer;
import org.cef.CefClient;
import org.cef.browser.CefFrame;

/**
 * The privacy scripts a page's frames run as each of their documents starts loading (spec section 12), at once on
 * Chromium's thread, before the document's own scripts run as a rule: a page's main frame runs
 * {@code notifications.js}, Jasper's stand-in for {@code window.Notification}, called with the permission the page's
 * item's store holds for the document's site ({@link #state}), or {@code denied} for a /dev/null page and outside a
 * secure context. A document a sandbox gave an opaque origin is given its site's permission: the {@code links}
 * package's router, which alone knows the sandbox, refuses what it asks (ruling V22). It runs in the page's own world,
 * as it replaces the page's {@code Notification}, so the page's scripts can change it; what it asks Jasper is untrusted
 * ({@code links.PrivacyQueries}). A sub-frame keeps Chromium's own, which CEF asks no one about and so denies, and so
 * does a challenge frame ({@link ChallengeFrames}), even as a main frame: Jasper's page scripts stay out of those (spec
 * section 5). A script of the page that ran before the stand-in keeps Chromium's {@code Notification} too, and a
 * service worker's {@code showNotification} is not Jasper's. Both hooks are given to the client once, when a page first
 * starts, as {@link PageEvents#prepare} gives its handlers.
 */
final class FrameScripts {
    /** The notifications stand-in: a function of the document's permission. */
    static final String NOTIFICATIONS = PageEvents.script("notifications.js");
    /** The permission a document is given: Allowed, Blocked or none stored. */
    static final String GRANTED = "granted", DENIED = "denied", DEFAULT = "default";
    // The name the script goes by in a page's console and stack traces.
    private static final String NOTIFICATIONS_URL = "jasper-about:notifications.js";
    private static final Consumer<CefClient> INSTALL = FrameScripts::install;
    private static final System.Logger LOG = System.getLogger(FrameScripts.class.getName());

    private FrameScripts() { }

    /**
     * Has Chromium hook its client's main-frame and sub-frame load starts before any page is made on it; once, however
     * often asked. Called when a page first asks for Chromium, never at the plugin's start: this class names Chromium's
     * types.
     */
    static void prepare(Chromium chromium) {
        chromium.onClient(INSTALL);
    }

    private static void install(CefClient client) {
        PageEvents.onMainFrameLoadStart(client, FrameScripts::loadStarting);
        PageEvents.onSubFrameLoadStart(client, FrameScripts::loadStarting);
    }

    /**
     * {@code frame} of a page's {@code browser} starts loading a document: it runs the scripts {@link #script} gives it.
     * A browser that is no page's runs none. Chromium's thread; it never throws.
     *
     * @param browser the page's browser
     * @param frame the frame, which is not disposed here
     */
    static void loadStarting(PageBrowser browser, CefFrame frame) {
        Page page = browser.owner();
        if (page == null) return;
        try {
            // A sub-frame runs none of these scripts, so its address is not read.
            if (!frame.isMain()) return;
            script(true, frame.getURL(), page).ifPresent(script -> frame.executeJavaScript(script, NOTIFICATIONS_URL, 0));
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "A page's notifications script could not run", failure);
        }
    }

    /**
     * What a frame starting to load the document at {@code address} runs.
     *
     * @param mainFrame whether it is its page's main frame
     * @param address the document's address
     * @param page the page, whose privacy hooks give its item's stored decisions
     * @return the notifications stand-in for a main frame that is no challenge frame; empty for any other
     */
    static Optional<String> script(boolean mainFrame, String address, Page page) {
        if (!mainFrame || ChallengeFrames.is(address)) return Optional.empty();
        return Optional.of(notifications(state(address, page)));
    }

    /**
     * The notifications permission of a document at {@code address}, as its item's store holds it for the document's
     * site, its address's origin. A /dev/null page denies notifications, as Chrome's Incognito does (ruling V20), and
     * only a secure context's site may have them, as in Chromium (ruling V21): neither asks the store. Any thread.
     *
     * @param address the document's address
     * @param page the page, whose privacy hooks give its item's stored decisions
     * @return {@value #GRANTED} for Allowed; {@value #DENIED} for Blocked, for a /dev/null page, and for an address
     *     without a site or outside a secure context, a {@code file:} one included; and {@value #DEFAULT} when none is
     *     stored
     */
    static String state(String address, Page page) {
        Optional<String> origin = page.inMemory() ? Optional.empty() : Origins.of(address).filter(Origins::secure);
        if (origin.isEmpty()) return DENIED;
        return page.privacy().storedDecision(origin.get(), Permission.NOTIFICATIONS)
            .map(decision -> decision == Decision.ALLOWED ? GRANTED : DENIED).orElse(DEFAULT);
    }

    /**
     * The notifications stand-in, called with {@code state} quoted.
     *
     * @param state {@value #GRANTED}, {@value #DENIED} or {@value #DEFAULT}; anything else is {@value #DENIED}
     * @return the script
     */
    static String notifications(String state) {
        String known = GRANTED.equals(state) || DEFAULT.equals(state) ? state : DENIED;
        return NOTIFICATIONS + "\n(" + StrictJson.quote(known) + ");";
    }
}
