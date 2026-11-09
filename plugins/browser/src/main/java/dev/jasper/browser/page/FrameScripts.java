package dev.jasper.browser.page;

import dev.jasper.browser.adblock.FilterList;
import dev.jasper.browser.adblock.RuleSet;
import dev.jasper.browser.engine.Chromium;
import dev.jasper.browser.json.StrictJson;
import dev.jasper.browser.permissions.AudioMode;
import dev.jasper.browser.permissions.Decision;
import dev.jasper.browser.permissions.Origins;
import dev.jasper.browser.permissions.Permission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
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
 * service worker's {@code showNotification} is not Jasper's.
 *
 * <p>Every frame but a challenge frame then runs {@code audio.js}, which mutes the document's sound, in the page's own
 * world too, called with an audio mode ({@link #audio}). Audio follows the page's top-level site (the plan's call): the
 * main frame's document starts in the mode its item's store holds for its site ({@link #audioMode}), a /dev/null item's
 * store in memory included (ruling V20), and that becomes the page's current mode ({@link PagePrivacy#audioMode}),
 * which every sub-frame starting a document is given, so an embedded player is muted with its page. Muting the page
 * runs {@code setMuted} in each frame ({@link #everyFrame(CefBrowser, String)}). The muting is cooperative: the page's
 * own scripts can undo it, and an {@code AudioContext} made before the script ran is not tracked.
 *
 * <p>While ad blocking's filter list has a rule set active, every frame but a challenge frame, a Jasper page's and one
 * whose sheet is empty then runs {@code hide-elements.js}, called with the lines its document hides ({@link Hiding}): a
 * main frame its own sheet, a sub-frame the list's generic part alone (F34). The script adds each line through
 * {@code CSSStyleSheet.insertRule} to a sheet the document adopts, with the one fixed declaration
 * {@code display:none!important}, so a hostile list can hide elements and do nothing else (F5); a /dev/null page's
 * frames hide them too, as blocking is a protection. Both hooks are given to the client once, when a page first starts,
 * as {@link PageEvents#prepare} gives its handlers.
 */
final class FrameScripts {
    /** The notifications stand-in: a function of the document's permission. */
    static final String NOTIFICATIONS = PageEvents.script("notifications.js");
    /** The audio muting: a function of the document's audio mode. */
    static final String AUDIO = PageEvents.script("audio.js");
    /** The element hiding: a function of the document's sheet. */
    static final String HIDE_ELEMENTS = PageEvents.script("hide-elements.js");
    /** How many characters a frame's element hiding script has at most (ruling V39). */
    static final int SCRIPT_LIMIT = 4 * 1024 * 1024;
    /** The permission a document is given: Allowed, Blocked or none stored. */
    static final String GRANTED = "granted", DENIED = "denied", DEFAULT = "default";
    // The names the scripts go by in a page's console and stack traces.
    private static final String NOTIFICATIONS_URL = "jasper-about:notifications.js", AUDIO_URL = "jasper-about:audio.js",
        HIDE_URL = "jasper-about:hide-elements.js";
    private static final String JASPER_ABOUT = "jasper-about:";
    // The Chromiums given the hooks, held weakly: one installer for each, so one for each plugin run, however often a page asks.
    private static final Set<Chromium> PREPARED = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final System.Logger LOG = System.getLogger(FrameScripts.class.getName());

    private FrameScripts() { }

    /**
     * Has Chromium hook its client's main-frame and sub-frame load starts before any page is made on it; once, however
     * often asked, with one installer for the plugin's run. Called when a page first asks for Chromium, never at the
     * plugin's start: this class names Chromium's types.
     *
     * @param chromium the plugin's Chromium
     * @param filters the plugin's filter list, whose rule set active hides each document's elements
     */
    static void prepare(Chromium chromium, FilterList filters) {
        if (PREPARED.add(chromium)) {
            var hiding = new Hiding(filters::active);
            chromium.onClient(client -> install(client, hiding));
        }
    }

    /**
     * The element hiding, called with {@code sheet} quoted: a string literal, as {@link StrictJson#quote} writes it, so no
     * selector can end the literal or the call, and nothing is written as HTML. The script is at most
     * {@value #SCRIPT_LIMIT} characters (ruling V39): a sheet whose quoted form would not fit is cut first after its last
     * whole line that does, so it hides less and never more, however many escapes a hostile list's selectors need.
     *
     * @param sheet the lines to hide, {@code selector{display:none!important}} each, as a {@link RuleSet} gives them
     * @return the script
     */
    static String hideElements(String sheet) {
        return HIDE_ELEMENTS + "\n(" + StrictJson.quote(fitted(sheet)) + ");";
    }

    /** The longest start of {@code sheet}, in whole lines, whose quoted form leaves the script within its limit. */
    private static String fitted(String sheet) {
        int room = SCRIPT_LIMIT - HIDE_ELEMENTS.length() - "\n(\"\");".length(), used = 0, lines = 0;
        for (int at = 0; at < sheet.length(); at++) {
            char c = sheet.charAt(at);
            used += StrictJson.quotedLength(c);
            if (used > room) return sheet.substring(0, lines);
            if (c == '\n') lines = at + 1;
        }
        return sheet;
    }

    /**
     * Ad blocking's part of the frames' scripts (spec section 12): the element hiding of a frame starting a document, as
     * the filter list's rule set active then gives its sheet. A main frame hides what its own document's sheet names,
     * the generic lines and its site's ({@link RuleSet#stylesheet}); a sub-frame hides the generic lines alone
     * ({@link RuleSet#genericStylesheet}), which its list shares with every sub-frame (F34), so their script is made once
     * and kept for the next, and a main frame whose sheet is that one, as a page with no rules of its own hides, is
     * given that script too (ruling V39). The rule set indexes its rules and bounds its work, so no sheet scans every
     * rule. No script for a Jasper page ({@code jasper-about:}), nor for a sheet that is empty. Chromium's thread.
     */
    static final class Hiding {
        private final Supplier<Optional<RuleSet>> rules;
        private final Function<String, String> scripts;
        // The last sub-frame's sheet and its script, which the next sub-frame most likely shares; guarded by this.
        private String sheet = "", script = "";

        /**
         * The hiding of {@code rules}.
         *
         * @param rules the rule set active, read as each document starts: the filter list's
         */
        Hiding(Supplier<Optional<RuleSet>> rules) {
            this(rules, FrameScripts::hideElements);
        }

        /** As {@link #Hiding(Supplier)}, each sheet's script made by {@code scripts}: a test's counting seam. */
        Hiding(Supplier<Optional<RuleSet>> rules, Function<String, String> scripts) {
            this.rules = rules;
            this.scripts = scripts;
        }

        /**
         * The element hiding a frame starting to load the document at {@code address} runs.
         *
         * @param mainFrame whether it is its page's main frame
         * @param address the document's address
         * @return the script; empty without a rule set, for a Jasper page, and when the sheet is empty
         */
        Optional<Script> script(boolean mainFrame, String address) {
            Optional<RuleSet> active = rules.get();
            if (active.isEmpty() || address == null || address.regionMatches(true, 0, JASPER_ABOUT, 0, JASPER_ABOUT.length()))
                return Optional.empty();
            String hidden = mainFrame ? active.get().stylesheet(address) : active.get().genericStylesheet(address);
            if (hidden.isEmpty()) return Optional.empty();
            synchronized (this) {
                if (hidden.equals(sheet)) return Optional.of(new Script(HIDE_URL, script));
            }
            String made = scripts.apply(hidden);
            // A page's own sheet is its own; a sub-frame's generic one the next sub-frame most likely shares.
            if (!mainFrame) {
                synchronized (this) {
                    sheet = hidden;
                    script = made;
                }
            }
            return Optional.of(new Script(HIDE_URL, made));
        }
    }

    private static void install(CefClient client, Hiding hiding) {
        PageEvents.onMainFrameLoadStart(client, (browser, frame) -> loadStarting(browser, frame, hiding));
        PageEvents.onSubFrameLoadStart(client, (browser, frame) -> loadStarting(browser, frame, hiding));
    }

    /**
     * {@code frame} of a page's {@code browser} starts loading a document: a main frame's document starts in its site's
     * audio mode, which becomes the page's current one, and the frame runs the scripts {@link #scripts} gives it. A
     * browser that is no page's runs none. Chromium's thread; it never throws.
     *
     * @param browser the page's browser
     * @param frame the frame, which is not disposed here
     * @param hiding ad blocking's element hiding
     */
    static void loadStarting(PageBrowser browser, CefFrame frame, Hiding hiding) {
        Page page = browser.owner();
        if (page == null) return;
        try {
            boolean main = frame.isMain();
            String address = frame.getURL();
            if (main) page.privacy().documentStarted(audioMode(address, page));
            for (Script script : scripts(main, address, page, hiding)) frame.executeJavaScript(script.text(), script.url(), 0);
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "A page's privacy scripts could not run", failure);
        }
    }

    /**
     * A script a frame runs, and the address it goes by in the page's console.
     *
     * @param url the address it goes by
     * @param text the script
     */
    record Script(String url, String text) { }

    /**
     * What a frame starting to load the document at {@code address} runs, in order. A main frame's document has started
     * in its audio mode first.
     *
     * @param mainFrame whether it is its page's main frame
     * @param address the document's address
     * @param page the page, whose privacy hooks give its item's stored decisions and its current audio mode
     * @param hiding ad blocking's element hiding
     * @return for a main frame that is no challenge frame, the notifications stand-in and the audio muting; for a
     *     sub-frame that is none, the audio muting in the page's current mode; then the element hiding, when
     *     {@code hiding} gives one; none for a challenge frame
     */
    static List<Script> scripts(boolean mainFrame, String address, Page page, Hiding hiding) {
        if (ChallengeFrames.is(address)) return List.of();
        var scripts = new ArrayList<Script>(3);
        if (mainFrame) scripts.add(new Script(NOTIFICATIONS_URL, notifications(state(address, page))));
        scripts.add(new Script(AUDIO_URL, audio(page.privacy().audioMode())));
        hiding.script(mainFrame, address).ifPresent(scripts::add);
        return List.copyOf(scripts);
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
     * The audio mode a main-frame document at {@code address} starts in: what its item's store holds for its site, the
     * address's origin, secure context or not, as muting is a protection; a document without a site has no choice to
     * ask for or store, and is muted. Any thread.
     *
     * @param address the document's address
     * @param page the page, whose privacy hooks give its item's stored decisions
     * @return {@link AudioMode#ALLOW} for Allowed, {@link AudioMode#BLOCK} for Blocked and for an address without a site,
     *     and {@link AudioMode#ASK} when none is stored
     */
    static AudioMode audioMode(String address, Page page) {
        return Origins.of(address).map(origin -> AudioMode.of(page.privacy().storedDecision(origin, Permission.AUDIO_PLAYBACK)))
            .orElse(AudioMode.BLOCK);
    }

    /**
     * The audio muting, called with {@code mode} quoted, in lower case.
     *
     * @param mode the document's audio mode; null is {@link AudioMode#BLOCK}
     * @return the script
     */
    static String audio(AudioMode mode) {
        AudioMode known = mode == null ? AudioMode.BLOCK : mode;
        return AUDIO + "\n(" + StrictJson.quote(known.name().toLowerCase(Locale.ROOT)) + ");";
    }

    /**
     * Runs {@code script} in each frame of {@code page}'s browser but challenge frames, as
     * {@link #everyFrame(CefBrowser, String)} does; nothing before the browser is ready. UI thread.
     *
     * @param page the page
     * @param script what each frame runs
     */
    static void everyFrame(Page page, String script) {
        PageBrowser ready = page.readyBrowser();
        if (ready != null) everyFrame((CefBrowser) ready, script);
    }

    /**
     * Runs {@code script} in each frame {@code browser} lists but challenge frames, by their addresses now, then
     * releases each handle: a frame gone meanwhile, or one that fails, which is logged, stops none of the others. Any
     * thread; it never throws.
     *
     * @param browser the page's browser
     * @param script what each frame runs
     */
    static void everyFrame(CefBrowser browser, String script) {
        List<String> frames;
        try {
            frames = browser.getFrameIdentifiers();
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "A page's frames could not be listed", failure);
            return;
        }
        if (frames == null) return;
        for (String id : frames) {
            if (id == null) continue;
            try {
                CefFrame frame = browser.getFrameByIdentifier(id);
                if (frame == null) continue;
                try {
                    if (frame.isValid() && !ChallengeFrames.is(frame.getURL())) frame.executeJavaScript(script, AUDIO_URL, 0);
                } finally {
                    frame.dispose();
                }
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, "A page's frame could not be muted or unmuted", failure);
            }
        }
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
