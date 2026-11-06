package dev.jasper.browser.pages;

import dev.jasper.browser.engine.AboutScheme;
import dev.jasper.browser.json.StrictJson;
import dev.jasper.browser.store.BookmarkStore;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * {@code jasper-about:bookmarks} (spec section 10), Jasper's page of the bookmarks: its template {@code bookmarks.html}
 * and script {@code bookmarks.js}, and the answers to what the script asks. The page lists the bookmarks 50 at a time,
 * searches them, sorts them, filters them by tags, and deletes one bookmark or all of them; it reaches Java only through
 * its queries ({@link AboutScheme#registerQueries}). Each request is one JSON object, flat but for the list of tags, and
 * each answer one JSON object {@link StrictJson#write} writes:
 * <ul>
 *   <li>{@code {"op":"list","query":q,"sort":"date"|"title"|"url","tags":[t,...],"offset":"<n>"}}, every member but
 *       {@code op} optional: the page of bookmarks matching {@code q} and every tag of {@code tags}, in the order
 *       {@code sort} names (the date added, newest first, when it is left out), after the first {@code n}, as
 *       {@code {"bookmarks":[{"id","url","title","added","tags":[...]}],"more":bool,"tags":[...]}}, where {@code id} is
 *       the bookmark's id as decimal text, {@code added} when it was added, in milliseconds since the epoch, {@code more}
 *       whether bookmarks follow, and the answer's {@code tags} every tag any bookmark has;</li>
 *   <li>{@code {"op":"delete","id":"<id>"}}: deletes the bookmark, if it is still there, and answers {@code {}};</li>
 *   <li>{@code {"op":"clear"}}: deletes every bookmark, and answers {@code {}}.</li>
 * </ul>
 * Members a request does not use are ignored. Anything else fails with "Jasper does not understand this request.".
 */
public final class BookmarksPage {
    /** The page's name. */
    public static final String NAME = "bookmarks";
    /** The page's address. */
    public static final URI ADDRESS = URI.create(AboutScheme.NAME + ":" + NAME);
    /** How many bookmarks a list answers at most. */
    static final int PAGE = 50;
    /** The reason a request the page cannot make fails with. */
    static final String NOT_UNDERSTOOD = HistoryPage.NOT_UNDERSTOOD;
    private static final Pattern ID = Pattern.compile("[0-9]{1,19}");
    private static final Pattern OFFSET = Pattern.compile("[0-9]{1,9}");
    private static final String EMPTY = "{}";

    private BookmarksPage() { }

    /**
     * The answer to one of the page's requests, from {@code bookmarks}. Any thread: it only hands the work to the
     * database's own thread.
     *
     * @param bookmarks the bookmarks the page shows
     * @param request what the page asked, as JSON
     * @return the answer's JSON; failed with "Jasper does not understand this request." for a request the page does not
     *     make, or with the database's own reason when it cannot answer. Whatever the request, never an exception
     */
    public static CompletableFuture<String> answer(BookmarkStore bookmarks, String request) {
        Objects.requireNonNull(bookmarks, "bookmarks");
        try {
            Map<String, Object> asked = StrictJson.object(request, 2).orElse(null);
            if (asked == null) return notUnderstood();
            return switch (asked.get("op") instanceof String op ? op : "") {
                case "list" -> list(bookmarks, asked);
                case "delete" -> id(asked.get("id")).map(id -> bookmarks.delete(id).thenApply(deleted -> EMPTY)).orElseGet(BookmarksPage::notUnderstood);
                case "clear" -> bookmarks.clear().thenApply(cleared -> EMPTY);
                default -> notUnderstood();
            };
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    /** A list request: its search, order, tags and offset, each optional. */
    private static CompletableFuture<String> list(BookmarkStore bookmarks, Map<String, Object> asked) {
        if (!(asked.getOrDefault("query", "") instanceof String words)) return notUnderstood();
        Optional<BookmarkStore.Sort> sort = sort(asked.getOrDefault("sort", "date"));
        Optional<List<String>> tags = tags(asked.getOrDefault("tags", List.of()));
        Object written = asked.getOrDefault("offset", "0");
        if (sort.isEmpty() || tags.isEmpty() || !(written instanceof String digits) || !OFFSET.matcher(digits).matches()) return notUnderstood();
        int offset = Integer.parseInt(digits);
        return bookmarks.list(words, sort.get(), new LinkedHashSet<>(tags.get()), offset, PAGE)
            .thenCombine(bookmarks.allTags(), BookmarksPage::listed);
    }

    private static Optional<BookmarkStore.Sort> sort(Object written) {
        return switch (written instanceof String name ? name : "") {
            case "date" -> Optional.of(BookmarkStore.Sort.DATE_ADDED);
            case "title" -> Optional.of(BookmarkStore.Sort.TITLE);
            case "url" -> Optional.of(BookmarkStore.Sort.URL);
            default -> Optional.empty();
        };
    }

    /** The tags a request chose, by the tag rule; empty when the member is no list of strings. */
    private static Optional<List<String>> tags(Object written) {
        if (!(written instanceof List<?> given)) return Optional.empty();
        var strings = new ArrayList<String>(given.size());
        for (Object tag : given) {
            if (!(tag instanceof String text)) return Optional.empty();
            strings.add(text);
        }
        return Optional.of(BookmarkStore.keptTags(strings));
    }

    /** A page of bookmarks, and every tag, as the page reads them. */
    private static String listed(BookmarkStore.BookmarkPage page, List<String> allTags) {
        var found = new ArrayList<Object>(page.bookmarks().size());
        for (BookmarkStore.Bookmark bookmark : page.bookmarks()) {
            var listed = new LinkedHashMap<String, Object>();
            listed.put("id", Long.toString(bookmark.id()));
            listed.put("url", bookmark.url());
            listed.put("title", bookmark.title());
            listed.put("added", bookmark.addedAt());
            listed.put("tags", new ArrayList<Object>(bookmark.tags()));
            found.add(listed);
        }
        var answer = new LinkedHashMap<String, Object>();
        answer.put("bookmarks", found);
        answer.put("more", page.more());
        answer.put("tags", new ArrayList<Object>(allTags));
        return StrictJson.write(answer);
    }

    /** A bookmark's id, written as the page writes it: decimal digits that fit a {@code long}. */
    private static Optional<Long> id(Object written) {
        if (!(written instanceof String text) || !ID.matcher(text).matches()) return Optional.empty();
        try {
            return Optional.of(Long.parseLong(text));
        } catch (NumberFormatException tooLarge) {
            return Optional.empty();
        }
    }

    private static CompletableFuture<String> notUnderstood() {
        return CompletableFuture.failedFuture(new IllegalArgumentException(NOT_UNDERSTOOD));
    }
}
