package dev.jasper.browser.item;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Spec section 14: Continue allows that host with that certificate, in memory only. */
class CertificateAllowanceTest {
    private static final String SELF_SIGNED = "d53a1b43dbe103e328aa1f2e50c9ee35d35d116b19c8912f86aeff71eda21e61";
    private static final String OTHER = "7eb3a6625f1d49cbc2b992e72c0b85a31dd5bd3d98b2b1c6e7f40750484ceb49";

    @Test void aFreshAllowanceAllowsNothing() {
        var allowance = new CertificateAllowance();
        assertThat(allowance.allows("self-signed.badssl.com", SELF_SIGNED)).isFalse();
        assertThat(allowance.allowsAny("self-signed.badssl.com")).isFalse();
    }

    @Test void itAllowsOnlyThatHostWithThatCertificate() {
        var allowance = new CertificateAllowance();
        allowance.allow("self-signed.badssl.com", SELF_SIGNED);
        assertThat(allowance.allows("self-signed.badssl.com", SELF_SIGNED)).isTrue();
        assertThat(allowance.allows("self-signed.badssl.com", OTHER)).as("another certificate for the same host asks again").isFalse();
        assertThat(allowance.allows("expired.badssl.com", SELF_SIGNED)).as("the same certificate for another host").isFalse();
        assertThat(allowance.allowsAny("self-signed.badssl.com")).isTrue();
        assertThat(allowance.allowsAny("badssl.com")).isFalse();
    }

    @Test void hostsAndFingerprintsCompareWithoutCase() {
        var allowance = new CertificateAllowance();
        allowance.allow("Self-Signed.BadSSL.com", SELF_SIGNED.toUpperCase(java.util.Locale.ROOT));
        assertThat(allowance.allows("self-signed.badssl.com", SELF_SIGNED)).isTrue();
        assertThat(allowance.allowsAny("SELF-SIGNED.badssl.COM")).isTrue();
    }

    @Test void eachAllowanceIsItsOwn() {
        var one = new CertificateAllowance();
        one.allow("self-signed.badssl.com", SELF_SIGNED);
        assertThat(new CertificateAllowance().allows("self-signed.badssl.com", SELF_SIGNED)).as("as after Jasper quits").isFalse();
    }

    @Test void aBlankHostOrFingerprintIsRefused() {
        var allowance = new CertificateAllowance();
        assertThatThrownBy(() -> allowance.allow("", SELF_SIGNED)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> allowance.allow("self-signed.badssl.com", " ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(allowance.allows("", "")).isFalse();
        assertThat(allowance.allowsAny("")).isFalse();
    }
}
