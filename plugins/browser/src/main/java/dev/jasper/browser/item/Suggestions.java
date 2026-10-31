package dev.jasper.browser.item;

import dev.jasper.browser.settings.BrowserSettings;
import dev.jasper.sdk.ui.Nodes;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The address field's suggestions (spec section 6). Each row's id says what it loads, so the same row keeps its id from
 * one keystroke to the next: {@value #NAVIGATE} and an address, which loads that address, or {@value #SEARCH} and terms,
 * which searches for them with the chosen engine. A store's rows use the same ids.
 */
final class Suggestions {
    /** The start of a row's id that loads the address after it. */
    static final String NAVIGATE = "navigate:";
    /** The start of a row's id that searches for the terms after it. */
    static final String SEARCH = "search:";
    /** The most rows of the engine's suggestions. */
    static final int ENGINE_ROWS = 5;
    /** The most characters of the typed text a row shows. */
    static final int SHOWN = 300;
    /** The longest text the engine is asked about. */
    static final int LONGEST = 1_000;
    private static final Set<String> LOCAL_SCHEMES = Set.of("file", "about", "jasper-about");

    private Suggestions() { }

    /**
     * The rows for {@code typed}, in order:
     * <ol>
     *   <li>the typed address, "Navigate to “https://…”" with the detail "URL", when the text names an address that is no
     *       bare word: with a scheme, a local path, a dot, {@code localhost}, a port or an IP address;</li>
     *   <li>"Search for “…”", with the detail "Web Search";</li>
     *   <li>up to {@value #ENGINE_ROWS} of the engine's suggestions, with the detail "Search Suggestion", leaving out the
     *       typed text itself;</li>
     *   <li>a bare word's host, such as {@code jcef} or {@code devbox/app}, as the typed address is;</li>
     *   <li>then {@code stores}' rows.</li>
     * </ol>
     * When the typed text is itself a results page's address, a search for its terms comes first. A row whose id is
     * already listed is left out. Chromium's own {@code chrome:} pages are not offered (spec section 6; plan 4b's P45):
     * {@code chrome:} text lists no row, and an engine's {@code chrome:} suggestion is left out.
     *
     * @param typed the field's text
     * @param settings the settings, which say what a results page is
     * @param engineSuggestions the engine's suggestions for the text, in its order
     * @param stores the rows of Jasper's bookmarks and history, in their order
     * @return the rows; none for blank or {@code chrome:} text
     */
    static List<Nodes.Suggestion> rank(String typed, BrowserSettings settings, List<String> engineSuggestions, List<Nodes.Suggestion> stores) {
        String text = typed.strip();
        if (!AddressInput.resolves(text)) return List.of();
        var rows = new Rows();
        Optional<URI> address = AddressInput.address(text);
        address.flatMap(typedAddress -> SearchEngine.queryOf(typedAddress, settings)).ifPresent(rows::search);
        // Plan 4b's P27: only a bare word ranks its address after the search; any other address comes first.
        boolean bareWord = address.isPresent() && AddressInput.bareWord(text);
        if (!bareWord) address.ifPresent(rows::navigate);
        rows.search(text);
        int engine = 0;
        for (String suggestion : engineSuggestions) {
            if (engine == ENGINE_ROWS) break;
            String terms = suggestion.strip();
            if (!AddressInput.resolves(terms) || terms.equalsIgnoreCase(text)) continue;
            if (rows.add(new Nodes.Suggestion(SEARCH + terms, shown(terms), "Search Suggestion"))) engine++;
        }
        if (bareWord) address.ifPresent(rows::navigate);
        stores.forEach(rows::add);
        return rows.list;
    }

    /**
     * What the row {@code id} loads.
     *
     * @param id a row's id
     * @param settings the settings, which choose the engine
     * @return the address, or the search for the terms; empty for an id that loads nothing, such as one of a
     *     {@code chrome:} page, blank terms or {@code chrome:} terms
     */
    static Optional<URI> target(String id, BrowserSettings settings) {
        if (id.startsWith(NAVIGATE)) return AddressInput.address(id.substring(NAVIGATE.length()));
        if (!id.startsWith(SEARCH)) return Optional.empty();
        String terms = id.substring(SEARCH.length()).strip();
        return AddressInput.resolves(terms) ? Optional.of(settings.searchEngine().search(terms, settings)) : Optional.empty();
    }

    /**
     * Where to ask the engine for suggestions for {@code typed}. Plan 4b's P24: text that is an absolute local path,
     * has a {@code file:}, {@code about:} or {@code jasper-about:} scheme, or carries a user name or password is never
     * sent, nor blank text, {@code chrome:} text (P45) or text over {@value #LONGEST} characters.
     *
     * @param typed the field's text
     * @param settings the settings, which choose the engine
     * @return the address of the engine's suggestions; empty for text that is not sent, or an engine without them
     */
    static Optional<URI> request(String typed, BrowserSettings settings) {
        String text = typed.strip();
        if (!AddressInput.resolves(text) || text.length() > LONGEST || AddressInput.localPath(text).isPresent()
            || AddressInput.scheme(text).filter(LOCAL_SCHEMES::contains).isPresent() || userInfo(text)) return Optional.empty();
        return settings.searchEngine().suggestions(text, settings);
    }

    /**
     * Whether {@code text} carries a user name or password: an {@code @} before its path, query or fragment, after any
     * {@code scheme://}; or, after {@code scheme://}, a colon there that starts no port, as a password does while it is
     * typed, before its {@code @}.
     */
    private static boolean userInfo(String text) {
        int scheme = text.indexOf("://");
        int from = scheme < 0 ? 0 : scheme + 3, end = from;
        while (end < text.length() && "/?#".indexOf(text.charAt(end)) < 0) end++;
        String authority = text.substring(from, end);
        if (authority.indexOf('@') >= 0) return true;
        if (scheme < 0) return false;
        int bracket = authority.startsWith("[") ? authority.indexOf(']') : -1;
        String host = bracket > 0 ? authority.substring(bracket + 1) : authority;
        int colon = host.indexOf(':');
        return colon >= 0 && !host.substring(colon + 1).chars().allMatch(c -> c >= '0' && c <= '9');
    }

    /** {@code text} as a row shows it: its first {@value #SHOWN} characters, then an ellipsis. */
    private static String shown(String text) {
        return text.length() <= SHOWN ? text : text.substring(0, SHOWN) + "…";
    }

    /** The rows so far, each id once. */
    private static final class Rows {
        final List<Nodes.Suggestion> list = new ArrayList<>();
        private final Set<String> ids = new HashSet<>();

        boolean add(Nodes.Suggestion row) {
            if (!ids.add(row.id())) return false;
            list.add(row);
            return true;
        }

        void navigate(URI address) {
            String shown = address.toString();
            add(new Nodes.Suggestion(NAVIGATE + shown, "Navigate to “" + shown(shown) + "”", "URL"));
        }

        void search(String terms) {
            add(new Nodes.Suggestion(SEARCH + terms, "Search for “" + shown(terms) + "”", "Web Search"));
        }
    }
}
