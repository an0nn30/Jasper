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
     * {@code text}, which names a scheme, as a URI, with any character a URI cannot hold escaped.
     *
     * @param text an address with a scheme
     * @return the URI; empty when it still is not one
     */
    static Optional<URI> parse(String text) {
        if (text.indexOf(':') < 1) return Optional.empty();
        try {
            return Optional.of(new URI(text));
        } catch (URISyntaxException strict) {
            int colon = text.indexOf(':'), hash = text.indexOf('#', colon + 1);
            String part = hash < 0 ? text.substring(colon + 1) : text.substring(colon + 1, hash);
            try {
                return Optional.of(new URI(text.substring(0, colon), part, hash < 0 ? null : text.substring(hash + 1)));
            } catch (URISyntaxException lenient) {
                return Optional.empty();
            }
        }
    }
}
