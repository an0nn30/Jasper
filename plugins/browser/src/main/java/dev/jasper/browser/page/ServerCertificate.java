package dev.jasper.browser.page;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A server's certificate that Chromium could not verify, as Jasper keeps it (spec section 14): the host it was presented
 * for, the SHA-256 fingerprint of the server's own certificate, which with the host is what Continue allows, and the
 * subjects of the chain the server sent, its own first, which the error page shows. Plain JDK types, so it names no
 * Chromium type.
 *
 * @param host the host of the address whose load met the certificate, in lower case
 * @param sha256 the fingerprint, 64 lowercase hexadecimal digits
 * @param subjects each certificate's subject as RFC 2253 writes it, the server's first; copied
 * @param selfSigned whether the server's certificate is the whole chain and its own issuer
 */
public record ServerCertificate(String host, String sha256, List<String> subjects, boolean selfSigned) {
    /** The most certificates of a chain whose subjects are kept. */
    public static final int MAX_CHAIN = 10;
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    /** Rejects a blank host and a fingerprint that is not 64 hexadecimal digits; keeps both in lower case. */
    public ServerCertificate {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(sha256, "sha256");
        host = host.toLowerCase(Locale.ROOT);
        sha256 = sha256.toLowerCase(Locale.ROOT);
        if (host.isBlank()) throw new IllegalArgumentException("A certificate's host is not blank");
        if (!SHA256.matcher(sha256).matches()) throw new IllegalArgumentException("Not a SHA-256 fingerprint: " + sha256);
        subjects = List.copyOf(subjects);
    }

    /**
     * The certificate Chromium reports for the load of {@code url}: the chain its {@code CefX509Certificate} gives, the
     * server's first.
     *
     * @param url the address whose load met the certificate, as Chromium gave it
     * @param chain the chain, the server's certificate first
     * @return the certificate; empty for an address without a host, an empty chain or one whose first certificate is
     *     missing or cannot be encoded
     */
    public static Optional<ServerCertificate> of(String url, X509Certificate[] chain) {
        Optional<String> host = host(url);
        if (host.isEmpty() || chain == null || chain.length == 0 || chain[0] == null) return Optional.empty();
        X509Certificate server = chain[0];
        String sha256;
        try {
            sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(server.getEncoded()));
        } catch (CertificateEncodingException | NoSuchAlgorithmException unreadable) {
            return Optional.empty();
        }
        var subjects = new ArrayList<String>();
        for (int at = 0; at < chain.length && subjects.size() < MAX_CHAIN; at++)
            if (chain[at] != null) subjects.add(chain[at].getSubjectX500Principal().getName());
        boolean selfSigned = chain.length == 1 && server.getSubjectX500Principal().equals(server.getIssuerX500Principal());
        return Optional.of(new ServerCertificate(host.get(), sha256, subjects, selfSigned));
    }

    private static Optional<String> host(String url) {
        if (url == null) return Optional.empty();
        try {
            return Optional.ofNullable(new URI(url).getHost()).filter(host -> !host.isBlank());
        } catch (URISyntaxException notAUri) {
            return Optional.empty();
        }
    }
}
