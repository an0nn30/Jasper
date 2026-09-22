package dev.jasper.vault;

import dev.jasper.sdk.plugin.PluginConfig;
import java.nio.file.Path;
import java.time.Duration;

/** The three keys of {@code dev.jasper.vault.toml}, with the spec's defaults. */
record VaultSettings(Duration autoLock, Path keysDirectory, boolean bindByDefault) {
    static VaultSettings read(PluginConfig config, Path dataDirectory) {
        long minutes = config.integer("auto_lock_minutes").orElse(15);
        String keys = config.string("keys_directory").orElse("").strip();
        return new VaultSettings(Duration.ofMinutes(Math.max(0, minutes)), keys.isEmpty() ? dataDirectory.resolve("keys") : Path.of(keys),
            config.bool("bind_new_vaults_to_device").orElse(true));
    }
}
