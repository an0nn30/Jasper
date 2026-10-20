package dev.jasper.app.notifications;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DisplayModeTest {
    @Test void eachModeHasTheTokenConfigWritesAndParsesBack() {
        assertThat(DisplayMode.CARD.token()).isEqualTo("card");
        assertThat(DisplayMode.STICKY.token()).isEqualTo("sticky");
        assertThat(DisplayMode.HISTORY.token()).isEqualTo("history");
        assertThat(DisplayMode.OFF.token()).isEqualTo("off");
        for (DisplayMode mode : DisplayMode.values()) assertThat(DisplayMode.parse(mode.token())).contains(mode);
    }

    @Test void anythingElseIsNoMode() {
        assertThat(DisplayMode.parse("Card")).isEmpty();
        assertThat(DisplayMode.parse("CARD")).isEmpty();
        assertThat(DisplayMode.parse("")).isEmpty();
        assertThat(DisplayMode.parse(null)).isEmpty();
    }
}
