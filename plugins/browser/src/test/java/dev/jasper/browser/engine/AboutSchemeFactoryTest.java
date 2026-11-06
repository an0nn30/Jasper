package dev.jasper.browser.engine;

import java.awt.EventQueue;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.cef.browser.CefBrowser;
import org.cef.callback.CefCallback;
import org.cef.handler.CefLoadHandler;
import org.cef.misc.BoolRef;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest.ResourceType;
import org.cef.network.CefResponse;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chromium's handler of one {@code jasper-about:} request (P5), driven as Chromium's IO thread drives it, without
 * Chromium: an answer ready at once is handled at once, and one still to come lets {@code open} return and continues
 * the request once it arrives, never after the request was cancelled; only a page's main-frame request is answered
 * (plan 4b's P47); and a page's redirect is Chromium's to follow (plan 5's Task 3).
 */
class AboutSchemeFactoryTest {
    @Test void anAnswerReadyAtOnceIsHandledAtOnceAndReadAsUtf8() {
        var handler = new AboutSchemeFactory.Page(CompletableFuture.completedFuture(new AboutScheme.Answer(200, "<p>café</p>")));
        var handle = new BoolRef();
        var continued = new Calls();
        assertThat(handler.open(null, handle, continued)).isTrue();
        assertThat(handle.get()).as("handled at once").isTrue();
        assertThat(read(handler)).isEqualTo("<p>café</p>");
        assertThat(continued.count()).isZero();
    }

    @Test void anAnswerStillToComeLetsOpenReturnAndContinuesTheRequestOnceItArrives() throws Exception {
        var later = new CompletableFuture<AboutScheme.Answer>();
        var handler = new AboutSchemeFactory.Page(later);
        var handle = new BoolRef(true);
        var continued = new Calls();
        assertThat(handler.open(null, handle, continued)).isTrue();
        assertThat(handle.get()).as("decided later").isFalse();
        assertThat(continued.count()).isZero();
        later.complete(new AboutScheme.Answer(200, "<p>source</p>"));
        assertThat(continued.awaitContinue()).isTrue();
        assertThat(read(handler)).isEqualTo("<p>source</p>");
        assertThat(continued.count()).as("once").isEqualTo(1);
    }

    @Test void aRequestCancelledWhileItWaitsIsNeverContinuedAndItsAnswerIsCancelled() throws Exception {
        var later = new CompletableFuture<AboutScheme.Answer>();
        var handler = new AboutSchemeFactory.Page(later);
        var continued = new Calls();
        assertThat(handler.open(null, new BoolRef(), continued)).isTrue();
        handler.cancel();
        assertThat(later).isCancelled();
        assertThat(continued.awaitContinue()).as("never continued").isFalse();
    }

    /**
     * P47: only a main-frame request of a browser the pages name is answered. A sub-frame's, an image's, a script's or a
     * fetch's, and any of another browser, such as a page's DevTools, or of none, is handled at once with 404 and an
     * empty body, and runs no action.
     */
    @Test void onlyAPagesMainFrameRequestIsAnsweredOrRunsAnAction() throws Exception {
        var about = new AboutScheme();
        var runs = new AtomicInteger();
        about.register("welcome", () -> "<p>welcome</p>");
        about.registerAction("settings", () -> runs.incrementAndGet());
        CefBrowser page = browser(), devTools = browser();
        var factory = new AboutSchemeFactory(about, browser -> browser == page);
        assertThat(opened(factory.handler(page, ResourceType.RT_MAIN_FRAME, "jasper-about://welcome/"))).isEqualTo("<p>welcome</p>");
        for (ResourceType type : List.of(ResourceType.RT_SUB_FRAME, ResourceType.RT_IMAGE, ResourceType.RT_SCRIPT,
                ResourceType.RT_STYLESHEET, ResourceType.RT_XHR, ResourceType.RT_FAVICON, ResourceType.RT_OBJECT)) {
            assertThat(opened(factory.handler(page, type, "jasper-about://welcome/"))).as(type.name()).isEmpty();
            opened(factory.handler(page, type, "jasper-about://settings/"));
        }
        assertThat(opened(factory.handler(devTools, ResourceType.RT_MAIN_FRAME, "jasper-about://welcome/"))).as("no page's").isEmpty();
        assertThat(opened(factory.handler(null, ResourceType.RT_MAIN_FRAME, "jasper-about://welcome/"))).as("no browser's").isEmpty();
        opened(factory.handler(devTools, ResourceType.RT_MAIN_FRAME, "jasper-about://settings/"));
        opened(factory.handler(null, ResourceType.RT_MAIN_FRAME, "jasper-about://settings/"));
        EventQueue.invokeAndWait(() -> { });
        assertThat(runs).as("no action but from a page's main frame").hasValue(0);
        opened(factory.handler(page, ResourceType.RT_MAIN_FRAME, "jasper-about://settings/"));
        EventQueue.invokeAndWait(() -> { });
        assertThat(runs).hasValue(1);
    }

