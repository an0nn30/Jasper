package dev.jasper.browser.tools;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/**
 * The addresses Save Page As reads and writes (plan 7's Task 7): what a page or a stylesheet writes read as a
 * {@link URI}, which takes less than a browser does, without the user name and password a saved page must never keep
 * (4a's R46), and the key that one fetch of an address goes by. Pure.
 */
final class SaveAddresses {
    // What a browser leaves in an address and a URI takes only escaped.
    private static final String UNSAFE = " \"<>\\^`{|}";
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private SaveAddresses() { }

    /**
     * {@code text} as a URI: each character no URI takes unescaped, as a browser leaves {@code |}, {@code ^}, a
     * {@code [} outside the host or a {@code #} in the fragment, percent-encoded as UTF-8, a {@code %} that starts no
     * escape among them; then without its user information.
     *
     * @param text an address as a page or a stylesheet writes it
     * @return the URI; empty when even the escaped text is none
     */
    static Optional<URI> parse(String text) {
        var escaped = new StringBuilder(text.length() + 16);
        // Where the authority ends, past which a bracket is no IPv6 host's; and whether the fragment has begun.
        int authorityEnd = authorityEnd(text);
        boolean fragment = false;
        for (int at = 0; at < text.length(); ) {
            int c = text.codePointAt(at);
            int width = Character.charCount(c);
            boolean escape = c <= 0x20 || c >= 0x7F || UNSAFE.indexOf(c) >= 0
                || c == '%' && !(hex(text, at + 1) && hex(text, at + 2))
                || (c == '[' || c == ']') && at >= authorityEnd
                || c == '#' && fragment;
            if (c == '#') fragment = true;
            if (escape) for (byte b : new String(Character.toChars(c)).getBytes(StandardCharsets.UTF_8)) percent(escaped, b);
            else escaped.appendCodePoint(c);
            at += width;
        }
        try {
            return Optional.of(withoutUserInfo(new URI(escaped.toString())));
        } catch (URISyntaxException unreadable) {
            return Optional.empty();
        }
    }

    /**
     * {@code address} without its user name and password, everything before the last {@code @} of its authority, a
     * host Java reads as a registry name's included (ruling D43); itself when it has none. A scheme-relative address
     * stays one, {@code //host/…} (ruling D44).
     *
     * @param address an address
     * @return the address
     */
    static URI withoutUserInfo(URI address) {
        String authority = address.getRawAuthority();
        if (address.isOpaque() || authority == null || authority.indexOf('@') < 0) return address;
        var text = new StringBuilder();
        if (address.getScheme() != null) text.append(address.getScheme()).append(':');
        text.append("//").append(authority.substring(authority.lastIndexOf('@') + 1));
        if (address.getRawPath() != null) text.append(address.getRawPath());
        if (address.getRawQuery() != null) text.append('?').append(address.getRawQuery());
        if (address.getRawFragment() != null) text.append('#').append(address.getRawFragment());
        try {
            return new URI(text.toString());
        } catch (URISyntaxException unreadable) {
            return address;
        }
    }

    /**
     * Whether {@code address} is one Save Page As fetches: an absolute {@code http} or {@code https} address.
     *
     * @param address an address
     * @return whether it is
     */
    static boolean web(URI address) {
        String scheme = address.getScheme();
        return !address.isOpaque() && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) && address.getRawAuthority() != null;
    }

    /**
     * The address one fetch of {@code address} goes by: its scheme and authority in lower case, its path normalized and
     * at least {@code /}, its query, and no fragment.
     *
     * @param address a web address ({@link #web})
     * @return the key
     */
    static String key(URI address) {
        URI normal = address.normalize();
        String path = normal.getRawPath() == null || normal.getRawPath().isEmpty() ? "/" : normal.getRawPath();
        String query = normal.getRawQuery() == null ? "" : "?" + normal.getRawQuery();
        return normal.getScheme().toLowerCase(Locale.ROOT) + "://" + normal.getRawAuthority().toLowerCase(Locale.ROOT) + path + query;
    }

    /**
     * {@code address} without its fragment, as it is asked for.
     *
     * @param address a web address
     * @return the address
     */
    static URI withoutFragment(URI address) {
        if (address.getRawFragment() == null) return address;
        String text = address.toString();
        return URI.create(text.substring(0, text.indexOf('#')));
    }

    /** Where the authority of {@code text}, an absolute address with {@code //}, ends; 0 for an address without one. */
    private static int authorityEnd(String text) {
        int scheme = text.indexOf(':');
        if (scheme <= 0 || !text.startsWith("//", scheme + 1)) return 0;
        int start = scheme + 3, end = start;
        while (end < text.length() && "/?#".indexOf(text.charAt(end)) < 0) end++;
        return end;
    }

    private static boolean hex(String text, int at) {
        return at < text.length() && Character.digit(text.charAt(at), 16) >= 0;
    }

    private static void percent(StringBuilder out, byte b) {
        out.append('%').append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
    }
}
