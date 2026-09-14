package dev.jasper.app;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BuddyBubbleContentTest {
    @Test void blankTitlesAreRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BuddyBubbleContent("", null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new BuddyBubbleContent("   ", null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> BuddyBubbleContent.menu(" "));
    }

    @Test void titlesAreRequired() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BuddyBubbleContent(null, "detail", null));
    }

    @Test void theMenuVariantIsATitleOnly() {
        BuddyBubbleContent content = BuddyBubbleContent.menu("Hide Jasper");
        assertThat(content.title()).isEqualTo("Hide Jasper");
        assertThat(content.detail()).isNull();
        assertThat(content.glyph()).isNull();
        assertThat(content.style()).isEqualTo(BuddyBubbleContent.Style.MENU);
    }

    @Test void messagesUseTheBoldMessageStyle() {
        assertThat(BuddyBubbleContent.message("Blocked", "Waiting", null).style()).isEqualTo(BuddyBubbleContent.Style.MESSAGE);
        assertThat(new BuddyBubbleContent("Blocked", null, null).style()).isEqualTo(BuddyBubbleContent.Style.MESSAGE);
        assertThatIllegalArgumentException().isThrownBy(() -> new BuddyBubbleContent("Blocked", null, null, null));
    }

    @Test void detailsAndGlyphsAreKept() {
        BuddyBubbleContent content = new BuddyBubbleContent("Blocked", "Waiting for approval", null);
        assertThat(content.detail()).isEqualTo("Waiting for approval");
    }
}
