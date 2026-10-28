package dev.jasper.browser.item;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** What the address field loads for the text typed into it (spec section 6, "Typed text"). */
public final class AddressInput {
    private static final Set<String> SCHEMES = Set.of("http", "https", "file", "about", "jasper-about");
    private static final Pattern SCHEME = Pattern.compile("([A-Za-z][A-Za-z0-9+.-]*):.*", Pattern.DOTALL);
    // A host, an optional port and an optional path, query or fragment; the host is checked on its own.
    private static final Pattern ADDRESS = Pattern.compile("(\\[[0-9A-Fa-f:.]+]|[^\\s/?#:@\\[\\]]+)(?::(\\d{1,5}))?([/?#]\\S*)?");
    private static final String SEARCH = "https://google.com/search?q=";

    private AddressInput() { }

    /**
     * What {@code typed} loads, trimmed:
     * <ul>
     *   <li>with a scheme Jasper offers ({@code http}, {@code https}, {@code file}, {@code about} or {@code jasper-about}),
     *       the text as typed;</li>
     *   <li>a domain, an IP address, {@code localhost} or a host with a port, with {@code https://} before it;</li>
     *   <li>other text, a Google search for it.</li>
     * </ul>
     *
     * @param typed the field's text
     * @return the address; empty for empty text and Chromium's own {@code chrome:} pages
     */
    public static Optional<URI> resolve(String typed) {
        String text = typed == null ? "" : typed.strip();
        if (text.isEmpty()) return Optional.empty();
        Matcher scheme = SCHEME.matcher(text);
        if (scheme.matches()) {
            String name = scheme.group(1).toLowerCase(Locale.ROOT);
            if (name.equals("chrome")) return Optional.empty();
            if (SCHEMES.contains(name)) return parse(text);
        }
        if (address(text)) {
            Optional<URI> web = parse("https://" + text);
            if (web.isPresent()) return web;
        }
        return Optional.of(URI.create(SEARCH + URLEncoder.encode(text, StandardCharsets.UTF_8)));
    }

    /** Whether {@code text} is a host Jasper loads over https, with an optional port and path. */
    private static boolean address(String text) {
        Matcher address = ADDRESS.matcher(text);
        if (!address.matches()) return false;
        String host = address.group(1), port = address.group(2);
        if (port != null && (Integer.parseInt(port) == 0 || Integer.parseInt(port) > 65_535)) return false;
        if (host.startsWith("[")) return true;
        if (host.equalsIgnoreCase("localhost") || ipv4(host) || domain(host)) return true;
        return port != null && label(host);
    }

    private static boolean ipv4(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts)
            if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(c -> c >= '0' && c <= '9') || Integer.parseInt(part) > 255)
                return false;
        return true;
    }

    /** Two or more labels, the last of them letters only, at least two. */
    private static boolean domain(String host) {
        String[] labels = host.split("\\.", -1);
        if (labels.length < 2) return false;
        for (String label : labels) if (!label(label)) return false;
        String top = labels[labels.length - 1];
        return top.length() >= 2 && (top.chars().allMatch(Character::isLetter) || top.toLowerCase(Locale.ROOT).startsWith("xn--"));
    }

    /** Letters, digits and inner hyphens. */
    private static boolean label(String label) {
        return !label.isEmpty() && !label.startsWith("-") && !label.endsWith("-")
            && label.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '-');
    }

    /**
     * {@code text}, which names a scheme, as a URI. When it holds characters a URI refuses, those are escaped and the
     * escapes it already has are kept.
     *
     * @param text an address with a scheme
     * @return the URI; empty when it still is not one
     */
    static Optional<URI> parse(String text) {
        if (text.indexOf(':') < 1) return Optional.empty();
        try {
            return Optional.of(new URI(text));
        } catch (URISyntaxException strict) {
            try {
                return Optional.of(new URI(escaped(text)));
            } catch (URISyntaxException still) {
                return Optional.empty();
            }
        }
    }

    /**
     * {@code text} with each character a URI refuses written as its UTF-8 bytes, {@code %XX} each: spaces,
     * {@code "<>\^`{|}}, other control and non-ASCII characters, square brackets outside the authority (where they hold
     * an IPv6 address) and every {@code #} after the first. A valid {@code %XX} escape stays as it is; a {@code %} that
     * starts none is written {@code %25}.
     */
    private static String escaped(String text) {
        int colon = text.indexOf(':');
        int authority = text.startsWith("//", colon + 1) ? colon + 3 : text.length(), end = authority;
        while (end < text.length() && "/?#".indexOf(text.charAt(end)) < 0) end++;
        var out = new StringBuilder(text.length() + 16);
        boolean fragment = false;
        for (int i = 0; i < text.length(); ) {
            int c = text.codePointAt(i);
            int width = Character.charCount(c);
            boolean keep;
            if (c == '%') keep = i + 2 < text.length() && hex(text.charAt(i + 1)) && hex(text.charAt(i + 2));
            else if (c == '#') {
                keep = !fragment;
                fragment = true;
            } else if (c == '[' || c == ']') keep = i >= authority && i < end;
            else keep = c > 0x20 && c < 0x7f && "\"<>\\^`{|}".indexOf(c) < 0;
            if (keep) out.appendCodePoint(c);
            else for (byte b : new String(Character.toChars(c)).getBytes(StandardCharsets.UTF_8)) out.append(String.format("%%%02X", b & 0xFF));
            i += width;
        }
        return out.toString();
    }

    private static boolean hex(char c) {
        return c >= '0' && c <= '9' || c >= 'A' && c <= 'F' || c >= 'a' && c <= 'f';
    }
}
