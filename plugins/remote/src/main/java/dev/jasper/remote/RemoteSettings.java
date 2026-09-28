package dev.jasper.remote;

import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;
import java.time.Duration;

/**
 * Remote's connection settings as the client uses them.
 *
 * @param connectTimeout how long to wait for the server
 * @param authTimeout how long authentication may take
 * @param keepalive the server-alive interval; zero disables it
 * @param linger how long an idle shared session stays open after its last channel
 * @param readUserKnownHosts whether {@code ~/.ssh/known_hosts} is also read
 * @param useAgent whether the SSH agent offers identities
 */
public record RemoteSettings(Duration connectTimeout, Duration authTimeout, Duration keepalive, Duration linger, boolean readUserKnownHosts, boolean useAgent) {
    /**
     * SFTP limits.
     *
     * @param maxParallelFiles files copied at once
     * @param requestTimeout how long each SFTP request may take
     */
    public record Sftp(int maxParallelFiles, Duration requestTimeout) { }

    /** Remote's declared settings; values are safe to read from any thread. */
    static final class Declared {
        private final Setting<Integer> connectTimeout, authTimeout, keepalive, linger, maxParallelFiles, requestTimeout;
        private final Setting<Boolean> readUserKnownHosts, useAgent;

        Declared(PluginSettings settings) {
            connectTimeout = seconds(settings, "connect_timeout_seconds", 10, "Connect timeout", "Seconds to wait for the server to answer.");
            authTimeout = seconds(settings, "auth_timeout_seconds", 30, "Authentication timeout", "Seconds authentication may take.");
            keepalive = seconds(settings, "keepalive_seconds", 30, "Keepalive interval", "Seconds between server-alive messages; 0 turns them off.");
            linger = seconds(settings, "session_linger_seconds", 5, "Session linger", "Seconds an idle shared session stays open after its last channel closes.");
            readUserKnownHosts = settings.declare(SettingSpec.of("read_user_known_hosts", SettingKind.bool(), true)
                .label("Read ~/.ssh/known_hosts").description("Also match host keys against your OpenSSH known_hosts file; Jasper never writes it.")
                .section("Connection").keywords("ssh", "host keys"));
            useAgent = settings.declare(SettingSpec.of("use_ssh_agent", SettingKind.bool(), true)
                .label("Use the SSH agent").description("Offer identities from SSH_AUTH_SOCK or the OpenSSH named pipe.")
                .section("Connection").keywords("ssh", "agent"));
            maxParallelFiles = settings.declare(SettingSpec.of("sftp.max_parallel_files", SettingKind.integer(1, 8), 2)
                .label("Parallel files").description("Files copied at once; at most two per endpoint.")
                .section("SFTP").keywords("sftp", "transfer"));
            requestTimeout = settings.declare(SettingSpec.of("sftp.request_timeout_seconds", SettingKind.integer(1, 300), 30)
                .label("Request timeout").description("Seconds each SFTP request may take.")
                .section("SFTP").keywords("sftp", "timeout"));
        }

        /** The current connection settings; they apply to the next connection. */
        RemoteSettings connection() {
            return new RemoteSettings(Duration.ofSeconds(connectTimeout.get()), Duration.ofSeconds(authTimeout.get()),
                Duration.ofSeconds(keepalive.get()), Duration.ofSeconds(linger.get()), readUserKnownHosts.get(), useAgent.get());
        }

        /** The current SFTP limits. */
        Sftp sftp() { return new Sftp(maxParallelFiles.get(), Duration.ofSeconds(requestTimeout.get())); }

        private static Setting<Integer> seconds(PluginSettings settings, String key, int fallback, String label, String description) {
            return settings.declare(SettingSpec.of(key, SettingKind.integer(0, 3600), fallback).label(label)
                .description(description + " Applies to the next connection.").section("Connection").keywords("ssh", "timeout"));
        }
    }
}
