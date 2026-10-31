package dev.jasper.browser.item;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The certificates the user chose to go on past, Continue to this site (unsafe) on the error page (spec section 14): each
 * a host with the SHA-256 fingerprint of the certificate it presented, so a different certificate for the same host asks
 * again. It is held in memory only, one for each run of the plugin and shared by all its items, so it ends when Jasper
 * quits. Chromium asks it on its own thread, the items change it on the UI thread.
 */
final class CertificateAllowance {
    private final Set<Allowed> allowed = ConcurrentHashMap.newKeySet();
    private final Set<String> hosts = ConcurrentHashMap.newKeySet();

    /** An allowance that allows nothing. */
    CertificateAllowance() { }

    /**
     * Allows {@code host} with the certificate whose fingerprint is {@code sha256}, until Jasper quits.
     *
     * @param host the host, in any case
     * @param sha256 the certificate's SHA-256 fingerprint, in hexadecimal of any case
     * @throws IllegalArgumentException for a blank host or fingerprint
     */
    void allow(String host, String sha256) {
        String name = key(host), print = key(sha256);
        if (name.isBlank() || print.isBlank()) throw new IllegalArgumentException("A certificate is allowed for a host and a fingerprint");
        allowed.add(new Allowed(name, print));
        hosts.add(name);
    }

    /**
     * Whether {@code host} is allowed with the certificate whose fingerprint is {@code sha256}. Any thread.
     *
     * @param host the host
     * @param sha256 the fingerprint
     * @return whether it was allowed
     */
    boolean allows(String host, String sha256) {
        return host != null && sha256 != null && allowed.contains(new Allowed(key(host), key(sha256)));
    }

    /**
     * Whether some certificate of {@code host} is allowed: while a page of it is current, its item's toolbar says "Not
     * secure". Any thread.
     *
     * @param host the host
     * @return whether one was allowed
     */
    boolean allowsAny(String host) {
        return host != null && hosts.contains(key(host));
    }

    private static String key(String text) {
        return Objects.requireNonNull(text, "text").strip().toLowerCase(Locale.ROOT);
    }

    /** A host allowed with one certificate, both in lower case. */
    private record Allowed(String host, String sha256) { }
}
