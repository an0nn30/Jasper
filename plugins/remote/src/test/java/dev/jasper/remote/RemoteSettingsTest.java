package dev.jasper.remote;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RemoteSettingsTest {
    static final PluginInfo INFO = new PluginInfo("dev.jasper.remote", "Remote", "0.1.0", Set.of());

    private static RemoteSettings.Declared start(FakePluginHost host) {
        var declared = new AtomicReference<RemoteSettings.Declared>();
        host.start(INFO, Set.of(), Set.of(), context -> declared.set(new RemoteSettings.Declared(context.settings())));
        return declared.get();
    }

    @Test void defaultsAndOverrides() {
        try (var host = new FakePluginHost()) {
            var declared = start(host);
            assertThat(declared.connection()).isEqualTo(new RemoteSettings(Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(30), Duration.ofSeconds(5), true, true));
            assertThat(declared.sftp()).isEqualTo(new RemoteSettings.Sftp(2, Duration.ofSeconds(30)));
            assertThat(host.declared("dev.jasper.remote")).containsExactly("connect_timeout_seconds", "auth_timeout_seconds",
                "keepalive_seconds", "session_linger_seconds", "read_user_known_hosts", "use_ssh_agent",
                "sftp.max_parallel_files", "sftp.request_timeout_seconds");
        }
        try (var host = new FakePluginHost()) {
            host.setSetting("dev.jasper.remote", "connect_timeout_seconds", 3L);
            host.setSetting("dev.jasper.remote", "keepalive_seconds", 0L);
            host.setSetting("dev.jasper.remote", "session_linger_seconds", -1L);
            host.setSetting("dev.jasper.remote", "read_user_known_hosts", false);
            host.setSetting("dev.jasper.remote", "use_ssh_agent", false);
            host.setSetting("dev.jasper.remote", "sftp.max_parallel_files", 99L);
            host.setSetting("dev.jasper.remote", "sftp.request_timeout_seconds", 0L);
            var declared = start(host);
            RemoteSettings settings = declared.connection();
            assertThat(settings.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
            assertThat(settings.keepalive()).isEqualTo(Duration.ZERO);
            assertThat(settings.linger()).as("out of range: the default").isEqualTo(Duration.ofSeconds(5));
            assertThat(settings.readUserKnownHosts()).isFalse();
            assertThat(settings.useAgent()).isFalse();
            assertThat(declared.sftp()).as("out of range: the defaults").isEqualTo(new RemoteSettings.Sftp(2, Duration.ofSeconds(30)));
            assertThat(host.settingsReports("dev.jasper.remote")).extracting(line -> line.substring(0, line.indexOf(':')))
                .containsExactly("session_linger_seconds", "sftp.max_parallel_files", "sftp.request_timeout_seconds");
        }
    }
}
