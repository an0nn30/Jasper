package dev.jasper.browser.item;

import dev.jasper.browser.http.WebFetch;
import dev.jasper.browser.settings.BrowserSettings;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Settings › Tools › Browser's Default Search Engine (spec sections 6 and 17): where the address field's searches go
 * and where its search suggestions come from, each a URL with {@code %s} where the terms go. Custom takes both URLs from
 * the settings.
 */
public enum SearchEngine {
    /** Google, the default. */
    GOOGLE("google", "Google", "https://google.com/search?q=%s",
        "https://suggestqueries.google.com/complete/search?client=firefox&q=%s"),
    /** DuckDuckGo. */
    DUCKDUCKGO("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s", "https://duckduckgo.com/ac/?q=%s&type=list"),
    /** Bing. */
    BING("bing", "Bing", "https://www.bing.com/search?q=%s", "https://www.bing.com/osjson.aspx?query=%s"),
    /** Kagi. */
    KAGI("kagi", "Kagi", "https://kagi.com/search?q=%s", "https://kagi.com/api/autosuggest?q=%s"),
    /** The user's own: Custom Search URL and Custom Search Suggestions URL. */
    CUSTOM("custom", "Custom", "", "");

    /** Where the terms go in an engine's URLs. */
    public static final String TERMS = "%s";
    // What stands for the terms while a search URL is read as a URI: letters only, so nothing escapes it.
    private static final String MARK = "jasperSearchTermsMark";
    private final String token, displayName, searchUrl, suggestionUrl;

    SearchEngine(String token, String displayName, String searchUrl, String suggestionUrl) {
        this.token = token;
        this.displayName = displayName;
        this.searchUrl = searchUrl;
        this.suggestionUrl = suggestionUrl;
    }

    /**
     * The engine's token in the settings file.
     *
     * @return the token, such as {@code google}
     */
    public String token() {
        return token;
    }

    /**
     * The engine's name, as Settings lists it.
     *
     * @return the name, such as {@code Google}
     */
    public String displayName() {
        return displayName;
    }

    /**
     * The engine's name as "Search with ⟨engine⟩" says it: {@link #displayName()}, and for Custom the host of its search
     * URL, or "Custom" without one.
     *
     * @param settings the settings, which hold Custom's search URL
     * @return the name, such as {@code Google} or {@code search.example.com}
     */
    public String displayName(BrowserSettings settings) {
        String template = settings.customSearchUrl();
        if (this != CUSTOM || !template(template)) return displayName;
        return URI.create(template.replace(TERMS, MARK)).getHost();
    }

    /**
     * The search for {@code query}: the engine's search URL with the terms in it, escaped. Custom without a search URL
     * searches with Google.
     *
     * @param query the terms
     * @param settings the settings, which hold the custom URLs
     * @return the address of the results page
     */
    public URI search(String query, BrowserSettings settings) {
        Objects.requireNonNull(query, "query");
        String template = searchUrl(settings);
        // A custom URL is empty or a template (BrowserSettings holds no other), and any terms fill a template.
        return filled(template.isEmpty() ? GOOGLE.searchUrl : template, query).orElseThrow();
    }

    /**
     * Where the engine's suggestions for {@code query} come from: its suggestion URL with the text in it, escaped.
     *
     * @param query the typed text
     * @param settings the settings, which hold the custom URLs
     * @return the address of the OpenSearch suggestions; empty for Custom without a suggestion URL
     */
    public Optional<URI> suggestions(String query, BrowserSettings settings) {
        Objects.requireNonNull(query, "query");
        String template = this == CUSTOM ? settings.customSuggestionsUrl() : suggestionUrl;
        return template.isEmpty() ? Optional.empty() : filled(template, query);
    }

    /**
     * Whether {@code value} is a template, as Custom's URLs must be: an absolute http or https address, without a user
     * name or password, with {@code %s} after its host, so that any terms fill it.
     *
     * @param value a URL
     * @return whether it is one
     */
    public static boolean template(String value) {
        if (!value.contains(TERMS)) return false;
        try {
            URI pattern = new URI(value.replace(TERMS, MARK));
            return WebFetch.fetches(pattern) && !pattern.getHost().contains(MARK);
        } catch (URISyntaxException notAUri) {
            return false;
        }
    }

    /**
     * The search terms when {@code page} is a results page of any engine: a built-in one, or Custom, by its search URL,
     * while it is the chosen engine. A page is one when its host (with or without {@code www.}) and path are the search
     * URL's, and it has the terms where the search URL has {@code %s}: in the same query parameter, or in the same place
     * in the path.
     *
     * @param page an address
     * @param settings the settings, which say whether Custom is chosen and hold its URL
     * @return the terms, decoded; empty for any other page, blank terms, and an address that does not decode
     */
    public static Optional<String> queryOf(URI page, BrowserSettings settings) {
        Objects.requireNonNull(page, "page");
        for (SearchEngine engine : values()) {
            if (engine == CUSTOM && settings.searchEngine() != CUSTOM) continue;
            Optional<String> terms = terms(engine.searchUrl(settings), page);
            if (terms.isPresent()) return terms;
        }
        return Optional.empty();
    }

