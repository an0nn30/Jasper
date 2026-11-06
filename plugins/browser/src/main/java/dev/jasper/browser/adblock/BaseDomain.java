package dev.jasper.browser.adblock;

import java.util.Set;

/**
 * The site a host belongs to, by which a request is first-party (the page's own site) or third-party: the host's last
 * two labels, or its last three when the last is a two-letter country's and the one before it is a common second
 * level there, such as {@code co.uk}, {@code com.au} or {@code gob.mx}. Jasper has no public suffix list, so a private
 * suffix such as {@code github.io} counts as one site.
 */
public final class BaseDomain {
    // The second levels under which a two-letter country's sites are registered.
    private static final Set<String> SECOND_LEVELS = Set.of("co", "com", "net", "org", "gov", "edu", "ac", "ltd", "plc", "ne", "or",
        "go", "gob", "mil", "nom", "sch");

    private BaseDomain() { }

    /**
     * The site {@code host} belongs to.
     *
     * @param host a host, such as {@code www.example.co.uk}
     * @return its site in lower case, without a trailing dot, such as {@code example.co.uk}; a host of one or two
     *     labels and an IP address are themselves
     */
    public static String of(String host) {
        String name = UrlFilter.fold(host);
        if (name.endsWith(".")) name = name.substring(0, name.length() - 1);
        if (name.startsWith("[") || name.indexOf(':') >= 0) return name;
        int last = name.lastIndexOf('.');
        String top = name.substring(last + 1);
        // A host that ends in a number is an IPv4 address, as the URL standard reads one.
        if (last < 0 || digits(top)) return name;
        int second = name.lastIndexOf('.', last - 1);
        if (second < 0) return name;
        if (top.length() == 2 && letters(top) && SECOND_LEVELS.contains(name.substring(second + 1, last)))
            return name.substring(name.lastIndexOf('.', second - 1) + 1);
        return name.substring(second + 1);
    }

    /**
     * The host of {@code url}: its authority without user information or port, in lower case and without a trailing
     * dot, an IPv6 address in its brackets.
     *
     * @param url an address, as Chromium writes one
     * @return the host; empty for an address with none, such as {@code about:blank} or {@code data:}
     */
    static String host(String url) {
        int colon = url.indexOf(':');
        if (colon < 1 || !url.startsWith("//", colon + 1)) return "";
        int start = colon + 3, end = start;
        while (end < url.length() && "/?#\\".indexOf(url.charAt(end)) < 0) end++;
        String authority = url.substring(start, end);
        String host = authority.substring(authority.lastIndexOf('@') + 1);
        int port = host.startsWith("[") ? host.indexOf(']') + 1 : host.indexOf(':');
        if (port > 0 && port < host.length()) host = host.substring(0, port);
        host = UrlFilter.fold(host);
        return host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
    }

    private static boolean digits(String label) {
        if (label.isEmpty()) return false;
        for (int at = 0; at < label.length(); at++) if (label.charAt(at) < '0' || label.charAt(at) > '9') return false;
        return true;
    }

    private static boolean letters(String label) {
        for (int at = 0; at < label.length(); at++) {
            char c = label.charAt(at);
            if (c < 'a' || c > 'z') return false;
        }
        return true;
    }
}
