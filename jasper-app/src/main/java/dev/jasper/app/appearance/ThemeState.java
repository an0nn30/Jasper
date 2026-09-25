package dev.jasper.app.appearance;

import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.TerminalColors;
import dev.jasper.terminal.config.Palette;
import java.util.Objects;

/**
 * The saved variant and terminal colours, each with an optional temporary View choice. The chrome
 * follows the variant; the terminal palette follows the terminal choice, where MATCH uses the chrome's.
 */
record ThemeState(Appearance saved, Appearance override, TerminalColors terminalSaved, TerminalColors terminalOverride) {
    ThemeState { Objects.requireNonNull(saved); Objects.requireNonNull(terminalSaved); }

    static ThemeState defaults() { return new ThemeState(Appearance.DARK, null, TerminalColors.MATCH, null); }

    /** A changed saved variant clears the temporary choice; rewriting the same value keeps it. */
    ThemeState configure(Appearance next) {
        return new ThemeState(next, saved == next ? override : null, terminalSaved, terminalOverride);
    }

    /** A changed saved terminal choice clears its temporary choice; rewriting the same value keeps it. */
    ThemeState configureTerminal(TerminalColors next) {
        return new ThemeState(saved, override, Objects.requireNonNull(next), terminalSaved == next ? terminalOverride : null);
    }

    ThemeState choose(Appearance next) {
        return new ThemeState(saved, Objects.requireNonNull(next), terminalSaved, terminalOverride);
    }

    ThemeState chooseTerminal(TerminalColors next) {
        return new ThemeState(saved, override, terminalSaved, Objects.requireNonNull(next));
    }

    Appearance choice() { return override == null ? saved : override; }

    TerminalColors terminalChoice() { return terminalOverride == null ? terminalSaved : terminalOverride; }

    ResolvedTheme resolve() {
        Theme theme = Theme.of(choice());
        Palette palette = switch (terminalChoice()) {
            case MATCH -> theme.palette();
            case LIGHT -> Theme.LIGHT.palette();
            case DARK -> Theme.DARK.palette();
        };
        return new ResolvedTheme(theme, palette);
    }
}
