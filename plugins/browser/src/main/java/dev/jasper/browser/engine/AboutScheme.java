package dev.jasper.browser.engine;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Jasper's own pages, at {@code jasper-about:<page>}. This registry is plain Java, so the plugin's start holds it without
 * loading Chromium; {@link AboutSchemeFactory} is Chromium's side of the scheme and loads only once Chromium starts.
 */
public final class AboutScheme {
    /** The scheme's name. */
    public static final String NAME = "jasper-about";
    private static final Pattern PAGE = Pattern.compile("[a-z][a-z0-9-]*");
    private static final Answer NOT_FOUND = new Answer(404, "");
    private final Map<String, Supplier<String>> pages = new ConcurrentHashMap<>();

    /** An empty registry. */
    public AboutScheme() { }

    /**
     * Adds {@code jasper-about:<page>}. Chromium asks {@code html} on its own thread, once for each request, and the page
     * goes out as UTF-8.
     *
     * @param page the page's name: lowercase letters, digits and hyphens, starting with a letter
     * @param html the page's HTML
     * @throws IllegalArgumentException for any other name, or one already taken
     */
    public void register(String page, Supplier<String> html) {
        Objects.requireNonNull(html, "html");
        if (page == null || !PAGE.matcher(page).matches())
            throw new IllegalArgumentException("A jasper-about page's name is lowercase letters, digits and hyphens: " + page);
        if (pages.putIfAbsent(page, html) != null) throw new IllegalArgumentException(NAME + ":" + page + " is already registered");
    }

    /**
     * What a request for an address gets: the page's HTML with 200, or 404 and an empty body for an unknown page or one
     * with nothing to show. Chromium writes the address as {@code jasper-about://<page>/}, the scheme being standard.
     */
    Answer answer(String address) {
        return page(address).map(pages::get).map(Supplier::get).map(html -> new Answer(200, html)).orElse(NOT_FOUND);
    }

    /** The page an address names: what follows the scheme and any {@code //}, up to a path, query or fragment. */
    private static Optional<String> page(String address) {
        String prefix = NAME + ":";
        if (address == null || !address.regionMatches(true, 0, prefix, 0, prefix.length())) return Optional.empty();
        String rest = address.substring(prefix.length());
        if (rest.startsWith("//")) rest = rest.substring(2);
        int end = 0;
        while (end < rest.length() && "/?#".indexOf(rest.charAt(end)) < 0) end++;
        return Optional.of(rest.substring(0, end).toLowerCase(Locale.ROOT));
    }

    /**
     * One request's answer.
     *
     * @param status the HTTP status
     * @param html the body
     */
    record Answer(int status, String html) { }
}
