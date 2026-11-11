package dev.jasper.browser.logins;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

/**
 * The name a sign-in's login is saved under in the Credential Vault: the origin that challenged, then its realm, as
 * {@code scheme://host[:port] (realm)}. The same site and realm so always give the same name, and a remembered login is
 * found again by it.
 */
public final class LoginName {
    private LoginName() { }

    /**
     * The login name of a challenge from {@code origin} for {@code realm}: the scheme and host lowercased, an IPv6 host
     * in its brackets as {@link URI#getHost()} gives it, the port only when it is not the scheme's default (80 for
     * {@code http}, 443 for {@code https}), no user information, path, query or fragment, and no {@code (realm)} for a
     * blank realm. The realm is used as given; the challenge cleans it first.
     *
     * @param origin the address that challenged; only its scheme, host and port are used
     * @param realm the challenge's realm, blank for none
     * @return the name
     * @throws IllegalArgumentException when {@code origin} has no host or a scheme other than {@code http} or
     *     {@code https}, which no challenge Jasper remembers has; the message names neither
     */
    public static String of(URI origin, String realm) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(realm, "realm");
        String scheme = origin.getScheme() == null ? "" : origin.getScheme().toLowerCase(Locale.ROOT);
        int defaultPort = switch (scheme) {
            case "http" -> 80;
            case "https" -> 443;
            default -> throw new IllegalArgumentException("A login name needs an http or https origin");
        };
        String host = origin.getHost();
        if (host == null || host.isEmpty()) throw new IllegalArgumentException("A login name needs an origin with a host");
        int port = origin.getPort();
        String name = scheme + "://" + host.toLowerCase(Locale.ROOT) + (port == -1 || port == defaultPort ? "" : ":" + port);
        return realm.isBlank() ? name : name + " (" + realm + ")";
    }
}
