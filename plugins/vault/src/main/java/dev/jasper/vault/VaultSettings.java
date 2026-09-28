package dev.jasper.vault;

import dev.jasper.sdk.settings.EditorHint;
import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;
import java.nio.file.Path;
import java.time.Duration;

/**
 * The vault's settings as the plugin uses them.
 *
 * @param autoLock idle time before the vault locks; zero never locks
 * @param keysDirectory where generated SSH keys go
 * @param bindByDefault the initial state of "Bind to this device"
 */
record VaultSettings(Duration autoLock, Path keysDirectory, boolean bindByDefault) {
    /** The three declared settings of {@code dev.jasper.vault.toml}. */
    static final class Declared {
        private final Setting<Integer> autoLockMinutes;
        private final Setting<String> keysDirectory;
        private final Setting<Boolean> bindNewVaults;

        Declared(PluginSettings settings) {
            autoLockMinutes = settings.declare(SettingSpec.of("auto_lock_minutes", SettingKind.integer(0, 1440), 15)
                .label("Lock after idle minutes")
                .description("Lock after this many minutes without keyboard or mouse activity; 0 never locks automatically.")
                .keywords("vault", "lock", "timeout"));
            keysDirectory = settings.declare(SettingSpec.of("keys_directory", SettingKind.text(), "")
                .label("Generated keys folder")
                .description("Where generated SSH keys go; empty means the keys folder in this plugin's data folder.")
                .editor(EditorHint.directory()).keywords("ssh", "keys"));
            bindNewVaults = settings.declare(SettingSpec.of("bind_new_vaults_to_device", SettingKind.bool(), true)
                .label("Bind new vaults to this device")
                .description("The initial state of \"Bind to this device\" when you create a vault."));
        }

        /** The current values; an empty keys folder resolves under {@code dataDirectory}. */
        VaultSettings read(Path dataDirectory) {
            String keys = keysDirectory.get().strip();
            return new VaultSettings(Duration.ofMinutes(autoLockMinutes.get()),
                keys.isEmpty() ? dataDirectory.resolve("keys") : Path.of(keys), bindNewVaults.get());
        }
    }
}
