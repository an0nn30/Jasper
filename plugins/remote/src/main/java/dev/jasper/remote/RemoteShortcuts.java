package dev.jasper.remote;

import dev.jasper.sdk.plugin.PluginConfig;
import java.util.Optional;

/** User preferences for Remote's two navigation actions; the app resolves syntax and conflicts. */
record RemoteShortcuts(Optional<String> togglePanel, Optional<String> openPalette) {
    static RemoteShortcuts read(PluginConfig config) {
        var table = config.table("shortcuts");
        return new RemoteShortcuts(binding(table, "toggle_panel", "cmd+shift+s"),
            binding(table, "open_palette", "cmd+shift+h"));
    }

    private static Optional<String> binding(Optional<PluginConfig> table, String key, String fallback) {
        String value = table.flatMap(config -> config.string(key)).orElse(fallback).strip();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }
}
