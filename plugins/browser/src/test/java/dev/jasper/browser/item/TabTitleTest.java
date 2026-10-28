package dev.jasper.browser.item;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** A browser item's tab title, each branch of spec section 5's rule. */
class TabTitleTest {
    @Test void thePageTitleTrimmedComesFirst() {
        assertThat(TabTitle.of("  Example Domain \n", Optional.of("example.com"), "Before", true)).isEqualTo("Example Domain");
        assertThat(TabTitle.of("Example Domain", Optional.empty(), "", false)).isEqualTo("Example Domain");
    }

    @Test void aBlankTitleWhileLoadingKeepsThePreviousName() {
        assertThat(TabTitle.of("   ", Optional.of("example.org"), "Example Domain", true)).isEqualTo("Example Domain");
        assertThat(TabTitle.of("", Optional.empty(), "Example Domain", true)).isEqualTo("Example Domain");
    }

    @Test void whileLoadingWithNoPreviousNameTheHostShows() {
        assertThat(TabTitle.of("", Optional.of("example.org"), "  ", true)).isEqualTo("example.org");
    }

    @Test void onceLoadedABlankTitleShowsTheHost() {
        assertThat(TabTitle.of("", Optional.of("example.org"), "Example Domain", false)).isEqualTo("example.org");
    }

    @Test void withoutATitleOrAHostItIsWebBrowser() {
        assertThat(TabTitle.of("", Optional.empty(), "Example Domain", false)).isEqualTo("Web Browser");
        assertThat(TabTitle.of(null, Optional.of("  "), "", true)).isEqualTo("Web Browser");
        assertThat(TabTitle.of("", Optional.empty(), "", true)).isEqualTo("Web Browser");
    }
}
