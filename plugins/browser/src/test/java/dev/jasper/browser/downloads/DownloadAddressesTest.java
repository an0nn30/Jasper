package dev.jasper.browser.downloads;

import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The addresses downloads write and show: a download's source and referrer for its mark, http and https only, escaped
 * as no URI holds them; and the site ruling D1's notice names, which never shows a user name or password.
 */
class DownloadAddressesTest {
    @Test void aWebAddressIsTheSourceAndAnyOtherIsAboutInternet() {
        assertThat(DownloadAddresses.source("https://files.example/report.pdf")).isEqualTo(URI.create("https://files.example/report.pdf"));
        assertThat(DownloadAddresses.source("HTTP://files.example/a")).isEqualTo(URI.create("HTTP://files.example/a"));
        assertThat(DownloadAddresses.source("data:text/plain,hello")).isEqualTo(DownloadAddresses.NO_ADDRESS);
        assertThat(DownloadAddresses.source("blob:https://files.example/0f1e")).isEqualTo(DownloadAddresses.NO_ADDRESS);
        assertThat(DownloadAddresses.source("")).isEqualTo(DownloadAddresses.NO_ADDRESS);
        assertThat(DownloadAddresses.source("https://")).as("no authority").isEqualTo(DownloadAddresses.NO_ADDRESS);
    }

    @Test void charactersNoUriHoldsAreEscapedAndARegistryHostIsKept() {
        assertThat(DownloadAddresses.web("https://files.example/a|b c^d{e}.zip"))
            .contains(URI.create("https://files.example/a%7Cb%20c%5Ed%7Be%7D.zip"));
        assertThat(DownloadAddresses.web("https://my_host.example/a")).as("an underscore: Java's registry authority")
            .contains(URI.create("https://my_host.example/a"));
        assertThat(DownloadAddresses.web("https://files.example/%zz")).as("a broken escape").isEmpty();
        assertThat(DownloadAddresses.web("jasper-about:welcome")).isEqualTo(Optional.empty());
    }

    /** Review finding 3: the notice names the host, and never the address's user name or password. */
    @Test void theHostIsShownWithoutCredentialsAndANameItCannotReadIsThisSite() {
        assertThat(DownloadAddresses.host("https://example.com/page")).isEqualTo("example.com");
        assertThat(DownloadAddresses.host("https://user:pass@example.com/a|b")).isEqualTo("example.com");
        assertThat(DownloadAddresses.host("https://user:pass@my_host.example:8443/a|b")).isEqualTo("my_host.example:8443");
        assertThat(DownloadAddresses.host("https://user:pass@exa%zzmple.com/")).as("unreadable").isEqualTo("this site");
        assertThat(DownloadAddresses.host("about:blank")).isEqualTo("this site");
        assertThat(DownloadAddresses.host("")).isEqualTo("this site");
        assertThat(DownloadAddresses.host("file:///Users/someone/page.html")).isEqualTo("this site");
    }
}
