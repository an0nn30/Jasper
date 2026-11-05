package dev.jasper.browser.item;

import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.browser.store.BookmarkStore.Bookmark;
import dev.jasper.browser.store.HistoryStore.Visit;
import dev.jasper.sdk.ui.Nodes.Suggestion;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 6's bookmarks and history in the address field's suggestions, by prefix. */
class StoreSuggestionsTest {
    private static final BrowserSettings GOOGLE = SearchEngineTest.settings(SearchEngine.GOOGLE, "", "");

    @Test void anAddressMatchesWithoutItsSchemeOrALeadingWwwAndATitleFromItsStart() {
        List<Bookmark> bookmarks = List.of(bookmark(3, "https://github.com/x", "Repository"), bookmark(2, "https://www.github.com/y", ""),
            bookmark(1, "https://example.com/gh", "GitHub"), bookmark(0, "https://gitlab.example/", "Lab"));
        assertThat(StoreSuggestions.of("git", bookmarks, List.of())).extracting(Suggestion::id).containsExactly(
            "navigate:https://github.com/x", "navigate:https://www.github.com/y", "navigate:https://example.com/gh", "navigate:https://gitlab.example/");
        assertThat(StoreSuggestions.of("  GIT ", bookmarks, List.of())).as("trimmed, either case").hasSize(4);
        assertThat(StoreSuggestions.of("www.gith", bookmarks, List.of())).extracting(Suggestion::id)
            .containsExactly("navigate:https://www.github.com/y");
        assertThat(StoreSuggestions.of("https", bookmarks, List.of())).as("the scheme is no prefix").isEmpty();
        assertThat(StoreSuggestions.of("github.com/x", bookmarks, List.of())).extracting(Suggestion::id)
            .containsExactly("navigate:https://github.com/x");
    }

    @Test void aTitleMatchesAtAnyOfItsWordsButAnAddressOnlyFromItsStart() {
        List<Visit> visits = List.of(visit(1, "https://github.com/x", "GitHub", 4), visit(2, "https://registry.example/", "Docker Hub", 1),
            visit(3, "https://example.org/", "the-hub of things", 1));
        assertThat(StoreSuggestions.of("hub", List.of(), visits)).as("visited as often: the most recent first").extracting(Suggestion::id)
            .containsExactly("navigate:https://example.org/", "navigate:https://registry.example/");
        assertThat(StoreSuggestions.of("docker h", List.of(), visits)).extracting(Suggestion::id)
            .containsExactly("navigate:https://registry.example/");
        assertThat(StoreSuggestions.of("hub of", List.of(), visits)).extracting(Suggestion::id).containsExactly("navigate:https://example.org/");
        assertThat(StoreSuggestions.of("example", List.of(), visits)).as("an address's host from its start only")
            .extracting(Suggestion::id).containsExactly("navigate:https://example.org/");
    }

    @Test void upToTenBookmarksNewestFirstThenUpToFiveVisitsMostVisitedFirst() {
        var bookmarks = new ArrayList<Bookmark>();
        for (int at = 12; at >= 1; at--) bookmarks.add(bookmark(at, "https://a.example/b" + at, "Bookmark " + at));
        var visits = new ArrayList<Visit>();
        for (int at = 1; at <= 7; at++) visits.add(visit(at, "https://a.example/v" + at, "Visit " + at, at));
        List<Suggestion> rows = StoreSuggestions.of("a.example", bookmarks, visits);
        assertThat(rows).hasSize(15);
        assertThat(rows.subList(0, 10)).extracting(Suggestion::id)
            .containsExactlyElementsOf(IntStream.rangeClosed(3, 12).map(at -> 15 - at).mapToObj(at -> "navigate:https://a.example/b" + at).toList());
        assertThat(rows.subList(10, 15)).extracting(Suggestion::id).containsExactly("navigate:https://a.example/v7",
            "navigate:https://a.example/v6", "navigate:https://a.example/v5", "navigate:https://a.example/v4", "navigate:https://a.example/v3");
    }

