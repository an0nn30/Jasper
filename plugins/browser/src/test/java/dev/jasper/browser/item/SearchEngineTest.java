package dev.jasper.browser.item;

import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.browser.settings.BrowserSettings.LinkOpening;
import dev.jasper.browser.settings.BrowserSettings.LocalFileOpening;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The search engines of spec section 6, with its exact search and suggestion URLs and {@code %s} for the terms: what a
 * search loads, where its suggestions come from, and the terms a results page shows in the address field.
 */
class SearchEngineTest {
    private static final BrowserSettings GOOGLE = settings(SearchEngine.GOOGLE, "", "");

    @Test void eachEngineHasItsNameAndToken() {
        assertThat(SearchEngine.values()).extracting(SearchEngine::displayName)
            .containsExactly("Google", "DuckDuckGo", "Bing", "Kagi", "Custom");
        assertThat(SearchEngine.values()).extracting(SearchEngine::token)
            .containsExactly("google", "duckduckgo", "bing", "kagi", "custom");
    }

    /** Plan 4b's P26: "Search with \u27e8engine\u27e9" names Custom by its search URL's host, and "Custom" only without one. */
    @Test void customIsNamedByItsSearchUrlsHost() {
        assertThat(SearchEngine.GOOGLE.displayName(GOOGLE)).isEqualTo("Google");
        assertThat(SearchEngine.DUCKDUCKGO.displayName(settings(SearchEngine.CUSTOM, "https://x.example/?q=%s", ""))).isEqualTo("DuckDuckGo");
        assertThat(SearchEngine.CUSTOM.displayName(settings(SearchEngine.CUSTOM, "https://search.example.com/find?q=%s", "")))
            .isEqualTo("search.example.com");
        assertThat(SearchEngine.CUSTOM.displayName(settings(SearchEngine.CUSTOM, "", ""))).as("no search URL").isEqualTo("Custom");
    }

    @Test void eachEngineSearchesAtItsSearchUrl() {
        assertThat(search(SearchEngine.GOOGLE)).isEqualTo(URI.create("https://google.com/search?q=what+is+jcef%3F"));
        assertThat(search(SearchEngine.DUCKDUCKGO)).isEqualTo(URI.create("https://duckduckgo.com/?q=what+is+jcef%3F"));
        assertThat(search(SearchEngine.BING)).isEqualTo(URI.create("https://www.bing.com/search?q=what+is+jcef%3F"));
        assertThat(search(SearchEngine.KAGI)).isEqualTo(URI.create("https://kagi.com/search?q=what+is+jcef%3F"));
        var custom = settings(SearchEngine.CUSTOM, "https://search.example.com/find?lang=en&terms=%s", "");
        assertThat(SearchEngine.CUSTOM.search("what is jcef?", custom))
            .isEqualTo(URI.create("https://search.example.com/find?lang=en&terms=what+is+jcef%3F"));
        assertThat(SearchEngine.GOOGLE.search("c++ & café", GOOGLE)).as("the terms are escaped as UTF-8")
            .isEqualTo(URI.create("https://google.com/search?q=c%2B%2B+%26+caf%C3%A9"));
    }

    /** A custom URL with {@code %s} in its path writes a space as {@code %20}, which a path does not read as a plus. */
    @Test void aCustomUrlWithTheTermsInItsPathEscapesSpacesForAPath() {
        var path = settings(SearchEngine.CUSTOM, "https://example.com/search/%s/all", "");
        assertThat(SearchEngine.CUSTOM.search("a b+c", path)).isEqualTo(URI.create("https://example.com/search/a%20b%2Bc/all"));
        var twice = settings(SearchEngine.CUSTOM, "https://example.com/?q=%s&again=%s", "");
        assertThat(SearchEngine.CUSTOM.search("x y", twice)).as("every %s").isEqualTo(URI.create("https://example.com/?q=x+y&again=x+y"));
    }

