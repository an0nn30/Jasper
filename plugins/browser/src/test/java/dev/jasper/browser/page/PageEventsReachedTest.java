package dev.jasper.browser.page;

import dev.jasper.browser.store.Reached;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.cef.browser.CefFrame;
import org.cef.network.CefRequest.TransitionFlags;
import org.cef.network.CefRequest.TransitionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * How a main frame's load reached its page (spec section 10), as the client's load handler reads Chromium's transition:
 * its qualifiers first, then its source. JCEF ORs each load's qualifiers into the shared {@link TransitionType} constant
 * and never clears them (F1), so the handler clears the named ones as it reads them, whatever the load, and so does the
 * preview client's. Chromium's side is played by the test: the frame and its thread. No Chromium.
 */
class PageEventsReachedTest {
    private static final int FORWARD_BACK = TransitionFlags.TT_FORWARD_BACK_FLAG.getValue();
    private static final int CLIENT_REDIRECT = TransitionFlags.TT_CLIENT_REDIRECT_FLAG.getValue();
    private static final int SERVER_REDIRECT = TransitionFlags.TT_SERVER_REDIRECT_FLAG.getValue();
    private static final int CHAIN = TransitionFlags.TT_CHAIN_START_FLAG.getValue() | TransitionFlags.TT_CHAIN_END_FLAG.getValue();

    /** The constants are the JVM's own: none keeps a flag a failed test left on it. */
    @AfterEach void clearTheConstants() {
        for (TransitionType type : TransitionType.values())
            for (TransitionFlags flag : TransitionFlags.values()) type.removeQualifier(flag);
    }

    @Test void forwardBackAndRedirectsComeBeforeTheSource() {
        assertThat(PageEvents.reached(TransitionType.TT_LINK.getSource(), FORWARD_BACK | CHAIN)).isEqualTo(Reached.BACK_FORWARD);
        assertThat(PageEvents.reached(TransitionType.TT_EXPLICIT.getSource(), SERVER_REDIRECT | CHAIN)).isEqualTo(Reached.REDIRECT);
        assertThat(PageEvents.reached(TransitionType.TT_LINK.getSource(), CLIENT_REDIRECT)).isEqualTo(Reached.REDIRECT);
        assertThat(PageEvents.reached(TransitionType.TT_RELOAD.getSource(), FORWARD_BACK | SERVER_REDIRECT)).isEqualTo(Reached.BACK_FORWARD);
    }

    @Test void theFourSourcesMapToTheirWays() {
        assertThat(PageEvents.reached(TransitionType.TT_RELOAD.getSource(), 0)).isEqualTo(Reached.RELOAD);
        assertThat(PageEvents.reached(TransitionType.TT_FORM_SUBMIT.getSource(), CHAIN)).isEqualTo(Reached.FORM_SUBMIT);
        assertThat(PageEvents.reached(TransitionType.TT_LINK.getSource(), CHAIN)).isEqualTo(Reached.LINK);
        assertThat(PageEvents.reached(TransitionType.TT_EXPLICIT.getSource(), CHAIN)).as("the address field's and Jasper's own loads")
            .isEqualTo(Reached.TYPED);
    }

    @Test void anyOtherSourceIsOther() {
        assertThat(PageEvents.reached(TransitionType.TT_AUTO_SUBFRAME.getSource(), 0)).isEqualTo(Reached.OTHER);
        assertThat(PageEvents.reached(TransitionType.TT_MANUAL_SUBFRAME.getSource(), CHAIN)).isEqualTo(Reached.OTHER);
        for (int source : List.of(2, 5, 6, 9, 10)) assertThat(PageEvents.reached(source, 0)).as("source %d", source).isEqualTo(Reached.OTHER);
    }

    /** F1: a flag a Back left on TT_LINK reads once, with that load, and never with the next link. */
    @Test void aLoadStartReadsItsTransitionAndClearsTheFlagsJcefLeftOnIt() throws Exception {
        Page page = onUiThread(() -> new Page(URI.create("https://a.example/"), () -> { }, PageTestAccess.noFavicons(), Runnable::run, PageContext.persistent()));
        var visits = new CopyOnWriteArrayList<Reached>();
        onUiThread(() -> page.onVisit((url, reached) -> visits.add(reached)));
        PageBrowser browser = PageTestAccess.pageBrowser(page);
        var load = new PageEvents.Load();
        TransitionType.TT_LINK.addQualifier(TransitionFlags.TT_FORWARD_BACK_FLAG);
        load.onLoadStart(browser, frame(true), TransitionType.TT_LINK);
        assertThat(TransitionType.TT_LINK.getQualifiers()).as("cleared as it is read").isZero();
        load.onLoadStart(browser, frame(true), TransitionType.TT_LINK);
        TransitionType.TT_AUTO_SUBFRAME.addQualifier(TransitionFlags.TT_SERVER_REDIRECT_FLAG);
        load.onLoadStart(browser, frame(false), TransitionType.TT_AUTO_SUBFRAME);
        assertThat(TransitionType.TT_AUTO_SUBFRAME.getQualifiers()).as("a sub-frame's cleared too").isZero();
        TransitionType.TT_EXPLICIT.addQualifier(TransitionFlags.TT_CLIENT_REDIRECT_FLAG);
        load.onLoadStart(browser, null, TransitionType.TT_EXPLICIT);
        assertThat(TransitionType.TT_EXPLICIT.getQualifiers()).as("a load without a frame's cleared too").isZero();
        load.onLoadStart(browser, frame(true), null);
        onUiThread(() -> null);
        assertThat(visits).as("the main frame's, on the UI thread; a transition JCEF does not map is OTHER")
            .containsExactly(Reached.BACK_FORWARD, Reached.LINK, Reached.OTHER);
    }

    /**
     * Review item 4: the preview client's load starts use the same shared constants, so its load handler clears them too,
     * whatever the browser and frame.
     */
    @Test void thePreviewClientsLoadStartClearsTheFlagsToo() {
        TransitionType.TT_LINK.addQualifier(TransitionFlags.TT_FORWARD_BACK_FLAG);
        TransitionType.TT_LINK.addQualifier(TransitionFlags.TT_SERVER_REDIRECT_FLAG);
        new PreviewHandlers.Loads().onLoadStart(null, null, TransitionType.TT_LINK);
        assertThat(TransitionType.TT_LINK.getQualifiers()).isZero();
        new PreviewHandlers.Loads().onLoadStart(null, frame(true), null);
    }

    /** A frame that answers whether it is the main one and its address, and runs nothing. */
    private static CefFrame frame(boolean main) {
        return (CefFrame) Proxy.newProxyInstance(CefFrame.class.getClassLoader(), new Class<?>[] {CefFrame.class}, (proxy, method, args) ->
            switch (method.getName()) {
                case "isMain" -> main;
                case "getURL" -> "https://a.example/";
                default -> null;
            });
    }

    private static <T> T onUiThread(java.util.concurrent.Callable<T> task) throws Exception {
        var result = new AtomicReference<T>();
        var failure = new AtomicReference<Exception>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(task.call());
            } catch (Exception thrown) {
                failure.set(thrown);
            }
        });
        if (failure.get() != null) throw failure.get();
        return result.get();
    }
}
