package dev.jasper.remote;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RemoteSettingsTest {
    static final PluginInfo INFO = new PluginInfo("dev.jasper.remote", "Remote", "0.1.0", Set.of());

    @Test void defaultsAndOverrides() {
        try (var host = new FakePluginHost()) {
            var context = host.start(INFO, Set.of(), Set.of(), c -> { });
            RemoteSettings settings = RemoteSettings.read(context.config());
            assertThat(settings).isEqualTo(new RemoteSettings(Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(30), Duration.ofSeconds(5), true, true));
        }
        try (var host = new FakePluginHost()) {
            host.setConfig("dev.jasper.remote", Map.of("connect_timeout_seconds", 3L, "keepalive_seconds", 0L, "session_linger_seconds", -1L, "read_user_known_hosts", false, "use_ssh_agent", false));
            var context = host.start(INFO, Set.of(), Set.of(), c -> { });
            RemoteSettings settings = RemoteSettings.read(context.config());
            assertThat(settings.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
            assertThat(settings.keepalive()).isEqualTo(Duration.ZERO);
            assertThat(settings.linger()).as("negative clamps to zero").isEqualTo(Duration.ZERO);
            assertThat(settings.readUserKnownHosts()).isFalse();
            assertThat(settings.useAgent()).isFalse();
        }
    }
}
