package dev.jasper.browser.engine;

import java.awt.EventQueue;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.cef.browser.CefRequestContext;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    /**
     * Ruling V46: the proxy given reaches Chromium's global context, once it is known, and every context still kept,
     * never one disposed, as its native object may be gone, nor a browser's own object over a kept one.
     */
    @Test void theProxyIsGivenToTheGlobalContextAndEveryContextStillKept() {
        var network = new NetworkPreferences();
        RecordingContext open = new RecordingContext(1), closed = new RecordingContext(2), global = new RecordingContext(3);
        var kept = new KeptContext(open, CefRequestContext::dispose, () -> false);
        var gone = new KeptContext(closed, CefRequestContext::dispose, () -> false);
        gone.release();
        assertThat(closed.disposals()).isEqualTo(1);
        CefRequestContext browser = kept.handOut();
        network.remember(Optional.of("[::1]:8118"));
        network.give(List.of(kept, gone));
        var proxy = Map.of("mode", "fixed_servers", "server", "[::1]:8118");
        assertThat(open.getPreference("proxy")).isEqualTo(proxy);
        assertThat(global.hasPreference("proxy")).as("not known yet").isFalse();
        network.globalContext(global);
        network.give(List.of(kept, gone));
        assertThat(global.getPreference("proxy")).isEqualTo(proxy);
        assertThat(open.connectionsClosed()).isEqualTo(2);
        assertThat(closed.hasPreference("proxy")).as("disposed: never touched").isFalse();
        assertThat(closed.connectionsClosed()).isZero();
        assertThat(browser.getNativeRef("CefRequestContext")).as("a browser's own object is left as it is").isEqualTo(1);
        browser.dispose();
    }

    /** Each giving tells its first refusal, once, on the UI thread, with Chromium's reason. */
    @Test void aRefusalIsToldOnceForEachGivingOnTheUiThread() throws Exception {
        var network = new NetworkPreferences();
        var toldOf = new CopyOnWriteArrayList<String>();
        network.onRefused(reason -> toldOf.add(reason + (EventQueue.isDispatchThread() ? " on the UI thread" : "")));
        network.globalContext(new RecordingContext(1).refusing("called on invalid thread"));
        var kept = new KeptContext(new RecordingContext(2).refusing("no request context"), CefRequestContext::dispose, () -> false);
        network.give(List.of(kept));
        network.give(List.of(kept));
        EventQueue.invokeAndWait(() -> { });
        assertThat(toldOf).containsExactly("called on invalid thread on the UI thread", "called on invalid thread on the UI thread");
    }

    /**
     * Ruling F4: a context just made is given the proxy before anything is made over it; while the proxy is on, one that
     * refuses it is disposed and fails, and while it is off, one that refuses it is the system's, as asked, and is given
     * all the same. Each refusal is told.
     */
    @Test void aContextJustMadeIsGivenTheProxyAndWhileOnOneThatRefusesItIsDisposedAndFails() throws Exception {
        var network = new NetworkPreferences();
        var toldOf = new CopyOnWriteArrayList<String>();
        network.onRefused(toldOf::add);
        var disposed = new CopyOnWriteArrayList<CefRequestContext>();
        network.remember(Optional.of("127.0.0.1:8118"));
        var made = new RecordingContext(1);
        assertThat(network.given(made, disposed::add)).isSameAs(made);
        assertThat(made.getPreference("proxy")).isEqualTo(Map.of("mode", "fixed_servers", "server", "127.0.0.1:8118"));
        var refusing = new RecordingContext(2).refusing("no request context");
        assertThatThrownBy(() -> network.given(refusing, disposed::add)).isInstanceOf(IllegalStateException.class)
            .hasMessage(Chromium.PROXY_REFUSED + ": no request context");
        assertThat(disposed).containsExactly(refusing);
        network.remember(Optional.empty());
        var direct = new RecordingContext(3).refusing("no value to set");
        assertThat(network.given(direct, disposed::add)).as("off: the system's, as asked").isSameAs(direct);
        assertThat(disposed).containsExactly(refusing);
        EventQueue.invokeAndWait(() -> { });
        assertThat(toldOf).containsExactly("no request context", "no value to set");
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
