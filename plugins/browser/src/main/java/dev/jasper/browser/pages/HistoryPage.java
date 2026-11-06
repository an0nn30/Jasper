package dev.jasper.browser.pages;

import dev.jasper.browser.engine.AboutScheme;
import dev.jasper.browser.json.StrictJson;
import dev.jasper.browser.store.HistoryStore;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code jasper-about:history} (spec section 10), Jasper's page of the browsing history: its template {@code history.html}
 * and script {@code history.js}, and the answers to what the script asks. The page lists the entries newest first,
 * grouped by day, 50 at a time, searches them, and deletes one entry or all of them; it reaches Java only through its
 * queries ({@link AboutScheme#registerQueries}). Each request is one flat JSON object, and each answer one JSON object
 * {@link StrictJson#write} writes:
 * <ul>
 *   <li>{@code {"op":"list","query":q,"before":"<time>:<id>"}}, {@code query} and {@code before} optional: the page of
 *       entries matching {@code q} after the cursor {@code before}, or the newest, as
 *       {@code {"entries":[{"id","url","title","time"}],"next":"<time>:<id>"|null}}, where {@code id} is the entry's id as
 *       decimal text, {@code time} when its load started, in milliseconds since the epoch, and {@code next} the cursor of
 *       the next page, null after the last;</li>
 *   <li>{@code {"op":"delete","id":"<id>"}}: deletes the entry, if it is still there, and answers {@code {}};</li>
 *   <li>{@code {"op":"clear"}}: deletes every entry, and answers {@code {}}.</li>
 * </ul>
 * Members a request does not use are ignored. Anything else fails with "Jasper does not understand this request.".
 */
public final class HistoryPage {
    /** The page's name. */
    public static final String NAME = "history";
    /** The page's address. */
    public static final URI ADDRESS = URI.create(AboutScheme.NAME + ":" + NAME);
    /** How many entries a list answers at most. */
    static final int PAGE = 50;
    /** The reason a request the page cannot make fails with. */
    static final String NOT_UNDERSTOOD = "Jasper does not understand this request.";
    private static final Pattern ID = Pattern.compile("[0-9]{1,19}");
    private static final Pattern CURSOR = Pattern.compile("(-?[0-9]{1,19}):([0-9]{1,19})");
    private static final String EMPTY = "{}";

    private HistoryPage() { }

    /**
     * The answer to one of the page's requests, from {@code history}. Any thread: it only hands the work to the
     * history's own thread.
     *
     * @param history the history the page shows
     * @param request what the page asked, as JSON
     * @return the answer's JSON; failed with "Jasper does not understand this request." for a request the page does not
     *     make, or with the history's own reason when it cannot answer. Whatever the request, never an exception
     */
    public static CompletableFuture<String> answer(HistoryStore history, String request) {
        Objects.requireNonNull(history, "history");
        try {
            Map<String, Object> asked = StrictJson.object(request, 1).orElse(null);
            if (asked == null) return notUnderstood();
            return switch (asked.get("op") instanceof String op ? op : "") {
                case "list" -> list(history, asked);
                case "delete" -> id(asked.get("id")).map(id -> history.delete(id).thenApply(deleted -> EMPTY)).orElseGet(HistoryPage::notUnderstood);
                case "clear" -> history.clear().thenApply(cleared -> EMPTY);
                default -> notUnderstood();
            };
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    /** A list request: its search, blank when it has none, and its cursor, if it has one. */
    private static CompletableFuture<String> list(HistoryStore history, Map<String, Object> asked) {
        if (!(asked.getOrDefault("query", "") instanceof String words)) return notUnderstood();
        Object before = asked.getOrDefault("before", StrictJson.NULL);
        Optional<HistoryStore.Cursor> cursor = before instanceof String written ? cursor(written) : Optional.empty();
        if (before != StrictJson.NULL && cursor.isEmpty()) return notUnderstood();
        return history.list(words, cursor, PAGE).thenApply(HistoryPage::listed);
    }

    /** A page of entries as the page reads it. */
    private static String listed(HistoryStore.HistoryPage page) {
        var entries = new ArrayList<Object>(page.entries().size());
        for (HistoryStore.HistoryEntry entry : page.entries()) {
            var listed = new LinkedHashMap<String, Object>();
            listed.put("id", Long.toString(entry.id()));
            listed.put("url", entry.url());
            listed.put("title", entry.title());
            listed.put("time", entry.visitedAt());
            entries.add(listed);
        }
        var answer = new LinkedHashMap<String, Object>();
        answer.put("entries", entries);
        answer.put("next", page.next().<Object>map(next -> next.visitedAt() + ":" + next.id()).orElse(StrictJson.NULL));
        return StrictJson.write(answer);
    }

    /** An entry's id, written as the page writes it: decimal digits that fit a {@code long}. */
    private static Optional<Long> id(Object written) {
        if (!(written instanceof String text) || !ID.matcher(text).matches()) return Optional.empty();
        try {
            return Optional.of(Long.parseLong(text));
        } catch (NumberFormatException tooLarge) {
            return Optional.empty();
        }
    }

    /** A cursor, written as a list answer's {@code next}: the last entry's time, a colon and its id. */
    private static Optional<HistoryStore.Cursor> cursor(String written) {
        Matcher parts = CURSOR.matcher(written);
        if (!parts.matches()) return Optional.empty();
        try {
            return Optional.of(new HistoryStore.Cursor(Long.parseLong(parts.group(1)), Long.parseLong(parts.group(2))));
        } catch (NumberFormatException tooLarge) {
            return Optional.empty();
        }
    }

    private static CompletableFuture<String> notUnderstood() {
        return CompletableFuture.failedFuture(new IllegalArgumentException(NOT_UNDERSTOOD));
    }
}
