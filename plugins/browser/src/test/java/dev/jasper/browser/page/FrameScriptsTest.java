package dev.jasper.browser.page;

import dev.jasper.browser.adblock.BlockerJson;
import dev.jasper.browser.adblock.ContentRules;
import dev.jasper.browser.adblock.RuleSet;
import dev.jasper.browser.json.StrictJson;
import dev.jasper.browser.permissions.AudioMode;
import dev.jasper.browser.permissions.Decision;
import dev.jasper.browser.permissions.Permission;
import dev.jasper.browser.store.CapturedLog;
import java.awt.Point;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The scripts a page's frames run as their documents start loading (spec section 12): a main frame gets
 * {@code notifications.js} with the permission its item's store holds for the document's site, quoted into the call,
 * and no other frame gets it, nor a challenge frame, nor a browser that is no page's. A /dev/null page (ruling V20), and
 * a document outside a secure context (ruling V21), are denied without asking the store. Every frame but a challenge
 * frame gets {@code audio.js} (plan 6 Task 7): a main frame with the audio mode its item's store holds for the new
 * document's top-level site, which becomes the page's current mode, and a sub-frame with the page's current mode, which
 * its muting changes. Muting runs {@code setMuted} in every frame but challenge frames. Chromium's side is played by the
 * test: the frames it names and the browsers it calls about, made without Chromium. No Chromium.
 */
class FrameScriptsTest {
    private static final String SITE = "https://chat.example";
    /** No hiding: no rule set is ever active. */
    private static final FrameScripts.Hiding NONE = new FrameScripts.Hiding(Optional::empty);
    private static final String HIDDEN = "{display:none!important}";

    @Test void theStateIsQuotedIntoTheScriptAndAnyOtherBecomesDenied() {
        for (String state : List.of("granted", "denied", "default"))
            assertThat(FrameScripts.notifications(state)).as(state).isEqualTo(FrameScripts.NOTIFICATIONS + "\n(\"" + state + "\");");
        for (String other : new String[] {"Granted", "", " default", "prompt", "\");alert(1);//", null})
            assertThat(FrameScripts.notifications(other)).as(String.valueOf(other)).isEqualTo(FrameScripts.NOTIFICATIONS + "\n(\"denied\");");
        assertThat(FrameScripts.NOTIFICATIONS).as("a function the call follows").startsWith("/*").endsWith("})");
    }

    @Test void theAudioModeIsQuotedIntoTheAudioScriptAndNoneMutes() {
        assertThat(FrameScripts.audio(AudioMode.ALLOW)).isEqualTo(FrameScripts.AUDIO + "\n(\"allow\");");
        assertThat(FrameScripts.audio(AudioMode.BLOCK)).isEqualTo(FrameScripts.AUDIO + "\n(\"block\");");
        assertThat(FrameScripts.audio(AudioMode.ASK)).isEqualTo(FrameScripts.AUDIO + "\n(\"ask\");");
        assertThat(FrameScripts.audio(null)).isEqualTo(FrameScripts.AUDIO + "\n(\"block\");");
        assertThat(FrameScripts.AUDIO).as("a function the call follows").startsWith("/*").endsWith("})");
        assertThat(PagePrivacy.setMuted(true)).contains("Symbol.for('dev.jasper.browser.audio')").contains(".setMuted(true)");
        assertThat(PagePrivacy.setMuted(false)).contains(".setMuted(false)");
    }

