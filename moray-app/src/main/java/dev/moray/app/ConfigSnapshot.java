package dev.moray.app;

import java.util.Map;
import java.util.Objects;

/** Validated saved defaults; runtime View choices are kept separately by each owner. */
record ConfigSnapshot(int tabHeight, WindowContent.ToolbarMode toolbar, boolean statusBar,
                      float fontSize, BuiltinTheme theme, Map<String, String> keybindings) {
    ConfigSnapshot {
        if (tabHeight < 28 || tabHeight > 72) throw new IllegalArgumentException("Tab height must be 28–72.");
        if (!Float.isFinite(fontSize) || fontSize < 6 || fontSize > 72) {
            throw new IllegalArgumentException("Font size must be a finite number from 6–72.");
        }
        Objects.requireNonNull(toolbar, "toolbar");
        Objects.requireNonNull(theme, "theme");
        keybindings = Map.copyOf(keybindings);
        // A snapshot has no platform. At least one platform must accept its complete map;
        // the loader validates against the actual platform before constructing a snapshot.
        try {
            KeyBindings.withOverrides(true, keybindings);
        } catch (IllegalArgumentException macFailure) {
            try {
                KeyBindings.withOverrides(false, keybindings);
            } catch (IllegalArgumentException otherFailure) {
                throw new IllegalArgumentException("Keybindings must name known actions and valid, noncolliding shortcuts.");
            }
        }
    }

    static ConfigSnapshot defaults() {
        return new ConfigSnapshot(38, WindowContent.ToolbarMode.ICONS_AND_LABELS, true, 16f, BuiltinTheme.DARK, Map.of());
    }

    KeyBindings bindings(boolean macOs) {
        return KeyBindings.withOverrides(macOs, keybindings);
    }
}
