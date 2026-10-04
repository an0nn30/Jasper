package dev.jasper.app.application;

import dev.jasper.app.notifications.Delivery.Presence;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rule behind {@code JasperApplication.presence()}, as a table: a window counts once it has been activated, and a
 * resident Jasper with no window open and none being opened is idle. The last argument is whether it is idle.
 */
class JasperApplicationPresenceTest {
    @Test void noWindowOpenIsNoWindowWhateverElseIsTrue() {
        assertThat(JasperApplication.presence(false, false, false, false)).isEqualTo(Presence.NO_WINDOW);
        assertThat(JasperApplication.presence(false, true, true, false)).isEqualTo(Presence.NO_WINDOW);
        assertThat(JasperApplication.presence(false, true, false, false)).isEqualTo(Presence.NO_WINDOW);
    }

    @Test void aWindowThatWasNeverActivatedIsStillNoWindow() {
        // A failure raised while the first window opens arrives before the native activation event: its card waits.
        assertThat(JasperApplication.presence(true, false, false, false)).isEqualTo(Presence.NO_WINDOW);
        assertThat(JasperApplication.presence(true, false, true, false)).isEqualTo(Presence.NO_WINDOW);
    }

    @Test void anActivatedWindowIsFocusedOrInTheBackground() {
        assertThat(JasperApplication.presence(true, true, true, false)).isEqualTo(Presence.FOCUSED);
        assertThat(JasperApplication.presence(true, true, false, false)).isEqualTo(Presence.BACKGROUND);
    }

    @Test void aResidentJasperWithNothingOpenOrOpeningIsIdleWhateverElseIsTrue() {
        // The caller works idleness out from residency, the windows open and the window being opened; it wins over the rest.
        assertThat(JasperApplication.presence(false, false, false, true)).isEqualTo(Presence.IDLE);
        assertThat(JasperApplication.presence(false, true, true, true)).isEqualTo(Presence.IDLE);
        assertThat(JasperApplication.presence(false, true, false, true)).isEqualTo(Presence.IDLE);
        assertThat(JasperApplication.presence(false, false, true, true)).isEqualTo(Presence.IDLE);
    }
}
