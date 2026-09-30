package dev.jasper.app.themestore;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SafeHtmlTest {
    private static final List<String> FORBIDDEN = List.of("url(", "src", "background", "style", "<img", "<frame", "<input");

    private static void assertInert(String html) {
        String safe = MarketplaceTab.safeHtml(html);
        assertThat(safe.toLowerCase(java.util.Locale.ROOT)).doesNotContain(FORBIDDEN);
    }

    @Test void styleAttributesBackgroundAttributesFramesInputsAndUnclosedStylesAreInert() {
        assertInert("<p style=\"background-image:url(http://x/a.png)\">hi</p>");
        assertInert("<body background=\"http://x/a.png\"><table background=x><tr><td background=y>t</td></tr></table></body>");
        assertInert("<frameset><frame src=\"http://x/a\"></frameset>");
        assertInert("<input type=image src=\"http://x/a.png\">");
        assertInert("<p>text</p><style>@import url(http://x/a.css)");
        assertInert("<p>text</p><script>var a = 1;");
        assertInert("<!-- <img src=x> --><p>a</p><img src='x.png'>");
        assertInert("<p>a</p><img src=x.png");
        assertInert("<IMG SRC=x.png><Img\nsrc=x>");
    }

    @Test void formattingAndLinksSurviveWithoutOtherAttributes() {
        assertThat(MarketplaceTab.safeHtml("<a href=\"https://x\" onclick=\"evil()\">link</a> <B class=\"c\">bold</B> <blink>t</blink>"))
            .isEqualTo("<html><body><a href=\"https://x\">link</a> <b>bold</b> t</body></html>");
        assertThat(MarketplaceTab.safeHtml("<a href='/plugin/1?a=1&amp;b=2'>x</a>")).contains("href=\"/plugin/1?a=1&amp;b=2\"");
    }

    @Test void quotesAndOtherEntitiesInAnHrefAreEscapedOnce() {
        assertThat(MarketplaceTab.safeHtml("<a href=\"https://x/?q=&quot;a&quot;&amp;b=&lt;c&gt;\">x</a>"))
            .contains("href=\"https://x/?q=&quot;a&quot;&amp;b=&lt;c&gt;\"");
        assertThat(MarketplaceTab.safeHtml("<a href='https://x/?q=\"a\"'>x</a>")).contains("href=\"https://x/?q=&quot;a&quot;\"");
        assertThat(MarketplaceTab.safeHtml("<a href=\"https://x/&amp;amp;\">x</a>")).contains("href=\"https://x/&amp;amp;\"");
    }

    @Test void onlyTheFirst256KiBOfADescriptionIsRead() {
        String html = "<p>" + "a".repeat(MarketplaceTab.MAX_DESCRIPTION) + "TAIL</p>";
        String safe = MarketplaceTab.safeHtml(html);
        assertThat(safe).doesNotContain("TAIL");
        assertThat(safe.length()).isLessThanOrEqualTo(MarketplaceTab.MAX_DESCRIPTION + "<html><body></body></html>".length());
        String split = "a".repeat(MarketplaceTab.MAX_DESCRIPTION - 1) + "\uD83D\uDE00";
        assertThat(MarketplaceTab.safeHtml(split)).doesNotContain("\uD83D");
    }
}
