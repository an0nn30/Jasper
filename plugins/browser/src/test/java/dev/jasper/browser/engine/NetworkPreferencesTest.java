package dev.jasper.browser.engine;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plan 6 Task 11: the proxy preference Chromium's request contexts are given, and how it is set on one, with stand-in
 * contexts, as no context can be made without Chromium.
 */
class NetworkPreferencesTest {
    @Test void theProxyIsAFixedServerWhileOnAndTheSystemsSettingsWhileOff() {
        assertThat(NetworkPreferences.proxy(Optional.of("127.0.0.1:8118")))
            .isEqualTo(Map.of("mode", "fixed_servers", "server", "127.0.0.1:8118"));
        assertThat(NetworkPreferences.proxy(Optional.of("proxy.local:3128")))
            .isEqualTo(Map.of("mode", "fixed_servers", "server", "proxy.local:3128"));
        assertThat(NetworkPreferences.proxy(Optional.of("[::1]:8118"))).as("an IPv6 address in brackets, as Chromium writes one")
            .isEqualTo(Map.of("mode", "fixed_servers", "server", "[::1]:8118"));
        assertThat(NetworkPreferences.proxy(Optional.empty())).as("the plan's call: off is the system's proxy settings")
            .isEqualTo(Map.of("mode", "system"));
    }

    /** F38: a proxy set closes the context's connections, with a callback, never null, so no page keeps the old route. */
    @Test void aProxySetClosesTheContextsConnectionsWithACallback() {
        var context = new RecordingContext(1);
        Map<String, Object> proxy = NetworkPreferences.proxy(Optional.of("127.0.0.1:8118"));
        assertThat(NetworkPreferences.apply(context, proxy)).as("Chromium answered null: set").isEmpty();
        assertThat(context.getPreference("proxy")).isEqualTo(proxy);
        assertThat(context.connectionsClosed()).isEqualTo(1);
    }

    /**
     * F38: what Chromium reads back of the proxy once it is set is logged at DEBUG, for the GUI check (Task 13), and read
     * only then; the proxy server names no credentials, as Proxy Server takes none.
     */
    @Test void theProxyReadBackIsLoggedAtDebugOnly() {
        var logger = java.util.logging.Logger.getLogger(NetworkPreferences.class.getName());
        java.util.logging.Level level = logger.getLevel();
        try (var log = new dev.jasper.browser.store.CapturedLog(NetworkPreferences.class.getName())) {
            logger.setLevel(java.util.logging.Level.INFO);
            NetworkPreferences.apply(new RecordingContext(1), NetworkPreferences.proxy(Optional.of("127.0.0.1:8118")));
            assertThat(log.records()).as("not at INFO").isEmpty();
            logger.setLevel(java.util.logging.Level.FINE);
            NetworkPreferences.apply(new RecordingContext(2), NetworkPreferences.proxy(Optional.of("[::1]:8118")));
            assertThat(log.records()).singleElement().satisfies(record -> {
                assertThat(record.getLevel()).isEqualTo(java.util.logging.Level.FINE);
                assertThat(record.getMessage()).startsWith("Chromium's proxy preference reads ").contains("mode=fixed_servers", "server=[::1]:8118");
            });
        } finally {
            logger.setLevel(level);
        }
    }

    @Test void aRefusedProxyGivesChromiumsReasonAndClosesNothing() {
        var context = new RecordingContext(1).refusing("called on invalid thread");
        assertThat(NetworkPreferences.apply(context, NetworkPreferences.proxy(Optional.empty()))).contains("called on invalid thread");
        assertThat(context.hasPreference("proxy")).isFalse();
        assertThat(context.connectionsClosed()).isZero();
    }

    /** A context that throws, rather than answers, is refused with what it threw: nothing reaches Chromium's thread. */
    @Test void aContextThatThrowsGivesWhatItThrew() {
        var context = new RecordingContext(1).throwing(new IllegalStateException("broken"));
        assertThat(NetworkPreferences.apply(context, NetworkPreferences.proxy(Optional.empty()))).contains("broken");
        assertThat(context.connectionsClosed()).isZero();
    }
}
