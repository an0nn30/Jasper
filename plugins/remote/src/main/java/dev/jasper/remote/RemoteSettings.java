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

    public record Sftp(int maxParallelFiles,Duration requestTimeout) {}
    public static Sftp sftp(PluginConfig config) {
        var table=config.table("sftp");
        long parallel=table.map(value->value.integer("max_parallel_files").orElse(2L)).orElse(2L);
        long timeout=table.map(value->value.integer("request_timeout_seconds").orElse(30L)).orElse(30L);
        return new Sftp(Math.clamp(parallel,1,8),Duration.ofSeconds(Math.clamp(timeout,1,300)));
    }

    private static Duration seconds(PluginConfig config, String key, long fallback) {
        return Duration.ofSeconds(Math.max(0, config.integer(key).orElse(fallback)));
    }
}
