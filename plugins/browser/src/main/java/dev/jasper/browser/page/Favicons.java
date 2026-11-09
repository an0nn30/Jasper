package dev.jasper.browser.page;

import dev.jasper.browser.http.WebFetch;
import dev.jasper.sdk.ui.PixelImage;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Web pages' favicons, fetched each time a page names one and never saved (spec section 5). Chromium gives no favicon
 * callback, so a page's script names the icon and Jasper fetches it itself, apart from Chromium, as {@link WebFetch}
 * does: no cookie and no credentials, for at most {@value #LIMIT} bytes and {@code 5} seconds in all. Only an http or
 * https icon of an http or https page is fetched; anything else, a failure, an answer other than 200 and one over the
 * limit give nothing, so the item shows the globe. The HTTP client is made on the first fetch and ends with
 * {@link #close()}, so a plugin that shows no web page holds none. Any thread.
 */
public final class Favicons implements AutoCloseable {
    /** The most bytes of a favicon that are read. */
    public static final int LIMIT = 65_536;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String ACCEPT = "image/*,*/*;q=0.8";
    private static final System.Logger LOG = System.getLogger(Favicons.class.getName());
    private final Function<byte[], Optional<PixelImage>> decoder;
    private final WebFetch web;

    /**
     * A fetcher that decodes what it fetches with {@code decoder}.
     *
     * @param decoder turns a favicon file of at most {@value #LIMIT} bytes into the image shown, or empty
     * @param starts where each fetch starts, off the UI thread: the plugin's background executor
     */
    public Favicons(Function<byte[], Optional<PixelImage>> decoder, Executor starts) {
        this(decoder, starts, TIMEOUT);
    }

    /** As {@link #Favicons(Function, Executor)}, giving up on a fetch after {@code timeout}. */
    Favicons(Function<byte[], Optional<PixelImage>> decoder, Executor starts, Duration timeout) {
        this.decoder = Objects.requireNonNull(decoder, "decoder");
        web = new WebFetch(starts, timeout, LIMIT);
    }

    /**
     * Fetches {@code icon}, which the document at {@code page} names, and decodes it. The future completes on a thread of
     * the HTTP client's, the executor's or the timer's, never with an exception; cancelling it ends the fetch. Whether
     * the result still applies when it arrives, to a page that may have gone elsewhere, is the caller's to decide.
     *
     * @param icon the icon's address
     * @param page the address of the page that names it
     * @return the decoded icon, or empty: for an icon or page that is not http or https, a fetch that fails, times out or
     *     reads more than {@value #LIMIT} bytes, a file that does not decode, and after {@link #close()}
     */
    public CompletableFuture<Optional<PixelImage>> fetch(URI icon, URI page) {
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(page, "page");
        if (!WebFetch.fetches(page)) return CompletableFuture.completedFuture(Optional.empty());
        CompletableFuture<Optional<HttpResponse<byte[]>>> answer = web.get(icon, ACCEPT);
        CompletableFuture<Optional<PixelImage>> result = answer.thenApply(response -> response.flatMap(this::decoded));
        result.whenComplete((image, failure) -> answer.cancel(true));
        return result;
    }

    /**
     * Has the fetches from now on go through {@code proxy}, as {@link WebFetch#useProxy} says.
     *
     * @param proxy the HTTP proxy's address; empty for the JDK's default proxy selector
     */
    public void useProxy(Optional<InetSocketAddress> proxy) {
        web.useProxy(proxy);
    }

    /** Ends every fetch under way, which then gives nothing; later fetches give nothing at once. */
    @Override public void close() {
        web.close();
    }

    /** What a 200's body within the limit decodes to. */
    private Optional<PixelImage> decoded(HttpResponse<byte[]> response) {
        try {
            return Objects.requireNonNull(decoder.apply(response.body()), "decoded");
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "A favicon could not be decoded", failure);
            return Optional.empty();
        }
    }
}
