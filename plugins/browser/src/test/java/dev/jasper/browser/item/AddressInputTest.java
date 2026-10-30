package dev.jasper.browser.item;

import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** What the address field loads for the text typed into it. */
class AddressInputTest {
    @Test void aDomainGetsHttps() {
        assertThat(AddressInput.resolve("example.com")).contains(URI.create("https://example.com"));
        assertThat(AddressInput.resolve("docs.example.co.uk/guide?page=2#top")).contains(URI.create("https://docs.example.co.uk/guide?page=2#top"));
    }

    @Test void localhostAndAHostWithAPortGetHttps() {
        assertThat(AddressInput.resolve("localhost:8080")).contains(URI.create("https://localhost:8080"));
        assertThat(AddressInput.resolve("LOCALHOST")).contains(URI.create("https://LOCALHOST"));
        assertThat(AddressInput.resolve("devbox:3000/app")).contains(URI.create("https://devbox:3000/app"));
    }

    @Test void anIpAddressGetsHttps() {
        assertThat(AddressInput.resolve("192.168.1.10")).contains(URI.create("https://192.168.1.10"));
        assertThat(AddressInput.resolve("10.0.0.1:8443/status")).contains(URI.create("https://10.0.0.1:8443/status"));
        assertThat(AddressInput.resolve("[::1]:8080")).contains(URI.create("https://[::1]:8080"));
    }

    @Test void otherTextBecomesAGoogleSearch() {
        assertThat(AddressInput.resolve("what is jcef")).contains(URI.create("https://google.com/search?q=what+is+jcef"));
        assertThat(AddressInput.resolve("c++ & java")).contains(URI.create("https://google.com/search?q=c%2B%2B+%26+java"));
        assertThat(AddressInput.resolve("jcef")).as("one word without a port").contains(URI.create("https://google.com/search?q=jcef"));
        assertThat(AddressInput.resolve("3.14")).as("a number is not a domain").contains(URI.create("https://google.com/search?q=3.14"));
        assertThat(AddressInput.resolve("192.168.1.256")).contains(URI.create("https://google.com/search?q=192.168.1.256"));
        assertThat(AddressInput.resolve("javascript:alert(1)")).as("a scheme Jasper does not offer")
            .contains(URI.create("https://google.com/search?q=javascript%3Aalert%281%29"));
        assertThat(AddressInput.resolve("someone@example.com")).contains(URI.create("https://google.com/search?q=someone%40example.com"));
    }

    @Test void textWithAnOfferedSchemeLoadsAsTyped() {
        assertThat(AddressInput.resolve("https://example.com/a?b=c#d")).contains(URI.create("https://example.com/a?b=c#d"));
        assertThat(AddressInput.resolve("http://intranet")).contains(URI.create("http://intranet"));
        assertThat(AddressInput.resolve("file:///tmp/page.html")).contains(URI.create("file:///tmp/page.html"));
        assertThat(AddressInput.resolve("about:blank")).contains(URI.create("about:blank"));
        assertThat(AddressInput.resolve("jasper-about:welcome")).contains(URI.create("jasper-about:welcome"));
        assertThat(AddressInput.resolve("https://example.com/a b")).as("characters a URI cannot hold are escaped")
            .contains(URI.create("https://example.com/a%20b"));
    }

    /** Only the characters a URI refuses are escaped: the escapes a pasted address already has stay as they are. */
    @Test void aPastedAddressKeepsItsEscapes() {
        assertThat(AddressInput.resolve("https://fonts.googleapis.com/css?family=Roboto|Open%20Sans").map(URI::toString))
            .contains("https://fonts.googleapis.com/css?family=Roboto%7COpen%20Sans");
        assertThat(AddressInput.resolve("https://example.com/?q=%E2%9C%93&x={1}").map(URI::toString))
            .contains("https://example.com/?q=%E2%9C%93&x=%7B1%7D");
        assertThat(AddressInput.resolve("https://example.com/100% sure").map(URI::toString)).as("a % that starts no escape")
            .contains("https://example.com/100%25%20sure");
        assertThat(AddressInput.resolve("https://example.com/caf\u00e9 menu").map(URI::toString)).as("non-ASCII as UTF-8")
            .contains("https://example.com/caf%C3%A9%20menu");
        assertThat(AddressInput.resolve("https://example.com/a b#one#two").map(URI::toString)).contains("https://example.com/a%20b#one%23two");
        assertThat(AddressInput.resolve("http://[::1]:8080/a b").map(URI::toString)).as("an IPv6 host keeps its brackets")
            .contains("http://[::1]:8080/a%20b");
        assertThat(AddressInput.resolve("example.com/a|b").map(URI::toString)).contains("https://example.com/a%7Cb");
    }

    @Test void chromePagesAndEmptyTextGiveNothing() {
        assertThat(AddressInput.resolve("chrome://version")).isEqualTo(Optional.empty());
        assertThat(AddressInput.resolve("CHROME://settings")).isEmpty();
        assertThat(AddressInput.resolve("")).isEmpty();
        assertThat(AddressInput.resolve("   ")).isEmpty();
        assertThat(AddressInput.resolve(null)).isEmpty();
    }

    @Test void surroundingSpacesAreTrimmed() {
        assertThat(AddressInput.resolve("  example.com \t")).contains(URI.create("https://example.com"));
        assertThat(AddressInput.resolve("  what is jcef  ")).contains(URI.create("https://google.com/search?q=what+is+jcef"));
        assertThat(AddressInput.resolve(" https://example.com ")).contains(URI.create("https://example.com"));
    }
}