    /** Ruling V16: the document's site is its address's origin, the top-level site's, and its store decides. */
    @Test void theDocumentsStateIsWhatItsItemsStoreHoldsForItsSite() throws Exception {
        onUiThread(() -> {
            var stored = new HashMap<String, Decision>();
            var asked = new ArrayList<String>();
            Page page = page(stored, asked, PageContext.persistent());
            assertThat(FrameScripts.state("https://Chat.Example:443/room?x=1#top", page)).as("nothing stored").isEqualTo("default");
            assertThat(asked).containsExactly(SITE + " notifications");
            stored.put(SITE, Decision.ALLOWED);
            assertThat(FrameScripts.state(SITE + "/", page)).isEqualTo("granted");
            stored.put(SITE, Decision.BLOCKED);
            assertThat(FrameScripts.state(SITE + "/", page)).isEqualTo("denied");
            stored.put("https://chat.example:8443", Decision.ALLOWED);
            assertThat(FrameScripts.state("https://chat.example:8443/", page)).as("another origin").isEqualTo("granted");
            asked.clear();
            for (String noSite : new String[] {"about:blank", "data:text/html,hi", "jasper-about:welcome", "", null})
                assertThat(FrameScripts.state(noSite, page)).as(String.valueOf(noSite)).isEqualTo("denied");
            assertThat(asked).as("no site asks no store").isEmpty();
            page.close();
            assertThat(FrameScripts.state(SITE + "/", page)).as("a closed page's store is no longer asked").isEqualTo("default");
        });
    }

    /** Ruling V20: a /dev/null page denies notifications, as Chrome's Incognito does, whatever its store holds. */
    @Test void aDevNullPageIsDeniedWithoutAskingItsStore() throws Exception {
        onUiThread(() -> {
            var asked = new ArrayList<String>();
            Page page = page(new HashMap<>(Map.of(SITE, Decision.ALLOWED)), new HashMap<>(), asked, PageContext.memory());
            assertThat(FrameScripts.state(SITE + "/room", page)).isEqualTo("denied");
            var ran = new ArrayList<String>();
            FrameScripts.loadStarting(PageTestAccess.pageBrowser(page), frame(true, SITE + "/room", ran, new ArrayList<>()), NONE);
            assertThat(ran).containsExactly("jasper-about:notifications.js " + FrameScripts.notifications("denied"),
                "jasper-about:audio.js " + FrameScripts.audio(AudioMode.ASK));
            assertThat(asked).as("its notifications are not asked of the store; its audio is its own store's (ruling V20)")
                .containsExactly(SITE + " audio");
        });
    }

    /**
     * Ruling V21: notifications need a secure context, as in Chromium: https, or http on this computer. A {@code file:}
     * page is denied too, as Chrome denies files, and every file shares one site.
     */
    @Test void onlyASecureContextIsAskedItsStore() throws Exception {
        onUiThread(() -> {
            var stored = new HashMap<String, Decision>();
            for (String site : new String[] {"http://chat.example", "http://localhost:3000", "http://127.0.0.1", "http://[::1]:8080",
                    "http://app.localhost", "file://"})
                stored.put(site, Decision.ALLOWED);
            var asked = new ArrayList<String>();
            Page page = page(stored, asked, PageContext.persistent());
            for (String insecure : new String[] {"http://chat.example/room", "file:///Users/someone/page.html", "http://localhost.example/"})
                assertThat(FrameScripts.state(insecure, page)).as(insecure).isEqualTo("denied");
            assertThat(asked).as("no insecure site asks its store").isEmpty();
            for (String local : new String[] {"http://localhost:3000/", "http://127.0.0.1/", "http://[::1]:8080/", "http://app.localhost/"})
                assertThat(FrameScripts.state(local, page)).as(local).isEqualTo("granted");
            stored.clear();
            assertThat(FrameScripts.state("http://localhost:3000/", page)).isEqualTo("default");
        });
    }

