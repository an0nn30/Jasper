package dev.jasper.app.application;

import dev.jasper.app.notifications.Delivery.Presence;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rule behind {@code JasperApplication.presence()}, as a table: a window counts once it has been activated, and no
 * window at all is a resident Jasper with every window closed once start-up has settled.
 */
class JasperApplicationPresenceTest {
    @Test void beforeStartUpSettlesNoWindowIsStillOpening() {
        // Config and plugin problems are posted before the first window exists: their cards wait for it.
        assertThat(JasperApplication.presence(false, false, false, false)).isEqualTo(Presence.OPENING);
        assertThat(JasperApplication.presence(false, false, true, false)).isEqualTo(Presence.OPENING);
    }

    @Test void onceSettledNoWindowOpenIsNoWindowWhateverElseIsTrue() {
        assertThat(JasperApplication.presence(false, false, false, true)).isEqualTo(Presence.NO_WINDOW);
        assertThat(JasperApplication.presence(false, true, true, true)).isEqualTo(Presence.NO_WINDOW);
        assertThat(JasperApplication.presence(false, true, false, true)).isEqualTo(Presence.NO_WINDOW);
    }

    @Test void aWindowThatWasNeverActivatedIsStillOpening() {
        // A failure raised while a window opens arrives before the native activation event: its card waits.
        assertThat(JasperApplication.presence(true, false, false, true)).isEqualTo(Presence.OPENING);
        assertThat(JasperApplication.presence(true, false, true, false)).isEqualTo(Presence.OPENING);
    }

    @Test void anActivatedWindowIsFocusedOrInTheBackground() {
        assertThat(JasperApplication.presence(true, true, true, true)).isEqualTo(Presence.FOCUSED);
        assertThat(JasperApplication.presence(true, true, false, true)).isEqualTo(Presence.BACKGROUND);
    }
}
