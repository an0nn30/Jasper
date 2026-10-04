package dev.jasper.app.notifications;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

class DeliveryTest {
    @ParameterizedTest(name = "{0} while {1}")
    @CsvSource({
        "OFF, FOCUSED, false, false, false, false", "OFF, BACKGROUND, false, false, false, false",
        "OFF, NO_WINDOW, false, false, false, false", "OFF, IDLE, false, false, false, false",
        "HISTORY, FOCUSED, true, false, false, false", "HISTORY, BACKGROUND, true, false, false, false",
        "HISTORY, NO_WINDOW, true, false, false, false", "HISTORY, IDLE, true, false, false, false",
        "CARD, FOCUSED, true, true, false, false", "CARD, BACKGROUND, true, false, false, true",
        "CARD, NO_WINDOW, true, false, true, false", "CARD, IDLE, true, false, false, true",
        "STICKY, FOCUSED, true, true, false, false", "STICKY, BACKGROUND, true, false, true, true",
        "STICKY, NO_WINDOW, true, false, true, false", "STICKY, IDLE, true, false, true, true"})
    void everyModeAndPresence(DisplayMode mode, Delivery.Presence presence, boolean record, boolean card, boolean pending,
                              boolean system) {
        assertThat(Delivery.route(mode, presence)).isEqualTo(new Delivery.Outcome(record, card, pending, system));
    }
}
