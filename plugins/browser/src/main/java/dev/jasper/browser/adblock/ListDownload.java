package dev.jasper.browser.adblock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * A filter list's download (ruling F10): a GET of an https address, or an http one on this computer only
 * ({@link #fetches}), as TLS is the list's only integrity, with the client {@link #client(Optional)} makes, which
 * connects within 30 seconds and follows no redirect itself. The download follows at most 5 (ruling V38), each target resolved against
 * the address that answered, checked as the first address is and never from https to http, and only then asked, so a
 * target the list may not come from is never asked. Only a final 200 is read, at most 32 MiB of it, counted before it
 * is decoded, and one deadline covers the whole download, its redirects and body included ({@link #get}). Why a
 * download failed is told in words Settings shows ({@link #reason}). Any thread.
 */
public final class ListDownload {
    /** How long a connection to the list's server may take. */
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    /** How long a download may take in all, its body included. */
    static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(120);
    /** How many bytes of a list are read at most, before they are decoded. */
    static final int MAX_BYTES = 32 * 1024 * 1024;
    /** Why an address the list may not come from is refused. */
    static final String REFUSED_ADDRESS = "the filter list's address is not an https address, or an http one on this computer";
    /** How many redirects a download follows at most. */
    static final int MAX_REDIRECTS = 5;
    private static final String REDIRECTED = "the filter list was redirected to an address that is not https";
    private static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);
    private static final Pattern LOOPBACK = Pattern.compile("localhost|127(?:\\.[0-9]{1,3}){3}|\\[::1\\]");

    private ListDownload() { }

    /**
     * The list's client: one that connects within 30 seconds and follows no redirect, which {@link #get} follows itself.
     * It sends no cookie and no credentials, and goes through {@code proxy} (plan 6's F3). The one place the list's
     * client is made.
     *
     * @param proxy the HTTP proxy's address; empty for the JDK's default proxy selector
     * @return the client
     */
    public static HttpClient client(Optional<InetSocketAddress> proxy) {
        var builder = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).followRedirects(HttpClient.Redirect.NEVER);
        proxy.ifPresent(address -> builder.proxy(ProxySelector.of(address)));
        return builder.build();
    }

    /**
     * Whether the list may come from {@code address}: an https address with a host, or an http one whose host is this
     * computer ({@code localhost}, {@code 127.0.0.0/8} or {@code [::1]}), without a user name or password.
     *
     * @param address an address
     * @return whether it may
     */
    public static boolean fetches(URI address) {
        if (address == null || address.getScheme() == null || address.getHost() == null || address.getRawUserInfo() != null) return false;
        String scheme = address.getScheme().toLowerCase(Locale.ROOT);
        return scheme.equals("https") || scheme.equals("http") && LOOPBACK.matcher(address.getHost().toLowerCase(Locale.ROOT)).matches();
    }

    /**
     * Downloads the list at {@code address} with {@code client}: its body, once a final 200 has been read whole, after at
     * most 5 redirects, each of which is checked before it is asked. Cancelling the future, or its deadline passing, ends
     * the exchange under way and its body.
     *
     * @param client the list's client, which follows no redirect itself
     * @param address the list's address
     * @param deadline how long the whole download may take, its redirects and body included
     * @return the body; it fails with a {@link Refused} for an address {@link #fetches} refuses, a redirect to one, from
     *     https to http, or past the fifth, and any other status, a {@link TooLarge} for a body over 32 MiB, a
     *     {@link TimeoutException} once the deadline has passed, and with what the client failed with
     */
    static CompletableFuture<byte[]> get(HttpClient client, URI address, Duration deadline) {
        var result = new CompletableFuture<byte[]>();
        if (!fetches(address)) {
            result.completeExceptionally(new Refused(REFUSED_ADDRESS));
            return result;
        }
        var exchange = new Exchange();
        result.orTimeout(deadline.toNanos(), TimeUnit.NANOSECONDS);
        // However the download ends, by its answer, the deadline or a cancel, the exchange under way and its body end with it.
        result.whenComplete((bytes, failure) -> exchange.end());
        ask(client, address, deadline, 0, result, exchange);
        return result;
    }

    /** Asks {@code address}, the target of {@code redirects} redirects so far, and follows its answer. */
    private static void ask(HttpClient client, URI address, Duration deadline, int redirects, CompletableFuture<byte[]> result,
                            Exchange exchange) {
        Body body = new Body();
        CompletableFuture<HttpResponse<byte[]>> sent;
        try {
            sent = client.sendAsync(HttpRequest.newBuilder(address).timeout(deadline).header("Accept", "application/json").GET().build(), body);
        } catch (RuntimeException refused) {
            result.completeExceptionally(refused);
            return;
        }
        if (!exchange.started(sent, body)) return;
        sent.whenComplete((response, failure) -> {
            if (failure != null) {
                result.completeExceptionally(failure);
                return;
            }
            int status = response.statusCode();
            if (status == 200) {
                result.complete(response.body());
                return;
            }
            Optional<String> location = response.headers().firstValue("Location");
            if (!REDIRECTS.contains(status) || location.isEmpty()) {
                result.completeExceptionally(new Refused("the server answered HTTP " + status));
                return;
            }
            if (redirects == MAX_REDIRECTS) {
                result.completeExceptionally(new Refused("the filter list was redirected more than " + MAX_REDIRECTS + " times"));
                return;
            }
            Optional<URI> next = target(address, location.get());
            if (next.isEmpty()) {
                result.completeExceptionally(new Refused(REDIRECTED));
                return;
            }
            if (!result.isDone()) ask(client, next.get(), deadline, redirects + 1, result, exchange);
        });
    }

    /**
     * Where a redirect from {@code from} to {@code location} goes: the location resolved against {@code from}, when the
     * list may come from there ({@link #fetches}) and it does not leave https for http; empty otherwise.
     */
    static Optional<URI> target(URI from, String location) {
        try {
            URI next = from.resolve(location.strip());
            boolean downgrade = from.getScheme().equalsIgnoreCase("https") && !"https".equalsIgnoreCase(next.getScheme());
            return fetches(next) && !downgrade ? Optional.of(next) : Optional.empty();
        } catch (IllegalArgumentException unreadable) {
            return Optional.empty();
        }
    }

    /**
     * Why a download failed, in words Settings shows after "Could not update the filter list: ".
     *
     * @param failure what it failed with
     * @param address its address, or null
     * @param deadline the deadline it had
     * @return the reason
     */
    static String reason(Throwable failure, URI address, Duration deadline) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) cause = cause.getCause();
        for (Throwable link = cause; link != null; link = link.getCause()) if (link instanceof TooLarge) return link.getMessage();
        return switch (cause) {
            case Refused refused -> refused.getMessage();
            case HttpConnectTimeoutException late -> "the server did not answer within " + seconds(CONNECT_TIMEOUT);
            case TimeoutException late -> "the download took longer than " + seconds(deadline);
            case HttpTimeoutException late -> "the download took longer than " + seconds(deadline);
            case CancellationException cancelled -> "the download was cancelled";
            case ConnectException refused -> "could not connect to " + (address == null ? "its server" : address.getHost());
            default -> cause.getMessage() == null || cause.getMessage().isBlank() ? cause.getClass().getSimpleName() : cause.getMessage();
        };
    }

    private static String seconds(Duration duration) {
        return BigDecimal.valueOf(duration.toMillis(), 3).stripTrailingZeros().toPlainString() + " seconds";
    }

    /** A download Jasper refuses, with the reason Settings shows. Carries no stack. */
    static final class Refused extends IOException {
        Refused(String reason) {
            super(reason, null);
        }

        @Override public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }

    /** A list over 32 MiB. Carries no stack. */
    static final class TooLarge extends IOException {
        TooLarge() {
            super("the list is larger than " + (MAX_BYTES >> 20) + " MiB");
        }

        @Override public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }

    /** The exchange under way, and its body: the download's end ends it, and none starts after. */
    private static final class Exchange {
        private CompletableFuture<?> sent; // guarded by this
        private Body body; // guarded by this
        private boolean ended; // guarded by this

        /** Records the exchange just sent; false, and the exchange ended, once the download has ended. */
        boolean started(CompletableFuture<?> sent, Body body) {
            synchronized (this) {
                if (!ended) {
                    this.sent = sent;
                    this.body = body;
                    return true;
                }
            }
            sent.cancel(true);
            body.stop();
            return false;
        }

        /** Ends the exchange under way, and any later one. */
        void end() {
            CompletableFuture<?> now;
            Body reading;
            synchronized (this) {
                ended = true;
                now = sent;
                reading = body;
            }
            if (now != null) now.cancel(true);
            if (reading != null) reading.stop();
        }
    }

    /**
     * The answer's body: for a 200 that does not announce more than 32 MiB, at most 32 MiB of bytes, counted as they
     * come; for any other answer, nothing is read. The client calls it on one thread at a time.
     */
    private static final class Body implements HttpResponse.BodyHandler<byte[]> {
        private volatile Reading reading;

        @Override public HttpResponse.BodySubscriber<byte[]> apply(HttpResponse.ResponseInfo answer) {
            long length = answer.headers().firstValueAsLong("Content-Length").orElse(-1);
            Reading next = new Reading(answer.statusCode() != 200, length > MAX_BYTES);
            reading = next;
            return next;
        }

        /** Stops reading the body. */
        void stop() {
            Reading now = reading;
            if (now != null) now.end(null);
        }
    }

    private static final class Reading implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final boolean unread, tooLarge;
        private volatile Flow.Subscription subscription;

        Reading(boolean unread, boolean tooLarge) {
            this.unread = unread;
            this.tooLarge = tooLarge;
        }

        @Override public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override public void onSubscribe(Flow.Subscription given) {
            subscription = given;
            if (unread) end(new byte[0]);
            else if (tooLarge) end(new TooLarge());
            else if (body.isDone()) given.cancel();
            else given.request(Long.MAX_VALUE);
        }

        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (body.isDone()) return;
                if (buffer.remaining() > MAX_BYTES - bytes.size()) {
                    end(new TooLarge());
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

        /** Ends the reading: with {@code ending}, a body or a failure, or, null, as cancelled. */
        void end(Object ending) {
            Flow.Subscription now = subscription;
            if (now != null) now.cancel();
            if (ending instanceof byte[] read) body.complete(read);
            else body.completeExceptionally(ending instanceof Throwable failure ? failure : new CancellationException());
        }
    }
}
