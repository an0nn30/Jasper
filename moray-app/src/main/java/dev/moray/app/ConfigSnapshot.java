package dev.moray.app;

import dev.moray.terminal.Palette;
import dev.moray.terminal.TerminalOptions;

import java.util.Map;
import java.util.Objects;

/** Validated saved defaults; runtime View choices are kept separately by each owner. */
record ConfigSnapshot(int tabHeight, WindowContent.ToolbarMode toolbar, boolean statusBar,
                      FontConfig font, ColorsConfig colors, Map<String, String> keybindings,
                      int columns, int lines, TerminalConfig terminal) {
    ConfigSnapshot {
        if (tabHeight < 28 || tabHeight > 72) throw new IllegalArgumentException("Tab height must be 28–72.");
        if (columns < 5 || columns > 500) throw new IllegalArgumentException("Columns must be 5–500.");
        if (lines < 2 || lines > 200) throw new IllegalArgumentException("Lines must be 2–200.");
        Objects.requireNonNull(font, "font");
        Objects.requireNonNull(terminal, "terminal");
        Objects.requireNonNull(toolbar, "toolbar");
        Objects.requireNonNull(colors, "colors");
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

    ConfigSnapshot(int tabHeight, WindowContent.ToolbarMode toolbar, boolean statusBar,
                   float fontSize, BuiltinTheme theme, Map<String, String> keybindings) {
        this(tabHeight, toolbar, statusBar, FontConfig.defaults().withSize(fontSize), colors(theme),
            keybindings, 150, 45, TerminalConfig.defaults());
    }

    ConfigSnapshot(int tabHeight, WindowContent.ToolbarMode toolbar, boolean statusBar,
                   FontConfig font, BuiltinTheme theme, Map<String, String> keybindings,
                   int columns, int lines, TerminalConfig terminal) {
        this(tabHeight, toolbar, statusBar, font, colors(theme), keybindings, columns, lines, terminal);
    }

    private static ColorsConfig colors(BuiltinTheme theme) {
        Objects.requireNonNull(theme, "theme");
        return new ColorsConfig(theme == BuiltinTheme.LIGHT ? Appearance.LIGHT : Appearance.DARK, theme.id());
    }

    float fontSize() {
        return font.size();
    }

    TerminalOptions viewOptions(float effectiveSize, Palette effectivePalette) {
        return new TerminalOptions(font.family(), effectiveSize, font.fallback(), font.ligatures(),
            effectivePalette, terminal.cursorShape(), terminal.cursorBlink(), terminal.optionAsMeta(),
            terminal.scrollback(), terminal.copyOnSelect(), font.lineHeight(), terminal.bell());
    }

    static ConfigSnapshot defaults() {
        return new ConfigSnapshot(38, WindowContent.ToolbarMode.ICONS_AND_LABELS, true,
            FontConfig.defaults(), ColorsConfig.defaults(), Map.of(), 150, 45, TerminalConfig.defaults());
    }

    KeyBindings bindings(boolean macOs) {
        return KeyBindings.withOverrides(macOs, keybindings);
    }
}
