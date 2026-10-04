package dev.jasper.app.application;

import dev.jasper.app.notifications.Delivery.Presence;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** The rule behind {@code JasperApplication.presence()}, as a table: a window counts once it has been activated. */
class JasperApplicationPresenceTest {
    @Test void noWindowOpenIsNoWindowWhateverElseIsTrue() {
        assertThat(JasperApplication.presence(false, false, false)).isEqualTo(Presence.NO_WINDOW);
        assertThat(JasperApplication.presence(false, true, true)).isEqualTo(Presence.NO_WINDOW);
        assertThat(JasperApplication.presence(false, true, false)).isEqualTo(Presence.NO_WINDOW);
    }

    @Test void aWindowThatWasNeverActivatedIsStillNoWindow() {
        // A failure raised while the first window opens arrives before the native activation event: its card waits.
        assertThat(JasperApplication.presence(true, false, false)).isEqualTo(Presence.NO_WINDOW);
        assertThat(JasperApplication.presence(true, false, true)).isEqualTo(Presence.NO_WINDOW);
    }

    @Test void anActivatedWindowIsFocusedOrInTheBackground() {
        assertThat(JasperApplication.presence(true, true, true)).isEqualTo(Presence.FOCUSED);
        assertThat(JasperApplication.presence(true, true, false)).isEqualTo(Presence.BACKGROUND);
    }
}
