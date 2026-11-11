package dev.jasper.browser.page;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * An HTTP authentication challenge to one of a page's requests, a server's or a proxy's, as Chromium asks Jasper for a
 * sign-in (plan 8), in plain values: the address that asked, who challenged and for which realm and scheme, the
 * page's top address as Chromium asked ({@link Page#topAddress()}), and the one answer Chromium waits for, a user name
 * and password or a cancel. Chromium's request waits until it is answered, so each sign-in is answered exactly once:
 * {@link #proceed} and {@link #cancel()} share one atomic, and only the first reaches Chromium. It names no Chromium
 * type and keeps no user name or password. Any thread.
 */
public final class SignIn {
    private static final System.Logger LOG = System.getLogger(SignIn.class.getName());
    private final String originUrl, host, realm, scheme, topAddress;
    private final boolean proxy;
    private final int port;
    private final BiConsumer<String, String> proceed;
    private final Runnable cancel;
    private final AtomicBoolean answered = new AtomicBoolean();

    /**
     * A sign-in Chromium asks for, which nothing has answered. A null text reads as empty.
     *
     * @param originUrl the address that made the request, as Chromium names it: an origin or a full address
     * @param proxy whether a proxy challenged, not the server
     * @param host the challenger's host, as Chromium gives it: the proxy's for a proxy
     * @param port the challenger's port
     * @param realm the challenge's realm, as the challenger wrote it; empty for none
     * @param scheme the challenge's scheme, such as {@code basic} or {@code digest}, in any case
     * @param topAddress the page's top address as Chromium asked
     * @param proceed gives Chromium a user name and password, once at most
     * @param cancel tells Chromium there are none, once at most
     */
    public SignIn(String originUrl, boolean proxy, String host, int port, String realm, String scheme, String topAddress,
                  BiConsumer<String, String> proceed, Runnable cancel) {
        this.originUrl = Objects.requireNonNullElse(originUrl, "");
        this.proxy = proxy;
        this.host = Objects.requireNonNullElse(host, "");
        this.port = port;
        this.realm = Objects.requireNonNullElse(realm, "");
        this.scheme = Objects.requireNonNullElse(scheme, "").toLowerCase(Locale.ROOT);
        this.topAddress = Objects.requireNonNullElse(topAddress, "");
        this.proceed = Objects.requireNonNull(proceed, "proceed");
        this.cancel = Objects.requireNonNull(cancel, "cancel");
    }

    /**
     * The address that made the request, as Chromium names it.
     *
     * @return the address: an origin, or a full address ({@link #originOnly()}); empty when Chromium gave none
     */
    public String originUrl() {
        return originUrl;
    }

    /**
     * Whether a proxy challenged, not the server.
     *
     * @return whether one did
     */
    public boolean proxy() {
        return proxy;
    }

    /**
     * The challenger's host, as Chromium gives it: ASCII, punycode for an internationalized name. For a proxy, the
     * proxy's.
     *
     * @return the host
     */
    public String host() {
        return host;
    }

    /**
     * The challenger's port. For a proxy, the proxy's.
     *
     * @return the port
     */
    public int port() {
        return port;
    }

    /**
     * The challenge's realm, as the challenger wrote it: server text, of any length and any characters.
     *
     * @return the realm; empty for none
     */
    public String realm() {
        return realm;
    }

    /**
     * The challenge's scheme.
     *
     * @return the scheme in lower case, such as {@code basic}, {@code digest}, {@code ntlm} or {@code negotiate}
     */
    public String scheme() {
        return scheme;
    }

    /**
     * The page's top address as Chromium asked: the address of its main frame's latest navigation request, after its
     * redirects, or of its latest commit, whichever came last ({@link Page#topAddress()}).
     *
     * @return the address; empty when the page had none
     */
    public String topAddress() {
        return topAddress;
    }

    /**
     * Whether {@link #originUrl()} names an origin alone: an address with no path but {@code /} and no query, whatever
     * its fragment, as CEF may give the origin of the request rather than its address.
     *
     * @return whether it does; false for an address without {@code scheme://}
     */
    public boolean originOnly() {
        int start = originUrl.indexOf("://");
        if (start < 0) return false;
        int end = start + 3;
        while (end < originUrl.length() && "/?#\\".indexOf(originUrl.charAt(end)) < 0) end++;
        int fragment = originUrl.indexOf('#', end);
        String rest = originUrl.substring(end, fragment < 0 ? originUrl.length() : fragment);
        return rest.isEmpty() || rest.equals("/");
    }

    /**
     * Gives Chromium {@code user} and {@code password} for the request, once: unless the sign-in was answered first. A
     * failure to give them is logged, with the exception alone.
     *
     * @param user the user name
     * @param password the password, a {@code String} as Chromium takes one
     * @return whether they went to the sign-in's {@code proceed}: false once it was answered, and when that failed
     */
    public boolean proceed(String user, String password) {
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(password, "password");
        if (!answered.compareAndSet(false, true)) return false;
        try {
            proceed.accept(user, password);
            return true;
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Chromium could not be given a sign-in", failure);
            return false;
        }
    }

    /**
     * Tells Chromium there is no sign-in for the request, once: unless the sign-in was answered first. Chromium then
     * shows the challenger's response, as a 401 or 407 page. A failure to tell it is logged, with the exception alone.
     *
     * @return whether the sign-in's {@code cancel} ran: false once it was answered, and when that failed
     */
    public boolean cancel() {
        if (!answered.compareAndSet(false, true)) return false;
        try {
            cancel.run();
            return true;
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Chromium could not be told a sign-in was cancelled", failure);
            return false;
        }
    }

    /**
     * Whether the sign-in was answered: {@link #proceed} or {@link #cancel()} was called.
     *
     * @return whether it was
     */
    public boolean answered() {
        return answered.get();
    }
}
