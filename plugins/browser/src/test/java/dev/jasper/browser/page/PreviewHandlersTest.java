package dev.jasper.browser.page;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The preview client's rules, without Chromium: which navigations go ahead, which links the page's script did not catch,
 * and which router requests count.
 */
class PreviewHandlersTest {
    private static final String TOKEN = "0123456789abcdef0123456789abcdef", SHELL = "jasper-preview://" + TOKEN + "/";

    /** Review Focus 1: nothing navigates but the shell's own first load. */
    @Test void cancelsEveryNavigationButTheShellsFirstLoad() {
        assertThat(PreviewHandlers.navigates(SHELL, SHELL, true)).as("the shell's first load").isTrue();
        assertThat(PreviewHandlers.navigates(SHELL, SHELL, false)).as("a second load of the shell, or a reload").isFalse();
        for (String url : new String[] {"jasper-about:welcome", "https://example.com/", "http://localhost/", "file:///etc/passwd",
                "javascript:alert(1)", "data:text/html,x", "about:blank", "jasper-link:7", SHELL + "#intro", SHELL + "document/1",
                "jasper-preview://ffffffffffffffffffffffffffffffff/", "JASPER-PREVIEW://" + TOKEN + "/", ""})
            assertThat(PreviewHandlers.navigates(url, SHELL, true)).as(url).isFalse();
        assertThat(PreviewHandlers.navigates(null, SHELL, true)).isFalse();
    }

    @Test void aLinkTheScriptDidNotCatchIsItsId() {
        assertThat(PreviewHandlers.link("jasper-link:7")).isEqualTo(OptionalInt.of(7));
        assertThat(PreviewHandlers.link("jasper-link:0")).isEqualTo(OptionalInt.of(0));
        assertThat(PreviewHandlers.link("jasper-link:2147483647")).isEqualTo(OptionalInt.of(Integer.MAX_VALUE));
        for (String url : new String[] {"jasper-link:", "jasper-link:-1", "jasper-link:07", "jasper-link:7x", "jasper-link:2147483648",
                "jasper-link://7", "https://example.com/jasper-link:7", "jasper-about:7", "", null})
            assertThat(PreviewHandlers.link(url)).as(String.valueOf(url)).isEmpty();
    }

    @Test void aRequestCountsOnlyFromThePreviewsMainFrameOnItsOriginUpToFourMib() {
        assertThat(PreviewHandlers.counts(true, SHELL, TOKEN, "{\"op\":\"ready\"}")).isTrue();
        assertThat(PreviewHandlers.counts(true, SHELL + "document/1", TOKEN, "{}")).isTrue();
        assertThat(PreviewHandlers.counts(false, SHELL, TOKEN, "{}")).as("a sub-frame").isFalse();
        assertThat(PreviewHandlers.counts(true, "jasper-preview://ffffffffffffffffffffffffffffffff/", TOKEN, "{}"))
            .as("another preview's origin").isFalse();
        assertThat(PreviewHandlers.counts(true, "https://example.com/", TOKEN, "{}")).isFalse();
        assertThat(PreviewHandlers.counts(true, null, TOKEN, "{}")).isFalse();
        assertThat(PreviewHandlers.counts(true, SHELL, null, "{}")).as("no preview").isFalse();
        assertThat(PreviewHandlers.counts(true, SHELL, TOKEN, null)).isFalse();
        assertThat(PreviewHandlers.counts(true, SHELL, TOKEN, "x".repeat(PreviewHandlers.MESSAGE_LIMIT))).isTrue();
        assertThat(PreviewHandlers.counts(true, SHELL, TOKEN, "x".repeat(PreviewHandlers.MESSAGE_LIMIT + 1))).isFalse();
        assertThat(PreviewHandlers.MESSAGE_LIMIT).isEqualTo(4 * 1024 * 1024);
        assertThat(PreviewHandlers.QUERY).isEqualTo("jasperPreviewQuery");
        assertThat(PreviewHandlers.CANCEL).isEqualTo("jasperPreviewCancel");
    }
}
