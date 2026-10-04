package dev.jasper.app.notifications;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

class DeliveryTest {
    @ParameterizedTest(name = "{0} while {1}")
    @CsvSource({
        "OFF, FOCUSED, false, false, false", "OFF, BACKGROUND, false, false, false", "OFF, NO_WINDOW, false, false, false",
        "HISTORY, FOCUSED, true, false, false", "HISTORY, BACKGROUND, true, false, false", "HISTORY, NO_WINDOW, true, false, false",
        "CARD, FOCUSED, true, true, false", "CARD, BACKGROUND, true, false, false", "CARD, NO_WINDOW, true, false, true",
        "STICKY, FOCUSED, true, true, false", "STICKY, BACKGROUND, true, false, true", "STICKY, NO_WINDOW, true, false, true"})
    void everyModeAndPresence(DisplayMode mode, Delivery.Presence presence, boolean record, boolean card, boolean pending) {
        assertThat(Delivery.route(mode, presence)).isEqualTo(new Delivery.Outcome(record, card, pending));
    }
}
