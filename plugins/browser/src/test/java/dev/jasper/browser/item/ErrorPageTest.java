package dev.jasper.browser.item;

import dev.jasper.browser.engine.AboutScheme;
import dev.jasper.browser.page.LoadFailure;
import dev.jasper.browser.page.ServerCertificate;
import dev.jasper.sdk.ui.WorkspacePalette;
import java.awt.Color;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.cef.handler.CefLoadHandler.ErrorCode;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 14: {@code jasper-about:error}, the page a failed load shows, built without Chromium. */
class ErrorPageTest {
    private static final String SHA256 = "d53a1b43dbe103e328aa1f2e50c9ee35d35d116b19c8912f86aeff71eda21e61";
    private static final WorkspacePalette PALETTE = new WorkspacePalette(new Color(0x10, 0x20, 0x30), new Color(0x11, 0x21, 0x31),
        Color.BLACK, new Color(0xee, 0xee, 0xee), new Color(0x99, 0x99, 0x99), new Color(0x44, 0x44, 0x44, 0x80), Color.BLUE,
        Color.WHITE, Color.GRAY, new Color(0x33, 0x66, 0xff), Color.GREEN, new Color(0xff, 0xaa, 0x00), new Color(0xdd, 0x22, 0x11));

    @Test void eachErrorHasTheSpecsTitle() {
        Map<String, String> titles = Map.ofEntries(
            Map.entry("ERR_INTERNET_DISCONNECTED", "No Internet Connection"),
            Map.entry("ERR_NAME_NOT_RESOLVED", "Server Not Found"), Map.entry("ERR_NAME_RESOLUTION_FAILED", "Server Not Found"),
            Map.entry("ERR_TIMED_OUT", "The Connection Timed Out"), Map.entry("ERR_CONNECTION_TIMED_OUT", "The Connection Timed Out"),
            Map.entry("ERR_CONNECTION_REFUSED", "Can’t Connect to Server"), Map.entry("ERR_CONNECTION_FAILED", "Can’t Connect to Server"),
            Map.entry("ERR_ADDRESS_UNREACHABLE", "Can’t Connect to Server"),
            Map.entry("ERR_CONNECTION_RESET", "Network Connection Lost"), Map.entry("ERR_CONNECTION_CLOSED", "Network Connection Lost"),
            Map.entry("ERR_NETWORK_CHANGED", "Network Connection Lost"),
            Map.entry("ERR_TOO_MANY_REDIRECTS", "Too Many Redirects"),
            Map.entry("ERR_FILE_NOT_FOUND", "File Not Found"),
            Map.entry("ERR_SSL_PROTOCOL_ERROR", "Secure Connection Failed"), Map.entry("ERR_CERT_AUTHORITY_INVALID", "Secure Connection Failed"),
            Map.entry("ERR_CERT_DATE_INVALID", "Secure Connection Failed"),
            Map.entry("ERR_BLOCKED_BY_CLIENT", "Page Unavailable"), Map.entry("ERR_BLOCKED_BY_RESPONSE", "Page Unavailable"),
            Map.entry("ERR_ACCESS_DENIED", "Page Unavailable"),
            Map.entry("ERR_UNKNOWN_URL_SCHEME", "Page Can’t Be Loaded"), Map.entry("ERR_FAILED", "Page Can’t Be Loaded"),
            Map.entry("", "Page Can’t Be Loaded"), Map.entry("NOT_AN_ERROR", "Page Can’t Be Loaded"));
        titles.forEach((code, title) -> assertThat(ErrorPage.title(code)).as(code).contains(title));
    }

    /** Every certificate and secure connection error Chromium names is "Secure Connection Failed", and every error has a title. */
    @Test void everyErrorChromiumNamesHasATitleButAnAbortedLoad() {
        for (ErrorCode code : ErrorCode.values()) {
            String name = code.name();
            if (name.equals("ERR_ABORTED")) continue;
            assertThat(ErrorPage.title(name)).as(name).isPresent();
            if (name.startsWith("ERR_SSL_") || name.startsWith("ERR_CERT_"))
                assertThat(ErrorPage.title(name)).as(name).contains("Secure Connection Failed");
        }
    }

    /** A navigation a download or the user cancelled shows nothing. */
    @Test void anAbortedLoadShowsNoPage() {
        assertThat(ErrorPage.title("ERR_ABORTED")).isEmpty();
    }

