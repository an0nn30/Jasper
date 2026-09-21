package dev.jasper.app.appearance;

import dev.jasper.app.config.Appearance;
import java.util.Objects;

/** The saved variant plus an optional temporary View choice; both resolve to one built-in theme. */
record ThemeState(Appearance saved, Appearance override) {
    ThemeState { Objects.requireNonNull(saved); }

    static ThemeState defaults() { return new ThemeState(Appearance.DARK, null); }

    /** A changed saved variant clears the temporary choice; rewriting the same value keeps it. */
    ThemeState configure(Appearance next) { return new ThemeState(next, saved == next ? override : null); }

    ThemeState choose(Appearance next) { return new ThemeState(saved, Objects.requireNonNull(next)); }

    Appearance choice() { return override == null ? saved : override; }

    ResolvedTheme resolve() {
        BuiltinTheme theme = BuiltinTheme.of(choice());
        return new ResolvedTheme(theme, theme.palette());
    }
}
