package dev.jasper.browser.http;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The route of the plugin's own fetches while the HTTP proxy is on (plan 6's V43), the one Chromium gives its pages'
 * requests: direct for the hosts Chromium's implicit bypass rules name, which are {@code localhost} and
 * {@code *.localhost}, {@code 127.0.0.0/8} and {@code [::1]}, {@code 169.254.0.0/16} and {@code [fe80::]/10}, and
 * through the proxy for every other http or https address. A host is matched as the address writes it, an IP address
 * read as text, so no name is ever looked up. {@link #loopback} is the part of those hosts that is this computer, which
 * the filter list's download shares for its http rule (ruling F10). Any thread.
 */
public final class ProxyRoute extends ProxySelector {
    private static final List<Proxy> DIRECT = List.of(Proxy.NO_PROXY);
    // An IPv4 address as a URI writes it, and an IPv6 one's characters, which Inet4Address and Inet6Address then read.
    private static final Pattern DOTTED = Pattern.compile("[0-9]{1,3}(?:\\.[0-9]{1,3}){3}");
    private static final Pattern HEX = Pattern.compile("[0-9a-f:.]*:[0-9a-f:.]*");
    private final List<Proxy> proxy;

    private ProxyRoute(InetSocketAddress proxy) {
        this.proxy = List.of(new Proxy(Proxy.Type.HTTP, proxy));
    }

    /**
     * The selector of a client whose fetches go through {@code proxy}, but for the hosts Chromium never sends through one.
     *
     * @param proxy the HTTP proxy's address, unresolved as the settings give it
     * @return the selector
     */
    public static ProxySelector through(InetSocketAddress proxy) {
        return new ProxyRoute(Objects.requireNonNull(proxy, "proxy"));
    }

    /**
     * The proxy for {@code uri}: none for an address that is not http or https, has no host or names a host
     * {@link #bypassed} names; the HTTP proxy otherwise.
     *
     * @param uri the address the client is about to ask
     * @return the proxy, or {@link Proxy#NO_PROXY}
     */
    @Override public List<Proxy> select(URI uri) {
        if (uri == null) throw new IllegalArgumentException("No address");
        String scheme = uri.getScheme(), host = uri.getHost();
        boolean web = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        return web && host != null && !bypassed(host) ? proxy : DIRECT;
    }

    /** Nothing: a proxy that cannot be reached fails the fetch, which is never sent direct instead. */
    @Override public void connectFailed(URI uri, SocketAddress address, IOException failure) { }

    /**
     * Whether Chromium sends a request for {@code host} direct whatever proxy it is given: {@code localhost} and the
     * names under it, with or without a final dot, any loopback IP address, and link-local addresses
     * ({@code 169.254.0.0/16} and {@code [fe80::]/10}), each in any case and any spelling of the address.
     *
     * @param host the host as an address writes it, an IPv6 address in brackets or not
     * @return whether it is bypassed
     */
    public static boolean bypassed(String host) {
        String name = host.toLowerCase(Locale.ROOT);
        String root = name.endsWith(".") ? name.substring(0, name.length() - 1) : name;
        if (root.equals("localhost") || root.endsWith(".localhost")) return true;
        return literal(name).filter(address -> address.isLoopbackAddress() || address.isLinkLocalAddress()).isPresent();
    }

    /**
     * Whether {@code host} is this computer: {@code localhost} itself, {@code 127.0.0.0/8} or {@code [::1]}, in any case
     * and any spelling of the address; a name under {@code localhost}, which a name server would answer, is not.
     *
     * @param host the host as an address writes it, an IPv6 address in brackets or not
     * @return whether it is
     */
    public static boolean loopback(String host) {
        String name = host.toLowerCase(Locale.ROOT);
        return name.equals("localhost") || literal(name).filter(InetAddress::isLoopbackAddress).isPresent();
    }

    /** The IP address {@code host}, lower case, writes as text: four decimal parts, or IPv6; empty for anything else. */
    private static Optional<InetAddress> literal(String host) {
        String bare = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
        try {
            if (DOTTED.matcher(bare).matches()) return Optional.of(Inet4Address.ofLiteral(bare));
            if (HEX.matcher(bare).matches()) return Optional.of(Inet6Address.ofLiteral(bare));
        } catch (IllegalArgumentException unreadable) {
            // No address.
        }
        return Optional.empty();
    }
}
