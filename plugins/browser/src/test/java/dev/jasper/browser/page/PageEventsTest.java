package dev.jasper.browser.page;

import dev.jasper.browser.permissions.AudioMode;
import dev.jasper.browser.store.CapturedLog;
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
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandler;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the client's load handler hands a page when a load fails (spec section 14): a main frame's failure, by
 * Chromium's name for its error; that a main frame's commit is the page's top address at once (plan 8's L23); that a
 * main frame's load start runs the page's scripts in a page's browser only, never in its DevTools', and its login
 * script (plan 8) only in an http or https document once its item gave one; that the editing keys reach both; and that
 * {@code jasper-about:} answers a page's browser only. Chromium's side is played by the test: the frame it names, the
 * browsers it calls about (made without Chromium, {@link PageTestAccess#pageBrowser}), and its thread, whose hand-offs
 * to the UI thread the test runs. No Chromium.
 */
class PageEventsTest {
    private static final String FAILED = "https://unknown.example/";

    @Test void aMainFramesFailureGoesToThePageByTheErrorsName() throws Exception {
        onUiThread(() -> {
            Page page = page();
            var failures = new ArrayList<LoadFailure>();
            page.onLoadFailure(failures::add);
            var handedOff = new ArrayList<Runnable>();
            PageEvents.loadError(page, frame(true), CefLoadHandler.ErrorCode.ERR_NAME_NOT_RESOLVED, FAILED, handedOff::add);
            PageEvents.loadError(page, frame(true), null, FAILED, handedOff::add);
            assertThat(failures).as("told on the UI thread").isEmpty();
            handedOff.forEach(Runnable::run);
            assertThat(failures).containsExactly(new LoadFailure(FAILED, "ERR_NAME_NOT_RESOLVED", Optional.empty()),
                new LoadFailure(FAILED, "", Optional.empty()));
        });
    }

    @Test void aSubFramesFailureOrOneWithoutAFrameOrAPageIsNotTold() throws Exception {
        onUiThread(() -> {
            Page page = page();
            var handedOff = new ArrayList<Runnable>();
            PageEvents.loadError(page, frame(false), CefLoadHandler.ErrorCode.ERR_CONNECTION_REFUSED, FAILED, handedOff::add);
            PageEvents.loadError(page, null, CefLoadHandler.ErrorCode.ERR_CONNECTION_REFUSED, FAILED, handedOff::add);
            PageEvents.loadError(null, frame(true), CefLoadHandler.ErrorCode.ERR_CONNECTION_REFUSED, FAILED, handedOff::add);
            assertThat(handedOff).isEmpty();
        });
    }

    /**
     * Plan 8's L23: a main frame's commit is the page's top address at once, on Chromium's thread, before the page
     * hears of the commit on the UI thread, so a sign-in asked meanwhile is judged against the committed document; a
     * sub-frame's commit, and one without a page, change nothing.
     */
    @Test void aMainFramesCommitIsThePagesTopAddressBeforeItsHandOffRuns() throws Exception {
        onUiThread(() -> {
            Page page = page();
            page.mainFrameRequested("https://bank.example/account");
            var handedOff = new ArrayList<Runnable>();
            PageEvents.addressChanged(page, frame(false), "https://frame.example/", handedOff::add);
            PageEvents.addressChanged(null, frame(true), "https://other.example/", handedOff::add);
            assertThat(handedOff).isEmpty();
            assertThat(page.topAddress()).isEqualTo("https://bank.example/account");
            PageEvents.addressChanged(page, frame(true), "https://other.example/", handedOff::add);
            assertThat(page.topAddress()).as("before the hand-off").isEqualTo("https://other.example/");
            assertThat(page.committedAddress()).as("the page has not heard").isEmpty();
            handedOff.forEach(Runnable::run);
            assertThat(page.committedAddress()).isEqualTo("https://other.example/");
            assertThat(page.topAddress()).isEqualTo("https://other.example/");
        });
    }

    /** The page's scripts, its favicon script among them, run in a page's browser only: a page's DevTools have none. */
    @Test void aMainFramesLoadStartRunsThePagesScriptsInAPagesBrowserButNotInItsDevTools() throws Exception {
        onUiThread(() -> {
            Page page = page();
            var ran = new ArrayList<String>();
            new PageEvents.Load().onLoadStart(PageTestAccess.pageBrowser(page), recording(ran), null);
            assertThat(ran).as("the page's").containsExactly("executeJavaScript jasper-about:detect-favicon.js");
            ran.clear();
            PageBrowser devTools = PageTestAccess.devToolsBrowser(PageTestAccess.devTools(page, new Point()));
            new PageEvents.Load().onLoadStart(devTools, recording(ran), null);
            assertThat(ran).as("its DevTools'").isEmpty();
        });
    }

    /**
     * Plan 8's F13: the login script goes only into an http or https document, never a challenge provider's, as no
     * Jasper page script does, nor a file's, Jasper's own pages', a blank, data or blob document's, or any other.
     */
    @Test void theLoginScriptRunsInHttpAndHttpsDocumentsOnly() {
        for (String address : List.of("https://login.example/", "http://intranet.example:8080/sign-in", "HTTPS://Login.Example/",
                "https://www.google.com/search?q=jasper-about%3A"))
            assertThat(PageLoginHooks.runsIn(address)).as(address).isTrue();
        for (String address : List.of("file:///tmp/sign-in.html", "jasper-about:welcome", "about:blank", "data:text/html,x",
                "blob:https://a.example/1", "https://challenges.cloudflare.com/cdn-cgi/challenge-platform/h/b/turnstile",
                "https://www.google.com/recaptcha/api2/anchor", "https://newassets.hcaptcha.com/captcha/v1/", "ftp://files.example/",
                "javascript:alert(1)", "about:srcdoc", "chrome://settings", "http:", "https://", ""))
            assertThat(PageLoginHooks.runsIn(address)).as(address).isFalse();
        assertThat(PageLoginHooks.runsIn(null)).isFalse();
    }

    /**
     * Plan 8's F13: a main frame's load start runs the page's login script there and then, on Chromium's thread, once
     * its item gave one ({@link Page#handleLogins}) and only in a web document; never after the page's close.
     */
    @Test void aMainFramesLoadStartRunsThePagesLoginScriptInAWebDocument() throws Exception {
        onUiThread(() -> {
            Page page = page();
            PageBrowser browser = PageTestAccess.pageBrowser(page);
            var ran = new ArrayList<String>();
            var sources = new HashMap<String, String>();
            new PageEvents.Load().onLoadStart(browser, recording(ran, sources, "https://login.example/"), null);
            assertThat(ran).as("before its item gave one").containsExactly("executeJavaScript jasper-about:detect-favicon.js");
            page.handleLogins("login();", query -> { });
            for (String address : List.of("https://login.example/", "http://intranet.example/")) {
                ran.clear();
                new PageEvents.Load().onLoadStart(browser, recording(ran, sources, address), null);
                assertThat(ran).as(address).containsExactly("executeJavaScript jasper-about:detect-favicon.js",
                    "executeJavaScript jasper-about:login-fields.js");
                assertThat(sources).containsEntry("jasper-about:login-fields.js", "login();");
            }
            for (String address : List.of("jasper-about:welcome", "file:///tmp/sign-in.html", "about:blank",
                    "https://challenges.cloudflare.com/cdn-cgi/challenge-platform/h/b/turnstile")) {
                ran.clear();
                new PageEvents.Load().onLoadStart(browser, recording(ran, sources, address), null);
                assertThat(ran).as(address).containsExactly("executeJavaScript jasper-about:detect-favicon.js");
            }
            PageBrowser devTools = PageTestAccess.devToolsBrowser(PageTestAccess.devTools(page, new Point()));
            ran.clear();
            new PageEvents.Load().onLoadStart(devTools, recording(ran, sources, "https://login.example/"), null);
            assertThat(ran).as("its DevTools'").isEmpty();
            page.close();
            ran.clear();
            new PageEvents.Load().onLoadStart(browser, recording(ran, sources, "https://login.example/"), null);
            assertThat(ran).as("after the page's close").containsExactly("executeJavaScript jasper-about:detect-favicon.js");
        });
    }

    /** Plan 8's F13: a login script Chromium cannot run is logged, as the favicon script's is, and the load goes on. */
    @Test void aLoginScriptThatCannotRunIsLoggedAndTheLoadGoesOn() throws Exception {
        var made = new AtomicReference<Page>();
        var starts = new ArrayList<String>();
        onUiThread(() -> {
            Page page = page();
            page.onLoadStart(starts::add);
            page.handleLogins("login();", query -> { });
            made.set(page);
        });
        var ran = new ArrayList<String>();
        var failing = (CefFrame) Proxy.newProxyInstance(CefFrame.class.getClassLoader(), new Class<?>[] {CefFrame.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "isMain" -> true;
                case "getURL" -> "https://login.example/";
                case "executeJavaScript" -> {
                    if ("jasper-about:login-fields.js".equals(args[1])) throw new IllegalStateException("the frame has gone");
                    ran.add("executeJavaScript " + args[1]);
                    yield null;
                }
                default -> null;
            });
        try (var log = new CapturedLog(PageEvents.class.getName())) {
            new PageEvents.Load().onLoadStart(PageTestAccess.pageBrowser(made.get()), failing, null);
            assertThat(log.records()).singleElement().satisfies(record -> {
                assertThat(record.getMessage()).isEqualTo("A page's login script could not run");
                assertThat(record.getThrown()).hasMessage("the frame has gone");
            });
        }
        assertThat(ran).containsExactly("executeJavaScript jasper-about:detect-favicon.js");
        onUiThread(() -> assertThat(starts).as("the page heard the load start").containsExactly("https://login.example/"));
    }

    /**
     * Review round 1, item 2: a main frame's load start hands its page, on the UI thread, the document its hooks just
     * started on Chromium's thread, which the page's muting then acts on; a muting meant for that document changes nothing
     * once Chromium has started the next, before the UI thread hears of it.
     */
    @Test void aMainFramesLoadStartHandsThePageTheDocumentItsHooksStarted() throws Exception {
        var made = new AtomicReference<Page>();
        onUiThread(() -> made.set(page()));
        Page page = made.get();
        // As FrameScripts' hook starts the document's audio, inside the load start, on Chromium's thread.
        PageTestAccess.audioDocumentStarted(page, AudioMode.ASK);
        new PageEvents.Load().onLoadStart(PageTestAccess.pageBrowser(page), recording(new ArrayList<>()), null);
        onUiThread(() -> {
            page.privacy().mute(false);
            assertThat(page.privacy().audioMode()).as("the document the UI thread heard of").isEqualTo(AudioMode.ALLOW);
        });
        PageTestAccess.audioDocumentStarted(page, AudioMode.ASK);
        onUiThread(() -> {
            page.privacy().mute(true);
            assertThat(page.privacy().audioMode()).as("the next, which it has not heard of").isEqualTo(AudioMode.ASK);
        });
    }

    /** On macOS Chromium leaves these keys to the Edit menu: they reach a page's focused frame and its DevTools'. */
    @Test void theEditingKeysReachAPagesBrowserAndItsDevToolsAndNoOtherBrowser() throws Exception {
        onUiThread(() -> {
            Page page = page();
            var ran = new ArrayList<String>();
            assertThat(PageEvents.edit(PageTestAccess.pageBrowser(page), Optional.of(EditingCommand.SELECT_ALL), browser -> recording(ran)))
                .isTrue();
            PageBrowser devTools = PageTestAccess.devToolsBrowser(PageTestAccess.devTools(page, new Point()));
            assertThat(PageEvents.edit(devTools, Optional.of(EditingCommand.CUT), browser -> recording(ran))).isTrue();
            assertThat(ran).containsExactly("selectAll", "dispose", "cut", "dispose");
            ran.clear();
            CefBrowser other = (CefBrowser) Proxy.newProxyInstance(CefBrowser.class.getClassLoader(), new Class<?>[] {CefBrowser.class},
                (proxy, method, args) -> { throw new UnsupportedOperationException(method.getName()); });
            assertThat(PageEvents.edit(other, Optional.of(EditingCommand.UNDO), browser -> recording(ran))).as("no page's").isFalse();
            assertThat(PageEvents.edit(devTools, Optional.empty(), browser -> recording(ran))).as("no editing key").isFalse();
            assertThat(PageEvents.edit(devTools, Optional.of(EditingCommand.REDO), browser -> null)).as("no focused frame").isFalse();
            assertThat(ran).isEmpty();
        });
    }

    /** Plan 4b's P47: {@code jasper-about:} answers a page's browser, never its DevTools' or a browser no page's. */
    @Test void jasperAboutAnswersAPagesBrowserOnly() throws Exception {
        onUiThread(() -> {
            Page page = page();
            assertThat(PageEvents.answersAbout(PageTestAccess.pageBrowser(page))).isTrue();
            assertThat(PageEvents.answersAbout(PageTestAccess.devToolsBrowser(PageTestAccess.devTools(page, new Point()))))
                .as("its DevTools'").isFalse();
            CefBrowser other = (CefBrowser) Proxy.newProxyInstance(CefBrowser.class.getClassLoader(), new Class<?>[] {CefBrowser.class},
                (proxy, method, args) -> { throw new UnsupportedOperationException(method.getName()); });
            assertThat(PageEvents.answersAbout(other)).as("no page's").isFalse();
        });
    }

    /** A main frame that records what it is asked to run and do, by method name and, for a script, its address. */
    private static CefFrame recording(List<String> ran) {
        return (CefFrame) Proxy.newProxyInstance(CefFrame.class.getClassLoader(), new Class<?>[] {CefFrame.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "isMain" -> { return true; }
                case "getURL" -> { return "https://example.com/"; }
                case "executeJavaScript" -> ran.add("executeJavaScript " + args[1]);
                default -> ran.add(method.getName());
            }
            return null;
        });
    }

    /**
     * A main frame at {@code url} that records what it is asked to run and do, as {@link #recording(List)} does, and
     * each script's source by its address in {@code sources}.
     */
    private static CefFrame recording(List<String> ran, Map<String, String> sources, String url) {
        return (CefFrame) Proxy.newProxyInstance(CefFrame.class.getClassLoader(), new Class<?>[] {CefFrame.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "isMain" -> { return true; }
                case "getURL" -> { return url; }
                case "executeJavaScript" -> {
                    ran.add("executeJavaScript " + args[1]);
                    sources.put((String) args[1], (String) args[0]);
                }
                default -> ran.add(method.getName());
            }
            return null;
        });
    }

    private static Page page() {
        return new Page(URI.create("https://example.com/"), () -> { }, PageTestAccess.noFavicons(), Runnable::run, PageContext.persistent());
    }

    /** A frame that answers only whether it is the main one. */
    private static CefFrame frame(boolean main) {
        return (CefFrame) Proxy.newProxyInstance(CefFrame.class.getClassLoader(), new Class<?>[] {CefFrame.class}, (proxy, method, args) -> {
            if (method.getName().equals("isMain")) return main;
            throw new UnsupportedOperationException(method.getName());
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