    /**
     * The main frame's audio mode is what its item's store holds for the new document's top-level site, as
     * {@code PermissionStore.audioMode} reads it, secure context or not; a document without a site has no choice to ask
     * for, and is muted.
     */
    @Test void theMainFramesAudioModeIsWhatItsItemsStoreHoldsForItsSite() throws Exception {
        onUiThread(() -> {
            var audio = new HashMap<String, Decision>();
            var asked = new ArrayList<String>();
            Page page = page(new HashMap<>(), audio, asked, PageContext.persistent());
            assertThat(FrameScripts.audioMode("https://Chat.Example:443/room", page)).as("nothing stored").isEqualTo(AudioMode.ASK);
            assertThat(asked).containsExactly(SITE + " audio");
            audio.put(SITE, Decision.ALLOWED);
            assertThat(FrameScripts.audioMode(SITE + "/", page)).isEqualTo(AudioMode.ALLOW);
            audio.put(SITE, Decision.BLOCKED);
            assertThat(FrameScripts.audioMode(SITE + "/", page)).isEqualTo(AudioMode.BLOCK);
            assertThat(FrameScripts.audioMode("http://chat.example/", page)).as("muting applies everywhere").isEqualTo(AudioMode.ASK);
            assertThat(FrameScripts.audioMode("file:///Users/someone/song.html", page)).isEqualTo(AudioMode.ASK);
            asked.clear();
            for (String noSite : new String[] {"about:blank", "data:text/html,hi", "jasper-about:welcome", "", null})
                assertThat(FrameScripts.audioMode(noSite, page)).as(String.valueOf(noSite)).isEqualTo(AudioMode.BLOCK);
            assertThat(asked).as("no site asks no store").isEmpty();

            var ran = new ArrayList<String>();
            PageBrowser browser = PageTestAccess.pageBrowser(page);
            FrameScripts.loadStarting(browser, frame(true, SITE + "/room", ran, new ArrayList<>()), NONE);
            assertThat(ran).containsExactly("jasper-about:notifications.js " + FrameScripts.notifications("default"),
                "jasper-about:audio.js " + FrameScripts.audio(AudioMode.BLOCK));
            assertThat(page.privacy().audioMode()).as("the page's current mode").isEqualTo(AudioMode.BLOCK);
            audio.clear();
            ran.clear();
            FrameScripts.loadStarting(browser, frame(true, SITE + "/next", ran, new ArrayList<>()), NONE);
            assertThat(ran).last().isEqualTo("jasper-about:audio.js " + FrameScripts.audio(AudioMode.ASK));
            assertThat(page.privacy().audioMode()).isEqualTo(AudioMode.ASK);
        });
    }

    /**
     * A sub-frame gets the page's current mode, the one its main frame's document started with or its muting set since,
     * so an embedded player follows the top-level site (the plan's call); a challenge frame gets no audio script.
     */
    @Test void aSubFrameGetsThePagesCurrentModeAndAChallengeFrameNone() throws Exception {
        onUiThread(() -> {
            var audio = new HashMap<String, Decision>(Map.of(SITE, Decision.BLOCKED));
            Page page = page(new HashMap<>(), audio, new ArrayList<>(), PageContext.persistent());
            PageBrowser browser = PageTestAccess.pageBrowser(page);
            var ran = new ArrayList<String>();
            FrameScripts.loadStarting(browser, frame(true, SITE + "/room", ran, new ArrayList<>()), NONE);
            PageTestAccess.loadStarted(page, SITE + "/room");
            ran.clear();
            audio.put("https://player.example", Decision.ALLOWED);
            FrameScripts.loadStarting(browser, frame(false, "https://player.example/embed", ran, new ArrayList<>()), NONE);
            assertThat(ran).as("the top-level site's, not its own").containsExactly("jasper-about:audio.js " + FrameScripts.audio(AudioMode.BLOCK));
            page.privacy().mute(false);
            FrameScripts.loadStarting(browser, frame(false, "https://player.example/next", ran, new ArrayList<>()), NONE);
            page.privacy().mute(true);
            FrameScripts.loadStarting(browser, frame(false, "https://player.example/again", ran, new ArrayList<>()), NONE);
            assertThat(ran).containsExactly("jasper-about:audio.js " + FrameScripts.audio(AudioMode.BLOCK),
                "jasper-about:audio.js " + FrameScripts.audio(AudioMode.ALLOW), "jasper-about:audio.js " + FrameScripts.audio(AudioMode.BLOCK));
            ran.clear();
            FrameScripts.loadStarting(browser, frame(false, "https://challenges.cloudflare.com/turnstile", ran, new ArrayList<>()), NONE);
            FrameScripts.loadStarting(browser, frame(false, "https://newassets.hcaptcha.com/captcha/v1/audio", ran, new ArrayList<>()), NONE);
            assertThat(ran).as("challenge frames").isEmpty();
        });
    }

