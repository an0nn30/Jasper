package dev.jasper.browser.engine;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
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
 */
final class AboutSchemeFactory implements CefSchemeHandlerFactory {
    private final AboutScheme pages;

    AboutSchemeFactory(AboutScheme pages) { this.pages = pages; }

    /** Registers {@code jasper-about} as standard, local, secure and CORS-enabled, before Chromium initializes. */
    static void register(CefSchemeRegistrar registrar) {
        registrar.addCustomScheme(AboutScheme.NAME, true, true, false, true, true, false, false);
    }

    @Override public CefResourceHandler create(CefBrowser browser, CefFrame frame, String scheme, CefRequest request) {
        return new Page(pages.answerLater(request.getURL()));
    }

    /**
     * One request: the status, then the body. An answer that is ready when Chromium opens the request is handled at
     * once; one still to come lets {@code open} return, so Chromium's IO thread goes on, and continues the request once it
     * comes, off that thread, unless Chromium cancelled the request meanwhile, which cancels the answer. An action's 204
     * has no body.
     */
    static final class Page extends CefResourceHandlerAdapter {
        private static final AboutScheme.Answer NOT_FOUND = new AboutScheme.Answer(404, "");
        private final CompletableFuture<AboutScheme.Answer> answer;
        // Set before Chromium is told to go on, read on its thread after.
        private volatile int status;
        private volatile byte[] body = new byte[0];
        private volatile boolean cancelled;
        private int sent;

        Page(CompletableFuture<AboutScheme.Answer> answer) {
            this.answer = answer;
        }

        @Override public boolean open(CefRequest request, BoolRef handleRequest, CefCallback callback) {
            if (answer.isDone()) {
                take(answer.isCompletedExceptionally() ? NOT_FOUND : answer.join());
                handleRequest.set(true);
                return true;
            }
            // Decided later (P5): Chromium waits for the callback. Continued from another thread, so never from within
            // this call, even when the answer comes while it runs.
            handleRequest.set(false);
            answer.whenCompleteAsync((answered, failure) -> {
                take(failure == null ? answered : NOT_FOUND);
                if (!cancelled) callback.Continue();
            });
            return true;
        }

        @Override public void getResponseHeaders(CefResponse response, IntRef responseLength, StringRef redirectUrl) {
            response.setStatus(status);
            response.setStatusText(switch (status) {
                case 200 -> "OK";
                case 204 -> "No Content";
                default -> "Not Found";
            });
            response.setMimeType("text/html");
            // The charset, which JCEF's CefResponse has no setter for.
            response.setHeaderByName("Content-Type", "text/html; charset=utf-8", true);
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
            AboutScheme.Answer given = answered == null ? NOT_FOUND : answered;
            body = given.html().getBytes(StandardCharsets.UTF_8);
            status = given.status();
        }
    }
}