    @Test void thePageShowsTheTitleTheMessageTryAgainAndTheHint() {
        String html = render("https://unknown.example/path", "ERR_NAME_NOT_RESOLVED", Optional.empty());
        assertThat(html).contains("<title>Server Not Found</title>", "<h1>Server Not Found</h1>",
            "Jasper can’t find the server “unknown.example”.", "https://unknown.example/path",
            "<form action=\"jasper-about:error-retry\" method=\"get\">",
            "<input type=\"hidden\" name=\"token\" value=\"" + token('a') + "\">", "<button type=\"submit\" autofocus>Try Again</button>",
            "Check your internet connection and try reloading the page.", "ERR_NAME_NOT_RESOLVED");
        assertThat(html).doesNotContain("Continue to this site", "can’t verify the identity");
    }

    @Test void thePageIsInTheWorkspaceColoursAndRunsNoScript() {
        String html = render("https://unknown.example/", "ERR_NAME_NOT_RESOLVED", Optional.empty());
        assertThat(html).contains("<meta charset=\"utf-8\">", "<meta name=\"color-scheme\" content=\"dark\">",
            "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; style-src 'unsafe-inline'\">",
            "--background: #102030;", "--card: #112131;", "--text: #eeeeee;", "--muted: #999999;", "--border: rgba(68, 68, 68, 0.502);",
            "--accent: #3366ff;", "--warning: #ffaa00;", "--error: #dd2211;");
        assertThat(html).doesNotContain("<script", "transition", "animation", "src=", "href=\"http");
        assertThat(ErrorPage.render(shown("https://unknown.example/", "ERR_NAME_NOT_RESOLVED", Optional.empty()), PALETTE, false))
            .contains("content=\"light\"");
    }

    @Test void theAddressAndTheMessageAreEscaped() {
        String html = render("https://example.com/<script>alert(1)</script>?a=\"b\"&c='d'", "ERR_CONNECTION_REFUSED", Optional.empty());
        assertThat(html).doesNotContain("<script>", "alert(1)</script>", "\"b\"", "'d'")
            .contains("https://example.com/&lt;script&gt;alert(1)&lt;/script&gt;?a=&quot;b&quot;&amp;c=&#39;d&#39;");
        String file = render("file:///tmp/<b>notes</b>.txt", "ERR_FILE_NOT_FOUND", Optional.empty());
        assertThat(file).as("a message that names the address").doesNotContain("<b>")
            .contains("Jasper can’t find the file “file:///tmp/&lt;b&gt;notes&lt;/b&gt;.txt”.");
        String token = ErrorPage.render(new ErrorPage.Shown("https://example.com/", "ERR_FAILED", "\"><script>", List.of(), false,
            Optional.of("\"><img>")), PALETTE, true);
        assertThat(token).doesNotContain("\"><script>", "\"><img>");
        String code = render("https://example.com/", "<i>ERR</i>", Optional.empty());
        assertThat(code).as("a code that is none Chromium writes is not shown").doesNotContain("<i>", "&lt;i&gt;");
    }

    /** 4a's R46: an error page shows its address without a user name or password. */
    @Test void theAddressIsShownAndCarriedWithoutItsUserInfo() {
        var failure = ErrorPage.Failure.of(new LoadFailure("https://ada:secret@example.com/docs", "ERR_CONNECTION_REFUSED", Optional.empty()));
        assertThat(failure.url()).as("kept for Try Again").isEqualTo("https://ada:secret@example.com/docs");
        assertThat(failure.address().toString()).doesNotContain("ada", "secret");
        String html = ErrorPage.html(failure.address(), PALETTE, true);
        assertThat(html).contains("https://example.com/docs").doesNotContain("ada", "secret");
        String forged = render("https://ada:secret@example.com/", "ERR_FAILED", Optional.empty());
        assertThat(forged).as("an address another page wrote").doesNotContain("ada", "secret");
    }

