package dev.jasper.browser.page;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A certificate Chromium refused, as Jasper keeps it: the host it was for, the SHA-256 fingerprint of the server's own
 * certificate and the chain's subjects. The certificates are openssl's, made for these tests, and the fingerprints are
 * the ones openssl printed for them.
 */
class ServerCertificateTest {
    /** A self-signed certificate for {@code CN=self-signed.test}. */
    static final String SELF_SIGNED = "MIIBazCCARKgAwIBAgIUXJ0YFqA8j2DFohhG2U+DKraTO7EwCgYIKoZIzj0EAwIwGzEZMBcGA1UEAwwQc2VsZi1zaWduZWQudGVz"
        + "dDAgFw0yNjEwMzEwNTMxMjBaGA8yMTI2MTAwNzA1MzEyMFowGzEZMBcGA1UEAwwQc2VsZi1zaWduZWQudGVzdDBZMBMGByqGSM49"
        + "AgEGCCqGSM49AwEHA0IABGYK8NpTZSmPFsfa+B72tmkydHxOZvljSfAPMQY873+v5SdfIjX6kRriYm1N27hEJ6NPVsMMVL7uGf66"
        + "GbSW57mjMjAwMB0GA1UdDgQWBBRzxal+rdsvo+Qi60paHQiivjQZTzAPBgNVHRMBAf8EBTADAQH/MAoGCCqGSM49BAMCA0cAMEQC"
        + "IGjHtRgeVE2JIwJA3gfRIhG/PqfFK6z7wd3YNHM15D25AiBzlN6E82X6MFVZ5/rdTZg3STBRr7PevClBmQkHPrDYSA==";
    static final String SELF_SIGNED_SHA256 = "d53a1b43dbe103e328aa1f2e50c9ee35d35d116b19c8912f86aeff71eda21e61";
    /** {@code CN=example.test,O=Jasper Tests}, issued by {@link #AUTHORITY}. */
    static final String LEAF = "MIIBrDCCAVGgAwIBAgIUaAXL9yT/1OSKNL/IeufTWmP/7HkwCgYIKoZIzj0EAwIwNzEeMBwGA1UEAwwVSmFzcGVyIFRlc3QgQXV0"
        + "aG9yaXR5MRUwEwYDVQQKDAxKYXNwZXIgVGVzdHMwIBcNMjYxMDMxMDUzMTIwWhgPMjEyNjEwMDcwNTMxMjBaMC4xFTATBgNVBAMM"
        + "DGV4YW1wbGUudGVzdDEVMBMGA1UECgwMSmFzcGVyIFRlc3RzMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE0TKRt9aR7NCJi3Gr"
        + "GRKjGdAMMkbPy9+NeaGTxIYpilQGfXP6lAS3Sb599J2hnsrB9u2SzGw3VRQiYhDrzZ+TqqNCMEAwHQYDVR0OBBYEFKJagNuTHTAH"
        + "6ocMrcT5clbbDZl+MB8GA1UdIwQYMBaAFOlrR0ejsitAt+pgkXsYFkDKt0PeMAoGCCqGSM49BAMCA0kAMEYCIQDEL1MoaRlGKKPR"
        + "CdSlXgfeJcitcJhhYo8iLVx2kwVEigIhAPp1l3X2at2Q/WowreNntTQ266a0Hzd19tkP72Mu4bjJ";
    static final String LEAF_SHA256 = "7eb3a6625f1d49cbc2b992e72c0b85a31dd5bd3d98b2b1c6e7f40750484ceb49";
    /** {@code CN=Jasper Test Authority,O=Jasper Tests}, self-signed. */
    static final String AUTHORITY = "MIIBpDCCAUqgAwIBAgIUTt5FxPh+QXeG5cNv8Nxb8BVmGuEwCgYIKoZIzj0EAwIwNzEeMBwGA1UEAwwVSmFzcGVyIFRlc3QgQXV0"
        + "aG9yaXR5MRUwEwYDVQQKDAxKYXNwZXIgVGVzdHMwIBcNMjYxMDMxMDUzMTIwWhgPMjEyNjEwMDcwNTMxMjBaMDcxHjAcBgNVBAMM"
        + "FUphc3BlciBUZXN0IEF1dGhvcml0eTEVMBMGA1UECgwMSmFzcGVyIFRlc3RzMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEP8wl"
        + "1sEP/XmdJitI7VKaW4i+oblIP0AEtYYmGJfsG7KJmDR+j0tzZgAS66bCBh1ofr8xDt62wYCYQpkaSaXM4KMyMDAwHQYDVR0OBBYE"
        + "FOlrR0ejsitAt+pgkXsYFkDKt0PeMA8GA1UdEwEB/wQFMAMBAf8wCgYIKoZIzj0EAwIDSAAwRQIgDbdgiX61LVptW5kBOlwGmSkW"
        + "KlWXRU87Twb4OqXYr3kCIQDZE71W9BxcG5Ds/B8fvqVWmXrefaUnFwEoG+r044CqUw==";

