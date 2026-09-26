package dev.jasper.app.appearance;

import dev.jasper.app.config.TerminalColors;
import dev.jasper.terminal.config.Palette;
import java.util.Objects;

/**
 * The configured theme and terminal colours, the latter with an optional temporary View choice. The
 * chrome is the configured theme; the terminal palette follows the terminal choice, where MATCH uses
 * the theme's own palette.
 */
record ThemeState(Theme saved, TerminalColors terminalSaved, TerminalColors terminalOverride) {
    ThemeState { Objects.requireNonNull(saved); Objects.requireNonNull(terminalSaved); }

    static ThemeState defaults() { return new ThemeState(Theme.DARK, TerminalColors.MATCH, null); }

    ThemeState configure(Theme next) {
        return new ThemeState(Objects.requireNonNull(next), terminalSaved, terminalOverride);
    }

    /** A changed saved terminal choice clears its temporary choice; rewriting the same value keeps it. */
    ThemeState configureTerminal(TerminalColors next) {
        return new ThemeState(saved, Objects.requireNonNull(next), terminalSaved == next ? terminalOverride : null);
    }

    ThemeState chooseTerminal(TerminalColors next) {
        return new ThemeState(saved, terminalSaved, Objects.requireNonNull(next));
    }

    Theme choice() { return saved; }

    TerminalColors terminalChoice() { return terminalOverride == null ? terminalSaved : terminalOverride; }

    ResolvedTheme resolve() {
        Palette palette = switch (terminalChoice()) {
            case MATCH -> saved.palette();
            case LIGHT -> Palette.jasperLight();
            case DARK -> Palette.jasperDark();
        };
        return new ResolvedTheme(saved, palette);
    }
}