    /**
     * The polyfill runs in a page's top frame, never in a sub-frame (plan 6's call) or a challenge frame (spec section
     * 5); a sub-frame runs the audio script alone, read by its address, and a page's DevTools get none.
     */
    @Test void theMainFrameGetsBothScriptsASubFrameTheAudioOneAndAChallengeFrameNone() throws Exception {
        onUiThread(() -> {
            var stored = new HashMap<String, Decision>(Map.of(SITE, Decision.ALLOWED));
            Page page = page(stored, new ArrayList<>(), PageContext.persistent());
            PageBrowser browser = PageTestAccess.pageBrowser(page);
            var ran = new ArrayList<String>();
            var read = new ArrayList<String>();
            FrameScripts.loadStarting(browser, frame(true, SITE + "/room", ran, read), NONE);
            assertThat(ran).containsExactly("jasper-about:notifications.js " + FrameScripts.notifications("granted"),
                "jasper-about:audio.js " + FrameScripts.audio(AudioMode.ASK));
            assertThat(read).containsExactly(SITE + "/room");
            ran.clear();
            read.clear();
            FrameScripts.loadStarting(browser, frame(false, SITE + "/frame", ran, read), NONE);
            assertThat(ran).as("a sub-frame").containsExactly("jasper-about:audio.js " + FrameScripts.audio(AudioMode.ASK));
            assertThat(read).as("read once, to tell a challenge frame").containsExactly(SITE + "/frame");
            ran.clear();
            FrameScripts.loadStarting(browser, frame(false, "https://challenges.cloudflare.com/turnstile", ran, read), NONE);
            FrameScripts.loadStarting(browser, frame(true, "https://challenges.cloudflare.com/turnstile", ran, read), NONE);
            FrameScripts.loadStarting(browser, frame(true, "https://www.google.com/recaptcha/api2/anchor", ran, read), NONE);
            assertThat(ran).as("challenge frames").isEmpty();
            FrameScripts.loadStarting(PageTestAccess.devToolsBrowser(PageTestAccess.devTools(page, new Point())), frame(true, SITE + "/", ran, read), NONE);
            assertThat(ran).as("a page's DevTools").isEmpty();
            FrameScripts.loadStarting(browser, frame(true, "about:blank", ran, read), NONE);
            assertThat(ran).as("a document without a site is denied and muted").containsExactly("jasper-about:notifications.js "
                + FrameScripts.notifications("denied"), "jasper-about:audio.js " + FrameScripts.audio(AudioMode.BLOCK));
        });
    }

