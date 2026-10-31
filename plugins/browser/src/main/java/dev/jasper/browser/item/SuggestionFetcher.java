package dev.jasper.browser.item;

import dev.jasper.browser.http.WebFetch;
import dev.jasper.browser.json.StrictJson;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * A search engine's suggestions for the address field (spec section 6), fetched as {@link WebFetch} does, with no
 * cookie, for at most {@value #LIMIT} bytes and {@link #TIMEOUT} in all, and read as OpenSearch JSON,
 * {@code [query, [s1, s2, …], …]}. Each fetch supersedes the one before, as each keystroke does: the one before ends at
 * once with no suggestions, so its answer, however late, is dropped. A failure, an answer other than 200, one over the
 * limit and one that is not such JSON give none. The HTTP client is made on the first fetch and ends with
 * {@link #close()}. Any thread.
 */
final class SuggestionFetcher implements AutoCloseable {
    /** The most bytes of an answer that are read. */
    static final int LIMIT = 65_536;
    /** How long a fetch may take in all. */
    static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final String ACCEPT = "application/x-suggestions+json, application/json;q=0.9, */*;q=0.8";
    // The answer's array, its list of suggestions, and room for what an engine adds after them (Google's subtypes).
    private static final int DEPTH = 8;
    private final WebFetch web;
    // The fetch under way; guarded by this.
    private CompletableFuture<List<String>> latest;

    /**
     * A fetcher.
     *
     * @param starts where each fetch starts, off the UI thread: the plugin's background executor
     */
    SuggestionFetcher(Executor starts) {
        this(starts, TIMEOUT);
    }

    /** As {@link #SuggestionFetcher(Executor)}, giving up on a fetch after {@code timeout}. */
    SuggestionFetcher(Executor starts, Duration timeout) {
        web = new WebFetch(starts, timeout, LIMIT);
    }

    /**
     * Fetches the suggestions at {@code suggestUrl}, ending the fetch before. The future completes on a thread of the
     * HTTP client's, the executor's, the timer's or the next fetch's caller, never with an exception; cancelling it ends
     * the fetch.
     *
     * @param suggestUrl the engine's suggestion URL with the typed text in it
     * @return the suggestions, in the engine's order, each stripped and none blank; none for an address that is not http
     *     or https, a failure, a timeout, any answer but OpenSearch JSON, a fetch superseded, and after {@link #close()}
     */
    CompletableFuture<List<String>> fetch(URI suggestUrl) {
        Objects.requireNonNull(suggestUrl, "suggestUrl");
        var result = new CompletableFuture<List<String>>();
        CompletableFuture<List<String>> before;
        synchronized (this) {
            before = latest;
            latest = result;
        }
        if (before != null) before.complete(List.of());
        CompletableFuture<Optional<HttpResponse<byte[]>>> answer = web.get(suggestUrl, ACCEPT);
        answer.whenComplete((response, failure) -> result.complete(failure == null ? response.map(SuggestionFetcher::answered).orElse(List.of())
            : List.of()));
        result.whenComplete((suggestions, failure) -> answer.cancel(true));
        return result;
    }

    /** Ends the fetch under way, which then gives none; later fetches give none at once. */
    @Override public void close() {
        CompletableFuture<List<String>> under;
        synchronized (this) {
            under = latest;
            latest = null;
        }
        web.close();
        if (under != null) under.complete(List.of());
    }

    /** The suggestions of an answer, read in the charset its {@code Content-Type} names, else UTF-8. */
    private static List<String> answered(HttpResponse<byte[]> response) {
        return read(new String(response.body(), charset(response.headers().firstValue("Content-Type").orElse(""))));
    }

    /**
     * The suggestions of OpenSearch JSON: the strings of the array's second member.
     *
     * @param json the answer's text
     * @return the strings, stripped and none blank; none for any other text
     */
    static List<String> read(String json) {
        Optional<List<Object>> values = StrictJson.array(json, DEPTH);
        if (values.isEmpty() || values.get().size() < 2 || !(values.get().get(1) instanceof List<?> suggestions)) return List.of();
        return suggestions.stream().filter(String.class::isInstance).map(value -> ((String) value).strip()).filter(value -> !value.isEmpty())
            .toList();
    }

    private static Charset charset(String contentType) {
        for (String parameter : contentType.split(";")) {
            String[] pair = parameter.strip().split("=", 2);
            if (pair.length == 2 && pair[0].strip().toLowerCase(Locale.ROOT).equals("charset")) {
                try {
                    return Charset.forName(pair[1].strip().replace("\"", ""));
                } catch (IllegalArgumentException unknown) {
                    return StandardCharsets.UTF_8;
                }
            }
        }
        return StandardCharsets.UTF_8;
    }
}
