package dev.jasper.browser.store;

import dev.jasper.browser.restore.BrowserRestore;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Which addresses history and bookmarks keep, and the host and path a visit of one counts under. */
public final class Addresses {
    /** The longest address kept: longer ones bloat the database. */
    static final int MAX_LENGTH = 8_192;
    private static final Set<String> SCHEMES = Set.of("http", "https", "file");

    private Addresses() { }

    /**
     * The address as history and bookmarks keep it. Only {@code http}, {@code https} and {@code file} addresses can be
     * visited again: spec section 10 leaves out {@code jasper-about:} pages, and {@code about:}, {@code data:},
     * {@code blob:} and {@code javascript:} addresses cannot be revisited.
     *
     * @param address a page's address
     * @return the address without its user name and password (R46); empty for another scheme, for an address longer than
     *     8,192 characters, and for one with user information that is not a URI, which cannot lose it
     */
    public static Optional<String> storable(String address) {
        if (address.length() > MAX_LENGTH) return Optional.empty();
        int colon = address.indexOf(':');
        if (colon < 0 || !SCHEMES.contains(address.substring(0, colon).toLowerCase(Locale.ROOT))) return Optional.empty();
        if (address.indexOf('@') < 0) return Optional.of(address); // user information needs one; Chromium's addresses need not be URIs
        try {
            return Optional.of(BrowserRestore.withoutUserInfo(new URI(address)).toString());
        } catch (URISyntaxException notAUri) {
            return Optional.empty();
        }
    }

    /**
     * Where a visit of a stored address counts (spec section 10's per-host-and-path table): its host in lower case, with
     * its port, so two servers on one machine count apart, and its path, {@code /} when it has none, without its query
     * or fragment. Either scheme counts alike.
     *
     * @param stored an address {@link #storable} gave
     * @return its host and path
     */
    static VisitKey visitKey(String stored) {
        String rest = stored.substring(stored.indexOf(':') + 1), host = "";
        if (rest.startsWith("//")) {
            int end = end(rest, 2, "/?#");
            host = rest.substring(2, end).toLowerCase(Locale.ROOT);
            rest = rest.substring(end);
        }
        String path = rest.substring(0, end(rest, 0, "?#"));
        return new VisitKey(host, path.isEmpty() ? "/" : path);
    }

    /** The index in {@code text} of the first of {@code stops} at or after {@code from}, or its length. */
    private static int end(String text, int from, String stops) {
        for (int i = from; i < text.length(); i++)
            if (stops.indexOf(text.charAt(i)) >= 0) return i;
        return text.length();
    }

    /**
     * The host and path a visit counts under.
     *
     * @param host the host in lower case, with its port; empty for a {@code file:} address without one
     * @param path the path, without query or fragment
     */
    record VisitKey(String host, String path) { }
}