    /**
     * Muting a page sets its current mode and runs {@code setMuted} in every frame its browser lists but challenge
     * frames, each handle released after; a frame gone meanwhile, or one that fails, stops none of the others.
     */
    @Test void mutingRunsSetMutedInEveryFrameButChallengeFramesAndSetsTheCurrentMode() throws Exception {
        var ran = new ArrayList<String>();
        var disposed = new ArrayList<String>();
        var frames = new HashMap<String, CefFrame>();
        frames.put("main", frame("main", SITE + "/room", ran, disposed, false));
        frames.put("player", frame("player", "https://player.example/embed", ran, disposed, false));
        frames.put("challenge", frame("challenge", "https://challenges.cloudflare.com/turnstile", ran, disposed, false));
        frames.put("broken", frame("broken", "https://broken.example/", ran, disposed, true));
        CefBrowser browser = browser(new Vector<>(List.of("main", "gone", "broken", "challenge", "player")), frames);
        try (var log = new CapturedLog(FrameScripts.class.getName())) {
            FrameScripts.everyFrame(browser, PagePrivacy.setMuted(true));
            assertThat(log.records()).singleElement().satisfies(record -> assertThat(record.getThrown()).hasMessage("broken frame"));
        }
        assertThat(ran).containsExactly("main jasper-about:audio.js " + PagePrivacy.setMuted(true),
            "player jasper-about:audio.js " + PagePrivacy.setMuted(true));
        assertThat(disposed).containsExactlyInAnyOrder("main", "broken", "challenge", "player");

        var scripts = new ArrayList<String>();
        var privacy = new PagePrivacy(scripts::add);
        assertThat(privacy.audioMode()).as("before any document").isEqualTo(AudioMode.BLOCK);
        privacy.mute(false);
        assertThat(privacy.audioMode()).isEqualTo(AudioMode.ALLOW);
        privacy.mute(true);
        assertThat(privacy.audioMode()).isEqualTo(AudioMode.BLOCK);
        assertThat(scripts).containsExactly(PagePrivacy.setMuted(false), PagePrivacy.setMuted(true));
        privacy.close();
        privacy.mute(false);
        assertThat(scripts).as("a closed page runs nothing").hasSize(2);
    }

    /**
     * Review round 1, item 2: a muting meant for a document the main frame has left, as Chromium started the next before
     * the UI thread heard of it, changes neither the next document's mode nor any frame; once the UI thread hears of the
     * next, muting acts on it.
     */
    @Test void aMutingMeantForTheDocumentBeforeChangesNothing() {
        var scripts = new ArrayList<String>();
        var privacy = new PagePrivacy(scripts::add);
        assertThat(privacy.documents()).isZero();
        privacy.documentStarted(AudioMode.ASK);
        privacy.documentShown(privacy.documents());
        privacy.mute(true);
        assertThat(privacy.audioMode()).isEqualTo(AudioMode.BLOCK);
        privacy.documentStarted(AudioMode.ASK);
        assertThat(privacy.documents()).isEqualTo(2);
        privacy.mute(false);
        assertThat(privacy.audioMode()).as("the next document's, as Chromium set it").isEqualTo(AudioMode.ASK);
        assertThat(scripts).as("no frame told").containsExactly(PagePrivacy.setMuted(true));
        privacy.documentShown(2);
        privacy.mute(false);
        assertThat(privacy.audioMode()).isEqualTo(AudioMode.ALLOW);
        assertThat(scripts).containsExactly(PagePrivacy.setMuted(true), PagePrivacy.setMuted(false));
    }

    /** A page's playing reaches its item's handler on the UI thread, until the page closes; a handler that throws is logged. */
    @Test void aPagesSoundReachesItsItemUntilThePageCloses() throws Exception {
        var privacy = new PagePrivacy(script -> { });
        privacy.audioPlaying();
        var heard = new ArrayList<String>();
        privacy.onAudioPlaying(() -> heard.add("playing"));
        privacy.audioPlaying();
        assertThat(heard).containsExactly("playing");
        privacy.onAudioPlaying(() -> { throw new IllegalStateException("the item broke"); });
        try (var log = new CapturedLog(PagePrivacy.class.getName())) {
            privacy.audioPlaying();
            assertThat(log.records()).singleElement().satisfies(record -> assertThat(record.getThrown()).hasMessage("the item broke"));
        }
        privacy.onAudioPlaying(() -> heard.add("again"));
        privacy.close();
        privacy.audioPlaying();
        privacy.onAudioPlaying(() -> heard.add("after"));
        privacy.audioPlaying();
        assertThat(heard).containsExactly("playing");
    }

