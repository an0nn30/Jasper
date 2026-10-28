package dev.jasper.browser.engine;

import java.nio.charset.StandardCharsets;
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
        return new Page(pages.answer(request.getURL()));
    }

    /** One request: the status, then the body, at once. */
    private static final class Page extends CefResourceHandlerAdapter {
        private final int status;
        private final byte[] body;
        private int sent;

        Page(AboutScheme.Answer answer) {
            status = answer.status();
            body = answer.html().getBytes(StandardCharsets.UTF_8);
        }

        @Override public boolean open(CefRequest request, BoolRef handleRequest, CefCallback callback) {
            handleRequest.set(true);
            return true;
        }

        @Override public void getResponseHeaders(CefResponse response, IntRef responseLength, StringRef redirectUrl) {
            response.setStatus(status);
            response.setStatusText(status == 200 ? "OK" : "Not Found");
            response.setMimeType("text/html");
            // The charset, which JCEF's CefResponse has no setter for.
            response.setHeaderByName("Content-Type", "text/html; charset=utf-8", true);
            responseLength.set(body.length);
        }

        @Override public boolean read(byte[] dataOut, int bytesToRead, IntRef bytesRead, CefResourceReadCallback callback) {
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
    }
}
