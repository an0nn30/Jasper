package dev.jasper.browser.page;

import java.awt.Point;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandler;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the client's load handler hands a page when a load fails (spec section 14): a main frame's failure, by Chromium's
 * name for its error; that a main frame's load start runs the page's scripts in a page's browser only, never in its
 * DevTools'; that the editing keys reach both; and that {@code jasper-about:} answers a page's browser only. Chromium's
 * side is played by the test: the frame it names, the browsers it calls about (made without Chromium,
 * {@link PageTestAccess#pageBrowser}), and its thread, whose hand-offs to the UI thread the test runs. No Chromium.
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