    /**
     * Plan 6 Task 10 (F5, F34): while the filter list has a rule set active, a page's main frame hides what its sheet
     * names, its own rules and the generic ones, and a sub-frame the generic part alone; a challenge frame, a Jasper
     * page and a frame whose sheet is empty get no hiding, and nothing hides without a rule set. The hiding runs after
     * the privacy scripts.
     */
    @Test void aMainFrameHidesItsSheetASubFrameTheGenericPartAndAnEmptySheetNothing() throws Exception {
        RuleSet rules = rules("""
            [{"trigger": {"url-filter": ".*"}, "action": {"type": "css-display-none", "selector": ".ad-box"}},
             {"trigger": {"url-filter": ".*", "if-domain": ["news.example"]}, "action": {"type": "css-display-none", "selector": ".banner, #promo"}},
             {"trigger": {"url-filter": "quiet.example"}, "action": {"type": "ignore-previous-rules"}}]
            """);
        String generic = ".ad-box{display:none!important}\n", own = ".banner{display:none!important}\n#promo{display:none!important}\n";
        onUiThread(() -> {
            Page page = page(new HashMap<>(), new ArrayList<>(), PageContext.persistent());
            PageBrowser browser = PageTestAccess.pageBrowser(page);
            var hiding = new FrameScripts.Hiding(() -> Optional.of(rules));
            var ran = new ArrayList<String>();
            FrameScripts.loadStarting(browser, frame(true, "https://news.example/today", ran, new ArrayList<>()), hiding);
            assertThat(ran).containsExactly("jasper-about:notifications.js " + FrameScripts.notifications("default"),
                "jasper-about:audio.js " + FrameScripts.audio(AudioMode.ASK),
                "jasper-about:hide-elements.js " + FrameScripts.hideElements(generic + own));
            ran.clear();
            FrameScripts.loadStarting(browser, frame(false, "https://news.example/frame", ran, new ArrayList<>()), hiding);
            assertThat(ran).as("F34: a sub-frame gets the generic part alone").containsExactly(
                "jasper-about:audio.js " + FrameScripts.audio(AudioMode.ASK), "jasper-about:hide-elements.js " + FrameScripts.hideElements(generic));
            ran.clear();
            FrameScripts.loadStarting(browser, frame(true, "https://quiet.example/", ran, new ArrayList<>()), hiding);
            assertThat(ran).as("an empty sheet injects nothing").noneMatch(script -> script.startsWith("jasper-about:hide-elements.js"));
            ran.clear();
            for (String none : new String[] {"https://challenges.cloudflare.com/turnstile", "jasper-about:welcome", "JASPER-ABOUT://history/"})
                FrameScripts.loadStarting(browser, frame(true, none, ran, new ArrayList<>()), hiding);
            FrameScripts.loadStarting(browser, frame(false, "https://challenges.cloudflare.com/turnstile", ran, new ArrayList<>()), hiding);
            assertThat(ran).as("challenge frames and Jasper's pages").noneMatch(script -> script.startsWith("jasper-about:hide-elements.js"));
            ran.clear();
            FrameScripts.loadStarting(browser, frame(true, "https://news.example/today", ran, new ArrayList<>()),
                new FrameScripts.Hiding(Optional::empty));
            assertThat(ran).as("no rule set").hasSize(2).noneMatch(script -> script.startsWith("jasper-about:hide-elements.js"));
        });
    }