    @Test void aCertificateErrorSaysWhyAndShowsTheChainWithContinueBehindDetails() {
        String html = ErrorPage.render(new ErrorPage.Shown("https://self-signed.badssl.com/", "ERR_CERT_AUTHORITY_INVALID", token('a'),
            List.of("CN=*.badssl.com,O=<BadSSL>", "CN=Root"), true, Optional.of(token('b'))), PALETTE, true);
        assertThat(html).contains("<h1>Secure Connection Failed</h1>", "Jasper can’t verify the identity of the website “self-signed.badssl.com”.",
            "The certificate for this website is self-signed.", "<li>CN=*.badssl.com,O=&lt;BadSSL&gt;</li>", "<li>CN=Root</li>",
            "<details>", "<summary>Details</summary>",
            "<a class=\"unsafe\" href=\"jasper-about:error-continue?token=" + token('b') + "\">Continue to this site (unsafe)</a>");
        assertThat(html.indexOf("Continue to this site")).as("behind Details").isGreaterThan(html.indexOf("<details>"));
        assertThat(html).doesNotContain("<BadSSL>");
    }

    @Test void eachCertificateErrorHasItsReason() {
        assertThat(ErrorPage.reason("ERR_CERT_AUTHORITY_INVALID", true)).contains("The certificate for this website is self-signed.");
        assertThat(ErrorPage.reason("ERR_CERT_AUTHORITY_INVALID", false))
            .contains("The certificate for this website was issued by an authority this computer doesn’t trust.");
        assertThat(ErrorPage.reason("ERR_CERT_DATE_INVALID", false)).contains("The certificate for this website has expired or isn’t valid yet.");
        assertThat(ErrorPage.reason("ERR_CERT_COMMON_NAME_INVALID", false)).contains("The certificate for this website is for a different website.");
        assertThat(ErrorPage.reason("ERR_CERT_REVOKED", false)).contains("The certificate for this website has been revoked.");
        assertThat(ErrorPage.reason("ERR_CERT_WEAK_KEY", false)).contains("The certificate for this website isn’t valid.");
        assertThat(ErrorPage.reason("ERR_SSL_PROTOCOL_ERROR", false)).as("no certificate's fault").isEmpty();
        assertThat(ErrorPage.reason("ERR_NAME_NOT_RESOLVED", false)).isEmpty();
    }

    /** A certificate error Chromium gave no certificate for, such as one it never asked about, has no way past it. */
    @Test void aCertificateErrorWithoutACertificateSaysWhyAndHasNoContinue() {
        String html = render("https://expired.badssl.com/", "ERR_CERT_DATE_INVALID", Optional.empty());
        assertThat(html).contains("Jasper can’t verify the identity of the website “expired.badssl.com”.",
            "The certificate for this website has expired or isn’t valid yet.");
        assertThat(html).doesNotContain("<details>", "Continue to this site", "<ol");
        String protocol = render("https://tls-v1-0.badssl.com/", "ERR_SSL_VERSION_OR_CIPHER_MISMATCH", Optional.empty());
        assertThat(protocol).contains("<h1>Secure Connection Failed</h1>").doesNotContain("verify the identity", "Continue to this site");
    }

    @Test void aFailureGetsFreshTokensAndOnlyACertificateErrorWithItsCertificateCanBeContinued() {
        var certificate = new ServerCertificate("self-signed.badssl.com", SHA256, List.of("CN=*.badssl.com"), true);
        var one = ErrorPage.Failure.of(new LoadFailure("https://self-signed.badssl.com/", "ERR_CERT_AUTHORITY_INVALID", Optional.of(certificate)));
        var two = ErrorPage.Failure.of(new LoadFailure("https://self-signed.badssl.com/", "ERR_CERT_AUTHORITY_INVALID", Optional.of(certificate)));
        assertThat(one.token()).matches("[0-9a-f]{32}").isNotEqualTo(two.token());
        assertThat(one.proceed()).hasValueSatisfying(proceed -> assertThat(proceed).matches("[0-9a-f]{32}").isNotEqualTo(one.token()));
        assertThat(one.spent().proceed()).isEmpty();
        assertThat(one.spent().token()).isEqualTo(one.token());
        assertThat(ErrorPage.Failure.of(new LoadFailure("https://example.com/", "ERR_CONNECTION_RESET", Optional.of(certificate))).proceed())
            .as("not a certificate error").isEmpty();
        assertThat(ErrorPage.Failure.of(new LoadFailure("https://expired.badssl.com/", "ERR_CERT_DATE_INVALID", Optional.empty())).proceed())
            .as("no certificate").isEmpty();
    }

