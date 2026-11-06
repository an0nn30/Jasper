package dev.jasper.browser.page;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.jasper.browser.item.FaviconDecoder;
import dev.jasper.browser.store.Reached;
import dev.jasper.sdk.ui.PixelImage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A page's favicon from the web (spec section 5; plan 4b Task 2), against a local HTTP server: at most 64 KiB is read,
 * a slow answer gives up after the timeout, no cookie goes out, only http and https addresses are fetched, and a
 * favicon applies to its page only while the page is still where it was fetched for. Nothing here needs Chromium.
 */
class FaviconsTest {
    private static final Duration TIMEOUT = Duration.ofMillis(400);
    // Each fetch starts on a virtual thread of its own, as on the plugin's background executor.
    private static final Executor STARTS = task -> Thread.ofVirtual().start(task);
    private final CountDownLatch release = new CountDownLatch(1);
    private final List<Headers> asked = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private Favicons favicons;
    private byte[] png;

    @BeforeEach void serve() throws IOException {
        png = png(16);
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        handle("/favicon.ico", exchange -> answer(exchange, 200, png));
        handle("/missing.ico", exchange -> answer(exchange, 404, new byte[0]));
        handle("/moved.ico", exchange -> {
            exchange.getResponseHeaders().add("Location", "/favicon.ico");
            answer(exchange, 301, new byte[0]);
        });
        handle("/cookie.ico", exchange -> {
            exchange.getResponseHeaders().add("Set-Cookie", "session=secret; Path=/");
            answer(exchange, 200, png);
        });
        handle("/exact.ico", exchange -> answer(exchange, 200, padded(Favicons.LIMIT)));
        handle("/over.ico", exchange -> answer(exchange, 200, padded(Favicons.LIMIT + 1)));
        handle("/chunked.ico", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream body = exchange.getResponseBody()) {
                byte[] block = new byte[4_096];
                for (int sent = 0; sent <= Favicons.LIMIT; sent += block.length) body.write(block);
            }
        });
        handle("/silent.ico", exchange -> {
            hold();
            answer(exchange, 200, png);
        });
        handle("/trickle.ico", exchange -> {
            exchange.sendResponseHeaders(200, png.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(png, 0, 10);
                body.flush();
                hold();
                body.write(png, 10, png.length - 10);
            }
        });
        server.start();
    }

    @AfterEach void stop() {
        release.countDown();
        if (favicons != null) favicons.close();
        server.stop(0);
        ((ExecutorService) server.getExecutor()).shutdownNow();
    }

    @Test void aFaviconIsFetchedFollowingRedirectsAndDecoded() throws Exception {
        favicons = new Favicons(FaviconDecoder::toPng, STARTS, TIMEOUT);
        Optional<PixelImage> fetched = favicons.fetch(at("/favicon.ico"), at("/page")).get(5, TimeUnit.SECONDS);
        assertThat(fetched).isPresent().isEqualTo(FaviconDecoder.toPng(png));
        assertThat(favicons.fetch(at("/moved.ico"), at("/page")).get(5, TimeUnit.SECONDS)).as("after a redirect").isEqualTo(fetched);
        assertThat(favicons.fetch(at("/missing.ico"), at("/page")).get(5, TimeUnit.SECONDS)).as("a 404").isEmpty();
    }

    /** Spec Review Focus 4: nothing over 64 KiB is read, with a length given or not. */
    @Test void anAnswerOver64KibIsNeverDecoded() throws Exception {
        var decoded = new CopyOnWriteArrayList<Integer>();
        favicons = new Favicons(bytes -> {
            decoded.add(bytes.length);
            return Optional.empty();
        }, STARTS, TIMEOUT);
        assertThat(favicons.fetch(at("/over.ico"), at("/page")).get(5, TimeUnit.SECONDS)).isEmpty();
        assertThat(favicons.fetch(at("/chunked.ico"), at("/page")).get(5, TimeUnit.SECONDS)).isEmpty();
        assertThat(decoded).as("over the cap: never decoded").isEmpty();
        favicons.fetch(at("/exact.ico"), at("/page")).get(5, TimeUnit.SECONDS);
        assertThat(decoded).as("at the cap").containsExactly(Favicons.LIMIT);
    }

    /** A server that never answers, or stops halfway, costs the timeout and gives nothing. */
    @Test void aSlowAnswerGivesNothingAfterTheTimeout() throws Exception {
        favicons = new Favicons(FaviconDecoder::toPng, STARTS, TIMEOUT);
        for (String path : List.of("/silent.ico", "/trickle.ico")) {
            long start = System.nanoTime();
            assertThat(favicons.fetch(at(path), at("/page")).get(5, TimeUnit.SECONDS)).as(path).isEmpty();
            assertThat(Duration.ofNanos(System.nanoTime() - start)).as(path).isGreaterThanOrEqualTo(TIMEOUT).isLessThan(Duration.ofSeconds(3));
        }
    }

    @Test void noCookieGoesOutAndOnlyWebAddressesAreFetched() throws Exception {
        favicons = new Favicons(FaviconDecoder::toPng, STARTS, TIMEOUT);
        assertThat(favicons.fetch(at("/cookie.ico"), at("/page")).get(5, TimeUnit.SECONDS)).isPresent();
        assertThat(favicons.fetch(at("/cookie.ico"), at("/page")).get(5, TimeUnit.SECONDS)).isPresent();
        assertThat(asked).hasSize(2).allSatisfy(headers -> assertThat(headers.containsKey("Cookie")).as("a cookie").isFalse());
        for (String icon : List.of("file:///etc/hosts", "data:image/png;base64,AAAA", "ftp://127.0.0.1/favicon.ico",
                "jasper-about:favicon.ico", "http://user:secret@127.0.0.1/favicon.ico"))
            assertThat(favicons.fetch(URI.create(icon), at("/page")).get(5, TimeUnit.SECONDS)).as(icon).isEmpty();
        for (String page : List.of("file:///tmp/page.html", "jasper-about:welcome", "about:blank"))
            assertThat(favicons.fetch(at("/favicon.ico"), URI.create(page)).get(5, TimeUnit.SECONDS)).as(page).isEmpty();
        assertThat(asked).as("nothing more was asked").hasSize(2);
    }

    @Test void aClosedFetcherFetchesNothing() throws Exception {
        favicons = new Favicons(FaviconDecoder::toPng, STARTS, TIMEOUT);
        var waiting = favicons.fetch(at("/silent.ico"), at("/page"));
        await(() -> asked.size() == 1, "the request");
        favicons.close();
        assertThat(waiting.get(5, TimeUnit.SECONDS)).as("the fetch under way").isEmpty();
        assertThat(favicons.fetch(at("/favicon.ico"), at("/page")).get(5, TimeUnit.SECONDS)).isEmpty();
        assertThat(asked).hasSize(1);
    }

    /** The page's own favicon, fetched for where it is, once it arrives; a fragment is still the same page. */
    @Test void aFaviconForThePageStillThereBecomesItsFavicon() throws Exception {
        favicons = new Favicons(FaviconDecoder::toPng, STARTS, TIMEOUT);
        var changes = new AtomicInteger();
        Page page = onUi(() -> new Page(at("/page"), changes::incrementAndGet, favicons, Runnable::run, PageContext.persistent()));
        onUi(() -> {
            page.faviconDeclared(at("/silent.ico"), at("/page").toString());
            assertThat(page.declaredFavicon()).contains(at("/silent.ico"));
            page.addressChanged(at("/page") + "#section");
            return null;
        });
        int before = onUi(changes::get);
        await(() -> asked.size() == 1, "the request");
        release.countDown();
        await(() -> onUi(() -> !page.fetchingFavicon()), "the fetch");
        onUi(() -> {
            assertThat(page.favicon()).isPresent().isEqualTo(FaviconDecoder.toPng(png));
            assertThat(changes.get()).as("told").isEqualTo(before + 1);
            page.close();
            return null;
        });
    }

    /**
     * Plan 4b's W5: a script's {@code pushState} or {@code replaceState} while the fetch runs changes the page's address,
     * not its document, so the favicon still becomes the page's.
     */
    @Test void aFaviconThatArrivesAfterAPushStateStillBecomesTheDocumentsFavicon() throws Exception {
        favicons = new Favicons(FaviconDecoder::toPng, STARTS, TIMEOUT);
        var changes = new AtomicInteger();
        Page page = onUi(() -> new Page(at("/page"), changes::incrementAndGet, favicons, Runnable::run, PageContext.persistent()));
        onUi(() -> {
            page.faviconDeclared(at("/silent.ico"), at("/page").toString());
            return null;
        });
        await(() -> asked.size() == 1, "the request");
        onUi(() -> {
            page.addressChanged(at("/elsewhere").toString());
            return null;
        });
        int before = onUi(changes::get);
        release.countDown();
        await(() -> onUi(() -> !page.fetchingFavicon()), "the fetch");
        onUi(() -> {
            assertThat(page.favicon()).isPresent().isEqualTo(FaviconDecoder.toPng(png));
            assertThat(changes.get()).as("told").isEqualTo(before + 1);
            page.close();
            return null;
        });
    }

    /** Plan 4b's ruling: a favicon never lands on a document it does not belong to; a load start begins another. */
    @Test void aFaviconForADocumentThePageNoLongerShowsIsDropped() throws Exception {
        favicons = new Favicons(FaviconDecoder::toPng, STARTS, TIMEOUT);
        var changes = new AtomicInteger();
        Page page = onUi(() -> new Page(at("/page"), changes::incrementAndGet, favicons, Runnable::run, PageContext.persistent()));
        onUi(() -> {
            long declaredIn = PageTestAccess.document(page);
            page.loadStarted(at("/next").toString(), Reached.LINK);
            int before = changes.get();
            PageTestAccess.faviconArrived(page, declaredIn, FaviconDecoder.toPng(png));
            assertThat(page.favicon()).as("dropped").isEmpty();
            assertThat(changes.get()).as("nothing told").isEqualTo(before);
            PageTestAccess.faviconArrived(page, PageTestAccess.document(page), FaviconDecoder.toPng(png));
            assertThat(page.favicon()).as("the document it shows").isPresent();
            page.close();
            return null;
        });
    }

    /**
     * Each main-frame load start clears the favicon and drops a fetch under way; the new document may ask again, once.
     * A document the page no longer shows asks for nothing.
     */
    @Test void aLoadStartClearsTheFaviconAndEachDocumentAsksOnce() throws Exception {
        favicons = new Favicons(FaviconDecoder::toPng, STARTS, TIMEOUT);
        var changes = new AtomicInteger();
        Page page = onUi(() -> new Page(at("/page"), changes::incrementAndGet, favicons, Runnable::run, PageContext.persistent()));
        onUi(() -> {
            page.faviconDeclared(at("/favicon.ico"), at("/page").toString());
            return null;
        });
        await(() -> onUi(() -> page.favicon().isPresent()), "the favicon");
        onUi(() -> {
            page.faviconDeclared(at("/missing.ico"), at("/page").toString());
            assertThat(page.declaredFavicon()).as("one icon a document").contains(at("/favicon.ico"));
            assertThat(page.fetchingFavicon()).isFalse();
            int before = changes.get();
            page.loadStarted(at("/next").toString(), Reached.LINK);
            assertThat(page.favicon()).as("cleared at the load start").isEmpty();
            assertThat(page.declaredFavicon()).isEmpty();
            assertThat(changes.get()).as("told once").isEqualTo(before + 1);
            page.addressChanged(at("/next").toString());
            page.faviconDeclared(at("/favicon.ico"), at("/page").toString());
            assertThat(page.declaredFavicon()).as("from the document before").isEmpty();
            page.faviconDeclared(at("/silent.ico"), at("/next").toString());
            assertThat(page.declaredFavicon()).contains(at("/silent.ico"));
            assertThat(page.fetchingFavicon()).isTrue();
            return null;
        });
        await(() -> asked.size() == 2, "the next document's request");
        onUi(() -> {
            page.loadStarted(at("/third").toString(), Reached.LINK);
            assertThat(page.fetchingFavicon()).as("dropped at the load start").isFalse();
            page.close();
            return null;
        });
    }

    /**
     * The detector is the plugin's own page script: once the document is parsed, it names the document's first icon link
     * or its origin's {@code /favicon.ico} through the router's {@code favicon} op, in ASCII with no raw control.
     */
    @Test void theDetectorNamesTheDocumentsIconThroughTheRouter() throws Exception {
        String script;
        try (var in = Page.class.getResourceAsStream("detect-favicon.js")) {
            assertThat(in).as("detect-favicon.js").isNotNull();
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(PageEvents.script("detect-favicon.js")).isEqualTo(script.strip());
        assertThat(script).contains("window.jasperBridgeQuery", "op: 'favicon'", "link[rel~=\"icon\" i], link[rel=\"mask-icon\" i]",
            "location.origin + '/favicon.ico'", "DOMContentLoaded");
        assertThat(script.chars().filter(c -> (c < 0x20 && c != '\n') || c >= 0x7F)).as("ASCII only, with no control character but the newline")
            .isEmpty();
    }

    private URI at(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    private void handle(String path, HttpHandler handler) {
        server.createContext(path, exchange -> {
            asked.add(exchange.getRequestHeaders());
            try {
                handler.handle(exchange);
            } catch (IOException gone) {
                // The client gave up, as it does on its timeout.
            } finally {
                exchange.close();
            }
        });
    }

    /** Waits, in the server's thread, until the test releases it or ends. */
    private void hold() {
        try {
            release.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void answer(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    /** The test's PNG followed by zeros, {@code length} bytes in all. */
    private byte[] padded(int length) {
        return Arrays.copyOf(png, length);
    }

    private static byte[] png(int size) throws IOException {
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) image.setRGB(x, y, 0xFF00A000);
        var bytes = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
        try (var output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output);
            writer.write(image);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static <T> T onUi(Callable<T> task) throws Exception {
        var result = new AtomicReference<T>();
        var failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(task.call());
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        if (failure.get() instanceof Error error) throw error;
        if (failure.get() instanceof Exception exception) throw exception;
        return result.get();
    }

    private interface Check {
        boolean holds() throws Exception;
    }

    private static void await(Check check, String what) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!check.holds()) {
            if (System.nanoTime() > deadline) throw new AssertionError("timed out waiting for " + what);
            Thread.sleep(10);
        }
    }
}