    /** Custom without a search URL searches with Google, the default, rather than loading nothing. */
    @Test void customWithAnEmptySearchUrlSearchesWithGoogle() {
        assertThat(SearchEngine.CUSTOM.search("jcef", settings(SearchEngine.CUSTOM, "", "")))
            .isEqualTo(URI.create("https://google.com/search?q=jcef"));
        assertThat(SearchEngine.GOOGLE.search("jcef", settings(SearchEngine.GOOGLE, "https://example.com/?q=%s", "")))
            .as("a built-in engine ignores the custom URL").isEqualTo(URI.create("https://google.com/search?q=jcef"));
    }

    /**
     * Plan 4b's P28: a custom URL is an absolute http or https address with {@code %s} in it, after its host, so any terms
     * fill it. Anything else is no template: the settings never hold one, and a broken Custom URL never searches with
     * Google unnoticed.
     */
    @Test void aTemplateIsAnAbsoluteWebAddressWithThePlaceholder() {
        for (String template : List.of("https://search.example.com/?q=%s", "http://intranet:8080/find/%s", "HTTPS://example.com/?q=%s",
                "https://example.com/#q=%s"))
            assertThat(SearchEngine.template(template)).as(template).isTrue();
        for (String template : List.of("", "https://example.com/?q=", "%s", "search.example.com/?q=%s", "ftp://example.com/?q=%s",
                "file:///tmp/%s", "javascript:%s", "https://exa mple.com/?q=%s", "https:///?q=%s", "https://ada:secret@example.com/?q=%s",
                "https://%s.example.com/", "https://example.com:%s/", "https://example.com/?q=%%s"))
            assertThat(SearchEngine.template(template)).as(template).isFalse();
        for (String template : List.of("https://example.com/?q=", "ftp://example.com/?q=%s")) {
            assertThatThrownBy(() -> settings(SearchEngine.CUSTOM, template, "")).as(template)
                .isInstanceOf(IllegalArgumentException.class).hasMessage("The URL must start with https:// or http:// and contain %s");
            assertThatThrownBy(() -> settings(SearchEngine.CUSTOM, "", template)).as(template)
                .isInstanceOf(IllegalArgumentException.class).hasMessage("The URL must start with https:// or http:// and contain %s");
        }
    }

    @Test void eachEngineSuggestsFromItsSuggestionUrl() {
        assertThat(SearchEngine.GOOGLE.suggestions("what is", GOOGLE))
            .contains(URI.create("https://suggestqueries.google.com/complete/search?client=firefox&q=what+is"));
        assertThat(SearchEngine.DUCKDUCKGO.suggestions("what is", GOOGLE)).contains(URI.create("https://duckduckgo.com/ac/?q=what+is&type=list"));
        assertThat(SearchEngine.BING.suggestions("what is", GOOGLE)).contains(URI.create("https://www.bing.com/osjson.aspx?query=what+is"));
        assertThat(SearchEngine.KAGI.suggestions("what is", GOOGLE)).contains(URI.create("https://kagi.com/api/autosuggest?q=what+is"));
        var custom = settings(SearchEngine.CUSTOM, "https://example.com/?q=%s", "https://example.com/suggest?q=%s&n=5");
        assertThat(SearchEngine.CUSTOM.suggestions("what is", custom)).contains(URI.create("https://example.com/suggest?q=what+is&n=5"));
    }

    @Test void customWithoutASuggestionUrlSuggestsNothing() {
        assertThat(SearchEngine.CUSTOM.suggestions("jcef", settings(SearchEngine.CUSTOM, "https://example.com/?q=%s", ""))).isEmpty();
    }

