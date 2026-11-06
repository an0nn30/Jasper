package dev.jasper.browser.engine;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
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
 * Chromium's side of {@link AboutScheme}: the scheme's registration and the handler that answers each request from the
 * registry. Loaded only once Chromium starts.
 *
 * <p>Plan 4b's P47: only a main-frame request of a page's browser is answered from the registry. Every other request, a
 * sub-frame's, an image's, a script's or a fetch's, and any of a browser no page's, such as a page's DevTools, or of
 * none, is answered 404 with an empty body and runs no action: no Jasper page loads a {@code jasper-about:} address but
 * as its main frame, so a local page's {@code <img>} or {@code <iframe>} can neither run an action nor have a source
 * page built.
 *
 * <p>A page's redirect is answered 302, its address handed to Chromium as the request's redirect, with no body;
 * Chromium then requests that address in its place.
 */
final class AboutSchemeFactory implements CefSchemeHandlerFactory {
    private final AboutScheme pages;
    private final Predicate<CefBrowser> pageBrowser;

    /**
     * The factory answering from {@code pages}.
     *
     * @param pages the registry
     * @param pageBrowser whether a browser is a page's, asked on Chromium's IO thread
     */
    AboutSchemeFactory(AboutScheme pages, Predicate<CefBrowser> pageBrowser) {
        this.pages = Objects.requireNonNull(pages, "pages");
        this.pageBrowser = Objects.requireNonNull(pageBrowser, "pageBrowser");
    }

    /** Registers {@code jasper-about} as standard, local, secure and CORS-enabled, before Chromium initializes. */
    static void register(CefSchemeRegistrar registrar) {
        registrar.addCustomScheme(AboutScheme.NAME, true, true, false, true, true, false, false);
    }

    @Override public CefResourceHandler create(CefBrowser browser, CefFrame frame, String scheme, CefRequest request) {
        return handler(browser, request.getResourceType(), request.getURL());
    }

    /** The handler of a request of {@code type} for {@code url} from {@code browser}, as the class says. IO thread. */
    Page handler(CefBrowser browser, CefRequest.ResourceType type, String url) {
        boolean answered = type == CefRequest.ResourceType.RT_MAIN_FRAME && browser != null && pageBrowser.test(browser);
        return new Page(answered ? pages.answerLater(url) : CompletableFuture.completedFuture(AboutScheme.NOT_FOUND));
    }

    /**
     * One request: the status, then the body. An answer that is ready when Chromium opens the request is handled at
     * once; one still to come lets {@code open} return, so Chromium's IO thread goes on, and continues the request once it
     * comes, off that thread, unless Chromium cancelled the request meanwhile, which cancels the answer. An action's 204
     * and a redirect's 302 have no body.
     */
    static final class Page extends CefResourceHandlerAdapter {
        private final CompletableFuture<AboutScheme.Answer> answer;
        // Set before Chromium is told to go on, read on its thread after.
        private volatile int status;
        private volatile byte[] body = new byte[0];
        private volatile String location;
        private volatile boolean cancelled;
        private int sent;

        Page(CompletableFuture<AboutScheme.Answer> answer) {
            this.answer = answer;
        }

        @Override public boolean open(CefRequest request, BoolRef handleRequest, CefCallback callback) {
            if (answer.isDone()) {
                take(answer.isCompletedExceptionally() ? AboutScheme.NOT_FOUND : answer.join());
                handleRequest.set(true);
                return true;
            }
            // Decided later (P5): Chromium waits for the callback. Continued from another thread, so never from within
            // this call, even when the answer comes while it runs.
            handleRequest.set(false);
            answer.whenCompleteAsync((answered, failure) -> {
                take(failure == null ? answered : AboutScheme.NOT_FOUND);
                if (!cancelled) callback.Continue();
            });
            return true;
        }

        @Override public void getResponseHeaders(CefResponse response, IntRef responseLength, StringRef redirectUrl) {
            response.setStatus(status);
            response.setStatusText(switch (status) {
                case 200 -> "OK";
                case 204 -> "No Content";
                case 302 -> "Found";
                default -> "Not Found";
            });
            response.setMimeType("text/html");
            // The charset, which JCEF's CefResponse has no setter for.
            response.setHeaderByName("Content-Type", "text/html; charset=utf-8", true);
            String to = location;
            if (to != null) redirectUrl.set(to);
            responseLength.set(body.length);
        }

        @Override public boolean read(byte[] dataOut, int bytesToRead, IntRef bytesRead, CefResourceReadCallback callback) {
            byte[] page = body;
            int count = Math.min(bytesToRead, page.length - sent);
            if (count <= 0) {
                bytesRead.set(0);
                return false;
            }
            System.arraycopy(page, sent, dataOut, 0, count);
            sent += count;
            bytesRead.set(count);
            return true;
        }

        /** Chromium ends the request: one still waiting is never continued, and its answer's work ends. */
        @Override public void cancel() {
            cancelled = true;
            answer.cancel(true);
        }

        private void take(AboutScheme.Answer answered) {
            AboutScheme.Answer given = answered == null ? AboutScheme.NOT_FOUND : answered;
            body = given.html().getBytes(StandardCharsets.UTF_8);
            location = given.location().orElse(null);
            status = given.status();
        }
    }
}
