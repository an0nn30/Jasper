package dev.jasper.app.appearance;

import dev.jasper.app.config.TerminalColors;
import dev.jasper.terminal.config.Palette;
import java.util.Objects;

/**
 * The configured theme and terminal colours. The chrome is the configured theme; the terminal palette follows the
 * terminal choice, where MATCH uses the theme's own palette.
 */
record ThemeState(Theme saved, TerminalColors terminal) {
    ThemeState { Objects.requireNonNull(saved); Objects.requireNonNull(terminal); }

    static ThemeState defaults() { return new ThemeState(Theme.DARK, TerminalColors.MATCH); }

    ThemeState configure(Theme next) { return new ThemeState(Objects.requireNonNull(next), terminal); }

    ThemeState configureTerminal(TerminalColors next) { return new ThemeState(saved, Objects.requireNonNull(next)); }

    Theme choice() { return saved; }

    TerminalColors terminalChoice() { return terminal; }

    ResolvedTheme resolve() {
        Palette palette = switch (terminal) {
            case MATCH -> saved.palette();
            case LIGHT -> Palette.jasperLight();
            case DARK -> Palette.jasperDark();
        };
        return new ResolvedTheme(saved, palette);
    }
}