    /** Each engine's results page gives its terms, as the address field shows them; any other address gives none. */
    @Test void aResultsPageOfAnyEngineGivesItsTerms() {
        assertThat(SearchEngine.queryOf(URI.create("https://www.google.com/search?q=what+is+jcef&sca_esv=1&source=hp"), GOOGLE))
            .contains("what is jcef");
        assertThat(SearchEngine.queryOf(URI.create("https://google.com/search?q=jcef"), GOOGLE)).contains("jcef");
        assertThat(SearchEngine.queryOf(URI.create("https://duckduckgo.com/?t=h_&q=c%2B%2B+%26+java&ia=web"), GOOGLE)).contains("c++ & java");
        assertThat(SearchEngine.queryOf(URI.create("https://www.bing.com/search?q=caf%C3%A9&form=QBLH"), GOOGLE)).contains("café");
        assertThat(SearchEngine.queryOf(URI.create("https://bing.com/search?q=jcef"), GOOGLE)).contains("jcef");
        assertThat(SearchEngine.queryOf(URI.create("https://kagi.com/search?q=jcef"), GOOGLE)).as("whichever engine is chosen")
            .contains("jcef");
        assertThat(SearchEngine.queryOf(URI.create("http://www.google.com/search?q=jcef"), GOOGLE)).contains("jcef");
        assertThat(SearchEngine.queryOf(URI.create("https://WWW.Google.COM/search?q=jcef"), GOOGLE)).contains("jcef");
        assertThat(SearchEngine.queryOf(URI.create("https://www.google.com/search?q=one&q=two"), GOOGLE)).as("the first").contains("one");
    }

    @Test void theCustomEnginesResultsPageIsKnownByItsTemplate() {
        var query = settings(SearchEngine.CUSTOM, "https://search.example.com/find?lang=en&terms=%s", "");
        assertThat(SearchEngine.queryOf(URI.create("https://search.example.com/find?terms=a+b&lang=de"), query)).contains("a b");
        assertThat(SearchEngine.queryOf(URI.create("https://search.example.com/other?terms=a+b"), query)).as("another path").isEmpty();
        var path = settings(SearchEngine.CUSTOM, "https://example.com/search/%s/all", "");
        assertThat(SearchEngine.queryOf(URI.create("https://example.com/search/a%20b+c/all"), path)).contains("a b+c");
        assertThat(SearchEngine.queryOf(URI.create("https://example.com/search/a/b/some"), path)).isEmpty();
        assertThat(SearchEngine.queryOf(URI.create("https://search.example.com/find?terms=a"), GOOGLE))
            .as("not while another engine is chosen").isEmpty();
    }

    @Test void otherAddressesAndOddOnesGiveNoTerms() {
        for (String address : List.of("https://www.google.com/", "https://www.google.com/search", "https://www.google.com/search?q=",
                "https://www.google.com/search?q=+", "https://www.google.com/maps?q=jcef", "https://example.com/search?q=jcef",
                "https://notgoogle.com/search?q=jcef", "https://google.com.evil.example/search?q=jcef", "https://duckduckgo.com/about?q=x",
                "https://www.google.com/search?q=%E2%28", "https://www.google.com/search?qq=jcef",
                "file:///search?q=jcef", "jasper-about:welcome", "about:blank", "mailto:someone@example.com", "urn:isbn:0451450523",
                "https://www.google.com/search?&&=&q", "/search?q=relative"))
            assertThat(SearchEngine.queryOf(URI.create(address), GOOGLE)).as(address).isEmpty();
        var fragment = settings(SearchEngine.CUSTOM, "https://example.com/#q=%s", "");
        assertThat(SearchEngine.queryOf(URI.create("https://example.com/#q=x"), fragment)).as("terms after a fragment").isEmpty();
    }

    private static URI search(SearchEngine engine) {
        return engine.search("what is jcef?", GOOGLE);
    }

    static BrowserSettings settings(SearchEngine engine, String customSearch, String customSuggestions) {
        return new BrowserSettings(BrowserSettings.WELCOME, 100, LinkOpening.ASK, LocalFileOpening.ASK, engine, customSearch, customSuggestions, false);
    }
}