    private String searchUrl(BrowserSettings settings) {
        return this == CUSTOM ? settings.customSearchUrl() : searchUrl;
    }

    /** {@code template} with {@code query} in place of each {@code %s}: an http or https address, or empty. */
    private static Optional<URI> filled(String template, String query) {
        int terms = template.indexOf(TERMS);
        if (terms < 0) return Optional.empty();
        int queryStart = template.indexOf('?');
        String escaped = URLEncoder.encode(query, StandardCharsets.UTF_8);
        // In a query a plus is a space; in a path it is a plus.
        if (queryStart < 0 || terms < queryStart) escaped = escaped.replace("+", "%20");
        try {
            URI filled = new URI(template.replace(TERMS, escaped));
            return WebFetch.fetches(filled) ? Optional.of(filled) : Optional.empty();
        } catch (URISyntaxException notAUri) {
            return Optional.empty();
        }
    }

    /** The terms {@code page} has where {@code template} has {@code %s}, as {@link #queryOf} describes. */
    private static Optional<String> terms(String template, URI page) {
        if (!template.contains(TERMS) || page.getRawPath() == null) return Optional.empty();
        try {
            URI pattern = new URI(template.replace(TERMS, MARK));
            String scheme = page.getScheme() == null ? "" : page.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https") || !sameHost(pattern.getHost(), page.getHost())) return Optional.empty();
            String patternPath = path(pattern.getRawPath()), pagePath = path(page.getRawPath());
            Optional<String> parameter = parameter(pattern.getRawQuery(), MARK);
            if (parameter.isPresent()) {
                if (!patternPath.equals(pagePath)) return Optional.empty();
                return value(page.getRawQuery(), parameter.get()).flatMap(raw -> decoded(raw, true));
            }
            int mark = patternPath.indexOf(MARK);
            if (mark < 0) return Optional.empty();
            String before = patternPath.substring(0, mark), after = patternPath.substring(mark + MARK.length());
            if (pagePath.length() < before.length() + after.length() || !pagePath.startsWith(before) || !pagePath.endsWith(after))
                return Optional.empty();
            return decoded(pagePath.substring(before.length(), pagePath.length() - after.length()), false);
        } catch (URISyntaxException | RuntimeException odd) {
            return Optional.empty();
        }
    }

    private static boolean sameHost(String pattern, String page) {
        return pattern != null && page != null && bare(pattern).equals(bare(page));
    }

    private static String bare(String host) {
        String lower = host.toLowerCase(Locale.ROOT);
        return lower.startsWith("www.") ? lower.substring(4) : lower;
    }

    private static String path(String raw) {
        return raw.isEmpty() ? "/" : raw;
    }

    /** The name of the parameter of {@code query} whose value is {@code value}. */
    private static Optional<String> parameter(String query, String value) {
        if (query == null) return Optional.empty();
        for (String pair : query.split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0 && pair.substring(equals + 1).equals(value)) return Optional.of(pair.substring(0, equals));
        }
        return Optional.empty();
    }

    /** The raw value of {@code query}'s first parameter named {@code name}. */
    private static Optional<String> value(String query, String name) {
        if (query == null) return Optional.empty();
        for (String pair : query.split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0 && pair.substring(0, equals).equals(name)) return Optional.of(pair.substring(equals + 1));
        }
        return Optional.empty();
    }

    /**
     * {@code raw} with its {@code %XX} escapes read as UTF-8, and in a query each plus as a space: empty for an escape
     * that is no escape, bytes that are no UTF-8, and blank terms.
     */
    private static Optional<String> decoded(String raw, boolean query) {
        var bytes = new ByteArrayOutputStream(raw.length());
        for (int i = 0; i < raw.length(); ) {
            int c = raw.codePointAt(i);
            if (c == '%') {
                if (i + 2 >= raw.length() || !HexFormat.isHexDigit(raw.charAt(i + 1)) || !HexFormat.isHexDigit(raw.charAt(i + 2)))
                    return Optional.empty();
                bytes.write(HexFormat.fromHexDigits(raw, i + 1, i + 3));
                i += 3;
                continue;
            }
            if (c == '+' && query) bytes.write(' ');
            else bytes.writeBytes(Character.toString(c).getBytes(StandardCharsets.UTF_8));
            i += Character.charCount(c);
        }
        try {
            String terms = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString().strip();
            return terms.isEmpty() ? Optional.empty() : Optional.of(terms);
        } catch (CharacterCodingException notUtf8) {
            return Optional.empty();
        }
    }
}