    @Test void visitsVisitedAsOftenComeMostRecentFirst() {
        List<Visit> visits = List.of(new Visit(1, "https://a.example/1", "", 2, 1, 10), new Visit(2, "https://a.example/2", "", 2, 1, 30),
            new Visit(3, "https://a.example/3", "", 2, 1, 20));
        assertThat(StoreSuggestions.of("a.ex", List.of(), visits)).extracting(Suggestion::id)
            .containsExactly("navigate:https://a.example/2", "navigate:https://a.example/3", "navigate:https://a.example/1");
    }

    @Test void anAddressBookmarkedAndVisitedIsListedOnceAsTheBookmarkAndFiveVisitsFollow() {
        List<Bookmark> bookmarks = List.of(bookmark(1, "https://a.example/1", "One"));
        var visits = new ArrayList<Visit>();
        for (int at = 1; at <= 6; at++) visits.add(visit(at, "https://a.example/" + at, "Visit " + at, 10 - at));
        List<Suggestion> rows = StoreSuggestions.of("a.example", bookmarks, visits);
        assertThat(rows).extracting(Suggestion::id).containsExactly("navigate:https://a.example/1", "navigate:https://a.example/2",
            "navigate:https://a.example/3", "navigate:https://a.example/4", "navigate:https://a.example/5", "navigate:https://a.example/6");
        assertThat(rows.getFirst().detail()).isEqualTo("Bookmark");
    }

    @Test void eachRowShowsItsTitleOrItsAddressAndSaysWhatItIs() {
        List<Suggestion> rows = StoreSuggestions.of("a.example", List.of(bookmark(1, "https://a.example/b", "  ")),
            List.of(visit(1, "https://a.example/once", "Once", 1), visit(2, "https://a.example/thrice", "", 3)));
        assertThat(rows).containsExactly(new Suggestion("navigate:https://a.example/b", "https://a.example/b", "Bookmark"),
            new Suggestion("navigate:https://a.example/thrice", "https://a.example/thrice", "Visited 3 times"),
            new Suggestion("navigate:https://a.example/once", "Once", "Visited 1 time"));
        String longTitle = "x".repeat(400);
        assertThat(StoreSuggestions.of("a.ex", List.of(bookmark(1, "https://a.example/", longTitle)), List.of()).getFirst().title())
            .as("a title cut as the typed text is").isEqualTo("x".repeat(Suggestions.SHOWN) + "…");
    }

    @Test void blankTextSuggestsNothing() {
        List<Bookmark> bookmarks = List.of(bookmark(1, "https://a.example/", "A"));
        List<Visit> visits = List.of(visit(1, "https://a.example/", "A", 1));
        assertThat(StoreSuggestions.of("", bookmarks, visits)).isEmpty();
        assertThat(StoreSuggestions.of("   ", bookmarks, visits)).isEmpty();
    }

    /** A store's rows take the address field's ids, so the typed address's own row wins over the same address stored. */
    @Test void theTypedAddressRowWinsOverTheSameAddressStored() {
        List<Suggestion> stored = StoreSuggestions.of("example.com", List.of(bookmark(1, "https://example.com", "Example")),
            List.of(visit(1, "https://example.com/docs", "Docs", 2)));
        List<Suggestion> rows = Suggestions.rank("example.com", GOOGLE, List.of(), stored);
        assertThat(rows).extracting(Suggestion::id).containsExactly("navigate:https://example.com", "search:example.com",
            "navigate:https://example.com/docs");
        assertThat(rows.getFirst().detail()).isEqualTo("URL");
        assertThat(Suggestions.target(rows.get(2).id(), GOOGLE)).map(Object::toString).contains("https://example.com/docs");
    }

    private static Bookmark bookmark(long id, String url, String title) {
        return new Bookmark(id, url, title, id, List.of());
    }

    private static Visit visit(long id, String url, String title, int count) {
        return new Visit(id, url, title, count, id, id);
    }
}
