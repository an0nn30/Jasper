package dev.jasper.browser.adblock;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The site a host belongs to, for first and third party: its last two labels, or its last three under a two-letter
 * country's fixed second level. Jasper has no public suffix list.
 */
class BaseDomainTest {
    @Test void theLastTwoLabels() {
        assertThat(BaseDomain.of("a.b.example.com")).isEqualTo("example.com");
        assertThat(BaseDomain.of("example.com")).isEqualTo("example.com");
        assertThat(BaseDomain.of("A.Example.COM")).isEqualTo("example.com");
        assertThat(BaseDomain.of("www.example.com.")).as("a trailing dot").isEqualTo("example.com");
    }

    @Test void aShortNamedDotComIsOneSite() {
        assertThat(BaseDomain.of("www.cnn.com")).isEqualTo("cnn.com");
        assertThat(BaseDomain.of("edition.cnn.com")).isEqualTo("cnn.com");
        assertThat(BaseDomain.of("cdn.ibm.com")).isEqualTo("ibm.com");
    }

    @Test void theLastThreeUnderACountrysSecondLevel() {
        assertThat(BaseDomain.of("x.co.uk")).isEqualTo("x.co.uk");
        assertThat(BaseDomain.of("a.b.x.co.uk")).isEqualTo("x.co.uk");
        assertThat(BaseDomain.of("shop.example.com.au")).isEqualTo("example.com.au");
        assertThat(BaseDomain.of("www.city.gob.mx")).isEqualTo("city.gob.mx");
        assertThat(BaseDomain.of("co.uk")).isEqualTo("co.uk");
        assertThat(BaseDomain.of("a.cdn.uk")).as("a second level outside the list").isEqualTo("cdn.uk");
        assertThat(BaseDomain.of("a.co.com")).as("a three-letter top level").isEqualTo("co.com");
    }

    @Test void aPrivateSuffixCountsAsOneSite() {
        assertThat(BaseDomain.of("x.github.io")).isEqualTo("github.io");
        assertThat(BaseDomain.of("y.github.io")).isEqualTo("github.io");
    }

    @Test void aSingleLabelAndAnIpAddressAreThemselves() {
        assertThat(BaseDomain.of("localhost")).isEqualTo("localhost");
        assertThat(BaseDomain.of("10.0.0.1")).isEqualTo("10.0.0.1");
        assertThat(BaseDomain.of("[::1]")).isEqualTo("[::1]");
        assertThat(BaseDomain.of("[2001:db8::1]")).isEqualTo("[2001:db8::1]");
        assertThat(BaseDomain.of("")).isEmpty();
    }

    @Test void anAddressesHostIsLowerCaseWithoutItsUserInformationOrPort() {
        assertThat(BaseDomain.host("https://u:p@Sub.Example.COM:8443/x?y#z")).isEqualTo("sub.example.com");
        assertThat(BaseDomain.host("http://[::1]:3000/")).isEqualTo("[::1]");
        assertThat(BaseDomain.host("wss://a.test")).isEqualTo("a.test");
        assertThat(BaseDomain.host("https://good.test\\@evil.test/")).isEqualTo("good.test");
        assertThat(BaseDomain.host("https://a.test./")).isEqualTo("a.test");
        assertThat(BaseDomain.host("about:blank")).isEmpty();
        assertThat(BaseDomain.host("data:,x")).isEmpty();
        assertThat(BaseDomain.host("")).isEmpty();
        assertThat(BaseDomain.host("//a.test/")).as("no scheme").isEmpty();
    }
}
