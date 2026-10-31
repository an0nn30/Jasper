package dev.jasper.browser.item;

import dev.jasper.browser.settings.BrowserSettings;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static org.assertj.core.api.Assertions.assertThat;

/** What the address field loads for the text typed into it (spec section 6, "Typed text"). */
class AddressInputTest {
    private static final BrowserSettings GOOGLE = SearchEngineTest.settings(SearchEngine.GOOGLE, "", "");

    @Test void aDomainGetsHttps() {
        assertThat(resolve("example.com")).contains(URI.create("https://example.com"));
        assertThat(resolve("docs.example.co.uk/guide?page=2#top")).contains(URI.create("https://docs.example.co.uk/guide?page=2#top"));
        assertThat(resolve("example.com.")).as("a trailing dot").contains(URI.create("https://example.com."));
        assertThat(resolve("example.com./a")).contains(URI.create("https://example.com./a"));
        assertThat(resolve("example.com..")).as("two dots").contains(URI.create("https://google.com/search?q=example.com.."));
    }

    /** A single word without spaces is a host, as {@code localhost} and an intranet name are, underscores allowed. */
    @Test void aSingleWordGetsHttps() {
        assertThat(resolve("my_host")).contains(URI.create("https://my_host"));
        assertThat(resolve("jcef")).contains(URI.create("https://jcef"));
        assertThat(resolve("my_host:8080/status")).contains(URI.create("https://my_host:8080/status"));
        assertThat(resolve("build-01")).contains(URI.create("https://build-01"));
        assertThat(resolve("42")).as("a number is no word").contains(URI.create("https://google.com/search?q=42"));
        assertThat(resolve("-x")).contains(URI.create("https://google.com/search?q=-x"));
        assertThat(resolve("~")).contains(URI.create("https://google.com/search?q=%7E"));
        assertThat(resolve("\u6771\u4eac")).as("a word in another script, often a query without spaces")
            .contains(URI.create("https://google.com/search?q=%E6%9D%B1%E4%BA%AC"));
    }

    @Test void anAbsoluteLocalPathBecomesAFileUrl() {
        assertThat(resolve("/Users/me/a.html")).contains(URI.create("file:///Users/me/a.html"));
        assertThat(resolve("/tmp/a b#1%.html")).as("escaped").contains(URI.create("file:///tmp/a%20b%231%25.html"));
        assertThat(resolve("/")).contains(URI.create("file:///"));
        String home = Path.of(System.getProperty("user.home")).toUri().getRawPath();
        assertThat(resolve("~/notes/a.html")).contains(URI.create("file://" + home.replaceAll("/$", "") + "/notes/a.html"));
    }

    @Test @EnabledOnOs(OS.WINDOWS) void aWindowsPathBecomesAFileUrlOnWindows() {
        assertThat(resolve("C:\\a.html")).contains(URI.create("file:///C:/a.html"));
        assertThat(resolve("d:\\My Pages\\b.html")).contains(URI.create("file:///d:/My%20Pages/b.html"));
        assertThat(resolve("C:/a.html")).contains(URI.create("file:///C:/a.html"));
    }

    @Test @DisabledOnOs(OS.WINDOWS) void aWindowsPathIsASearchElsewhere() {
        assertThat(resolve("C:\\a.html")).contains(URI.create("https://google.com/search?q=C%3A%5Ca.html"));
    }

    @Test void localhostAndAHostWithAPortGetHttps() {
        assertThat(resolve("localhost:8080")).contains(URI.create("https://localhost:8080"));
        assertThat(resolve("LOCALHOST")).contains(URI.create("https://LOCALHOST"));
        assertThat(resolve("devbox:3000/app")).contains(URI.create("https://devbox:3000/app"));
    }

    @Test void anIpAddressGetsHttps() {
        assertThat(resolve("192.168.1.10")).contains(URI.create("https://192.168.1.10"));
        assertThat(resolve("10.0.0.1:8443/status")).contains(URI.create("https://10.0.0.1:8443/status"));
        assertThat(resolve("[::1]:8080")).contains(URI.create("https://[::1]:8080"));
    }

    @Test void otherTextBecomesASearchWithTheChosenEngine() {
        assertThat(resolve("what is jcef")).contains(URI.create("https://google.com/search?q=what+is+jcef"));
        assertThat(resolve("c++ & java")).contains(URI.create("https://google.com/search?q=c%2B%2B+%26+java"));
        assertThat(resolve("3.14")).as("a number is not a domain").contains(URI.create("https://google.com/search?q=3.14"));
        assertThat(resolve("192.168.1.256")).contains(URI.create("https://google.com/search?q=192.168.1.256"));
        assertThat(resolve("javascript:alert(1)")).as("a scheme Jasper does not offer")
            .contains(URI.create("https://google.com/search?q=javascript%3Aalert%281%29"));
        assertThat(resolve("someone@example.com")).contains(URI.create("https://google.com/search?q=someone%40example.com"));
    }

