package dev.jasper.browser.logins;

import java.net.URI;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plan 8 Task 3: the name a sign-in's login is saved under in the Vault is its origin, {@code scheme://host[:port]},
 * and its realm, so a login is found again for the same site and realm only.
 */
class LoginNameTest {
    @Test void theSchemeAndHostAreLowercasedAndTheDefaultPortPathQueryAndFragmentDropped() {
        assertThat(LoginName.of(URI.create("https://Example.COM:443/x?q#f"), "Corp")).isEqualTo("https://example.com (Corp)");
        assertThat(LoginName.of(URI.create("HTTP://Intranet.Example:80/"), "Corp")).isEqualTo("http://intranet.example (Corp)");
    }

    @Test void aPortThatIsNotTheSchemesDefaultIsKeptAndABlankRealmLeftOut() {
        assertThat(LoginName.of(URI.create("http://example.com:8080"), "")).isEqualTo("http://example.com:8080");
        assertThat(LoginName.of(URI.create("https://example.com:80/"), " ")).isEqualTo("https://example.com:80");
        assertThat(LoginName.of(URI.create("http://example.com:443/"), "Corp")).isEqualTo("http://example.com:443 (Corp)");
    }

    /** R46: no user info, so no {@code user:password@}, ever reaches the name. */
    @Test void theUserInfoIsLeftOut() {
        assertThat(LoginName.of(URI.create("https://user:pw@example.com/"), "")).isEqualTo("https://example.com");
    }

    @Test void anIpv6HostStaysBracketed() {
        assertThat(LoginName.of(URI.create("https://[::1]:8443/"), "")).isEqualTo("https://[::1]:8443");
        assertThat(LoginName.of(URI.create("http://[FE80::1]/"), "Corp")).isEqualTo("http://[fe80::1] (Corp)");
    }

    /** Task 5a's challenge cleans the realm first (F9), so the name uses it as given. */
    @Test void aRealmWithQuotesIsKept() {
        assertThat(LoginName.of(URI.create("https://intranet.example/"), "The \"Corp\" realm")).isEqualTo("https://intranet.example (The \"Corp\" realm)");
    }

    /** No challenge Jasper remembers comes from an origin without a host, or from a scheme other than http or https. */
    @Test void anOriginWithoutAHostOrOfAnotherSchemeIsRefused() {
        for (String origin : java.util.List.of("file:///etc/hosts", "ftp://example.com/", "about:blank", "/relative", "https:///no-host"))
            assertThatThrownBy(() -> LoginName.of(URI.create(origin), "Corp")).as(origin).isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining(origin);
    }
}
