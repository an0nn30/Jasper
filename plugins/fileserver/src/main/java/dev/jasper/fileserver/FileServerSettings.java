package dev.jasper.fileserver;

import dev.jasper.fileserver.definition.PortRange;
import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;

/** File Server's declared settings, stored in {@code plugins/dev.jasper.fileserver/dev.jasper.fileserver.toml}. */
final class FileServerSettings {
    final Setting<Boolean> confirmQuit;
    final Setting<Integer> activityEntries;
    final Setting<String> passivePorts;

    FileServerSettings(PluginSettings settings) {
        confirmQuit = settings.declare(SettingSpec.of("confirm_quit", SettingKind.bool(), true)
            .label("Ask before quitting while servers run")
            .description("Quitting or restarting Jasper stops every file server.")
            .section("File Servers").keywords("quit", "server", "share"));
        activityEntries = settings.declare(SettingSpec.of("activity_entries", SettingKind.integer(50, 5000), 500)
            .label("Activity entries kept per server")
            .description("Older entries are dropped. Applies to servers started afterwards.")
            .section("File Servers").keywords("log", "activity", "server"));
        passivePorts = settings.declare(SettingSpec.of("passive_ports", SettingKind.text(FileServerSettings::validRange,
                "Use a port range such as 50000-50100."), "50000-50100")
            .label("Default FTP passive port range")
            .description("FTP data connections use these ports unless a server sets its own range. Applies to servers started afterwards.")
            .section("File Servers").keywords("ftp", "passive", "port", "firewall"));
    }

    /** The range in force; {@link Setting#get()} falls back to the default when the file holds something invalid. */
    PortRange passiveRange() { return PortRange.parse(passivePorts.get()); }

    static boolean validRange(String text) {
        try { PortRange.parse(text); return true; }
        catch (IllegalArgumentException invalid) { return false; }
    }
}
