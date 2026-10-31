package dev.jasper.browser.page;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 4a's R36: which addresses a page's links open, and which a page may open with Jasper's rights. The right-click menu's
 * Open Link and Open Image, the DevTools' links and the links Chromium would open elsewhere ({@code onOpenURLFromTab},
 * plan 4b's P48) all ask {@link LinkSchemes#opensFrom}.
 */
class LinkSchemesTest {
    @Test void onlyTheAddressFieldsSchemesOpen() {
        for (String url : List.of("http://example.com", "HTTPS://EXAMPLE.COM/a?b#c", "file:///tmp/a.html", "about:blank", "about:version",
                "jasper-about:welcome", "Jasper-About:settings"))
            assertThat(LinkSchemes.opens(url)).as(url).isTrue();
        for (String url : List.of("mailto:someone@example.com", "javascript:alert(1)", "data:text/html,hi", "blob:https://example.com/0f",
                "chrome://settings", "ftp://example.com", "", "example.com", ":nothing"))
            assertThat(LinkSchemes.opens(url)).as(url).isFalse();
        assertThat(LinkSchemes.opens(null)).isFalse();
    }

    @Test void webAddressesAndAboutBlankOpenFromAnyPage() {
        for (String sender : List.of("https://example.com/", "http://example.com/", "file:///tmp/a.html", "jasper-about:welcome",
                "about:blank", "", "not an address")) {
            for (String url : List.of("https://example.org/", "HTTP://example.org/", "about:blank", "ABOUT:BLANK#top", "about:blank?x"))
                assertThat(LinkSchemes.opensFrom(sender, url)).as(sender + " -> " + url).isTrue();
        }
        assertThat(LinkSchemes.opensFrom(null, "https://example.org/")).as("no sender").isTrue();
    }

    @Test void localAddressesOpenOnlyFromAPageOfTheirOwnScheme() {
        assertThat(LinkSchemes.opensFrom("file:///tmp/a.html", "file:///etc/hosts")).isTrue();
        assertThat(LinkSchemes.opensFrom("FILE:///tmp/a.html", "file:///etc/hosts")).as("any case").isTrue();
        assertThat(LinkSchemes.opensFrom("jasper-about:welcome", "jasper-about:settings")).isTrue();
        assertThat(LinkSchemes.opensFrom("jasper-about://source/?url=x", "JASPER-ABOUT:settings")).isTrue();
        for (String sender : List.of("https://example.com/", "http://example.com/", "about:blank", "", "data:text/html,hi")) {
            assertThat(LinkSchemes.opensFrom(sender, "file:///etc/hosts")).as(sender).isFalse();
            assertThat(LinkSchemes.opensFrom(sender, "jasper-about:settings")).as(sender).isFalse();
        }
        assertThat(LinkSchemes.opensFrom("file:///tmp/a.html", "jasper-about:settings")).isFalse();
        assertThat(LinkSchemes.opensFrom("jasper-about:welcome", "file:///etc/hosts")).isFalse();
        assertThat(LinkSchemes.opensFrom(null, "file:///etc/hosts")).as("no sender").isFalse();
    }

    @Test void nothingElseOpensFromAnyPage() {
        for (String sender : List.of("https://example.com/", "file:///tmp/a.html", "jasper-about:welcome"))
            for (String url : List.of("about:version", "about:settings", "chrome://settings", "javascript:alert(1)", "data:text/html,hi",
                    "mailto:someone@example.com", "", "example.com"))
                assertThat(LinkSchemes.opensFrom(sender, url)).as(sender + " -> " + url).isFalse();
        assertThat(LinkSchemes.opensFrom("https://example.com/", null)).isFalse();
    }
}
