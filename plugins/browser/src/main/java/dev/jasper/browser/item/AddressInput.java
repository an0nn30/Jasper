package dev.jasper.browser.item;

import dev.jasper.browser.page.LinkSchemes;
import dev.jasper.browser.settings.BrowserSettings;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** What the address field loads for the text typed into it (spec section 6, "Typed text"). */
public final class AddressInput {
    private static final Pattern SCHEME = Pattern.compile("([A-Za-z][A-Za-z0-9+.-]*):.*", Pattern.DOTALL);
    // A host, an optional port and an optional path, query or fragment; the host is checked on its own.
    private static final Pattern ADDRESS = Pattern.compile("(\\[[0-9A-Fa-f:.]+]|[^\\s/?#:@\\[\\]]+)(?::(\\d{1,5}))?([/?#]\\S*)?");
    private static final Pattern WINDOWS_PATH = Pattern.compile("[A-Za-z]:[\\\\/].*", Pattern.DOTALL);
    private static final boolean WINDOWS = File.separatorChar == '\\';

    private AddressInput() { }

    /**
     * What {@code typed} loads, trimmed:
     * <ul>
     *   <li>with a scheme Jasper offers ({@code http}, {@code https}, {@code file}, {@code about} or {@code jasper-about}),
     *       the text as typed;</li>
     *   <li>an absolute local path ({@code /…}, {@code ~/…}, or {@code C:\…} on Windows), its {@code file:} URL;</li>
     *   <li>a domain (a trailing dot allowed), an IP address, {@code localhost}, a host with a port, or a single word
     *       without spaces (underscores allowed), with {@code https://} before it;</li>
     *   <li>other text, a search for it with the chosen engine.</li>
     * </ul>
     *
     * @param typed the field's text
     * @param settings the settings, which choose the engine
     * @return the address; empty for empty text and Chromium's own {@code chrome:} pages
     */
    public static Optional<URI> resolve(String typed, BrowserSettings settings) {
        if (!resolves(typed)) return Optional.empty();
        String text = typed.strip();
        Optional<URI> address = address(text);
        return address.isPresent() ? address : Optional.of(settings.searchEngine().search(text, settings));
    }

    /**
     * Whether {@link #resolve} loads anything for {@code typed}, whichever engine is chosen.
     *
     * @param typed the field's text
     * @return whether it is neither blank nor one of Chromium's own {@code chrome:} pages
     */
    public static boolean resolves(String typed) {
        String text = typed == null ? "" : typed.strip();
        return !text.isEmpty() && scheme(text).filter("chrome"::equals).isEmpty();
    }

    /**
     * What {@code typed} loads when it is no search, as {@link #resolve} reads it: with an offered scheme, a local path,
     * or a host.
     *
     * @param typed the field's text
     * @return the address; empty for text {@link #resolve} searches for or loads nothing for
     */
    public static Optional<URI> address(String typed) {
        String text = typed == null ? "" : typed.strip();
        if (text.isEmpty()) return Optional.empty();
        Optional<URI> path = localPath(text);
        if (path.isPresent()) return path;
        Optional<String> scheme = scheme(text);
        if (scheme.filter("chrome"::equals).isPresent()) return Optional.empty();
        if (scheme.filter(LinkSchemes.LOADED::contains).isPresent()) {
            Optional<URI> typedAddress = parse(text);
            if (typedAddress.isPresent()) return typedAddress;
        }
        return host(text) ? parse("https://" + text) : Optional.empty();
    }

    /**
     * The scheme {@code text} begins with, in lower case: {@code https} for {@code HTTPS://…}. A Windows path's drive is
     * no scheme there.
     *
     * @param text the field's text, trimmed
     * @return the scheme; empty for text without one
     */
    static Optional<String> scheme(String text) {
        if (WINDOWS && WINDOWS_PATH.matcher(text).matches()) return Optional.empty();
        Matcher scheme = SCHEME.matcher(text);
        return scheme.matches() ? Optional.of(scheme.group(1).toLowerCase(Locale.ROOT)) : Optional.empty();
    }

    /**
     * The {@code file:} URL of {@code text} when it is an absolute local path: {@code /…}, {@code ~/…} (the user's home
     * folder), or on Windows {@code C:\…} or {@code C:/…}. It reads no file.
     *
     * @param text the field's text, trimmed
     * @return the URL, its characters escaped as UTF-8; empty for other text
     */
    static Optional<URI> localPath(String text) {
        String path;
        if (text.startsWith("~/") || WINDOWS && text.startsWith("~\\")) path = System.getProperty("user.home") + text.substring(1);
        else if (text.startsWith("/") || WINDOWS && WINDOWS_PATH.matcher(text).matches()) path = text;
        else return Optional.empty();
        if (WINDOWS) path = path.replace('\\', '/');
        if (!path.startsWith("/")) path = "/" + path;
        try {
            return Optional.of(new URI(new URI("file", "", path, null).toASCIIString()));
        } catch (URISyntaxException notAPath) {
            return Optional.empty();
        }
    }

    /** Whether {@code text} is a host Jasper loads over https, with an optional port and path. */
    private static boolean host(String text) {
        Matcher address = ADDRESS.matcher(text);
        if (!address.matches()) return false;
        String host = address.group(1), port = address.group(2);
        if (port != null && (Integer.parseInt(port) == 0 || Integer.parseInt(port) > 65_535)) return false;
        if (host.startsWith("[")) return true;
        if (host.equalsIgnoreCase("localhost") || ipv4(host) || domain(host.endsWith(".") ? host.substring(0, host.length() - 1) : host))
            return true;
        // A word is a host on its own, with a port or with a path; "c#" and "why?" are searches.
        return word(host) && (port != null || alone(address.group(3)));
    }

    /**
     * Whether {@code text} names a bare word's host: a single word without a port, on its own or with a path, such as
     * {@code jcef} or {@code devbox/app}, but not {@code localhost}. Such text is as often a search.
     *
     * @param text the field's text, trimmed
     * @return whether it is one
     */
    static boolean bareWord(String text) {
        Matcher address = ADDRESS.matcher(text);
        if (!address.matches() || address.group(2) != null) return false;
        String host = address.group(1);
        return !host.equalsIgnoreCase("localhost") && word(host) && alone(address.group(3));
    }

    /** Whether what follows a host without a port leaves it a word: nothing, or a path. */
    private static boolean alone(String rest) {
        return rest == null || rest.startsWith("/");
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
     * A single word, as an intranet host's name is: ASCII letters, digits, underscores and inner hyphens, with a letter
     * or underscore, so not a number. Words in other scripts are searches, as a query without spaces often is in them.
     */
    private static boolean word(String host) {
        return !host.startsWith("-") && !host.endsWith("-")
            && host.chars().allMatch(c -> c < 0x80 && (Character.isLetterOrDigit(c) || c == '_' || c == '-'))
            && host.chars().anyMatch(c -> Character.isLetter(c) || c == '_');
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
