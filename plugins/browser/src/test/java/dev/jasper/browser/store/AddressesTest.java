package dev.jasper.browser.store;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Which addresses history and bookmarks keep, and the host and path a visit counts under. */
class AddressesTest {
    /** R46: an address is kept without its user name and password. */
    @Test void anAddressIsKeptWithoutItsUserNameAndPassword() {
        assertThat(Addresses.storable("https://user:pw@example.com/a?b#c")).contains("https://example.com/a?b#c");
        assertThat(Addresses.storable("http://ada@example.com:8080/")).as("a user name alone").contains("http://example.com:8080/");
        assertThat(Addresses.storable("https://example.com/@ada?to=ada@example.com#@")).as("an @ outside the authority")
            .contains("https://example.com/@ada?to=ada@example.com#@");
    }

    @Test void webAndFileAddressesAreKeptAsTheyAre() {
        for (String address : List.of("file:///tmp/a.html", "http://example.com/", "https://example.com/a|b?q={x}",
                "HTTPS://Example.COM/Path", "https://[::1]:8443/x"))
            assertThat(Addresses.storable(address)).as(address).contains(address);
    }

    /** Spec section 10 leaves out only {@code jasper-about:}; the others cannot be visited again. */
    @Test void addressesThatCannotBeVisitedAgainAreNotKept() {
        for (String address : List.of("jasper-about:history", "about:blank", "data:text/html,x", "blob:https://a/1", "javascript:1",
                "view-source:https://a.example/", "ftp://a.example/", "chrome://settings", "mailto:ada@example.com", "example.com", ""))
            assertThat(Addresses.storable(address)).as(address).isEmpty();
    }

    /** Only a URI can lose its user information, so an address with user information that is not one is not kept. */
    @Test void anAddressWithUserInformationThatIsNotAUriIsNotKept() {
        assertThat(Addresses.storable("https://user:pw@example.com/a|b")).isEmpty();
    }

    @Test void anAddressOfAtMost8192CharactersIsKept() {
        String start = "https://example.com/";
        String longest = start + "a".repeat(8_192 - start.length());
        assertThat(Addresses.storable(longest)).contains(longest);
        assertThat(Addresses.storable(longest + "a")).isEmpty();
    }

    /** A visit counts under the address's host, with its port, in lower case, and its path without query or fragment. */
    @Test void aVisitCountsUnderTheHostWithItsPortAndThePath() {
        assertThat(Addresses.visitKey("https://Example.COM:8443/a/B?x=1#f")).isEqualTo(new Addresses.VisitKey("example.com:8443", "/a/B"));
        assertThat(Addresses.visitKey("http://example.com:8080/a/B")).isEqualTo(new Addresses.VisitKey("example.com:8080", "/a/B"));
        assertThat(Addresses.visitKey("https://example.com")).isEqualTo(new Addresses.VisitKey("example.com", "/"));
        assertThat(Addresses.visitKey("https://example.com?q#f")).isEqualTo(new Addresses.VisitKey("example.com", "/"));
        assertThat(Addresses.visitKey("http://[::1]:8080/x")).isEqualTo(new Addresses.VisitKey("[::1]:8080", "/x"));
        assertThat(Addresses.visitKey("file:///tmp/a.html")).isEqualTo(new Addresses.VisitKey("", "/tmp/a.html"));
        assertThat(Addresses.visitKey("file://server/share/a.html")).isEqualTo(new Addresses.VisitKey("server", "/share/a.html"));
        assertThat(Addresses.visitKey("https://a.example/p?x=1")).isEqualTo(Addresses.visitKey("https://a.example/p?x=2"));
        assertThat(Addresses.visitKey("http://a.example/p")).as("either scheme").isEqualTo(Addresses.visitKey("https://a.example/p"));
    }
}
