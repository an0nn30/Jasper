package dev.jasper.browser.logins;

import dev.jasper.browser.page.SignIn;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plan 8 Task 5a: how Jasper judges an HTTP sign-in. A challenge from another origin than the page's top address is
 * framed (spec section 21), and only the top-level navigation's own is remembered (F5); the Vault is offered only for
 * Basic from a server, outside /dev/null, with the Vault, and from an origin with a login name (L13); and the realm,
 * server text, is cleaned before it is shown or names a login (F9). The sign-ins are made without Chromium.
 */
class ChallengeTest {
    @Test void aChallengeFromTheTopAddressOriginIsNotFramedAndFromAnotherItIs() {
        assertThat(challenge("https://a.example", "https://a.example/x").framed()).isFalse();
        assertThat(challenge("https://a.example", "https://b.example/").framed()).isTrue();
        assertThat(challenge("https://a.example/x", "http://a.example/x").framed()).as("another scheme").isTrue();
        assertThat(challenge("https://a.example:8443/", "https://a.example/").framed()).as("another port").isTrue();
        assertThat(challenge("https://a.example/", "").framed()).as("a page with no address yet").isTrue();
        assertThat(challenge("", "").framed()).as("no origin").isTrue();
    }

    @Test void basicCountsInAnyCase() {
        for (String scheme : List.of("Basic", "basic", "BASIC"))
            assertThat(challenge("https://a.example/", "https://a.example/", false, scheme, "Corp").basic()).as(scheme).isTrue();
        assertThat(challenge("https://a.example/", "https://a.example/", false, "Digest", "Corp").scheme()).isEqualTo("digest");
    }

    @Test void digestNtlmAProxyDevNullAndNoVaultEachTurnOffTheVault() {
        Challenge basic = challenge("https://a.example/", "https://a.example/");
        assertThat(basic.offersVault(false, true)).isTrue();
        assertThat(basic.remembers(false, true)).isTrue();
        for (String scheme : List.of("Digest", "NTLM", "Negotiate")) {
            Challenge other = challenge("https://a.example/", "https://a.example/", false, scheme, "Corp");
            assertThat(other.offersVault(false, true)).as(scheme).isFalse();
            assertThat(other.remembers(false, true)).as(scheme).isFalse();
        }
        Challenge proxy = challenge("https://a.example/", "https://a.example/", true, "Basic", "Corp");
        assertThat(proxy.offersVault(false, true)).as("a proxy").isFalse();
        assertThat(proxy.remembers(false, true)).as("a proxy").isFalse();
        assertThat(basic.offersVault(true, true)).as("/dev/null").isFalse();
        assertThat(basic.remembers(true, true)).as("/dev/null").isFalse();
        assertThat(basic.offersVault(false, false)).as("no Vault").isFalse();
        assertThat(basic.remembers(false, false)).as("no Vault").isFalse();
    }

    @Test void aFramedBasicChallengeOffersTheVaultButIsNotRemembered() {
        Challenge framed = challenge("https://b.example/", "https://a.example/");
        assertThat(framed.offersVault(false, true)).isTrue();
        assertThat(framed.remembers(false, true)).isFalse();
    }

    /** F5: a same-origin sub-resource's challenge is not the top-level navigation's own, so it is not remembered. */
    @Test void aSameOriginSubresourceIsNotTopLevel() {
        Challenge fetch = challenge("https://a.example/api", "https://a.example/page");
        assertThat(fetch.framed()).isFalse();
        assertThat(fetch.topLevel()).isFalse();
        assertThat(fetch.offersVault(false, true)).as("Password Manager stays").isTrue();
        assertThat(fetch.remembers(false, true)).isFalse();
    }

    @Test void theTopNavigationsOwnChallengeIsTopLevel() {
        assertThat(challenge("https://a.example/page?q=1", "https://a.example/page?q=1").topLevel()).isTrue();
        assertThat(challenge("https://a.example/page#x", "https://a.example/page").topLevel()).as("a fragment aside").isTrue();
        assertThat(challenge("https://a.example/page", "https://a.example/page#y").topLevel()).as("a fragment aside").isTrue();
        assertThat(challenge("https://a.example/page", "https://a.example/page").remembers(false, true)).isTrue();
        assertThat(challenge("https://a.example/page?q=1", "https://a.example/page?q=2").topLevel()).as("another query").isFalse();
    }

    /** F5: when Chromium names only the origin that asked, the test is that origin against the top address's. */
    @Test void anOriginOnlyOriginUrlIsTopLevelForItsOrigin() {
        for (String origin : List.of("https://a.example", "https://a.example/")) {
            assertThat(challenge(origin, "https://a.example/deep/page?q=1").topLevel()).as(origin).isTrue();
            assertThat(challenge(origin, "https://a.example/deep/page?q=1").remembers(false, true)).as(origin).isTrue();
            assertThat(challenge(origin, "https://b.example/").topLevel()).as(origin).isFalse();
        }
    }

    /**
     * F5's bank example: while the page's navigation to {@code https://bank.example/slow} is pending, the document it
     * leaves fetches {@code https://bank.example/transfer}. The fetch's challenge shares the top address's origin but
     * is not its own, so no remembered login goes out with it.
     */
    @Test void aPendingCrossOriginNavigationDoesNotMakeAFetchTopLevel() {
        Challenge fetch = challenge("https://bank.example/transfer", "https://bank.example/slow");
        assertThat(fetch.framed()).isFalse();
        assertThat(fetch.topLevel()).isFalse();
        assertThat(fetch.remembers(false, true)).isFalse();
    }

