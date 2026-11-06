package dev.jasper.browser.permissions;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The site a permission is kept for: an address's origin, as Chromium writes one; and whether it is a secure context's,
 * where Chromium offers notifications.
 */
public final class Origins {
    /** The one origin of every {@code file:} address. */
    static final String FILE = "file://";
    private static final Pattern SCHEME = Pattern.compile("[A-Za-z][A-Za-z0-9+.-]*");
    // A host as Chromium writes one, letters and digits and URL punctuation, or an IPv6 address in its brackets.
    private static final Pattern HOST = Pattern.compile("[A-Za-z0-9._~%!$&'()*+,;=-]+"), IPV6 = Pattern.compile("\\[[0-9A-Fa-f:.]+]");
    private static final Pattern PORT = Pattern.compile("[0-9]{1,5}");
    // A host on this computer, as Chromium counts one for a secure context.
    private static final Pattern LOOPBACK =
        Pattern.compile("localhost|.+\\.localhost|127(?:\\.[0-9]{1,3}){3}|\\[::1\\]");

    private Origins() { }

    /**
     * Whether {@code origin} is a secure context's, as Chromium decides one for an origin: an {@code https} origin, or
     * an {@code http} one on this computer ({@code localhost} and its subdomains, {@code 127.0.0.0/8} and
     * {@code [::1]}).
     *
     * @param origin an origin, as {@link #of} writes one
     * @return whether it is; false for a file's and any other
     */
    public static boolean secure(String origin) {
        Objects.requireNonNull(origin, "origin");
        if (origin.startsWith("https://")) return true;
        if (!origin.startsWith("http://")) return false;
        return LOOPBACK.matcher(origin.substring("http://".length()).replaceFirst(":[0-9]+$", "")).matches();
    }

    /**
     * The origin of {@code address}.
     *
     * @param address an address, as Chromium names a page's
     * @return for an http or https address, {@code scheme://host[:port]}: the scheme and host in lower case, an IPv6
     *     host in its brackets, the port only when it is not the scheme's own, and no user name or password; for any
     *     {@code file:} address, {@code file://}; empty for any other address, and for an http or https one with no
     *     host, or a host or port no address can have
     */
    public static Optional<String> of(String address) {
        if (address == null) return Optional.empty();
        int colon = address.indexOf(':');
        if (colon < 1 || !SCHEME.matcher(address.substring(0, colon)).matches()) return Optional.empty();
        String scheme = address.substring(0, colon).toLowerCase(Locale.ROOT);
        if (scheme.equals("file")) return Optional.of(FILE);
        int standard = switch (scheme) {
            case "http" -> 80;
            case "https" -> 443;
            default -> -1;
        };
        if (standard < 0 || !address.startsWith("//", colon + 1)) return Optional.empty();
        int start = colon + 3, end = start;
        while (end < address.length() && "/?#\\".indexOf(address.charAt(end)) < 0) end++;
        String authority = address.substring(start, end);
        String hostAndPort = authority.substring(authority.lastIndexOf('@') + 1);
        int portAt = hostAndPort.startsWith("[") ? hostAndPort.indexOf(']') + 1 : hostAndPort.lastIndexOf(':');
        if (portAt <= 0) portAt = hostAndPort.length();
        String host = hostAndPort.substring(0, portAt), rest = hostAndPort.substring(portAt);
        if (!(HOST.matcher(host).matches() || IPV6.matcher(host).matches())) return Optional.empty();
        int port = standard;
        if (!rest.isEmpty()) {
            if (rest.charAt(0) != ':') return Optional.empty();
            String digits = rest.substring(1);
            if (!digits.isEmpty()) {
                if (!PORT.matcher(digits).matches()) return Optional.empty();
                port = Integer.parseInt(digits);
                if (port > 65_535) return Optional.empty();
            }
        }
        String origin = scheme + "://" + host.toLowerCase(Locale.ROOT);
        return Optional.of(port == standard ? origin : origin + ":" + port);
    }
}