    /**
     * F5: the sheet arrives as one string literal, as {@code StrictJson.quote} writes it: quotes, backslashes and line
     * feeds escaped, so no selector can end the literal or the call; nothing is written as HTML, so {@code </style>}
     * needs no escape.
     */
    @Test void theSheetIsQuotedIntoTheHidingScript() {
        String sheet = "a[title=\"x\\\"</style>\"]{display:none!important}\n.b{display:none!important}\n";
        String script = FrameScripts.hideElements(sheet);
        assertThat(script).isEqualTo(FrameScripts.HIDE_ELEMENTS + "\n(" + StrictJson.quote(sheet) + ");");
        assertThat(script.substring(FrameScripts.HIDE_ELEMENTS.length())).isEqualTo(
            "\n(\"a[title=\\\"x\\\\\\\"</style>\\\"]{display:none!important}\\n.b{display:none!important}\\n\");")
            .as("one line: the sheet's line feeds are escapes").doesNotContain("important}\n.");
        assertThat(FrameScripts.HIDE_ELEMENTS).as("a function the call follows").startsWith("/*").endsWith("})");
    }

    /** F34: sub-frames share their list's generic part, so its script is made once and handed to each. */
    @Test void subFramesShareTheGenericPartsScript() {
        RuleSet rules = rules("[{\"trigger\": {\"url-filter\": \".*\"}, \"action\": {\"type\": \"css-display-none\", \"selector\": \".ad\"}}]");
        var hiding = new FrameScripts.Hiding(() -> Optional.of(rules));
        var first = hiding.script(false, "https://a.example/").orElseThrow();
        var second = hiding.script(false, "https://b.example/frame").orElseThrow();
        assertThat(second.text()).isSameAs(first.text());
        assertThat(first.url()).isEqualTo("jasper-about:hide-elements.js");
        assertThat(hiding.script(true, "https://a.example/").orElseThrow().text()).isEqualTo(first.text());
        assertThat(hiding.script(false, "jasper-about:welcome")).isEmpty();
    }

    /**
     * Ruling V39: a frame's script is at most 4 MiB, its sheet cut at a line to fit before it is quoted, which hides
     * less, never more: a hostile list's selectors of emoji, each surrogate written as a six-character escape, would
     * otherwise make three times their sheet's length.
     */
    @Test void aFramesScriptIsAtMostFourMibOfWholeLines() {
        String emoji = new String(Character.toChars(0x1F600)).repeat(100);
        var sheet = new StringBuilder();
        for (int at = 0; sheet.length() < 2 * 1024 * 1024; at++) sheet.append(".a").append(at).append(emoji).append(HIDDEN).append('\n');
        String script = FrameScripts.hideElements(sheet.toString());
        assertThat(FrameScripts.SCRIPT_LIMIT).isEqualTo(4 * 1024 * 1024);
        assertThat(script.length()).isLessThanOrEqualTo(FrameScripts.SCRIPT_LIMIT);
        String prefix = FrameScripts.HIDE_ELEMENTS + "\n(";
        assertThat(script).startsWith(prefix).endsWith(");");
        String kept = (String) StrictJson.array("[" + script.substring(prefix.length(), script.length() - 2) + "]", 1).orElseThrow().getFirst();
        assertThat(kept).as("whole lines from the sheet's start").isNotEmpty().endsWith(HIDDEN + "\n");
        assertThat(sheet.toString()).startsWith(kept);
        assertThat(kept.length()).isLessThan(sheet.length());
        String next = sheet.substring(kept.length(), sheet.indexOf("\n", kept.length()) + 1);
        assertThat(prefix.length() + StrictJson.quote(kept + next).length() + 2).as("the sheet's next line does not fit")
            .isGreaterThan(FrameScripts.SCRIPT_LIMIT);
        String small = ".a" + HIDDEN + "\n.b" + HIDDEN + "\n";
        assertThat(FrameScripts.hideElements(small)).as("a sheet that fits is whole").isEqualTo(prefix + StrictJson.quote(small) + ");");
    }

