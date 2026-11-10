package dev.jasper.browser.downloads;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

/**
 * The addresses downloads write and show: a download's source and its page as referrer, for the downloaded-file mark,
 * which Save Page As's files take too, only when they are {@code http} or {@code https} addresses, and the site ruling
 * D1's notice names (D11), which never shows a user name or password. An address Chromium gives may hold characters no {@link URI} takes unescaped, such as
 * {@code |}; they are escaped first. Pure.
 */
public final class DownloadAddresses {
    /** What the mark writes for a download whose addresses must not be kept, and for one from no web address. */
    static final URI NO_ADDRESS = URI.create("about:internet");
    /** The site D1's notice names when the page's address shows no host. */
    static final String SOME_SITE = "this site";
    /** What no URI holds unescaped, though an address Chromium gives may. */
    private static final String UNSAFE = " \"<>\\^`{|}";

    private DownloadAddresses() { }

    /**
     * The address a download came from, for its mark: an {@code http} or {@code https} one; any other, a {@code data:}
     * address among them, as {@code about:internet}, as the mark writes it anyway.
     *
     * @param url the download's address, as Chromium gives it, or a saved page's
     * @return the address
     */
    public static URI source(String url) {
        return web(url).orElse(NO_ADDRESS);
    }

    /**
     * {@code url} when it is an {@code http} or {@code https} address with an authority, its unsafe characters escaped; a
     * host Java reads as a registry name, as one with an underscore, is kept through its authority. Other addresses,
     * {@code data:} ones among them, are never parsed.
     *
     * @param url the address
     * @return the address; empty for any other, and for one no URI can be made of
     */
    public static Optional<URI> web(String url) {
        if (!(url.regionMatches(true, 0, "http://", 0, 7) || url.regionMatches(true, 0, "https://", 0, 8))) return Optional.empty();
        return parsed(url).filter(address -> address.getRawAuthority() != null);
    }

    /**
     * The site the page at {@code pageUrl} is, as D1's notice names it (ruling D11): its host, and port when Java reads the
     * host as a registry name, never the user name or password before it; "this site" for an address with no host, and
     * for one no URI can be made of.
     *
     * @param pageUrl the page's address
     * @return the site
     */
    static String host(String pageUrl) {
        Optional<URI> address = parsed(pageUrl);
        if (address.isEmpty()) return SOME_SITE;
        String host = address.get().getHost(), authority = address.get().getRawAuthority();
        if (host == null && authority != null) host = authority.substring(authority.lastIndexOf('@') + 1);
        return host == null || host.isBlank() ? SOME_SITE : host;
    }

    /** {@code url} as a URI, its unsafe characters escaped; empty when no URI can be made of it. */
    private static Optional<URI> parsed(String url) {
        var escaped = new StringBuilder(url.length());
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c < 0x20 || c == 0x7f || UNSAFE.indexOf(c) >= 0)
                escaped.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16)))
                    .append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
            else escaped.append(c);
        }
        try {
            return Optional.of(new URI(escaped.toString()));
        } catch (URISyntaxException unreadable) {
            return Optional.empty();
        }
    }
}
