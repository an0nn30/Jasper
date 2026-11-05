package dev.jasper.browser.item;

import dev.jasper.browser.store.BookmarkStore.Bookmark;
import dev.jasper.browser.store.HistoryStore.Visit;
import dev.jasper.sdk.ui.Nodes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The address field's rows of Jasper's bookmarks and history (spec section 6), matched by prefix. With the text trimmed,
 * an address matches when the text begins it after its scheme ({@code github.com/x} of {@code https://github.com/x}), or
 * after its scheme and a leading {@code www.}; a title matches when the text begins it, or begins one of its words. Case
 * is ignored. Each row's id is the field's own for the address it loads ({@link Suggestions#NAVIGATE}), so an address is
 * listed once ({@link Suggestions#key}), the typed address's own row first.
 */
final class StoreSuggestions {
    /** The most rows of bookmarks. */
    static final int BOOKMARKS = 10;
    /** The most rows of history, after the bookmarks' (the plan's call: the spec gives no count). */
    static final int VISITS = 5;
    /** The most visited first, and of those visited as often, the most recent. */
    private static final Comparator<Visit> MOST_VISITED = Comparator.comparingInt(Visit::count).thenComparingLong(Visit::lastVisit)
        .thenComparingLong(Visit::id).reversed();

    private StoreSuggestions() { }

    /**
     * The rows for {@code typed}: up to {@value #BOOKMARKS} bookmarks, newest first, with the detail "Bookmark", then up to
     * {@value #VISITS} visited sites not already listed, the most visited first, with the detail "Visited N times". Each
     * shows its title, or its address when it has none.
     *
     * @param typed the field's text
     * @param bookmarks the bookmarks, newest first
     * @param visits the visited sites
     * @return the rows; none for blank text
     */
    static List<Nodes.Suggestion> of(String typed, List<Bookmark> bookmarks, List<Visit> visits) {
        String text = typed.strip();
        if (text.isEmpty()) return List.of();
        var rows = new ArrayList<Nodes.Suggestion>();
        Set<String> listed = new HashSet<>();
        for (Bookmark bookmark : bookmarks) {
            if (rows.size() == BOOKMARKS) break;
            if (matches(text, bookmark.url(), bookmark.title()) && listed.add(key(bookmark.url())))
                rows.add(row(bookmark.url(), bookmark.title(), "Bookmark"));
        }
        List<Visit> matching = visits.stream().filter(visit -> matches(text, visit.url(), visit.title())).sorted(MOST_VISITED).toList();
        int visited = 0;
        for (Visit visit : matching) {
            if (visited == VISITS) break;
            if (!listed.add(key(visit.url()))) continue;
            rows.add(row(visit.url(), visit.title(), "Visited " + visit.count() + (visit.count() == 1 ? " time" : " times")));
            visited++;
        }
        return rows;
    }

    /** What tells {@code address}'s row from the others' ({@link Suggestions#key}). */
    private static String key(String address) {
        return Suggestions.key(Suggestions.NAVIGATE + address);
    }

    /** Whether {@code text} begins {@code address} after its scheme, or after that and {@code www.}, or a word of {@code title}. */
    private static boolean matches(String text, String address, String title) {
        int rest = afterScheme(address);
        if (begins(address, rest, text)) return true;
        if (begins(address, rest, "www.") && begins(address, rest + 4, text)) return true;
        for (int at = 0; at < title.length(); at++)
            if (wordStart(title, at) && begins(title, at, text)) return true;
        return false;
    }

    /** Where {@code address} goes on after its scheme's colon and the {@code //} that may follow it. */
    private static int afterScheme(String address) {
        int colon = address.indexOf(':') + 1;
        return address.startsWith("//", colon) ? colon + 2 : colon;
    }

    /** Whether {@code text} begins {@code value} at {@code at}, ignoring case. */
    private static boolean begins(String value, int at, String text) {
        return value.regionMatches(true, at, text, 0, text.length());
    }

    /** Whether a word of {@code title} starts at {@code at}: its first character, or a letter or digit after neither. */
    private static boolean wordStart(String title, int at) {
        return at == 0 || Character.isLetterOrDigit(title.charAt(at)) && !Character.isLetterOrDigit(title.charAt(at - 1));
    }

    private static Nodes.Suggestion row(String address, String title, String detail) {
        return new Nodes.Suggestion(Suggestions.NAVIGATE + address, Suggestions.shown(title.isBlank() ? address : title), detail);
    }
}
