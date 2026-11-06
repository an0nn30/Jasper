package dev.jasper.browser.page;

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
            FrameScripts.loadStarting(PageTestAccess.pageBrowser(page), frame(true, SITE + "/room", ran, new ArrayList<>()));
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
            FrameScripts.loadStarting(browser, frame(true, SITE + "/room", ran, new ArrayList<>()));
            assertThat(ran).containsExactly("jasper-about:notifications.js " + FrameScripts.notifications("default"),
                "jasper-about:audio.js " + FrameScripts.audio(AudioMode.BLOCK));
            assertThat(page.privacy().audioMode()).as("the page's current mode").isEqualTo(AudioMode.BLOCK);
            audio.clear();
            ran.clear();
            FrameScripts.loadStarting(browser, frame(true, SITE + "/next", ran, new ArrayList<>()));
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
            FrameScripts.loadStarting(browser, frame(true, SITE + "/room", ran, new ArrayList<>()));
            ran.clear();
            audio.put("https://player.example", Decision.ALLOWED);
            FrameScripts.loadStarting(browser, frame(false, "https://player.example/embed", ran, new ArrayList<>()));
            assertThat(ran).as("the top-level site's, not its own").containsExactly("jasper-about:audio.js " + FrameScripts.audio(AudioMode.BLOCK));
            page.privacy().mute(false);
            FrameScripts.loadStarting(browser, frame(false, "https://player.example/next", ran, new ArrayList<>()));
            page.privacy().mute(true);
            FrameScripts.loadStarting(browser, frame(false, "https://player.example/again", ran, new ArrayList<>()));
            assertThat(ran).containsExactly("jasper-about:audio.js " + FrameScripts.audio(AudioMode.BLOCK),
                "jasper-about:audio.js " + FrameScripts.audio(AudioMode.ALLOW), "jasper-about:audio.js " + FrameScripts.audio(AudioMode.BLOCK));
            ran.clear();
            FrameScripts.loadStarting(browser, frame(false, "https://challenges.cloudflare.com/turnstile", ran, new ArrayList<>()));
            FrameScripts.loadStarting(browser, frame(false, "https://newassets.hcaptcha.com/captcha/v1/audio", ran, new ArrayList<>()));
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
            FrameScripts.loadStarting(browser, frame(true, SITE + "/room", ran, read));
            assertThat(ran).containsExactly("jasper-about:notifications.js " + FrameScripts.notifications("granted"),
                "jasper-about:audio.js " + FrameScripts.audio(AudioMode.ASK));
            assertThat(read).containsExactly(SITE + "/room");
            ran.clear();
            read.clear();
            FrameScripts.loadStarting(browser, frame(false, SITE + "/frame", ran, read));
            assertThat(ran).as("a sub-frame").containsExactly("jasper-about:audio.js " + FrameScripts.audio(AudioMode.ASK));
            assertThat(read).as("read once, to tell a challenge frame").containsExactly(SITE + "/frame");
            ran.clear();
            FrameScripts.loadStarting(browser, frame(false, "https://challenges.cloudflare.com/turnstile", ran, read));
            FrameScripts.loadStarting(browser, frame(true, "https://challenges.cloudflare.com/turnstile", ran, read));
            FrameScripts.loadStarting(browser, frame(true, "https://www.google.com/recaptcha/api2/anchor", ran, read));
            assertThat(ran).as("challenge frames").isEmpty();
            FrameScripts.loadStarting(PageTestAccess.devToolsBrowser(PageTestAccess.devTools(page, new Point())), frame(true, SITE + "/", ran, read));
            assertThat(ran).as("a page's DevTools").isEmpty();
            FrameScripts.loadStarting(browser, frame(true, "about:blank", ran, read));
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