    /**
     * Ruling V39: a main frame whose sheet is the generic sheet a sub-frame was given, as a page with no rules of its own
     * hides, reuses that script: the sheet is quoted once.
     */
    @Test void aMainFrameWhoseSheetIsTheGenericOneReusesItsScript() {
        RuleSet rules = rules("[{\"trigger\": {\"url-filter\": \".*\"}, \"action\": {\"type\": \"css-display-none\", \"selector\": \".ad\"}}]");
        var quoted = new ArrayList<String>();
        var hiding = new FrameScripts.Hiding(() -> Optional.of(rules), sheet -> {
            quoted.add(sheet);
            return FrameScripts.hideElements(sheet);
        });
        String generic = hiding.script(false, "https://a.example/frame").orElseThrow().text();
        String main = hiding.script(true, "https://a.example/").orElseThrow().text();
        assertThat(main).isSameAs(generic);
        assertThat(hiding.script(false, "https://b.example/frame").orElseThrow().text()).isSameAs(generic);
        assertThat(quoted).as("quoted once").containsExactly(".ad" + HIDDEN + "\n");
    }

    private static RuleSet rules(String list) {
        return RuleSet.of(ContentRules.compile(BlockerJson.read(list)).rules());
    }

    /** A page whose stored decisions come from {@code stored}, by site, recording each site and permission it is asked. */
    private static Page page(Map<String, Decision> stored, List<String> asked, PageContext context) {
        return page(stored, new HashMap<>(), asked, context);
    }

    /** As {@link #page(Map, List, PageContext)}, its stored audio playback decisions from {@code audio}. */
    private static Page page(Map<String, Decision> stored, Map<String, Decision> audio, List<String> asked, PageContext context) {
        Page page = new Page(URI.create(SITE + "/"), () -> { }, PageTestAccess.noFavicons(), Runnable::run, context);
        page.privacy().onStoredDecision((origin, permission) -> {
            asked.add(origin + " " + permission.token());
            return switch (permission) {
                case NOTIFICATIONS -> Optional.ofNullable(stored.get(origin));
                case AUDIO_PLAYBACK -> Optional.ofNullable(audio.get(origin));
                default -> Optional.empty();
            };
        });
        return page;
    }

    /** A frame showing {@code url} that records each script it runs, by its address and text, and each read of its address. */
    private static CefFrame frame(boolean main, String url, List<String> ran, List<String> read) {
        return (CefFrame) Proxy.newProxyInstance(CefFrame.class.getClassLoader(), new Class<?>[] {CefFrame.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "isMain" -> { return main; }
                case "getURL" -> {
                    read.add(url);
                    return url;
                }
                case "executeJavaScript" -> ran.add(args[1] + " " + args[0]);
                default -> throw new UnsupportedOperationException(method.getName());
            }
            return null;
        });
    }

    /**
     * A frame named {@code id} that Chromium lists, showing {@code url}: it records each script it runs, with its name,
     * and its handle's release; a {@code broken} one fails to run any.
     */
    private static CefFrame frame(String id, String url, List<String> ran, List<String> disposed, boolean broken) {
        return (CefFrame) Proxy.newProxyInstance(CefFrame.class.getClassLoader(), new Class<?>[] {CefFrame.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "isValid" -> { return true; }
                case "getURL" -> { return url; }
                case "dispose" -> disposed.add(id);
                case "executeJavaScript" -> {
                    if (broken) throw new IllegalStateException("broken frame");
                    ran.add(id + " " + args[1] + " " + args[0]);
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
            return null;
        });
    }

    /** A browser listing the frames {@code listed}, each found in {@code frames}, or none for one gone meanwhile. */
    private static CefBrowser browser(Vector<String> listed, Map<String, CefFrame> frames) {
        return (CefBrowser) Proxy.newProxyInstance(CefBrowser.class.getClassLoader(), new Class<?>[] {CefBrowser.class}, (proxy, method, args) ->
            switch (method.getName()) {
                case "getFrameIdentifiers" -> listed;
                case "getFrameByIdentifier" -> frames.get((String) args[0]);
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static void onUiThread(Runnable test) throws Exception {
        var failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                test.run();
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        if (failure.get() instanceof Error error) throw error;
        if (failure.get() instanceof Exception exception) throw exception;
    }
}