    @Test void theAddressCarriesWhatThePageShowsAndItsToken() {
        var certificate = new ServerCertificate("self-signed.badssl.com", SHA256, List.of("CN=*.badssl.com", "CN=Root"), true);
        var failure = ErrorPage.Failure.of(new LoadFailure("https://self-signed.badssl.com/?q=a&b", "ERR_CERT_AUTHORITY_INVALID",
            Optional.of(certificate)));
        URI address = failure.address();
        assertThat(address.toString()).startsWith("jasper-about:error?token=" + failure.token() + "&");
        assertThat(AboutScheme.page(address.toString())).contains(ErrorPage.NAME);
        assertThat(ErrorPage.Shown.of(address.toString())).isEqualTo(failure.shown());
        assertThat(failure.shown()).isEqualTo(new ErrorPage.Shown("https://self-signed.badssl.com/?q=a&b", "ERR_CERT_AUTHORITY_INVALID",
            failure.token(), List.of("CN=*.badssl.com", "CN=Root"), true, failure.proceed()));
        assertThat(address.toString()).as("the fingerprint stays in Java").doesNotContain(SHA256);
        assertThat(ErrorPage.html(address, PALETTE, true)).isEqualTo(ErrorPage.render(failure.shown(), PALETTE, true));
        assertThat(ErrorPage.token(address.toString())).contains(failure.token());
        assertThat(ErrorPage.token("jasper-about://error/?token=" + failure.token() + "&code=ERR_FAILED")).as("as Chromium writes it")
            .contains(failure.token());
    }

    @Test void onlyAnErrorPagesAddressHasAToken() {
        assertThat(ErrorPage.token("jasper-about://welcome/?token=" + token('a'))).isEmpty();
        assertThat(ErrorPage.token("https://error/?token=" + token('a'))).isEmpty();
        assertThat(ErrorPage.token("jasper-about://error/?token=short")).isEmpty();
        assertThat(ErrorPage.token("jasper-about://error/?code=ERR_FAILED")).isEmpty();
        assertThat(ErrorPage.token("")).isEmpty();
    }

    @Test void theAddressKeepsWithinItsBounds() {
        String longUrl = "https://example.com/" + "a".repeat(ErrorPage.MAX_URL * 2);
        var subjects = new java.util.ArrayList<String>();
        for (int at = 0; at < ErrorPage.MAX_SUBJECTS + 3; at++) subjects.add("CN=" + "b".repeat(ErrorPage.MAX_SUBJECT * 2));
        var certificate = new ServerCertificate("example.com", SHA256, subjects, false);
        var failure = ErrorPage.Failure.of(new LoadFailure(longUrl, "ERR_CERT_AUTHORITY_INVALID", Optional.of(certificate)));
        assertThat(failure.url()).as("kept whole for Try Again").isEqualTo(longUrl);
        ErrorPage.Shown shown = failure.shown();
        assertThat(shown.url()).hasSize(ErrorPage.MAX_URL).endsWith("…");
        assertThat(shown.subjects()).hasSize(ErrorPage.MAX_SUBJECTS).allSatisfy(subject -> assertThat(subject).hasSize(ErrorPage.MAX_SUBJECT));
        assertThat(ErrorPage.Shown.of(failure.address().toString())).isEqualTo(shown);
    }

    /** An address another page wrote, or a forged one, shows what it says, escaped; its buttons act only as the item allows. */
    @Test void anyErrorAddressShowsAPage() {
        assertThat(ErrorPage.html(URI.create("jasper-about://error/"), PALETTE, false)).contains("<h1>Page Can’t Be Loaded</h1>", "Try Again");
        assertThat(ErrorPage.html(URI.create("jasper-about://error/?code=ERR_ABORTED&url=https%3A%2F%2Fexample.com%2F"), PALETTE, false))
            .contains("<h1>Page Can’t Be Loaded</h1>", "https://example.com/");
    }

    private static String render(String url, String code, Optional<String> proceed) {
        return ErrorPage.render(shown(url, code, proceed), PALETTE, true);
    }

    private static ErrorPage.Shown shown(String url, String code, Optional<String> proceed) {
        return new ErrorPage.Shown(url, code, token('a'), List.of(), false, proceed);
    }

    private static String token(char digit) {
        return String.valueOf(digit).repeat(32);
    }
}
