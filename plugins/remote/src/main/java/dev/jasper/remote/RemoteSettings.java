package dev.jasper.remote;

import dev.jasper.sdk.plugin.PluginConfig;
import java.time.Duration;

/** The six keys of {@code dev.jasper.remote.toml}, with the spec's defaults; negative durations clamp to zero. */
public record RemoteSettings(Duration connectTimeout, Duration authTimeout, Duration keepalive, Duration linger, boolean readUserKnownHosts, boolean useAgent) {
    public static RemoteSettings read(PluginConfig config) {
        return new RemoteSettings(seconds(config, "connect_timeout_seconds", 10), seconds(config, "auth_timeout_seconds", 30),
            seconds(config, "keepalive_seconds", 30), seconds(config, "session_linger_seconds", 5),
            config.bool("read_user_known_hosts").orElse(true), config.bool("use_ssh_agent").orElse(true));
    }

    private static Duration seconds(PluginConfig config, String key, long fallback) {
        return Duration.ofSeconds(Math.max(0, config.integer(key).orElse(fallback)));
    }
}
