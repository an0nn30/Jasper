package dev.jasper.browser.engine;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.cef.browser.CefBrowser;
import org.cef.handler.CefLoadHandler;
import org.cef.misc.BoolRef;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefResponse;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chromium's handler of one {@code jasper-preview:} request, driven as Chromium's IO thread drives it, without Chromium:
 * the scheme's registration, the headers every answer carries, 404s too, and only a preview's browser answered (N-F12,
 * as plan 4b's P47 has {@code jasper-about:} answer only a page's).
 */
class PreviewSchemeFactoryTest {
    /** N-F3: standard, display-isolated, secure and fetch-enabled; not local, not CORS-enabled and not CSP-bypassing. */
    @Test void theSchemeIsRegisteredStandardDisplayIsolatedSecureAndFetchEnabledOnly() {
        var flags = new LinkedHashMap<String, List<Boolean>>();
        PreviewSchemeFactory.register((name, standard, local, displayIsolated, secure, corsEnabled, cspBypassing, fetchEnabled) -> {
            flags.put(name, List.of(standard, local, displayIsolated, secure, corsEnabled, cspBypassing, fetchEnabled));
            return true;
        });
        assertThat(flags).containsExactly(Map.entry("jasper-preview", List.of(true, false, true, true, false, false, true)));
    }

    @Test void everyAnswerCarriesThePolicyAndTheSafetyHeaders() {
        var scheme = new PreviewScheme();
        var origin = scheme.open();
        origin.image(1, new byte[] {1, 2, 3});
        CefBrowser preview = browser();
        var factory = new PreviewSchemeFactory(scheme, (browser, url) -> browser == preview);
        var shell = new Response();
        assertThat(body(factory.handler(preview, "GET", origin.shell().toString()), shell)).startsWith("<!doctype html>");
        assertThat(shell.status).isEqualTo(200);
        assertThat(shell.statusText).isEqualTo("OK");
        assertThat(shell.mimeType).isEqualTo("text/html");
        assertThat(shell.headers).containsExactlyInAnyOrderEntriesOf(Map.of("Content-Type", "text/html; charset=utf-8",
            "Content-Security-Policy", PreviewScheme.POLICY, "X-Content-Type-Options", "nosniff", "Cache-Control", "no-store",
            "Referrer-Policy", "no-referrer"));
        var image = new Response();
        assertThat(body(factory.handler(preview, "GET", origin.shell() + "image/1.png"), image).getBytes(StandardCharsets.ISO_8859_1))
            .containsExactly(1, 2, 3);
        assertThat(image.length).isEqualTo(3);
        assertThat(image.headers).containsEntry("Content-Type", "image/png").containsEntry("Content-Security-Policy", PreviewScheme.POLICY);
        var missing = new Response();
        assertThat(body(factory.handler(preview, "GET", origin.shell() + "missing"), missing)).isEmpty();
        assertThat(missing.status).isEqualTo(404);
        assertThat(missing.statusText).isEqualTo("Not Found");
        assertThat(missing.length).isZero();
        assertThat(missing.headers).containsEntry("Content-Security-Policy", PreviewScheme.POLICY)
            .containsEntry("X-Content-Type-Options", "nosniff").containsEntry("Cache-Control", "no-store")
            .containsEntry("Referrer-Policy", "no-referrer");
    }

    /**
     * N-F12: a browser that is no preview's, such as a page's, and a request of no browser, are answered 404; and N6: a
     * preview's browser only on its own origin.
     */
    @Test void onlyAPreviewsBrowserIsAnswered() {
        var scheme = new PreviewScheme();
        var origin = scheme.open();
        String shell = origin.shell().toString(), elsewhereShell = scheme.open().shell().toString();
        CefBrowser preview = browser(), page = browser();
        var factory = new PreviewSchemeFactory(scheme, (browser, url) -> browser == preview && PreviewScheme.allows(url, origin.token()));
        var answered = new Response();
        body(factory.handler(preview, "GET", shell), answered);
        assertThat(answered.status).isEqualTo(200);
        var elsewhere = new Response();
        assertThat(body(factory.handler(preview, "GET", elsewhereShell), elsewhere)).as("another preview's shell").isEmpty();
        assertThat(elsewhere.status).isEqualTo(404);
        for (CefBrowser other : new CefBrowser[] {page, null}) {
            var refused = new Response();
            assertThat(body(factory.handler(other, "GET", shell), refused)).as(other == null ? "no browser" : "a page's").isEmpty();
            assertThat(refused.status).isEqualTo(404);
            assertThat(refused.headers).containsEntry("Content-Security-Policy", PreviewScheme.POLICY);
        }
        var posted = new Response();
        body(factory.handler(preview, "POST", shell), posted);
        assertThat(posted.status).as("N-F11: a method other than GET").isEqualTo(404);
    }

    /** Opens the request, reads its headers, then its body a few bytes at a time, as Chromium does. */
    private static String body(PreviewSchemeFactory.Resource handler, Response response) {
        var handle = new BoolRef();
        assertThat(handler.open(null, handle, null)).isTrue();
        assertThat(handle.get()).as("handled at once").isTrue();
        var length = new IntRef();
        handler.getResponseHeaders(response, length, new StringRef());
        response.length = length.get();
        var body = new byte[0];
        var chunk = new byte[4];
        var count = new IntRef();
        while (handler.read(chunk, chunk.length, count, null)) {
            int at = body.length;
            body = Arrays.copyOf(body, at + count.get());
            System.arraycopy(chunk, 0, body, at, count.get());
        }
        return new String(body, StandardCharsets.ISO_8859_1);
    }

    /** A browser that is only itself: the factory hands it to the previews' test. */
    private static CefBrowser browser() {
        return (CefBrowser) Proxy.newProxyInstance(CefBrowser.class.getClassLoader(), new Class<?>[] {CefBrowser.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    /** The response Chromium hands the handler, recording what it is given. */
    private static final class Response extends CefResponse {
        int status, length;
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
