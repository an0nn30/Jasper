package dev.jasper.browser.page;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandler;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the client's load handler hands a page when a load fails (spec section 14): a main frame's failure, by Chromium's
 * name for its error. Chromium's side is played by the test: the frame it names, and its thread, whose hand-offs to
 * the UI thread the test runs. No Chromium.
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

    private static Page page() {
        return new Page(URI.create("https://example.com/"), () -> { }, PageTestAccess.noFavicons());
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
