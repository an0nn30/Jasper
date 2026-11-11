package dev.jasper.browser.logins;

import dev.jasper.browser.page.SignIn;
import dev.jasper.browser.permissions.Origins;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * An HTTP sign-in as Jasper judges it (plan 8, spec section 13), in plain values: the origin that asked, who
 * challenged, the realm and the scheme; whether a proxy challenged; whether the asking origin differs from the page's
 * top address's, which stands in for a frame, as JCEF names none (spec section 21); and whether the challenge is the
 * top-level navigation's own (F5). From these it decides what the sign-in dialog offers, and the name a login is saved
 * under. The realm is server text, so it is cleaned once, as the challenge is made, of what could pass for Jasper's
 * own words (F9); the host is kept as Chromium gives it, punycode for an internationalized name, never decoded. Names
 * no Chromium type.
 *
 * @param origin the origin that made the request, as {@link Origins#of} writes one: for a proxy too, the origin of the
 *     address the page asked for; empty when it has none
 * @param host the challenger's host, as Chromium gives it: the proxy's for a proxy
 * @param port the challenger's port: the proxy's for a proxy
 * @param realm the realm, cleaned ({@link #REALM_LIMIT}); empty for none
 * @param scheme the scheme, in lower case, such as {@code basic}
 * @param proxy whether a proxy challenged, not the server
 * @param framed whether the asking origin is not the page's top address's, or either has none
 * @param topLevel whether the challenge is the top-level navigation's own: a server's, not a proxy's, whose asking
 *     address is the top address, a fragment aside, or, when Chromium named only an origin, whose origin is the top
 *     address's
 */
public record Challenge(String origin, String host, int port, String realm, String scheme, boolean proxy, boolean framed,
                        boolean topLevel) {
    /**
     * The most code points of a realm Jasper shows: a longer one is cut to the first {@code REALM_LIMIT - 1}, then
     * "…". Before that, each control and format character ({@code Cc}, {@code Cf}, bidi controls and zero-width
     * characters among them), U+2028 and U+2029 become a space, and each run of white space one space, stripped from
     * both ends.
     */
    public static final int REALM_LIMIT = 100;
    private static final String ELLIPSIS = "\u2026";

    /**
     * A challenge of these values, its realm cleaned and its scheme lowercased.
     *
     * @throws NullPointerException for a null text
     */
    public Challenge {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(host, "host");
        realm = clean(Objects.requireNonNull(realm, "realm"));
        scheme = Objects.requireNonNull(scheme, "scheme").toLowerCase(Locale.ROOT);
    }

    /**
     * How Jasper judges {@code signIn}.
     *
     * @param signIn a sign-in Chromium asks for
     * @return its challenge
     */
    public static Challenge of(SignIn signIn) {
        Optional<String> asking = Origins.of(signIn.originUrl()), top = Origins.of(signIn.topAddress());
        boolean framed = asking.isEmpty() || !asking.equals(top);
        boolean topLevel = !signIn.proxy() && asking.isPresent() && (signIn.originOnly() ? asking.equals(top)
            : withoutFragment(signIn.originUrl()).equals(withoutFragment(signIn.topAddress())));
        return new Challenge(asking.orElse(""), signIn.host(), signIn.port(), signIn.realm(), signIn.scheme(),
            signIn.proxy(), framed, topLevel);
    }

    /**
     * Whether the scheme is Basic, in any case.
     *
     * @return whether it is
     */
    public boolean basic() {
        return scheme.equals("basic");
    }

    /**
     * Whether the sign-in dialog offers the Vault's Password Manager: for Basic, from a server, not a proxy, outside
     * /dev/null, with the Vault, and from an origin that has a login name ({@link #loginName()}). Every other challenge
     * gets the plain dialog.
     *
     * @param devNull whether the page's item is in /dev/null mode
     * @param vault whether the Vault plugin is there
     * @return whether it does
     */
    public boolean offersVault(boolean devNull, boolean vault) {
        return basic() && !proxy && !devNull && vault && named();
    }

    /**
     * Whether the sign-in can be remembered, and a remembered one filled without the dialog: as {@link #offersVault}
     * offers the Vault, for a challenge that is not framed and is the top-level navigation's own (F5).
     *
     * @param devNull whether the page's item is in /dev/null mode
     * @param vault whether the Vault plugin is there
     * @return whether it can
     */
    public boolean remembers(boolean devNull, boolean vault) {
        return offersVault(devNull, vault) && !framed && topLevel;
    }

    /**
     * The name the sign-in's login is saved under in the Vault ({@link LoginName#of}), with the cleaned realm.
     *
     * @return the name
     * @throws IllegalArgumentException when the origin has none: it is empty, or its host is one {@link URI} cannot
     *     read, such as one with an underscore, which Chromium accepts (L13); {@link #offersVault} is then false
     */
    public String loginName() {
        return LoginName.of(URI.create(origin), realm);
    }

    /** Leaves out the realm, which is server text, so no log line carries it. */
    @Override public String toString() {
        return "Challenge[origin=" + origin + ", host=" + host + ", port=" + port + ", scheme=" + scheme + ", proxy=" + proxy
            + ", framed=" + framed + ", topLevel=" + topLevel + "]";
    }

    /** Whether the origin has a login name; nothing names its host when it has none. */
    private boolean named() {
        try {
            loginName();
            return true;
        } catch (IllegalArgumentException none) {
            return false;
        }
    }

    /** {@code realm} as Jasper shows it, as {@link #REALM_LIMIT} says. */
    static String clean(String realm) {
        var spaced = new StringBuilder(realm.length());
        boolean space = false;
        for (int at = 0; at < realm.length(); ) {
            int point = realm.codePointAt(at);
            at += Character.charCount(point);
            int type = Character.getType(point);
            boolean blank = type == Character.CONTROL || type == Character.FORMAT || Character.isWhitespace(point)
                || Character.isSpaceChar(point);
            if (blank) {
                space = true;
                continue;
            }
            if (space && !spaced.isEmpty()) spaced.append(' ');
            space = false;
            spaced.appendCodePoint(point);
        }
        String cleaned = spaced.toString();
        if (cleaned.codePointCount(0, cleaned.length()) <= REALM_LIMIT) return cleaned;
        return cleaned.substring(0, cleaned.offsetByCodePoints(0, REALM_LIMIT - 1)).stripTrailing() + ELLIPSIS;
    }

    private static String withoutFragment(String address) {
        int fragment = address.indexOf('#');
        return fragment < 0 ? address : address.substring(0, fragment);
    }
}
