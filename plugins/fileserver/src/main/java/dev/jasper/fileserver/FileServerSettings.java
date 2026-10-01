package dev.jasper.fileserver;

import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;

/** File Server's declared settings, stored in {@code plugins/dev.jasper.fileserver/dev.jasper.fileserver.toml}. */
final class FileServerSettings {
    final Setting<Boolean> confirmQuit;
    final Setting<Integer> activityEntries;

    FileServerSettings(PluginSettings settings) {
        confirmQuit = settings.declare(SettingSpec.of("confirm_quit", SettingKind.bool(), true)
            .label("Ask before quitting while servers run")
            .description("Quitting or restarting Jasper stops every file server.")
            .section("File Servers").keywords("quit", "server", "share"));
        activityEntries = settings.declare(SettingSpec.of("activity_entries", SettingKind.integer(50, 5000), 500)
            .label("Activity entries kept per server")
            .description("Older entries are dropped. Applies to servers started afterwards.")
            .section("File Servers").keywords("log", "activity", "server"));
    }
}
