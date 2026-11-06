package dev.jasper.browser.page;

import dev.jasper.browser.permissions.Decision;
import dev.jasper.browser.permissions.Permission;
import java.awt.Point;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.cef.browser.CefFrame;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The scripts a page's frames run as their documents start loading (spec section 12): a main frame gets
 * {@code notifications.js} with the permission its item's store holds for the document's site, quoted into the call,
 * and no other frame gets it, nor a challenge frame, nor a browser that is no page's. Chromium's side is played by the
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

    /** Ruling V16: the document's site is its address's origin, the top-level site's, and its store decides. */
    @Test void theDocumentsStateIsWhatItsItemsStoreHoldsForItsSite() throws Exception {
        onUiThread(() -> {
            var stored = new HashMap<String, Decision>();
            var asked = new ArrayList<String>();
            Page page = page(stored, asked);
            assertThat(FrameScripts.state("https://Chat.Example:443/room?x=1#top", page.privacy())).as("nothing stored").isEqualTo("default");
            assertThat(asked).containsExactly(SITE + " notifications");
            stored.put(SITE, Decision.ALLOWED);
            assertThat(FrameScripts.state(SITE + "/", page.privacy())).isEqualTo("granted");
            stored.put(SITE, Decision.BLOCKED);
            assertThat(FrameScripts.state(SITE + "/", page.privacy())).isEqualTo("denied");
            stored.put("https://chat.example:8443", Decision.ALLOWED);
            assertThat(FrameScripts.state("https://chat.example:8443/", page.privacy())).as("another origin").isEqualTo("granted");
            asked.clear();
            for (String noSite : new String[] {"about:blank", "data:text/html,hi", "jasper-about:welcome", "", null})
                assertThat(FrameScripts.state(noSite, page.privacy())).as(String.valueOf(noSite)).isEqualTo("denied");
            assertThat(asked).as("no site asks no store").isEmpty();
            page.close();
            assertThat(FrameScripts.state(SITE + "/", page.privacy())).as("a closed page's store is no longer asked").isEqualTo("default");
        });
    }

    /**
     * The polyfill runs in a page's top frame, never in a sub-frame (plan 6's call) or a challenge frame (spec section
     * 5), and a page's DevTools get none.
     */
    @Test void onlyAPagesMainFrameGetsTheNotificationsScriptAndNeverAChallengeFrame() throws Exception {
        onUiThread(() -> {
            var stored = new HashMap<String, Decision>(Map.of(SITE, Decision.ALLOWED));
            Page page = page(stored, new ArrayList<>());
            PageBrowser browser = PageTestAccess.pageBrowser(page);
            var ran = new ArrayList<String>();
            FrameScripts.loadStarting(browser, frame(true, SITE + "/room", ran));
            assertThat(ran).containsExactly("jasper-about:notifications.js " + FrameScripts.notifications("granted"));
            ran.clear();
            FrameScripts.loadStarting(browser, frame(false, SITE + "/frame", ran));
            FrameScripts.loadStarting(browser, frame(false, "https://challenges.cloudflare.com/turnstile", ran));
            FrameScripts.loadStarting(browser, frame(true, "https://challenges.cloudflare.com/turnstile", ran));
            FrameScripts.loadStarting(browser, frame(true, "https://www.google.com/recaptcha/api2/anchor", ran));
            assertThat(ran).as("sub-frames and challenge frames").isEmpty();
            FrameScripts.loadStarting(PageTestAccess.devToolsBrowser(PageTestAccess.devTools(page, new Point())), frame(true, SITE + "/", ran));
            assertThat(ran).as("a page's DevTools").isEmpty();
            FrameScripts.loadStarting(browser, frame(true, "about:blank", ran));
            assertThat(ran).as("a document without a site is denied").containsExactly("jasper-about:notifications.js "
                + FrameScripts.notifications("denied"));
        });
    }

    /** A page whose stored decisions come from {@code stored}, by site, recording each site and permission it is asked. */
    private static Page page(Map<String, Decision> stored, List<String> asked) {
        Page page = new Page(URI.create(SITE + "/"), () -> { }, PageTestAccess.noFavicons(), Runnable::run, PageContext.persistent());
        page.privacy().onStoredDecision((origin, permission) -> {
            asked.add(origin + " " + permission.token());
            return permission == Permission.NOTIFICATIONS ? Optional.ofNullable(stored.get(origin)) : Optional.empty();
        });
        return page;
    }

    /** A frame showing {@code url} that records each script it runs, by its address and text. */
    private static CefFrame frame(boolean main, String url, List<String> ran) {
        return (CefFrame) Proxy.newProxyInstance(CefFrame.class.getClassLoader(), new Class<?>[] {CefFrame.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "isMain" -> { return main; }
                case "getURL" -> { return url; }
                case "executeJavaScript" -> ran.add(args[1] + " " + args[0]);
                default -> throw new UnsupportedOperationException(method.getName());
            }
            return null;
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
