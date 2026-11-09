package dev.jasper.browser.http;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
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
 * The plugin's own fetches from the web, apart from Chromium: with the JDK's HTTP client, which sends no cookie and no
 * credentials and goes through the HTTP proxy it is given ({@link #useProxy}) as Chromium would ({@link ProxyRoute}),
 * the JDK's default proxy selector until then, following redirects within the web schemes, for at most a limit of bytes
 * and a time limit in all. Only an http or https address with a host and without a user name or password is fetched;
 * anything else, a failure, an answer other than 200 and one over the limit give nothing. The client is made on the
 * first fetch, on the executor given, as making the first one takes tens of milliseconds, and outside any lock, so no
 * thread that fetches or closes waits for it; it ends with {@link #close()}, so an owner that fetches nothing holds
 * none, and is made again after the proxy changes. Any thread.
 */
public final class WebFetch implements AutoCloseable {
    private final Executor starts;
    private final Duration timeout;
    private final int limit;
    private final Function<Optional<InetSocketAddress>, HttpClient> clients;
    // Made on the first fetch, with the proxy then; guarded by this. Closed is written under the lock too, and read
    // without it.
    private HttpClient client;
    private Optional<InetSocketAddress> proxy = Optional.empty();
    private volatile boolean closed;

    /**
     * A fetcher.
     *
     * @param starts where each fetch starts, off the UI thread: the plugin's background executor
     * @param timeout how long a fetch may take in all
     * @param limit the most bytes of an answer that are read
     */
    public WebFetch(Executor starts, Duration timeout, int limit) {
        this(starts, timeout, limit, proxy -> newClient(timeout, proxy));
    }

    /** As {@link #WebFetch(Executor, Duration, int)}, its clients made by {@code clients}: a test's. */
    WebFetch(Executor starts, Duration timeout, int limit, Function<Optional<InetSocketAddress>, HttpClient> clients) {
        this.starts = Objects.requireNonNull(starts, "starts");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (limit < 0) throw new IllegalArgumentException("limit");
        this.limit = limit;
        this.clients = Objects.requireNonNull(clients, "clients");
    }

    /**
     * A fetcher's client: it connects within {@code timeout}, follows redirects within the web schemes and goes through
     * {@code proxy} as {@link ProxyRoute} routes, or through the JDK's default proxy selector.
     *
     * @param timeout how long a connection may take
     * @param proxy the HTTP proxy's address; empty for the JDK's default proxy selector
     * @return the client
     */
    static HttpClient newClient(Duration timeout, Optional<InetSocketAddress> proxy) {
        var builder = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NORMAL);
        proxy.ifPresent(address -> builder.proxy(ProxyRoute.through(address)));
        return builder.build();
    }

    /**
     * Fetches {@code address}. The future completes on a thread of the HTTP client's, the executor's or the timer's,
     * never with an exception; cancelling it, or completing it, ends the fetch.
     *
     * @param address what to fetch
     * @param accept the request's {@code Accept} header
     * @return the answer: a 200 whose body was read within the limit; empty for an address {@link #fetches} refuses, a
     *     fetch that fails, times out or would read more than the limit, any other status, and after {@link #close()}
     */
    public CompletableFuture<Optional<HttpResponse<byte[]>>> get(URI address, String accept) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(accept, "accept");
        var result = new CompletableFuture<Optional<HttpResponse<byte[]>>>();
        if (!fetches(address) || closed) {
            result.complete(Optional.empty());
            return result;
        }
        result.completeOnTimeout(Optional.empty(), timeout.toNanos(), TimeUnit.NANOSECONDS);
        try {
            starts.execute(() -> send(address, accept, result));
        } catch (RejectedExecutionException stopped) {
            result.complete(Optional.empty());
        }
        return result;
    }

    /**
     * Has every fetch from now on go through {@code proxy} (plan 6's F3), as {@link ProxyRoute} routes: the client is
     * made again at the next fetch, and the fetches under way end, giving nothing, so nothing more goes the way they went.
     *
     * @param proxy the HTTP proxy's address; empty for the JDK's default proxy selector
     */
    public void useProxy(Optional<InetSocketAddress> proxy) {
        Objects.requireNonNull(proxy, "proxy");
        HttpClient ending;
        synchronized (this) {
            this.proxy = proxy;
            ending = client;
            client = null;
        }
        if (ending != null) ending.shutdownNow();
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
     * Whether {@code address} is fetched: an http or https address with a host and without a user name or password.
     *
     * @param address an address
     * @return whether it is one
     */
    public static boolean fetches(URI address) {
        String scheme = address.getScheme() == null ? "" : address.getScheme().toLowerCase(Locale.ROOT);
        return (scheme.equals("http") || scheme.equals("https")) && address.getHost() != null && address.getRawUserInfo() == null;
    }

    /**
     * The charset an answer's {@code Content-Type} names, as its {@code charset} parameter.
     *
     * @param response an answer
     * @return the charset; empty when the answer names none, or one the JDK does not know
     */
    public static Optional<Charset> charset(HttpResponse<?> response) {
        for (String parameter : response.headers().firstValue("Content-Type").orElse("").split(";")) {
            String[] pair = parameter.strip().split("=", 2);
            if (pair.length == 2 && pair[0].strip().toLowerCase(Locale.ROOT).equals("charset")) {
                try {
                    return Optional.of(Charset.forName(pair[1].strip().replace("\"", "")));
                } catch (IllegalArgumentException unknown) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Sends the request for {@code address}, unless {@code result} has ended already, and completes {@code result} with
     * the answer. However {@code result} ends, by the answer, the timeout or a cancel, the exchange ends with it.
     */
    private void send(URI address, String accept, CompletableFuture<Optional<HttpResponse<byte[]>>> result) {
        HttpClient http = result.isDone() ? null : client();
        if (http == null) {
            result.complete(Optional.empty());
            return;
        }
        CompletableFuture<HttpResponse<byte[]>> exchange;
        try {
            exchange = http.sendAsync(HttpRequest.newBuilder(address).timeout(timeout).header("Accept", accept).GET().build(), this::body);
        } catch (IllegalArgumentException | IllegalStateException refused) { // an address the client takes no request for, or closed
            result.complete(Optional.empty());
            return;
        }
        exchange.whenComplete((response, failure) -> result.complete(failure == null && response.statusCode() == 200 && response.body() != null
            ? Optional.of(response) : Optional.empty()));
        result.whenComplete((answer, failure) -> exchange.cancel(true));
    }

    /**
     * The client, made on the first call, with the proxy given: outside the lock, so neither {@link #get},
     * {@link #useProxy} nor {@link #close()} waits while it is made. Of two made at once, one is kept and the other shut
     * down, and one made with a proxy changed meanwhile is made again. Null once closed.
     */
    private HttpClient client() {
        while (true) {
            Optional<InetSocketAddress> through;
            synchronized (this) {
                if (closed || client != null) return client;
                through = proxy;
            }
            HttpClient made = clients.apply(through);
            HttpClient kept;
            synchronized (this) {
                if (!closed && client == null && proxy == through) client = made;
                kept = client;
            }
            if (kept != made) made.shutdownNow();
            if (kept != null || closed) return kept;
        }
    }

    /** The body of an answer: read only for a 200 that does not announce more than the limit. */
    private HttpResponse.BodySubscriber<byte[]> body(HttpResponse.ResponseInfo answer) {
        long length = answer.headers().firstValue("Content-Length").map(WebFetch::length).orElse(-1L);
        return new Capped(limit, answer.statusCode() != 200 || length > limit);
    }

    private static long length(String header) {
        try {
            return Long.parseLong(header.strip());
        } catch (NumberFormatException unreadable) {
            return -1;
        }
    }

    /**
     * A body of at most {@code limit} bytes: one refused at once, or that turns out longer, ends its exchange and gives
     * null. The client calls it on one thread at a time.
     */
    private static final class Capped implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final int limit;
        private final boolean refused;
        private Flow.Subscription subscription;

        Capped(int limit, boolean refused) {
            this.limit = limit;
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
                if (buffer.remaining() > limit - bytes.size()) {
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
