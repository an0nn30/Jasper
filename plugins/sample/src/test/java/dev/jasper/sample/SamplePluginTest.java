package dev.jasper.sample;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.activity.ActivityEvent;
import dev.jasper.sdk.testing.FakePluginHost;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SamplePluginTest {
    private static final PluginInfo INFO = new PluginInfo("dev.jasper.sample", "Sample", "0.1.0", Set.of());

    @Test void isInertByDefault() {
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), new SamplePlugin());
            assertThat(host.active("dev.jasper.sample")).isTrue();
            assertThat(host.runBackground()).isZero();
            host.flush();
            assertThat(host.activityLog()).isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void runsItsDemoActivityWhenConfigured() {
        try (var host = new FakePluginHost()) {
            host.setConfig("dev.jasper.sample", Map.of("demo_activity", true, "demo_step_millis", 0L));
            host.start(INFO, Set.of(), Set.of(), new SamplePlugin());
            assertThat(host.runBackground()).isEqualTo(1);
            host.flush();
            assertThat(host.activityLog()).first().satisfies(event -> {
                assertThat(event.state()).isEqualTo(ActivityEvent.State.STARTED);
                assertThat(event.title()).isEqualTo("Sample plugin");
            });
            assertThat(host.activityLog()).last().satisfies(event -> {
                assertThat(event.state()).isEqualTo(ActivityEvent.State.SUCCEEDED);
                assertThat(event.detail()).isEqualTo("Ready");
            });
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void reportsAnOutOfRangeStepDelay() {
        try (var host = new FakePluginHost()) {
            host.setConfig("dev.jasper.sample", Map.of("demo_step_millis", 60_000L));
            host.start(INFO, Set.of(), Set.of(), new SamplePlugin());
            assertThat(host.reports()).containsExactly("dev.jasper.sample: demo_step_millis: Use 0 to 5000; using 300.");
        }
    }
}
