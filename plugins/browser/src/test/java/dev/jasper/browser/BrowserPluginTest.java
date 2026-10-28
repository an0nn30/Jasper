package dev.jasper.browser;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** The plugin's part in Chromium's life, on the SDK's fake host; nothing here starts Chromium. */
class BrowserPluginTest {
    @Test void aChromiumThatCannotStartPostsOneNotificationWithTheReasonWhileThePluginRuns() {
        try (var host = new FakePluginHost()) {
            var plugin = new BrowserPlugin();
            host.start(new PluginInfo("dev.jasper.browser", "Browser", "0.1.0", Set.of("browser.provide")), Set.of(), Set.of(), plugin);
            plugin.startFailed(new IllegalStateException("Chromium did not start within 90 seconds"));
            assertThat(host.notifications().lines())
                .containsExactly("dev.jasper.browser|ERROR|The browser could not start|Chromium did not start within 90 seconds");
            host.stopAll();
            plugin.startFailed(new IllegalStateException("late"));
            assertThat(host.notifications().lines()).as("a stopped plugin only logs").hasSize(1);
            assertThat(host.failures()).isEmpty();
        }
    }
}
