package dev.jasper.browser.item;

import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.sdk.ui.Nodes.Suggestion;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The address field's suggestions in spec section 6's order: the typed address, the web search, the engine's
 * suggestions, single-word hosts, then the stores; what each loads; and which typed text asks the engine at all (plan
 * 4b's P24).
 */
class SuggestionsTest {
    private static final BrowserSettings GOOGLE = SearchEngineTest.settings(SearchEngine.GOOGLE, "", "");

    @Test void aDomainRanksItsAddressThenTheSearchThenTheEnginesSuggestions() {
        assertThat(Suggestions.rank("example.com", GOOGLE, List.of("example.com login", "example.com mail"), List.of())).containsExactly(
            new Suggestion("navigate:https://example.com", "Navigate to “https://example.com”", "URL"),
            new Suggestion("search:example.com", "Search for “example.com”", "Web Search"),
            new Suggestion("search:example.com login", "example.com login", "Search Suggestion"),
            new Suggestion("search:example.com mail", "example.com mail", "Search Suggestion"));
    }

    @Test void textWithAnOfferedSchemeRanksItsAddressFirst() {
        assertThat(Suggestions.rank("  https://example.com/a b ", GOOGLE, List.of(), List.of())).containsExactly(
            new Suggestion("navigate:https://example.com/a%20b", "Navigate to “https://example.com/a%20b”", "URL"),
            new Suggestion("search:https://example.com/a b", "Search for “https://example.com/a b”", "Web Search"));
        assertThat(Suggestions.rank("/tmp/a.html", GOOGLE, List.of(), List.of())).first()
            .isEqualTo(new Suggestion("navigate:file:///tmp/a.html", "Navigate to “file:///tmp/a.html”", "URL"));
    }

    @Test void otherTextRanksTheSearchFirstAndAtMostFiveSuggestions() {
        assertThat(Suggestions.rank("what is", GOOGLE, List.of("what is", "What Is", " what is love ", "", "what is jcef", "what is a url",
                "what is java", "what is chromium", "what is jcef", "what is swing"), List.of()))
            .extracting(Suggestion::title).as("the typed text, blanks and repeats left out")
            .containsExactly("Search for “what is”", "what is love", "what is jcef", "what is a url", "what is java", "what is chromium");
        assertThat(Suggestions.rank("3.14", GOOGLE, List.of(), List.of())).as("a dot, yet no address")
            .containsExactly(new Suggestion("search:3.14", "Search for “3.14”", "Web Search"));
    }

    /** A bare word is a host too, which ranks after the engine's suggestions, as it is most often a search. */
    @Test void aBareWordHostRanksAfterTheEnginesSuggestions() {
        assertThat(Suggestions.rank("jcef", GOOGLE, List.of("jcef maven"), List.of())).containsExactly(
            new Suggestion("search:jcef", "Search for \u201cjcef\u201d", "Web Search"),
            new Suggestion("search:jcef maven", "jcef maven", "Search Suggestion"),
            new Suggestion("navigate:https://jcef", "Navigate to \u201chttps://jcef\u201d", "URL"));
        assertThat(Suggestions.rank("my_host", GOOGLE, List.of(), List.of())).extracting(Suggestion::id)
            .containsExactly("search:my_host", "navigate:https://my_host");
        assertThat(Suggestions.rank("devbox/app", GOOGLE, List.of(), List.of())).extracting(Suggestion::id)
            .containsExactly("search:devbox/app", "navigate:https://devbox/app");
    }

    /**
     * Plan 4b's P27: {@code localhost}, a host with a port and an IP address rank their address first, as text with a
     * scheme or a dot does, so Enter right after typing navigates there, as the field's Enter with no list does, and no
     * intranet name goes to the engine.
     */
    @Test void localhostAHostWithAPortAndAnIpAddressRankTheirAddressFirst() {
        for (String text : List.of("localhost", "LOCALHOST", "localhost:3000/app", "localhost/status", "foo:8080", "my_host:8080",
                "1.2.3.4", "10.0.0.1:8443/x", "[::1]:8080", "[::1]")) {
            Suggestion top = Suggestions.rank(text, GOOGLE, List.of("an engine suggestion"), List.of()).getFirst();
            URI resolved = AddressInput.resolve(text, GOOGLE).orElseThrow();
            assertThat(top).as(text).isEqualTo(new Suggestion("navigate:" + resolved, "Navigate to \u201c" + resolved + "\u201d", "URL"));
            assertThat(Suggestions.target(top.id(), GOOGLE)).as(text).contains(resolved);
        }
        assertThat(Suggestions.rank("localhost:3000", GOOGLE, List.of(), List.of())).extracting(Suggestion::id)
            .containsExactly("navigate:https://localhost:3000", "search:localhost:3000");
    }

    @Test void theStoresComeLastWithoutRowsAlreadyListed() {
        var bookmark = new Suggestion("navigate:https://example.com/docs", "Example docs", "Bookmark");
        var repeat = new Suggestion("navigate:https://example.com", "Example", "Bookmark");
        assertThat(Suggestions.rank("example.com", GOOGLE, List.of(), List.of(bookmark, repeat))).extracting(Suggestion::id)
            .containsExactly("navigate:https://example.com", "search:example.com", "navigate:https://example.com/docs");
    }

    /** When the typed text is itself a results page's address, its terms rank first. */
    @Test void aResultsAddressRanksItsTermsFirst() {
        assertThat(Suggestions.rank("https://www.google.com/search?q=what+is+jcef", GOOGLE, List.of(), List.of())).extracting(Suggestion::title)
            .containsExactly("Search for “what is jcef”", "Navigate to “https://www.google.com/search?q=what+is+jcef”",
                "Search for “https://www.google.com/search?q=what+is+jcef”");
        var custom = SearchEngineTest.settings(SearchEngine.CUSTOM, "https://example.com/find?terms=%s", "");
        assertThat(Suggestions.rank("example.com/find?terms=jcef", custom, List.of(), List.of())).first()
            .isEqualTo(new Suggestion("search:jcef", "Search for “jcef”", "Web Search"));
    }

    @Test void blankTextSuggestsNothing() {
        assertThat(Suggestions.rank("", GOOGLE, List.of("x"), List.of())).isEmpty();
        assertThat(Suggestions.rank("  \t", GOOGLE, List.of("x"), List.of())).isEmpty();
    }

    @Test void aLongTextShowsItsBeginningAndKeepsItWholeInTheId() {
        String text = "a".repeat(400) + " b";
        Suggestion search = Suggestions.rank(text, GOOGLE, List.of(), List.of()).getFirst();
        assertThat(search.id()).isEqualTo("search:" + text);
        assertThat(search.title()).hasSize("Search for “”".length() + Suggestions.SHOWN + 1).endsWith("…”");
    }

    @Test void aRowLoadsItsAddressOrSearchesItsTermsWithTheChosenEngine() {
        assertThat(Suggestions.target("navigate:https://example.com/a%20b", GOOGLE)).contains(URI.create("https://example.com/a%20b"));
        assertThat(Suggestions.target("search:what is jcef", GOOGLE)).contains(URI.create("https://google.com/search?q=what+is+jcef"));
        assertThat(Suggestions.target("search:what is jcef", SearchEngineTest.settings(SearchEngine.BING, "", "")))
            .contains(URI.create("https://www.bing.com/search?q=what+is+jcef"));
        assertThat(Suggestions.target("navigate:not a uri", GOOGLE)).isEmpty();
        assertThat(Suggestions.target("navigate:chrome://version", GOOGLE)).as("never Chromium's own pages").isEmpty();
        assertThat(Suggestions.target("search:", GOOGLE)).isEmpty();
        assertThat(Suggestions.target("bookmark:1", GOOGLE)).isEmpty();
    }

    @Test void theEngineIsAskedAboutOrdinaryText() {
        assertThat(Suggestions.request(" what is ", GOOGLE))
            .contains(URI.create("https://suggestqueries.google.com/complete/search?client=firefox&q=what+is"));
        assertThat(Suggestions.request("example.com/docs", GOOGLE)).isPresent();
        assertThat(Suggestions.request("https://example.com/@me", GOOGLE)).isPresent();
        assertThat(Suggestions.request("https://example.com:8080/a:b", GOOGLE)).as("a port").isPresent();
        assertThat(Suggestions.request("https://example.com:", GOOGLE)).as("a port being typed").isPresent();
        assertThat(Suggestions.request("http://[::1]:8080/", GOOGLE)).as("an IPv6 host").isPresent();
        assertThat(Suggestions.request("python: lists", GOOGLE)).as("a colon without scheme://").isPresent();
        assertThat(Suggestions.request("what is", SearchEngineTest.settings(SearchEngine.CUSTOM, "https://example.com/?q=%s", "")))
            .as("custom without a suggestion URL").isEmpty();
    }

    /**
     * P45: Chromium's own {@code chrome:} pages are not offered (spec section 6), so {@code chrome:} text, in any case
     * and with or without {@code //}, lists no row, loads nothing and asks the engine nothing; an engine's
     * {@code chrome:} suggestion is left out.
     */
    @Test void chromeTextOffersNothing() {
        for (String text : List.of("chrome://version", "  CHROME://settings ", "chrome:version", "Chrome:")) {
            assertThat(Suggestions.rank(text, GOOGLE, List.of("chrome://version", "chrome browser"), List.of())).as(text).isEmpty();
            assertThat(Suggestions.request(text, GOOGLE)).as(text).isEmpty();
            assertThat(Suggestions.target("search:" + text, GOOGLE)).as(text).isEmpty();
            assertThat(Suggestions.target("navigate:" + text, GOOGLE)).as(text).isEmpty();
        }
        assertThat(Suggestions.rank("chro", GOOGLE, List.of("chrome://flags", "CHROME:version", "chrome browser"), List.of()))
            .extracting(Suggestion::id).as("the engine's chrome: suggestions left out")
            .containsExactly("search:chro", "search:chrome browser", "navigate:https://chro");
        assertThat(Suggestions.target("search:chrome browser", GOOGLE)).contains(URI.create("https://google.com/search?q=chrome+browser"));
    }

    /** P24: typed local paths and passwords never reach the search engine, not even a password half typed. */
    @Test void localPathsLocalSchemesAndUserInfoAreNeverSent() {
        for (String text : java.util.List.of("", "   ", "/Users/me/secret.html", "~/notes.txt", "file:///etc/hosts", "FILE:/tmp/x",
                "about:blank", "jasper-about:welcome", "https://ada:secret@example.com/", "ada:secret@example.com",
                "ftp://ada@example.com", "someone@example.com", "https://ada:s", "https://ada:secret", "http://[::1]:x",
                "x".repeat(Suggestions.LONGEST + 1)))
            assertThat(Suggestions.request(text, GOOGLE)).as(text.length() > 40 ? "a long text" : text).isEmpty();
        assertThat(Suggestions.request("x".repeat(Suggestions.LONGEST), GOOGLE)).isPresent();
    }
}