    @Test void aSelfSignedCertificateIsKeptByItsHostAndFingerprint() throws Exception {
        ServerCertificate kept = ServerCertificate.of("https://Self-Signed.Test:8443/page?q=1", chain(SELF_SIGNED)).orElseThrow();
        assertThat(kept).isEqualTo(new ServerCertificate("self-signed.test", SELF_SIGNED_SHA256, List.of("CN=self-signed.test"), true));
    }

    @Test void aChainKeepsTheServersFingerprintAndEverySubjectServerFirst() throws Exception {
        ServerCertificate kept = ServerCertificate.of("https://example.test/", chain(LEAF, AUTHORITY)).orElseThrow();
        assertThat(kept.host()).isEqualTo("example.test");
        assertThat(kept.sha256()).isEqualTo(LEAF_SHA256);
        assertThat(kept.subjects()).containsExactly("O=Jasper Tests,CN=example.test", "O=Jasper Tests,CN=Jasper Test Authority");
        assertThat(kept.selfSigned()).as("issued by another").isFalse();
        assertThat(ServerCertificate.of("https://example.test/", chain(LEAF)).orElseThrow().selfSigned())
            .as("a server certificate alone, issued by an authority the chain leaves out").isFalse();
    }

    @Test void noChainOrNoHostKeepsNothing() throws Exception {
        assertThat(ServerCertificate.of("https://example.test/", null)).isEmpty();
        assertThat(ServerCertificate.of("https://example.test/", new X509Certificate[0])).isEmpty();
        assertThat(ServerCertificate.of("https://example.test/", new X509Certificate[] { null })).isEmpty();
        assertThat(ServerCertificate.of("file:///etc/hosts", chain(SELF_SIGNED))).isEmpty();
        assertThat(ServerCertificate.of("not a url", chain(SELF_SIGNED))).isEmpty();
        assertThat(ServerCertificate.of(null, chain(SELF_SIGNED))).isEmpty();
    }

    @Test void aLongChainKeepsItsFirstSubjects() throws Exception {
        var many = new X509Certificate[ServerCertificate.MAX_CHAIN + 5];
        java.util.Arrays.fill(many, certificate(AUTHORITY));
        many[0] = certificate(LEAF);
        assertThat(ServerCertificate.of("https://example.test/", many).map(ServerCertificate::subjects).orElseThrow())
            .hasSize(ServerCertificate.MAX_CHAIN);
    }

    /** As Chrome: a revoked, invalid or malformed certificate is fatal; any other certificate error can be gone past. */
    @Test void onlyARevokedInvalidOrMalformedCertificateIsFatal() {
        for (String fatal : List.of("ERR_CERT_REVOKED", "ERR_CERT_INVALID", "ERR_CERT_CONTAINS_ERRORS"))
            assertThat(ServerCertificate.overridable(fatal)).as(fatal).isFalse();
        for (String minor : List.of("ERR_CERT_AUTHORITY_INVALID", "ERR_CERT_DATE_INVALID", "ERR_CERT_COMMON_NAME_INVALID",
                "ERR_CERT_WEAK_SIGNATURE_ALGORITHM", "ERR_CERTIFICATE_TRANSPARENCY_REQUIRED"))
            assertThat(ServerCertificate.overridable(minor)).as(minor).isTrue();
        assertThat(ServerCertificate.overridable("ERR_SSL_PROTOCOL_ERROR")).as("no certificate's error").isFalse();
        assertThat(ServerCertificate.overridable("")).isFalse();
        assertThat(ServerCertificate.overridable(null)).isFalse();
    }

    @Test void itsPartsAreChecked() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ServerCertificate("", SELF_SIGNED_SHA256, List.of(), true))
            .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ServerCertificate("example.test", "AB", List.of(), true))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(new ServerCertificate("Example.Test", SELF_SIGNED_SHA256.toUpperCase(java.util.Locale.ROOT), List.of(), false))
            .isEqualTo(new ServerCertificate("example.test", SELF_SIGNED_SHA256, List.of(), false));
    }

    /** The certificates, as Chromium hands a chain over: the server's first. */
    static X509Certificate[] chain(String... encoded) throws CertificateException {
        var chain = new X509Certificate[encoded.length];
        for (int at = 0; at < encoded.length; at++) chain[at] = certificate(encoded[at]);
        return chain;
    }

    private static X509Certificate certificate(String encoded) throws CertificateException {
        return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)));
    }
}