    /** F9: a right-to-left override, the server's text reversed, becomes a space. */
    @Test void aBidiControlInTheRealmBecomesASpace() {
        Challenge challenge = challenge("https://a.example/", "https://a.example/", false, "Basic", "Corp\u202Etpircs");
        assertThat(challenge.realm()).isEqualTo("Corp tpircs");
        assertThat(challenge.loginName()).isEqualTo("https://a.example (Corp tpircs)");
    }

    @Test void lineBreaksZeroWidthCharactersAndRunsOfWhiteSpaceBecomeOneSpaceStripped() {
        assertThat(challenge("https://a.example/", "https://a.example/", false, "Basic", "Corp\nSession expired").realm())
            .isEqualTo("Corp Session expired");
        assertThat(challenge("https://a.example/", "https://a.example/", false, "Basic",
            " \t Corp\u2028\u2029Intranet\u200B\u00A0 \r\n").realm()).isEqualTo("Corp Intranet");
        assertThat(challenge("https://a.example/", "https://a.example/", false, "Basic", "\u200B\u202E ").realm()).isEmpty();
    }

    /** F9: a realm longer than {@link Challenge#REALM_LIMIT} code points is cut, never inside a surrogate pair. */
    @Test void aLongRealmIsCutWithAnEllipsis() {
        String realm = challenge("https://a.example/", "https://a.example/", false, "Basic", "r".repeat(300)).realm();
        assertThat(realm).isEqualTo("r".repeat(Challenge.REALM_LIMIT - 1) + "\u2026");
        assertThat(realm.codePointCount(0, realm.length())).isEqualTo(Challenge.REALM_LIMIT);
        String emoji = new String(Character.toChars(0x1F511));
        String pairs = challenge("https://a.example/", "https://a.example/", false, "Basic", "r" + emoji.repeat(150)).realm();
        assertThat(pairs).isEqualTo("r" + emoji.repeat(Challenge.REALM_LIMIT - 2) + "\u2026");
        String exact = "r".repeat(Challenge.REALM_LIMIT);
        assertThat(challenge("https://a.example/", "https://a.example/", false, "Basic", exact).realm()).as("no longer")
            .isEqualTo(exact);
    }

    /** F9: quotes are the server's text, and stay; the dialog's own quotes stand around the cleaned realm. */
    @Test void quotesInTheRealmAreKept() {
        String realm = "Corp\u201D. Your Jasper session expired: enter your master password";
        assertThat(challenge("https://a.example/", "https://a.example/", false, "Basic", realm).realm()).isEqualTo(realm);
        assertThat(challenge("https://a.example/", "https://a.example/", false, "Basic", "The \"Corp\" realm").loginName())
            .isEqualTo("https://a.example (The \"Corp\" realm)");
    }

    /** The user's call: a proxy challenge gets the plain dialog, which names the proxy's host and port. */
    @Test void aProxysHostAndPortAreTheProxys() {
        var signIn = new SignIn("https://intranet.example/page", true, "proxy.example", 8118, "Squid", "Basic",
            "https://intranet.example/page", 0, false, (user, password) -> { }, () -> { });
        Challenge proxy = Challenge.of(signIn);
        assertThat(proxy.proxy()).isTrue();
        assertThat(proxy.host()).isEqualTo("proxy.example");
        assertThat(proxy.port()).isEqualTo(8118);
        assertThat(proxy.origin()).as("the address the page asked for").isEqualTo("https://intranet.example");
        assertThat(proxy.topLevel()).as("a proxy's challenge is no navigation's own").isFalse();
        assertThat(proxy.offersVault(false, true)).isFalse();
    }

    @Test void aServersHostAndPortAndTheLoginName() {
        var signIn = new SignIn("https://user:pw@Intranet.Example:8443/page", false, "intranet.example", 8443, "Corp", "Basic",
            "https://intranet.example:8443/", 0, false, (user, password) -> { }, () -> { });
        Challenge challenge = Challenge.of(signIn);
        assertThat(challenge.origin()).as("no user info (R46)").isEqualTo("https://intranet.example:8443");
        assertThat(challenge.host()).isEqualTo("intranet.example");
        assertThat(challenge.port()).isEqualTo(8443);
        assertThat(challenge.loginName()).isEqualTo("https://intranet.example:8443 (Corp)");
        assertThat(challenge.toString()).as("no realm in its text").doesNotContain("Corp");
    }

    /**
     * L13: Chromium accepts a host with an underscore, which {@link java.net.URI} gives no host for, so the challenge
     * has no login name: the plain dialog, with no Password Manager, no Remember and no silent fill.
     */
    @Test void anOriginWithoutALoginNameGetsThePlainDialog() {
        Challenge challenge = challenge("https://my_host.example/", "https://my_host.example/");
        assertThat(challenge.framed()).isFalse();
        assertThat(challenge.topLevel()).isTrue();
        assertThat(challenge.offersVault(false, true)).isFalse();
        assertThat(challenge.remembers(false, true)).isFalse();
        assertThatThrownBy(challenge::loginName).isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("my_host");
        assertThat(challenge("", "").offersVault(false, true)).as("no origin").isFalse();
    }

    private static Challenge challenge(String originUrl, String topAddress) {
        return challenge(originUrl, topAddress, false, "Basic", "Corp");
    }

    private static Challenge challenge(String originUrl, String topAddress, boolean proxy, String scheme, String realm) {
        return Challenge.of(new SignIn(originUrl, proxy, "a.example", 443, realm, scheme, topAddress, 0, false,
            (user, password) -> { }, () -> { }));
    }
}
