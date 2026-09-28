package dev.jasper.app.config;

import dev.jasper.app.settings.Setting;
import java.util.Map;
import java.util.Optional;

/**
 * Settings to write, and what the caller last saw for each.
 *
 * @param changes each setting's new value, or empty to restore its default by removing its key
 * @param expected for exactly the same settings, the saved value the caller last read (empty when the file did not set it)
 */
public record SettingsEdit(Map<Setting<?>, Optional<?>> changes, Map<Setting<?>, Optional<?>> expected) {
    public SettingsEdit {
        changes = Map.copyOf(changes);
        expected = Map.copyOf(expected);
        if (!changes.keySet().equals(expected.keySet()))
            throw new IllegalArgumentException("Expected values must name exactly the changed settings.");
    }
}
