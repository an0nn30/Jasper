package dev.jasper.browser.page;

import dev.jasper.sdk.ui.PixelImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Web pages' favicons, fetched each time a page names one and never saved (spec section 5). Chromium gives no favicon
 * callback, so a page's script names the icon and Jasper fetches it itself, apart from Chromium: with the JDK's HTTP
 * client, which sends no cookie and no credentials, following redirects within the web schemes, for at most
 * {@value #LIMIT} bytes and {@code 5} seconds in all. Only an http or https icon of an http or https page is fetched;
 * anything else, a failure, an answer other than 200 and one over the limit give nothing, so the item shows the globe.
 * The client is made on the first fetch, on the executor given, as making the first one takes tens of milliseconds, and
 * it ends with {@link #close()}, so a plugin that shows no web page holds none. Any thread.
 */
public final class Favicons implements AutoCloseable {
    /** The most bytes of a favicon that are read. */
    public static final int LIMIT = 65_536;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String ACCEPT = "image/*,*/*;q=0.8";
    private static final System.Logger LOG = System.getLogger(Favicons.class.getName());
    private final Function<byte[], Optional<PixelImage>> decoder;
    private final Executor starts;
    private final Duration timeout;
    // Made on the first fetch; guarded by this.
    private HttpClient client;
    private boolean closed;

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
        this.starts = Objects.requireNonNull(starts, "starts");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
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
        var result = new CompletableFuture<Optional<PixelImage>>();
        if (!fetches(icon) || !fetches(page) || closed()) {
            result.complete(Optional.empty());
            return result;
        }
        result.completeOnTimeout(Optional.empty(), timeout.toNanos(), TimeUnit.NANOSECONDS);
        try {
            starts.execute(() -> send(icon, result));
        } catch (RejectedExecutionException stopped) {
            result.complete(Optional.empty());
        }
        return result;
    }

    /** Ends every fetch under way, which then gives nothing; later fetches give nothing at once. */
    @Override public void close() {
        HttpClient ending;
        synchronized (this) {
            closed = true;
            ending = client;
            client = null;
        }
        if (ending != null) ending.shutdownNow();
    }

    /**
     * Sends the request for {@code icon}, unless {@code result} has ended already, and completes {@code result} with what
     * the answer decodes to. However {@code result} ends, by the answer, the timeout or a cancel, the exchange ends with
     * it.
     */
    private void send(URI icon, CompletableFuture<Optional<PixelImage>> result) {
        HttpClient http = result.isDone() ? null : client();
        if (http == null) {
            result.complete(Optional.empty());
            return;
        }
        CompletableFuture<HttpResponse<byte[]>> exchange;
        try {
            exchange = http.sendAsync(HttpRequest.newBuilder(icon).timeout(timeout).header("Accept", ACCEPT).GET().build(), Favicons::body);
        } catch (IllegalArgumentException | IllegalStateException refused) { // an address the client takes no request for, or closed
            result.complete(Optional.empty());
            return;
        }
        exchange.whenComplete((response, failure) -> result.complete(failure == null ? decoded(response) : Optional.empty()));
        result.whenComplete((image, failure) -> exchange.cancel(true));
    }

    private synchronized boolean closed() {
        return closed;
    }

    private synchronized HttpClient client() {
        if (closed) return null;
        if (client == null) client = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NORMAL).build();
        return client;
    }

    /** What an answer decodes to: a 200's body within the limit, decoded; nothing for anything else. */
    private Optional<PixelImage> decoded(HttpResponse<byte[]> response) {
        if (response.statusCode() != 200 || response.body() == null) return Optional.empty();
        try {
            return Objects.requireNonNull(decoder.apply(response.body()), "decoded");
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "A favicon could not be decoded", failure);
            return Optional.empty();
        }
    }

    /**
     * Whether favicons are fetched at {@code address}, and for a page there: an http or https address with a host and
     * without a user name or password.
     *
     * @param address an address
     * @return whether it is one
     */
    public static boolean fetches(URI address) {
        String scheme = address.getScheme() == null ? "" : address.getScheme().toLowerCase(Locale.ROOT);
        return (scheme.equals("http") || scheme.equals("https")) && address.getHost() != null && address.getRawUserInfo() == null;
    }

    /** The body of an answer: read only for a 200 that does not announce more than the limit. */
    private static HttpResponse.BodySubscriber<byte[]> body(HttpResponse.ResponseInfo answer) {
        long length = answer.headers().firstValue("Content-Length").map(Favicons::length).orElse(-1L);
        return new Capped(answer.statusCode() != 200 || length > LIMIT);
    }

    private static long length(String header) {
        try {
            return Long.parseLong(header.strip());
        } catch (NumberFormatException unreadable) {
            return -1;
        }
    }

    /**
     * A body of at most {@value #LIMIT} bytes: one refused at once, or that turns out longer, ends its exchange and gives
     * null. The client calls it on one thread at a time.
     */
    private static final class Capped implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final boolean refused;
        private Flow.Subscription subscription;

        Capped(boolean refused) {
            this.refused = refused;
        }

        @Override public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override public void onSubscribe(Flow.Subscription given) {
            subscription = given;
            if (refused) end();
            else given.request(Long.MAX_VALUE);
        }

        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (body.isDone()) return;
                if (buffer.remaining() > LIMIT - bytes.size()) {
                    end();
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
        }

        @Override public void onError(Throwable failure) {
            body.completeExceptionally(failure);
        }

        @Override public void onComplete() {
            body.complete(bytes.toByteArray());
        }

        /** Stops reading: the exchange ends, and the body is null. */
        private void end() {
            subscription.cancel();
            body.complete(null);
        }
    }
}