    /** A redirect is 302 to its address, which Chromium is handed to follow, with no body; a page is no redirect. */
    @Test void aRedirectIs302ToItsAddressWithNoBody() {
        var about = new AboutScheme();
        about.register("welcome", () -> "<p>welcome</p>");
        about.redirect("welcome", () -> Optional.of("jasper-about:onboarding-intro"));
        CefBrowser page = browser();
        var factory = new AboutSchemeFactory(about, browser -> browser == page);
        AboutSchemeFactory.Page handler = factory.handler(page, ResourceType.RT_MAIN_FRAME, "jasper-about://welcome/");
        assertThat(handler.open(null, new BoolRef(), new Calls())).isTrue();
        var response = new Response();
        var length = new IntRef(-1);
        var redirect = new StringRef();
        handler.getResponseHeaders(response, length, redirect);
        assertThat(response.status).isEqualTo(302);
        assertThat(response.statusText).isEqualTo("Found");
        assertThat(redirect.get()).isEqualTo("jasper-about:onboarding-intro");
        assertThat(length.get()).as("no body").isZero();
        assertThat(read(handler)).isEmpty();

        var shown = new AboutSchemeFactory.Page(CompletableFuture.completedFuture(new AboutScheme.Answer(200, "<p>page</p>")));
        assertThat(shown.open(null, new BoolRef(), new Calls())).isTrue();
        var page200 = new Response();
        var none = new StringRef();
        shown.getResponseHeaders(page200, new IntRef(), none);
        assertThat(page200.status).isEqualTo(200);
        assertThat(none.get()).as("no redirect").isNull();
    }

    /** The body of a request whose answer is ready, opened and read as Chromium does. */
    private static String opened(AboutSchemeFactory.Page handler) {
        var handle = new BoolRef();
        assertThat(handler.open(null, handle, new Calls())).isTrue();
        assertThat(handle.get()).as("handled at once").isTrue();
        return read(handler);
    }

    /** A browser that is only itself: the factory hands it to the pages' test. */
    private static CefBrowser browser() {
        return (CefBrowser) Proxy.newProxyInstance(CefBrowser.class.getClassLoader(), new Class<?>[] {CefBrowser.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    /** The body, read as Chromium reads it, a few bytes at a time. */
    private static String read(AboutSchemeFactory.Page handler) {
        var body = new byte[0];
        var chunk = new byte[4];
        var count = new IntRef();
        while (handler.read(chunk, chunk.length, count, null)) {
            int at = body.length;
            body = Arrays.copyOf(body, at + count.get());
            System.arraycopy(chunk, 0, body, at, count.get());
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    /** Chromium's callback for a request decided later, counting its Continues. */
    private static final class Calls implements CefCallback {
        private final AtomicInteger continues = new AtomicInteger();
        private final CountDownLatch continued = new CountDownLatch(1);

        @Override public void Continue() {
            continues.incrementAndGet();
            continued.countDown();
        }

        @Override public void cancel() {
            throw new AssertionError("Jasper cancels no request itself");
        }

        int count() {
            return continues.get();
        }

        /** Whether the request was continued within a second. */
        boolean awaitContinue() throws InterruptedException {
            return continued.await(1, TimeUnit.SECONDS);
        }
    }

    /** The response Chromium hands the handler, recording what it is given. */
    private static final class Response extends CefResponse {
        int status;
        String statusText, mimeType;
        final Map<String, String> headers = new LinkedHashMap<>();

        @Override public void dispose() { }
        @Override public boolean isReadOnly() { return false; }
        @Override public CefLoadHandler.ErrorCode getError() { return null; }
        @Override public void setError(CefLoadHandler.ErrorCode errorCode) { }
        @Override public int getStatus() { return status; }
        @Override public void setStatus(int status) { this.status = status; }
        @Override public String getStatusText() { return statusText; }
        @Override public void setStatusText(String statusText) { this.statusText = statusText; }
        @Override public String getMimeType() { return mimeType; }
        @Override public void setMimeType(String mimeType) { this.mimeType = mimeType; }
        @Override public String getHeaderByName(String name) { return headers.get(name); }
        @Override public void setHeaderByName(String name, String value, boolean overwrite) {
            if (overwrite || !headers.containsKey(name)) headers.put(name, value);
        }
        @Override public void getHeaderMap(Map<String, String> into) { into.putAll(headers); }
        @Override public void setHeaderMap(Map<String, String> from) {
            headers.clear();
            headers.putAll(from);
        }
    }
}
