package dev.jasper.app;

import dev.jasper.terminal.config.Palette;
import dev.jasper.terminal.config.TerminalOptions;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Validated saved defaults; runtime View choices are kept separately by each owner. */
record ConfigSnapshot(int tabHeight, WindowContent.ToolbarMode toolbar, boolean statusBar,
                      FontConfig font, Appearance variant, Map<String, String> keybindings,
                      int columns, int lines, TerminalConfig terminal, boolean buddyEnabled,
                      boolean historyEnabled, int maxResults, List<String> trivialCommands,
                      int longCommandSeconds, boolean backgroundEnabled) {
    ConfigSnapshot {
        if (longCommandSeconds < 0 || longCommandSeconds > 3600)
            throw new IllegalArgumentException("Long-command seconds must be 0\u20133600.");
        if (maxResults < PaletteContext.MIN_MAX_RESULTS || maxResults > PaletteContext.MAX_MAX_RESULTS)
            throw new IllegalArgumentException("Max results must be 1\u201320.");
        if (tabHeight < 28 || tabHeight > 72) throw new IllegalArgumentException("Tab height must be 28–72.");
        if (columns < 5 || columns > 500) throw new IllegalArgumentException("Columns must be 5–500.");
        if (lines < 2 || lines > 200) throw new IllegalArgumentException("Lines must be 2–200.");
        Objects.requireNonNull(font, "font");
        Objects.requireNonNull(terminal, "terminal");
        Objects.requireNonNull(toolbar, "toolbar");
        Objects.requireNonNull(variant, "variant");
        keybindings = Map.copyOf(keybindings);
        trivialCommands = List.copyOf(trivialCommands);
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

    /**
     * Every constructor that predates long-command notifications and background residency: the
     * default notification threshold, and never resident, because residency is opt-in.
     */
    ConfigSnapshot(int tabHeight, WindowContent.ToolbarMode toolbar, boolean statusBar,
                   FontConfig font, Appearance variant, Map<String, String> keybindings,
                   int columns, int lines, TerminalConfig terminal, boolean buddyEnabled,
                   boolean historyEnabled, int maxResults, List<String> trivialCommands) {
        this(tabHeight, toolbar, statusBar, font, variant, keybindings, columns, lines, terminal,
            buddyEnabled, historyEnabled, maxResults, trivialCommands, 10, false);
    }

    ConfigSnapshot(int tabHeight, WindowContent.ToolbarMode toolbar, boolean statusBar,
                   FontConfig font, Appearance variant, Map<String, String> keybindings,
                   int columns, int lines, TerminalConfig terminal) {
        this(tabHeight, toolbar, statusBar, font, variant, keybindings, columns, lines, terminal, true, true);
    }

    ConfigSnapshot(int tabHeight, WindowContent.ToolbarMode toolbar, boolean statusBar,
                   FontConfig font, Appearance variant, Map<String, String> keybindings,
                   int columns, int lines, TerminalConfig terminal, boolean buddyEnabled) {
        this(tabHeight, toolbar, statusBar, font, variant, keybindings, columns, lines, terminal, buddyEnabled, true);
    }

    ConfigSnapshot(int tabHeight, WindowContent.ToolbarMode toolbar, boolean statusBar,
                   FontConfig font, Appearance variant, Map<String, String> keybindings,
                   int columns, int lines, TerminalConfig terminal, boolean buddyEnabled, boolean historyEnabled) {
        this(tabHeight, toolbar, statusBar, font, variant, keybindings, columns, lines, terminal, buddyEnabled,
            historyEnabled, PaletteContext.DEFAULT_MAX_RESULTS);
    }

    ConfigSnapshot(int tabHeight, WindowContent.ToolbarMode toolbar, boolean statusBar,
                   FontConfig font, Appearance variant, Map<String, String> keybindings,
                   int columns, int lines, TerminalConfig terminal, boolean buddyEnabled, boolean historyEnabled,
                   int maxResults) {
        this(tabHeight, toolbar, statusBar, font, variant, keybindings, columns, lines, terminal, buddyEnabled,
            historyEnabled, maxResults, ShellHistoryScope.DEFAULT_TRIVIAL);
    }

    ConfigSnapshot(int tabHeight, WindowContent.ToolbarMode toolbar, boolean statusBar,
                   float fontSize, BuiltinTheme theme, Map<String, String> keybindings) {
        this(tabHeight, toolbar, statusBar, FontConfig.defaults().withSize(fontSize), theme.appearance(),
            keybindings, 150, 45, TerminalConfig.defaults());
    }

    ConfigSnapshot(int tabHeight, WindowContent.ToolbarMode toolbar, boolean statusBar,
                   FontConfig font, BuiltinTheme theme, Map<String, String> keybindings,
                   int columns, int lines, TerminalConfig terminal) {
        this(tabHeight, toolbar, statusBar, font, theme.appearance(), keybindings, columns, lines, terminal);
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
            FontConfig.defaults(), Appearance.DARK, Map.of(), 150, 45, TerminalConfig.defaults());
    }

    KeyBindings bindings(boolean macOs) {
        return KeyBindings.withOverrides(macOs, keybindings);
    }
}
