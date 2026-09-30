package dev.jasper.app.appearance;

import dev.jasper.app.config.TerminalColors;
import dev.jasper.terminal.config.Palette;
import java.util.Objects;

/**
 * The configured theme and terminal colours. The chrome is the configured theme; the terminal palette follows the
 * terminal choice: MATCH uses the theme's own palette, LIGHT and DARK Jasper's, and a named choice {@code named}
 * (null when the name is unknown, which then resolves as MATCH).
 */
record ThemeState(Theme saved, TerminalColors terminal, Palette named) {
    ThemeState { Objects.requireNonNull(saved); Objects.requireNonNull(terminal); }

    static ThemeState defaults() { return new ThemeState(Theme.DARK, TerminalColors.MATCH, null); }

    ThemeState configure(Theme next) { return new ThemeState(Objects.requireNonNull(next), terminal, named); }

    ThemeState configureTerminal(TerminalColors next) { return configureTerminal(next, null); }

    /** {@code palette} is the named theme's; it is kept only for a named choice. */
    ThemeState configureTerminal(TerminalColors next, Palette palette) {
        return new ThemeState(saved, Objects.requireNonNull(next), next.named() ? palette : null);
    }

    Theme choice() { return saved; }

    TerminalColors terminalChoice() { return terminal; }

    ResolvedTheme resolve() {
        Palette palette;
        if (terminal.equals(TerminalColors.LIGHT)) palette = Palette.jasperLight();
        else if (terminal.equals(TerminalColors.DARK)) palette = Palette.jasperDark();
        else if (named != null) palette = named;
        else palette = saved.palette();
        return new ResolvedTheme(saved, palette);
    }
}
