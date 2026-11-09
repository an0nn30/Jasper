package dev.jasper.browser.engine;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefCallback;
import org.cef.callback.CefResourceReadCallback;
import org.cef.callback.CefSchemeHandlerFactory;
import org.cef.callback.CefSchemeRegistrar;
import org.cef.handler.CefResourceHandler;
import org.cef.handler.CefResourceHandlerAdapter;
import org.cef.misc.BoolRef;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;

/**
 * Chromium's side of {@link PreviewScheme}: the scheme's registration and the handler that answers each request from
 * the origins. Loaded only once Chromium starts.
 *
 * <p>Only a request of a browser on the preview client is answered from the origins, as plan 4b's P47 has
 * {@code jasper-about:} answer only a page's, and only for its own preview's origin (plan 9's N6); any other browser's,
 * such as a page's, a preview's for another origin, and a request of none, is answered 404. Every answer, 404s too,
 * carries the scheme's content security policy and forbids type sniffing, caching and referrers.
 */
final class PreviewSchemeFactory implements CefSchemeHandlerFactory {
    private final PreviewScheme origins;
    private final BiPredicate<CefBrowser, String> previewBrowser;

    /**
     * The factory answering from {@code origins}.
     *
     * @param origins the previews' origins
     * @param previewBrowser whether a browser is a preview's on the preview client and an address on its own origin,
     *     asked on Chromium's IO thread
     */
    PreviewSchemeFactory(PreviewScheme origins, BiPredicate<CefBrowser, String> previewBrowser) {
        this.origins = Objects.requireNonNull(origins, "origins");
        this.previewBrowser = Objects.requireNonNull(previewBrowser, "previewBrowser");
    }

    /**
     * Registers {@code jasper-preview} before Chromium initializes: standard, so each token is an origin of its own;
     * display-isolated, so no other scheme's page can link to it or frame it; secure; and fetch-enabled, so its shell can
     * fetch its documents. Not local, which would hold its fetches to file rules, and neither CORS-enabled nor
     * CSP-bypassing.
     */
    static void register(CefSchemeRegistrar registrar) {
        registrar.addCustomScheme(PreviewScheme.NAME, true, false, true, true, false, false, true);
    }

    @Override public CefResourceHandler create(CefBrowser browser, CefFrame frame, String scheme, CefRequest request) {
        return handler(browser, request.getMethod(), request.getURL());
    }

    /** The handler of a {@code method} request for {@code url} from {@code browser}, as the class says. IO thread. */
    Resource handler(CefBrowser browser, String method, String url) {
        boolean answered = browser != null && previewBrowser.test(browser, url);
        return new Resource(answered ? origins.answer(method, url) : CompletableFuture.completedFuture(PreviewScheme.NOT_FOUND));
    }

    /**
     * One request: the status and headers, then the body. An answer that is ready when Chromium opens the request, as
     * all are but a file of the plugin's on its first read, is handled at once; one still to come lets {@code open}
     * return, so Chromium's IO thread goes on, and continues the request once it comes, off that thread, unless Chromium
     * cancelled the request meanwhile, which cancels the answer but not the file's read (as
     * {@link AboutSchemeFactory.Page} answers a page that comes later).
     */
    static final class Resource extends CefResourceHandlerAdapter {
        private final CompletableFuture<PreviewScheme.Answer> later;
        // Set before Chromium is told to go on, read on its thread after.
        private volatile PreviewScheme.Answer answer = PreviewScheme.NOT_FOUND;
        private volatile boolean cancelled;
        private int sent;

        Resource(CompletableFuture<PreviewScheme.Answer> later) {
            this.later = later;
        }

        @Override public boolean open(CefRequest request, BoolRef handleRequest, CefCallback callback) {
            if (later.isDone()) {
                take(later.isCompletedExceptionally() ? PreviewScheme.NOT_FOUND : later.join());
                handleRequest.set(true);
                return true;
            }
            // Decided later: Chromium waits for the callback. Continued from another thread, so never from within this
            // call, even when the answer comes while it runs.
            handleRequest.set(false);
            later.whenCompleteAsync((answered, failure) -> {
                take(failure == null ? answered : PreviewScheme.NOT_FOUND);
                if (!cancelled) callback.Continue();
            });
            return true;
        }

        @Override public void getResponseHeaders(CefResponse response, IntRef responseLength, StringRef redirectUrl) {
            PreviewScheme.Answer answer = this.answer;
            response.setStatus(answer.status());
            response.setStatusText(answer.status() == 200 ? "OK" : "Not Found");
            response.setMimeType(answer.mimeType());
            // The charset, which JCEF's CefResponse has no setter for.
            response.setHeaderByName("Content-Type",
                answer.mimeType().startsWith("text/") ? answer.mimeType() + "; charset=utf-8" : answer.mimeType(), true);
            response.setHeaderByName("Content-Security-Policy", PreviewScheme.POLICY, true);
            response.setHeaderByName("X-Content-Type-Options", "nosniff", true);
            response.setHeaderByName("Cache-Control", "no-store", true);
            response.setHeaderByName("Referrer-Policy", "no-referrer", true);
            responseLength.set(answer.body().length);
        }

        @Override public boolean read(byte[] dataOut, int bytesToRead, IntRef bytesRead, CefResourceReadCallback callback) {
            byte[] body = answer.body();
            int count = Math.min(bytesToRead, body.length - sent);
            if (count <= 0) {
                bytesRead.set(0);
                return false;
            }
            System.arraycopy(body, sent, dataOut, 0, count);
            sent += count;
            bytesRead.set(count);
            return true;
        }

        /** Chromium ends the request: one still waiting is never continued, and its answer is cancelled. */
        @Override public void cancel() {
            cancelled = true;
            later.cancel(true);
        }

        private void take(PreviewScheme.Answer answered) {
            answer = answered == null ? PreviewScheme.NOT_FOUND : answered;
        }
    }
}