    @Test void textWithAnOfferedSchemeLoadsAsTyped() {
        assertThat(resolve("https://example.com/a?b=c#d")).contains(URI.create("https://example.com/a?b=c#d"));
        assertThat(resolve("http://intranet")).contains(URI.create("http://intranet"));
        assertThat(resolve("file:///tmp/page.html")).contains(URI.create("file:///tmp/page.html"));
        assertThat(resolve("about:blank")).contains(URI.create("about:blank"));
        assertThat(resolve("jasper-about:welcome")).contains(URI.create("jasper-about:welcome"));
        assertThat(resolve("https://example.com/a b")).as("characters a URI cannot hold are escaped")
            .contains(URI.create("https://example.com/a%20b"));
    }

    /** Only the characters a URI refuses are escaped: the escapes a pasted address already has stay as they are. */
    @Test void aPastedAddressKeepsItsEscapes() {
        assertThat(resolve("https://fonts.googleapis.com/css?family=Roboto|Open%20Sans").map(URI::toString))
            .contains("https://fonts.googleapis.com/css?family=Roboto%7COpen%20Sans");
        assertThat(resolve("https://example.com/?q=%E2%9C%93&x={1}").map(URI::toString))
            .contains("https://example.com/?q=%E2%9C%93&x=%7B1%7D");
        assertThat(resolve("https://example.com/100% sure").map(URI::toString)).as("a % that starts no escape")
            .contains("https://example.com/100%25%20sure");
        assertThat(resolve("https://example.com/caf\u00e9 menu").map(URI::toString)).as("non-ASCII as UTF-8")
            .contains("https://example.com/caf%C3%A9%20menu");
        assertThat(resolve("https://example.com/a b#one#two").map(URI::toString)).contains("https://example.com/a%20b#one%23two");
        assertThat(resolve("http://[::1]:8080/a b").map(URI::toString)).as("an IPv6 host keeps its brackets")
            .contains("http://[::1]:8080/a%20b");
        assertThat(resolve("example.com/a|b").map(URI::toString)).contains("https://example.com/a%7Cb");
    }

    @Test void chromePagesAndEmptyTextGiveNothing() {
        assertThat(resolve("chrome://version")).isEqualTo(Optional.empty());
        assertThat(resolve("CHROME://settings")).isEmpty();
        assertThat(resolve("")).isEmpty();
        assertThat(resolve("   ")).isEmpty();
        assertThat(resolve(null)).isEmpty();
    }

    @Test void surroundingSpacesAreTrimmed() {
        assertThat(resolve("  example.com \t")).contains(URI.create("https://example.com"));
        assertThat(resolve("  what is jcef  ")).contains(URI.create("https://google.com/search?q=what+is+jcef"));
        assertThat(resolve(" https://example.com ")).contains(URI.create("https://example.com"));
    }

    @Test void theSearchIsTheChosenEngines() {
        assertThat(AddressInput.resolve("what is jcef", SearchEngineTest.settings(SearchEngine.KAGI, "", "")))
            .contains(URI.create("https://kagi.com/search?q=what+is+jcef"));
        assertThat(AddressInput.resolve("what is jcef", SearchEngineTest.settings(SearchEngine.CUSTOM, "https://example.com/?s=%s", "")))
            .contains(URI.create("https://example.com/?s=what+is+jcef"));
    }

    /** What the text names when it is no search: the address the field's "Navigate to" row loads. */
    @Test void theAddressIsWhatResolvesWithoutASearch() {
        assertThat(AddressInput.address("example.com")).contains(URI.create("https://example.com"));
        assertThat(AddressInput.address("jasper-about:welcome")).contains(URI.create("jasper-about:welcome"));
        assertThat(AddressInput.address("/tmp/a.html")).contains(URI.create("file:///tmp/a.html"));
        assertThat(AddressInput.address("what is jcef")).isEmpty();
        assertThat(AddressInput.address("javascript:alert(1)")).isEmpty();
        assertThat(AddressInput.address("chrome://version")).isEmpty();
        assertThat(AddressInput.address("")).isEmpty();
    }

    private static Optional<URI> resolve(String typed) {
        return AddressInput.resolve(typed, GOOGLE);
    }
}
